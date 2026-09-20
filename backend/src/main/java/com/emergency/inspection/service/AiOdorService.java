package com.emergency.inspection.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.emergency.inspection.common.BizException;
import com.emergency.inspection.entity.AiAlgorithm;
import com.emergency.inspection.entity.AiAlgorithmAlarm;
import com.emergency.inspection.entity.AiOdorReading;
import com.emergency.inspection.entity.AiOdorStation;
import com.emergency.inspection.mapper.AiAlgorithmAlarmMapper;
import com.emergency.inspection.mapper.AiOdorReadingMapper;
import com.emergency.inspection.mapper.AiOdorStationMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 臭气检测与扩散溯源:
 *  - 传感网络读数按批次刷新(风向风速同批共享,随机游走,可手动改风);
 *  - 超标判定:H2S ≥ 0.05 ppm 警告 / ≥ 0.2 ppm 严重;
 *  - 溯源:以浓度峰值按上风向回溯定源,再沿下风向做高斯烟羽式扩散模拟,
 *    产出扩散轨迹(中心线 + 宽度 + 浓度衰减)与受影响站点排序;
 *  - 超标时落一条 ODOR_TRACE 算法告警,溯源结论写入 payload。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiOdorService {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    public static final double WARN_H2S = 0.05;
    public static final double ERROR_H2S = 0.20;

    /** 读数批次有效期:过期自动刷新 */
    private static final int BATCH_STALE_SECONDS = 180;
    /** 超标告警去重窗口(分钟) */
    private static final int ALARM_DEDUPE_MINUTES = 10;
    /** 泄漏候选站点(按布设位置加权) */
    private static final String[] LEAK_CANDIDATES = {"OS-03", "OS-04", "OS-02"};

    private final AiOdorStationMapper stationMapper;
    private final AiOdorReadingMapper readingMapper;
    private final AiAlgorithmAlarmMapper alarmMapper;

    /** 区域风(随机游走,setWind 可覆盖);demo 级内存态,读数批次落库持久 */
    private double windSpeed = 2.8;
    private int windDirection = 135;
    private Double windSpeedOverride = null;
    private Integer windDirectionOverride = null;

    /* ==================== 分布图 ==================== */

    /** 臭气告警分布图:站点 + 最新读数 + 超标分级 + 区域风 */
    public Map<String, Object> map() {
        List<AiOdorStation> stations = stationMapper.selectList(
                Wrappers.<AiOdorStation>lambdaQuery().orderByAsc(AiOdorStation::getId));
        if (stations.isEmpty()) {
            throw BizException.of("臭气监测点位未初始化");
        }
        ensureFresh(stations, false);

        List<Map<String, Object>> rows = new ArrayList<>();
        for (AiOdorStation st : stations) {
            AiOdorReading r = readingMapper.selectOne(Wrappers.<AiOdorReading>lambdaQuery()
                    .eq(AiOdorReading::getStationId, st.getId())
                    .orderByDesc(AiOdorReading::getReadTime).last("limit 1"));
            if (r == null) {
                continue;
            }
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", st.getId());
            row.put("stationCode", st.getStationCode());
            row.put("stationName", st.getStationName());
            row.put("area", st.getArea());
            row.put("longitude", st.getLongitude());
            row.put("latitude", st.getLatitude());
            row.put("h2sPpm", r.getH2sPpm());
            row.put("nh3Ppm", r.getNh3Ppm());
            row.put("odorUnit", r.getOdorUnit());
            row.put("readTime", r.getReadTime());
            row.put("level", levelOf(r.getH2sPpm().doubleValue()));
            rows.add(row);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("stations", rows);
        result.put("wind", wind());
        result.put("thresholds", Map.of("warnH2sPpm", WARN_H2S, "errorH2sPpm", ERROR_H2S));
        result.put("alarmStations", rows.stream()
                .filter(r -> !"NORMAL".equals(r.get("level"))).count());
        return result;
    }

    /* ==================== 扩散模拟与溯源 ==================== */

    /**
     * 扩散溯源:浓度超标的批次里,以浓度峰值上风向回溯定源,
     * 沿下风向模拟扩散轨迹,并给出受影响站点排序;超标时落 ODOR_TRACE 告警。
     */
    public Map<String, Object> dispersion() {
        Map<String, Object> snapshot = map();
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> stations = (List<Map<String, Object>>) snapshot.get("stations");
        @SuppressWarnings("unchecked")
        Map<String, Object> wind = (Map<String, Object>) snapshot.get("wind");
        double ws = ((Number) wind.get("speed")).doubleValue();
        int wd = ((Number) wind.get("direction")).intValue();

        List<Map<String, Object>> exceed = stations.stream()
                .filter(s -> ((Number) s.get("h2sPpm")).doubleValue() >= WARN_H2S)
                .sorted(Comparator.comparingDouble((Map<String, Object> s)
                        -> ((Number) s.get("h2sPpm")).doubleValue()).reversed())
                .toList();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("active", !exceed.isEmpty());
        result.put("wind", wind);
        if (exceed.isEmpty()) {
            result.put("message", "当前各站点臭气浓度均在限值内,无需溯源");
            return result;
        }

        double maxH2s = ((Number) exceed.get(0).get("h2sPpm")).doubleValue();
        // 浓度峰值质心(取前二按超标幅度加权)作为回溯起点
        double clng = 0, clat = 0, wsum = 0;
        for (int i = 0; i < Math.min(2, exceed.size()); i++) {
            double w = ((Number) exceed.get(i).get("h2sPpm")).doubleValue() - WARN_H2S;
            clng += ((Number) exceed.get(i).get("longitude")).doubleValue() * w;
            clat += ((Number) exceed.get(i).get("latitude")).doubleValue() * w;
            wsum += w;
        }
        clng /= wsum;
        clat /= wsum;

        // 上风向回溯:浓度越高,源点离峰值越远(泄漏越猛扩散越远)
        double backtrack = Math.max(80, Math.min(600, 80 + 900 * (maxH2s - WARN_H2S)));
        double[] source = move(clng, clat, wd, backtrack);

        // 下风向高斯烟羽式轨迹:中心线浓度指数衰减,宽度随距离展开
        double length = Math.max(400, Math.min(3000, 400 + 2500 * maxH2s));
        double lambda = 500 + ws * 120;
        List<Map<String, Object>> trajectory = new ArrayList<>();
        int steps = 12;
        for (int i = 0; i < steps; i++) {
            double x = length * i / (steps - 1);
            double[] p = move(source[0], source[1], (wd + 180) % 360, x);
            Map<String, Object> pt = new LinkedHashMap<>();
            pt.put("longitude", round6(p[0]));
            pt.put("latitude", round6(p[1]));
            pt.put("downwindM", Math.round(x));
            pt.put("widthM", Math.round(2 * (15 + 0.09 * x)));
            pt.put("h2sPpm", BigDecimal.valueOf(maxH2s * Math.exp(-x / lambda))
                    .setScale(3, RoundingMode.HALF_UP).doubleValue());
            trajectory.add(pt);
        }

        // 受影响站点(按浓度排序,附到源点距离)
        List<Map<String, Object>> affected = new ArrayList<>();
        for (Map<String, Object> s : exceed) {
            Map<String, Object> a = new LinkedHashMap<>(s);
            a.put("distanceToSourceM", Math.round(distanceM(source[0], source[1],
                    ((Number) s.get("longitude")).doubleValue(),
                    ((Number) s.get("latitude")).doubleValue())));
            affected.add(a);
        }

        result.put("peakStation", exceed.get(0));
        result.put("maxH2sPpm", maxH2s);
        result.put("source", Map.of(
                "longitude", round6(source[0]),
                "latitude", round6(source[1]),
                "backtrackM", Math.round(backtrack),
                "uncertaintyM", Math.round(backtrack * 0.3)));
        result.put("trajectory", trajectory);
        result.put("affectedStations", affected);
        upsertAlarm(exceed.get(0), maxH2s, ws, wd, backtrack, trajectory.size());
        return result;
    }

    /** 手动改风:覆盖区域风并立即重算一批读数(下一次刷新沿用覆盖值) */
    public Map<String, Object> setWind(Double speed, Integer direction) {
        if (speed != null && (speed < 0 || speed > 17)) {
            throw BizException.of("风速取值 0-17 m/s");
        }
        if (direction != null && (direction < 0 || direction > 359)) {
            throw BizException.of("风向取值 0-359 度");
        }
        if (speed != null) {
            windSpeedOverride = speed;
            windSpeed = speed;
        }
        if (direction != null) {
            windDirectionOverride = direction;
            windDirection = direction;
        }
        // 改风立即重算(强制产生一次浓度波动,便于观察扩散轨迹变化)
        ensureFresh(stationMapper.selectList(Wrappers.<AiOdorStation>lambdaQuery()), true);
        return wind();
    }

    /* ==================== 内部 ==================== */

    private Map<String, Object> wind() {
        Map<String, Object> w = new LinkedHashMap<>();
        w.put("speed", BigDecimal.valueOf(windSpeed).setScale(1, RoundingMode.HALF_UP).doubleValue());
        w.put("direction", windDirection);
        w.put("directionText", compass(windDirection));
        return w;
    }

    private static String levelOf(double h2s) {
        return h2s >= ERROR_H2S ? "ERROR" : h2s >= WARN_H2S ? "WARN" : "NORMAL";
    }

    /** 读数过期则刷新一批;forceEpisode 时保证出现一次浓度波动 */
    private void ensureFresh(List<AiOdorStation> stations, boolean forceEpisode) {
        AiOdorReading latest = readingMapper.selectOne(Wrappers.<AiOdorReading>lambdaQuery()
                .orderByDesc(AiOdorReading::getReadTime).last("limit 1"));
        boolean stale = latest == null
                || latest.getReadTime().isBefore(LocalDateTime.now().minusSeconds(BATCH_STALE_SECONDS));
        if (stale || forceEpisode) {
            synthesize(stations, forceEpisode);
        }
    }

    /**
     * 合成一批读数:常态各站小幅波动;60%(或强制)出现一次泄漏事件,
     * 从候选泄漏点沿下风向按距离衰减 + 风向对齐度叠加浓度。
     */
    private void synthesize(List<AiOdorStation> stations, boolean forceEpisode) {
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        // 区域风随机游走(未被覆盖时)
        if (windSpeedOverride == null) {
            windSpeed = Math.max(0.4, Math.min(9, windSpeed + rnd.nextDouble(-0.6, 0.6)));
        }
        if (windDirectionOverride == null) {
            windDirection = (windDirection + (int) rnd.nextDouble(-25, 25) + 360) % 360;
        }

        AiOdorStation leak = null;
        double magnitude = 0;
        if (forceEpisode || rnd.nextDouble() < 0.6) {
            List<AiOdorStation> candidates = stations.stream()
                    .filter(s -> List.of(LEAK_CANDIDATES).contains(s.getStationCode())).toList();
            if (!candidates.isEmpty()) {
                leak = candidates.get(rnd.nextInt(candidates.size()));
                // 改风强制的批次给足幅度,保证改风后能看到超标与溯源;自然刷新幅度偏小
                magnitude = forceEpisode ? 0.24 + rnd.nextDouble() * 0.24 : 0.06 + rnd.nextDouble() * 0.40;
            }
        }

        LocalDateTime readTime = LocalDateTime.now().withNano(0);
        for (AiOdorStation st : stations) {
            double h2s = baseline(st.getStationCode()) + rnd.nextDouble(-0.004, 0.004);
            if (leak != null && magnitude > 0) {
                double dist = distanceM(leak.getLongitude().doubleValue(), leak.getLatitude().doubleValue(),
                        st.getLongitude().doubleValue(), st.getLatitude().doubleValue());
                double align = Math.max(0, Math.cos(Math.toRadians(bearingDiff(
                        bearing(leak.getLongitude().doubleValue(), leak.getLatitude().doubleValue(),
                                st.getLongitude().doubleValue(), st.getLatitude().doubleValue()),
                        (windDirection + 180) % 360))));
                h2s += magnitude * Math.exp(-dist / 400) * (align + 0.1);
            }
            h2s = Math.max(0.002, h2s);

            AiOdorReading r = new AiOdorReading();
            r.setStationId(st.getId());
            r.setH2sPpm(BigDecimal.valueOf(h2s).setScale(3, RoundingMode.HALF_UP));
            r.setNh3Ppm(BigDecimal.valueOf(0.15 + h2s * 3 + rnd.nextDouble() * 0.3)
                    .setScale(3, RoundingMode.HALF_UP));
            r.setOdorUnit((int) Math.round(6 + h2s * 90 + rnd.nextDouble() * 4));
            r.setWindSpeed(BigDecimal.valueOf(windSpeed).setScale(2, RoundingMode.HALF_UP));
            r.setWindDirection(windDirection);
            r.setReadTime(readTime);
            readingMapper.insert(r);
        }
        log.debug("臭气读数批次刷新: wind={}/{} m/s, leak={}, magnitude={}",
                windDirection, windSpeed, leak == null ? "无" : leak.getStationCode(), magnitude);
    }

    /** 站点基线浓度:由编码哈希得到 0.008-0.035 ppm 的稳定底噪 */
    private static double baseline(String code) {
        int h = Math.abs(code.hashCode()) % 27;
        return 0.008 + h * 0.001;
    }

    /** 超标落告警:去重窗口内不重复,溯源结论写 payload */
    private void upsertAlarm(Map<String, Object> peak, double maxH2s, double ws, int wd,
                             double backtrack, int trajectoryPoints) {
        boolean exists = alarmMapper.selectCount(Wrappers.<AiAlgorithmAlarm>lambdaQuery()
                .eq(AiAlgorithmAlarm::getAlgorithmCode, AiAlgorithm.Code.ODOR_TRACE)
                .ge(AiAlgorithmAlarm::getOccurredAt,
                        LocalDateTime.now().minusMinutes(ALARM_DEDUPE_MINUTES))) > 0;
        if (exists) {
            return;
        }
        AiAlgorithmAlarm alarm = new AiAlgorithmAlarm();
        alarm.setAlgorithmCode(AiAlgorithm.Code.ODOR_TRACE);
        String stationName = String.valueOf(peak.get("stationName"));
        alarm.setTitle(String.format("%s臭气浓度超标(H2S %.3f ppm),溯源指向%s风向 %d m",
                stationName, maxH2s, compass(wd), Math.round(backtrack)));
        alarm.setLevel(maxH2s >= ERROR_H2S ? AiAlgorithmAlarm.Level.ERROR : AiAlgorithmAlarm.Level.WARN);
        alarm.setConfidence((int) Math.min(99, 70 + 60 * (maxH2s - WARN_H2S)));
        alarm.setLongitude(BigDecimal.valueOf(((Number) peak.get("longitude")).doubleValue()));
        alarm.setLatitude(BigDecimal.valueOf(((Number) peak.get("latitude")).doubleValue()));
        alarm.setAddress("首钢园区固废处置厂·" + peak.get("area"));
        alarm.setOccurredAt(LocalDateTime.now());

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("stationName", stationName);
        payload.put("h2sPpm", maxH2s);
        payload.put("nh3Ppm", peak.get("nh3Ppm"));
        payload.put("odorUnit", peak.get("odorUnit"));
        payload.put("sourceLng", peak.get("longitude"));
        payload.put("sourceLat", peak.get("latitude"));
        payload.put("backtrackM", Math.round(backtrack));
        payload.put("trajectoryPoints", trajectoryPoints);
        payload.put("windSpeedMps", ws);
        payload.put("windDirectionDeg", wd);
        payload.put("thumbObjectKey", String.format("media/odor/%d-map.jpg", System.currentTimeMillis()));
        try {
            alarm.setPayloadJson(MAPPER.writeValueAsString(payload));
        } catch (Exception e) {
            alarm.setPayloadJson("{}");
        }
        alarmMapper.insert(alarm);
        log.info("臭气超标告警: {} H2S={} ppm", stationName, maxH2s);
    }

    /* ---------- 几何:米制近似 ---------- */

    /** 沿方位角移动(度,0=北顺时针)distanceM 米,返回新经纬度 */
    private static double[] move(double lng, double lat, double bearingDeg, double distanceM) {
        double rad = Math.toRadians(bearingDeg);
        double dEast = Math.sin(rad) * distanceM;
        double dNorth = Math.cos(rad) * distanceM;
        return new double[]{
                lng + dEast / (111320 * Math.cos(Math.toRadians(lat))),
                lat + dNorth / 111320};
    }

    /** 两点平面距离(米,纬度圈校正) */
    private static double distanceM(double lng1, double lat1, double lng2, double lat2) {
        double dx = (lng2 - lng1) * 111320 * Math.cos(Math.toRadians((lat1 + lat2) / 2));
        double dy = (lat2 - lat1) * 111320;
        return Math.sqrt(dx * dx + dy * dy);
    }

    /** 两点方位角(度,0=北顺时针) */
    private static double bearing(double lng1, double lat1, double lng2, double lat2) {
        double rad = Math.atan2(lng2 - lng1, lat2 - lat1);
        return (Math.toDegrees(rad) + 360) % 360;
    }

    private static double bearingDiff(double a, double b) {
        return Math.abs(((a - b) % 360 + 540) % 360 - 180);
    }

    /** 8 方位中文 */
    private static String compass(int deg) {
        String[] names = {"北", "东北", "东", "东南", "南", "西南", "西", "西北"};
        return names[(int) Math.floor(((deg % 360) + 22.5) / 45) % 8];
    }

    private static double round6(double v) {
        return BigDecimal.valueOf(v).setScale(6, RoundingMode.HALF_UP).doubleValue();
    }
}
