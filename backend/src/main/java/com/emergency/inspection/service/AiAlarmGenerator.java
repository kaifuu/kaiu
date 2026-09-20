package com.emergency.inspection.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.emergency.inspection.entity.AiAlgorithm;
import com.emergency.inspection.entity.AiAlgorithmAlarm;
import com.emergency.inspection.entity.DeviceOsd;
import com.emergency.inspection.entity.WaylineJob;
import com.emergency.inspection.mapper.AiAlgorithmAlarmMapper;
import com.emergency.inspection.mapper.AiAlgorithmMapper;
import com.emergency.inspection.mapper.DeviceOsdMapper;
import com.emergency.inspection.mapper.WaylineJobMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 算法识别生成器:按算法编码合成一条识别命中告警。
 *
 * 平台侧算法(视频流 / 传感数据分析)没有真实 CV 推理可接,这里以
 * 「航线任务执行中自动识别 + 控制台手动执行」两个入口驱动,
 * 命中结果的字段结构与真实算法输出对齐(高温点 / 烟羽颜色 / 泄漏类型 / 取证录像)。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AiAlarmGenerator {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final DateTimeFormatter DIR_FMT = DateTimeFormatter.ofPattern("yyyyMMdd");

    /** 演示厂区锚点:首钢园区固废处置厂 */
    private static final double ANCHOR_LNG = 116.1836;
    private static final double ANCHOR_LAT = 39.9132;

    /** 飞行中同一算法的识别节流窗口(ms) */
    private static final long FLIGHT_DEDUPE_MS = 120_000;

    private final AiAlgorithmAlarmMapper alarmMapper;
    private final AiAlgorithmMapper algorithmMapper;
    private final WaylineJobMapper jobMapper;
    private final DeviceOsdMapper osdMapper;

    /**
     * 航线任务执行中的自动识别:有 RUNNING 任务时,对每个启用算法
     * (臭气除外——它由站点读数驱动)按节流窗口生成命中。
     */
    @Scheduled(fixedDelay = 20_000, initialDelay = 25_000)
    public void detectDuringFlight() {
        try {
            WaylineJob running = jobMapper.selectOne(Wrappers.<WaylineJob>lambdaQuery()
                    .eq(WaylineJob::getStatus, WaylineJob.Status.RUNNING)
                    .orderByDesc(WaylineJob::getId).last("limit 1"));
            if (running == null) {
                return;
            }
            double[] pos = dronePosition(running.getDroneSn());
            for (AiAlgorithm algo : algorithmMapper.selectList(Wrappers.<AiAlgorithm>lambdaQuery()
                    .eq(AiAlgorithm::getEnabled, true))) {
                if (algo.getCode() == AiAlgorithm.Code.ODOR_TRACE) {
                    continue;
                }
                if (alarmMapper.selectCount(Wrappers.<AiAlgorithmAlarm>lambdaQuery()
                        .eq(AiAlgorithmAlarm::getAlgorithmCode, algo.getCode())
                        .ge(AiAlgorithmAlarm::getOccurredAt,
                                LocalDateTime.now().minusSeconds(FLIGHT_DEDUPE_MS / 1000))) > 0) {
                    continue;
                }
                generate(algo, pos, running);
            }
        } catch (Exception e) {
            // 识别模拟失败不影响主流程,下个周期重试
            log.warn("飞行中算法识别生成失败: {}", e.getMessage());
        }
    }

    /**
     * 合成一次识别命中。pos 为识别发生位置(经纬度),为空时落到厂区锚点附近。
     */
    public AiAlgorithmAlarm generate(AiAlgorithm algo, double[] pos, WaylineJob job) {
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        AiAlgorithmAlarm alarm = new AiAlgorithmAlarm();
        alarm.setAlgorithmCode(algo.getCode());
        alarm.setOccurredAt(LocalDateTime.now());
        alarm.setStatus(AiAlgorithmAlarm.Status.PENDING);
        alarm.setConfidence(Math.min(99, algo.getConfidenceValue() + rnd.nextInt(0, 16)));

        if (job != null) {
            alarm.setDeviceSn(job.getDroneSn() != null ? job.getDroneSn() : job.getDockSn());
            alarm.setDockName(job.getDockSn());
            alarm.setFlightId(job.getFlightId());
        }
        double lng = (pos != null ? pos[0] : ANCHOR_LNG) + jitter();
        double lat = (pos != null ? pos[1] : ANCHOR_LAT) + jitter();
        alarm.setLongitude(BigDecimal.valueOf(lng).setScale(6, java.math.RoundingMode.HALF_UP));
        alarm.setLatitude(BigDecimal.valueOf(lat).setScale(6, java.math.RoundingMode.HALF_UP));

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("thumbObjectKey", thumbKey(alarm.getDeviceSn()));
        boolean severe = false;
        String zone;
        switch (algo.getCode()) {
            case SMOKE_FIRE -> severe = buildSmokeFire(alarm, payload, rnd);
            case ILLEGAL_DUMP -> severe = buildIllegalDump(alarm, payload, rnd);
            case COVER_MEMBRANE -> severe = buildCoverMembrane(alarm, payload, rnd);
            case CHIMNEY_EMISSION -> severe = buildChimneyEmission(alarm, payload, rnd);
            case LEAK_DETECT -> severe = buildLeakDetect(alarm, payload, rnd);
            // 臭气算法由 AiOdorService 生成(站点读数 + 溯源),不在此合成
            default -> {
                alarm.setTitle(algo.getName() + "识别命中");
            }
        }
        zone = str(payload, "leakLocation", "chimneyId", "membraneZone", "dumpType");
        alarm.setAddress("首钢园区固废处置厂" + (zone == null ? "" : "·" + zone));
        // 等级:算法配置为基线,严重命中(明火/危废/可燃泄漏/黑烟等)升为 ERROR
        alarm.setLevel(severe ? AiAlgorithmAlarm.Level.ERROR
                : AiAlgorithmAlarm.Level.valueOf(algo.getAlarmLevel().name()));
        if (Boolean.TRUE.equals(algo.getVideoRecord())) {
            alarm.setVideoObjectKey(videoKey(alarm.getDeviceSn(), algo.getCode()));
            alarm.setVideoSeconds(rnd.nextInt(15, 90));
            payload.put("videoAction", "命中后自动录像取证");
        }
        alarm.setPayloadJson(toJson(payload));
        alarmMapper.insert(alarm);
        log.info("算法识别命中: {} -> {} ({})", algo.getCode(), alarm.getTitle(), alarm.getLevel());
        return alarm;
    }

    /* ==================== 各算法命中模型 ==================== */

    /** 厂区烟火识别与异常高温点定位:可见光识别火焰/烟雾 + 红外定位高温点 */
    private boolean buildSmokeFire(AiAlgorithmAlarm alarm, Map<String, Object> payload, ThreadLocalRandom rnd) {
        boolean flame = rnd.nextBoolean();
        int maxTemp = flame ? rnd.nextInt(320, 680) : rnd.nextInt(45, 120);
        payload.put("fireType", flame ? "FLAME" : "SMOKE");
        payload.put("irMaxTempC", maxTemp);
        payload.put("ambientTempC", 18 + rnd.nextInt(4, 14));
        payload.put("tempPointLng", alarm.getLongitude().doubleValue() + jitter() / 3);
        payload.put("tempPointLat", alarm.getLatitude().doubleValue() + jitter() / 3);
        alarm.setTitle(flame
                ? String.format("识别到明火,红外定位异常高温点 %d℃", maxTemp)
                : String.format("识别到烟雾弥漫(红外最高 %d℃),疑似初期阴燃", maxTemp));
        return flame || maxTemp >= 90;
    }

    /** 非法倾倒识别:倾倒类型 / 面积 / 涉案车辆 */
    private boolean buildIllegalDump(AiAlgorithmAlarm alarm, Map<String, Object> payload, ThreadLocalRandom rnd) {
        String[] types = {"CONSTRUCTION_WASTE", "INDUSTRIAL_SOLID", "HAZARDOUS"};
        String[] labels = {"建筑垃圾", "工业固废", "危险废物"};
        int idx = rnd.nextInt(types.length);
        int area = rnd.nextInt(5, 90);
        payload.put("dumpType", labels[idx]);
        payload.put("areaM2", area);
        payload.put("dwellMinutes", rnd.nextInt(5, 55));
        if (rnd.nextBoolean()) {
            payload.put("vehiclePlate", "京A" + (10000 + rnd.nextInt(90000)));
        }
        alarm.setTitle(String.format("识别到非法倾倒(%s),面积约 %d ㎡", labels[idx], area));
        return "危险废物".equals(labels[idx]);
    }

    /** 覆盖膜异常状态识别:填埋库区覆盖膜破损 / 翘起 / 积水 / 覆土外露 */
    private boolean buildCoverMembrane(AiAlgorithmAlarm alarm, Map<String, Object> payload, ThreadLocalRandom rnd) {
        String[] anomalies = {"破损", "翘起移位", "积水下陷", "覆土外露"};
        String[] zones = {"填埋一区", "填埋二区", "应急调节池"};
        String anomaly = anomalies[rnd.nextInt(anomalies.length)];
        String zone = zones[rnd.nextInt(zones.length)];
        int area = rnd.nextInt(8, 240);
        payload.put("anomalyType", anomaly);
        payload.put("membraneZone", zone);
        payload.put("areaM2", area);
        alarm.setTitle(String.format("填埋库区覆盖膜%s(约 %d ㎡)", anomaly, area));
        return area >= 150;
    }

    /** 烟囱排放视觉监测:白/黑/灰/紫/浓黄烟羽分级 + 自动录像与时间记录 */
    private boolean buildChimneyEmission(AiAlgorithmAlarm alarm, Map<String, Object> payload, ThreadLocalRandom rnd) {
        String[] colors = {"白烟", "灰烟", "黑烟", "紫烟", "浓黄烟"};
        String[] chimneys = {"1#排气筒", "2#排气筒", "除臭系统排气筒"};
        String color = colors[rnd.nextInt(colors.length)];
        String chimney = chimneys[rnd.nextInt(chimneys.length)];
        int opacity = switch (color) {
            case "白烟" -> rnd.nextInt(10, 40);
            case "灰烟" -> rnd.nextInt(35, 65);
            case "紫烟" -> rnd.nextInt(50, 80);
            default -> rnd.nextInt(65, 96);   // 黑烟 / 浓黄烟
        };
        payload.put("smokeColor", color);
        payload.put("opacityPercent", opacity);
        payload.put("plumeHeightM", rnd.nextInt(8, 60));
        payload.put("chimneyId", chimney);
        payload.put("recordedAt", alarm.getOccurredAt().withNano(0).toString());
        alarm.setTitle(String.format("%s排放%s,不透光度 %d%%", chimney, color, opacity));
        return "黑烟".equals(color) || "浓黄烟".equals(color) || "紫烟".equals(color);
    }

    /** 厂区泄漏检测:路面含盐废水 / 围堰渗漏 / 可燃废液罐区 */
    private boolean buildLeakDetect(AiAlgorithmAlarm alarm, Map<String, Object> payload, ThreadLocalRandom rnd) {
        String[] types = {"路面含盐废水", "罐区围堰渗漏", "除臭系统围堰渗漏", "可燃废液罐区泄漏"};
        String type = types[rnd.nextInt(types.length)];
        int area = rnd.nextInt(2, 60);
        payload.put("leakType", type);
        payload.put("spreadAreaM2", area);
        if (type.contains("罐区泄漏")) {
            payload.put("flowRateM3h", 0.2 + rnd.nextDouble() * 2.5);
        }
        alarm.setTitle(String.format("识别到泄漏(%s),扩散面积约 %d ㎡", type, area));
        return type.contains("可燃");
    }

    /* ==================== 内部 ==================== */

    /** 飞行器最新遥测位置;无遥测时返回 null(命中落到厂区锚点附近) */
    private double[] dronePosition(String droneSn) {
        if (droneSn == null || droneSn.isBlank()) {
            return null;
        }
        DeviceOsd osd = osdMapper.selectOne(Wrappers.<DeviceOsd>lambdaQuery()
                .eq(DeviceOsd::getDeviceSn, droneSn)
                .orderByDesc(DeviceOsd::getId).last("limit 1"));
        if (osd == null || osd.getLongitude() == null || osd.getLatitude() == null) {
            return null;
        }
        return new double[]{osd.getLongitude().doubleValue(), osd.getLatitude().doubleValue()};
    }

    /** ±0.004 度抖动(约 ±350m)模拟识别位置散布 */
    private static double jitter() {
        return ThreadLocalRandom.current().nextDouble(-0.004, 0.004);
    }

    private static String str(Map<String, Object> payload, String... keys) {
        for (String key : keys) {
            Object v = payload.get(key);
            if (v != null) {
                return String.valueOf(v);
            }
        }
        return null;
    }

    private static String thumbKey(String deviceSn) {
        String sn = deviceSn == null ? "manual" : deviceSn;
        return String.format("media/%s/algo/%s/%d-thumb.jpg",
                sn, DIR_FMT.format(LocalDateTime.now()), System.currentTimeMillis());
    }

    private static String videoKey(String deviceSn, AiAlgorithm.Code code) {
        String sn = deviceSn == null ? "manual" : deviceSn;
        return String.format("media/%s/algo/%s/%d-%s.mp4",
                sn, DIR_FMT.format(LocalDateTime.now()), System.currentTimeMillis(),
                code.name().toLowerCase());
    }

    private static String toJson(Map<String, Object> payload) {
        try {
            return MAPPER.writeValueAsString(payload);
        } catch (Exception e) {
            return "{}";
        }
    }
}
