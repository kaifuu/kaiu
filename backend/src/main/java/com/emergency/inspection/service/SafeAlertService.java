package com.emergency.inspection.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.emergency.inspection.entity.GeoFence;
import com.emergency.inspection.entity.SafeAlert;
import com.emergency.inspection.mapper.GeoFenceMapper;
import com.emergency.inspection.mapper.SafeAlertMapper;
import com.emergency.inspection.utils.GeoUtils;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 飞行安全预警规则引擎(移植自 10_WRJ ThreatService 思路,适配大疆 OSD):
 * 每帧遥喂入内存环形缓冲,规则 = 围栏实时闯入 / 60s 线性外推预测闯入 / 电量骤降 / 高度突变。
 * 命中即落 safe_alert 表并进入冷却期,避免重复刷屏。阈值全部 application.yml 可配。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SafeAlertService {

    private static final int TRACK_CAP = 160;          // 0.5Hz × 160 帧 ≈ 5 分钟窗口
    private static final long TRACK_STALE_MS = 120_000;

    private final SafeAlertMapper alertMapper;
    private final GeoFenceMapper fenceMapper;
    private final ObjectMapper objectMapper;

    @Value("${safe-alert.predict-horizon-seconds:60}")
    private int predictHorizon;
    @Value("${safe-alert.predict-steps:8}")
    private int predictSteps;
    @Value("${safe-alert.predict-cooldown-ms:300000}")
    private long predictCooldown;
    @Value("${safe-alert.breach-cooldown-ms:300000}")
    private long breachCooldown;
    @Value("${safe-alert.battery-window-seconds:300}")
    private int batteryWindow;
    @Value("${safe-alert.battery-drop-percent:15}")
    private int batteryDrop;
    @Value("${safe-alert.battery-cooldown-ms:600000}")
    private long batteryCooldown;
    @Value("${safe-alert.altitude-jump-meters:40}")
    private double altitudeJump;
    @Value("${safe-alert.altitude-cooldown-ms:300000}")
    private long altitudeCooldown;

    /** 每机遥测环形缓冲(sn → 按时间升序) */
    private final Map<String, Deque<Sample>> tracks = new ConcurrentHashMap<>();
    /** 告警冷却(sn:type → 上次触发时刻) */
    private final Map<String, Long> cooldowns = new ConcurrentHashMap<>();

    /** OSD 入口:机场(无经纬度)直接忽略,飞行器入库轨迹并跑规则 */
    public void onOsd(String sn, JsonNode osd) {
        JsonNode lngN = osd.get("longitude"), latN = osd.get("latitude");
        if (lngN == null || latN == null || lngN.isNull() || latN.isNull()) {
            return;
        }
        Sample s = new Sample();
        s.t = System.currentTimeMillis();
        s.lng = lngN.asDouble();
        s.lat = latN.asDouble();
        s.alt = osd.path("height").asDouble(0);
        s.battery = osd.path("battery").path("capacity_percent").asInt(-1);

        Deque<Sample> q = tracks.computeIfAbsent(sn, k -> new ArrayDeque<>());
        synchronized (q) {
            // 停更两分钟即视为新一段飞行,清掉旧轨迹避免跨任务差分
            Sample last = q.peekLast();
            if (last != null && s.t - last.t > TRACK_STALE_MS) {
                q.clear();
            }
            q.addLast(s);
            while (q.size() > TRACK_CAP) {
                q.removeFirst();
            }
        }
        try {
            checkFence(sn, s, q);
            checkBattery(sn, s, q);
            checkAltitude(sn, s, q);
        } catch (Exception e) {
            log.warn("安全预警规则执行异常: {}", e.getMessage());
        }
    }

    /* ---------- 围栏:实时闯入 + 轨迹外推预测 ---------- */

    private void checkFence(String sn, Sample s, Deque<Sample> q) {
        List<GeoFence> fences = enabledFences();
        if (fences.isEmpty()) {
            return;
        }
        for (GeoFence fence : fences) {
            if (fence.getFenceType() != GeoFence.FenceType.NO_FLY) {
                continue;
            }
            if (GeoUtils.contains(fence, s.lng, s.lat, points(fence))) {
                if (cooldown(String.format("%s:FENCE_BREACH:%d", sn, fence.getId()), breachCooldown)) {
                    raise(SafeAlert.AlertType.FENCE_BREACH, SafeAlert.Level.ERROR, sn,
                            "闯入禁飞区",
                            String.format("当前位置已进入禁飞区「%s」,请立即返航或调整航向", fence.getName()),
                            s);
                }
                return;
            }
        }
        // 预测:与 ≥3s 前样本差分求速度,按 horizon 秒分步外推
        double[] v = velocity(q, s);
        if (v == null) {
            return;
        }
        double step = (double) predictHorizon / predictSteps;
        for (GeoFence fence : fences) {
            if (fence.getFenceType() != GeoFence.FenceType.NO_FLY) {
                continue;
            }
            List<Map<String, Object>> pts = points(fence);
            for (int i = 1; i <= predictSteps; i++) {
                double[] p = GeoUtils.extrapolate(s.lng, s.lat, v[0], v[1], step * i);
                if (GeoUtils.contains(fence, p[0], p[1], pts)) {
                    if (cooldown(String.format("%s:PREDICTED_BREACH:%d", sn, fence.getId()), predictCooldown)) {
                        raise(SafeAlert.AlertType.PREDICTED_BREACH, SafeAlert.Level.WARN, sn,
                                "预测闯入禁飞区",
                                String.format("按当前航速外推 %d 秒后将进入禁飞区「%s」,建议立即调整航向",
                                        (int) (step * i), fence.getName()),
                                s);
                    }
                    return;
                }
            }
        }
    }

    /* ---------- 电量骤降 ---------- */

    private void checkBattery(String sn, Sample s, Deque<Sample> q) {
        if (s.battery < 0) {
            return;
        }
        Sample head = windowHead(q, s.t - batteryWindow * 1000L);
        if (head == null || head.battery < 0) {
            return;
        }
        int drop = head.battery - s.battery;
        if (drop >= batteryDrop && s.battery > 20
                && cooldown(sn + ":BATTERY_ANOMALY", batteryCooldown)) {
            raise(SafeAlert.AlertType.BATTERY_ANOMALY, SafeAlert.Level.WARN, sn,
                    "电量骤降预警",
                    String.format("%d 秒内电量下降 %d%%(当前 %d%%),超出正常巡检消耗速率",
                            batteryWindow, drop, s.battery),
                    s);
        }
    }

    /* ---------- 高度突变 ---------- */

    private void checkAltitude(String sn, Sample s, Deque<Sample> q) {
        Sample prev = prevSample(q, s.t);
        if (prev == null) {
            return;
        }
        double jump = Math.abs(s.alt - prev.alt);
        if (jump >= altitudeJump && cooldown(sn + ":ALTITUDE_JUMP", altitudeCooldown)) {
            raise(SafeAlert.AlertType.ALTITUDE_JUMP, SafeAlert.Level.WARN, sn,
                    "高度突变预警",
                    String.format("相邻遥测高度跳变 %.0f 米(%.0f → %.0f),疑似气压计异常或强上升气流",
                            jump, prev.alt, s.alt),
                    s);
        }
    }

    /* ---------- 工具 ---------- */

    /** 与 ≥3s 前的最近样本差分,求度/秒速度(避开单帧抖动);样本不足返回 null */
    private double[] velocity(Deque<Sample> q, Sample now) {
        Sample ref = null;
        synchronized (q) {
            for (Sample s : q) {
                if (now.t - s.t >= 3000) {
                    ref = s;
                }
            }
        }
        if (ref == null) {
            return null;
        }
        double dt = (now.t - ref.t) / 1000.0;
        if (dt <= 0) {
            return null;
        }
        return new double[]{(now.lng - ref.lng) / dt, (now.lat - ref.lat) / dt};
    }

    private Sample prevSample(Deque<Sample> q, long nowT) {
        synchronized (q) {
            if (q.size() < 2) {
                return null;
            }
            Sample prev = null;
            for (Sample s : q) {
                if (s.t < nowT) {
                    prev = s;
                }
            }
            return prev;
        }
    }

    /** 窗口起点:最早满足 t ≥ since 的样本 */
    private Sample windowHead(Deque<Sample> q, long since) {
        synchronized (q) {
            for (Sample s : q) {
                if (s.t >= since) {
                    return s;
                }
            }
        }
        return null;
    }

    /** 冷却闸门:冷却期内返回 false,否则记录本次时刻并放行 */
    private boolean cooldown(String key, long ms) {
        long now = System.currentTimeMillis();
        Long last = cooldowns.get(key);
        if (last != null && now - last < ms) {
            return false;
        }
        if (cooldowns.size() > 500) {
            cooldowns.clear();
        }
        cooldowns.put(key, now);
        return true;
    }

    private List<GeoFence> enabledFences() {
        return fenceMapper.selectList(Wrappers.<GeoFence>lambdaQuery().eq(GeoFence::getEnabled, true));
    }

    private List<Map<String, Object>> points(GeoFence fence) {
        try {
            return objectMapper.readValue(fence.getPointsJson(), new TypeReference<>() {
            });
        } catch (Exception e) {
            return List.of();
        }
    }

    private void raise(SafeAlert.AlertType type, SafeAlert.Level level, String sn,
                       String title, String message, Sample s) {
        SafeAlert alert = new SafeAlert();
        alert.setAlertType(type);
        alert.setLevel(level);
        alert.setDeviceSn(sn);
        alert.setTitle(title);
        alert.setMessage(message);
        alert.setLongitude(BigDecimal.valueOf(s.lng).setScale(6, java.math.RoundingMode.HALF_UP));
        alert.setLatitude(BigDecimal.valueOf(s.lat).setScale(6, java.math.RoundingMode.HALF_UP));
        alert.setHeight(BigDecimal.valueOf(s.alt).setScale(1, java.math.RoundingMode.HALF_UP));
        alert.setOccurredAt(LocalDateTime.now());
        alert.setStatus(SafeAlert.Status.PENDING);
        alertMapper.insert(alert);
        log.info("安全预警: [{}] {} {} ({})", level, title, sn, message);
    }

    /** 一帧飞行遥测 */
    private static final class Sample {
        long t;
        double lng, lat, alt;
        int battery;
    }
}
