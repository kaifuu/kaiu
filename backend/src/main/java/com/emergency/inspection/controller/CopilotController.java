package com.emergency.inspection.controller;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.emergency.inspection.common.ApiResponse;
import com.emergency.inspection.entity.Device;
import com.emergency.inspection.entity.EmergencyEvent;
import com.emergency.inspection.entity.GeoFence;
import com.emergency.inspection.entity.Hazard;
import com.emergency.inspection.entity.InspectTask;
import com.emergency.inspection.entity.SafeAlert;
import com.emergency.inspection.mapper.DeviceMapper;
import com.emergency.inspection.mapper.EmergencyEventMapper;
import com.emergency.inspection.mapper.GeoFenceMapper;
import com.emergency.inspection.mapper.HazardMapper;
import com.emergency.inspection.mapper.InspectTaskMapper;
import com.emergency.inspection.mapper.SafeAlertMapper;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * AI 值班助手(规则版,离线可用):关键词意图识别 → 查库组装中文短回复。
 * 不接外部大模型,适合内网/演示环境;route 为当前页面路径,用于「今天怎么样」这类模糊问题按页面语境作答。
 */
@RestController
@RequestMapping("/api/copilot")
@RequiredArgsConstructor
public class CopilotController {

    private final InspectTaskMapper taskMapper;
    private final HazardMapper hazardMapper;
    private final EmergencyEventMapper eventMapper;
    private final DeviceMapper deviceMapper;
    private final SafeAlertMapper safeAlertMapper;
    private final GeoFenceMapper fenceMapper;

    @PostMapping("/ask")
    public ApiResponse<Map<String, Object>> ask(@RequestBody AskForm form) {
        String q = form.getQ() == null ? "" : form.getQ().trim();
        String route = form.getRoute() == null ? "" : form.getRoute();
        String intent = intentOf(q, route);
        String answer = switch (intent) {
            case "task" -> answerTask();
            case "hazard" -> answerHazard();
            case "event" -> answerEvent();
            case "device" -> answerDevice();
            case "safeAlert" -> answerSafeAlert();
            case "fence" -> answerFence();
            case "overview" -> answerOverview();
            default -> answerHelp();
        };
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("intent", intent);
        out.put("answer", answer);
        return ApiResponse.ok(out);
    }

    @GetMapping("/suggest")
    public ApiResponse<List<String>> suggest() {
        return ApiResponse.ok(List.of(
                "今天有多少巡检任务?",
                "当前有什么隐患?",
                "应急事件进展如何?",
                "机场和无人机在线情况",
                "最近有飞行安全预警吗?",
                "电子围栏有哪些?",
                "你能做什么?"));
    }

    /* ---------- 意图识别 ---------- */

    private String intentOf(String q, String route) {
        if (q.contains("任务") || q.contains("巡检") || route.startsWith("/tasks")) {
            return "task";
        }
        if (q.contains("隐患") || route.startsWith("/hazards")) {
            return "hazard";
        }
        if (q.contains("事件") || q.contains("应急") || route.startsWith("/events")) {
            return "event";
        }
        if (q.contains("机场") || q.contains("无人机") || q.contains("设备")
                || route.startsWith("/docks") || route.startsWith("/drones")) {
            return "device";
        }
        if (q.contains("预警") || q.contains("安全") || route.startsWith("/safe-alerts")) {
            return "safeAlert";
        }
        if (q.contains("围栏") || q.contains("禁飞") || route.startsWith("/fences")) {
            return "fence";
        }
        if (q.contains("今天怎么样") || q.contains("总体") || q.contains("概况") || q.contains("情况怎么样")) {
            return "overview";
        }
        return "help";
    }

    /* ---------- 各域作答 ---------- */

    private String answerTask() {
        long today = taskMapper.selectCount(Wrappers.<InspectTask>lambdaQuery()
                .between(InspectTask::getPlanStart, LocalDate.now().atStartOfDay(),
                        LocalDate.now().atTime(LocalTime.MAX)));
        long running = taskMapper.selectCount(Wrappers.<InspectTask>lambdaQuery()
                .eq(InspectTask::getStatus, InspectTask.Status.RUNNING));
        long pending = taskMapper.selectCount(Wrappers.<InspectTask>lambdaQuery()
                .eq(InspectTask::getStatus, InspectTask.Status.PENDING));
        long overdue = taskMapper.selectCount(Wrappers.<InspectTask>lambdaQuery()
                .eq(InspectTask::getStatus, InspectTask.Status.OVERDUE));
        InspectTask latest = taskMapper.selectOne(Wrappers.<InspectTask>lambdaQuery()
                .orderByDesc(InspectTask::getId).last("limit 1"));
        String head = String.format("今日计划任务 %d 个:执行中 %d、待执行 %d、已逾期 %d。",
                today, running, pending, overdue);
        if (overdue > 0) {
            head += " 存在逾期任务,建议优先处理。";
        }
        if (latest != null) {
            head += String.format("%n最近一条:%s(%s)。", latest.getName(),
                    latest.getPointName() == null ? "无点位" : latest.getPointName());
        }
        return head;
    }

    private String answerHazard() {
        long pending = hazardMapper.selectCount(Wrappers.<Hazard>lambdaQuery()
                .eq(Hazard::getStatus, Hazard.Status.PENDING));
        long processing = hazardMapper.selectCount(Wrappers.<Hazard>lambdaQuery()
                .eq(Hazard::getStatus, Hazard.Status.PROCESSING));
        Hazard latest = hazardMapper.selectOne(Wrappers.<Hazard>lambdaQuery()
                .orderByDesc(Hazard::getReportTime).last("limit 1"));
        String head = String.format("当前隐患:待处理 %d 起、整改中 %d 起。", pending, processing);
        if (latest != null) {
            head += String.format("%n最新上报:「%s」(%s)。", latest.getTitle(), latest.getPointName());
        }
        return head;
    }

    private String answerEvent() {
        long active = eventMapper.selectCount(Wrappers.<EmergencyEvent>lambdaQuery()
                .in(EmergencyEvent::getStatus, List.of(EmergencyEvent.Status.PENDING, EmergencyEvent.Status.RESPONDING)));
        EmergencyEvent latest = eventMapper.selectOne(Wrappers.<EmergencyEvent>lambdaQuery()
                .orderByDesc(EmergencyEvent::getOccurTime).last("limit 1"));
        String head = String.format("进行中的应急事件 %d 起。", active);
        if (latest != null) {
            head += String.format("%n最近事件:「%s」,%s。", latest.getTitle(), latest.getAddress());
        }
        return head;
    }

    private String answerDevice() {
        long dockTotal = deviceMapper.selectCount(Wrappers.<Device>lambdaQuery()
                .eq(Device::getDeviceType, Device.DeviceType.DOCK));
        long dockOnline = deviceMapper.selectCount(Wrappers.<Device>lambdaQuery()
                .eq(Device::getDeviceType, Device.DeviceType.DOCK).eq(Device::getStatus, Device.Status.ONLINE));
        long droneTotal = deviceMapper.selectCount(Wrappers.<Device>lambdaQuery()
                .eq(Device::getDeviceType, Device.DeviceType.DRONE));
        long droneOnline = deviceMapper.selectCount(Wrappers.<Device>lambdaQuery()
                .eq(Device::getDeviceType, Device.DeviceType.DRONE).eq(Device::getStatus, Device.Status.ONLINE));
        return String.format("机场 %d/%d 在线,无人机 %d/%d 在线。",
                dockOnline, dockTotal, droneOnline, droneTotal);
    }

    private String answerSafeAlert() {
        long pending = safeAlertMapper.selectCount(Wrappers.<SafeAlert>lambdaQuery()
                .eq(SafeAlert::getStatus, SafeAlert.Status.PENDING));
        SafeAlert latest = safeAlertMapper.selectOne(Wrappers.<SafeAlert>lambdaQuery()
                .orderByDesc(SafeAlert::getOccurredAt).last("limit 1"));
        String head = String.format("待处理飞行安全预警 %d 条。", pending);
        if (latest != null) {
            head += String.format("%n最近:%s — %s(%s)。", latest.getTitle(), latest.getMessage(), latest.getDeviceSn());
        }
        return head;
    }

    private String answerFence() {
        List<GeoFence> fences = fenceMapper.selectList(Wrappers.<GeoFence>lambdaQuery().eq(GeoFence::getEnabled, true));
        long noFly = fences.stream().filter(f -> f.getFenceType() == GeoFence.FenceType.NO_FLY).count();
        long limit = fences.stream().filter(f -> f.getFenceType() == GeoFence.FenceType.LIMIT).count();
        long work = fences.stream().filter(f -> f.getFenceType() == GeoFence.FenceType.WORK).count();
        String head = String.format("启用中的围栏 %d 条:禁飞区 %d、限飞区 %d、作业区 %d。",
                fences.size(), noFly, limit, work);
        if (!fences.isEmpty()) {
            head += String.format("%n包括:" + String.join("、",
                    fences.stream().map(GeoFence::getName).limit(5).toList()) + "。");
        }
        return head;
    }

    private String answerOverview() {
        long todayTask = taskMapper.selectCount(Wrappers.<InspectTask>lambdaQuery()
                .between(InspectTask::getPlanStart, LocalDate.now().atStartOfDay(),
                        LocalDate.now().atTime(LocalTime.MAX)));
        long hazardOpen = hazardMapper.selectCount(Wrappers.<Hazard>lambdaQuery()
                .ne(Hazard::getStatus, Hazard.Status.CLOSED));
        long eventActive = eventMapper.selectCount(Wrappers.<EmergencyEvent>lambdaQuery()
                .in(EmergencyEvent::getStatus, List.of(EmergencyEvent.Status.PENDING, EmergencyEvent.Status.RESPONDING)));
        long alertPending = safeAlertMapper.selectCount(Wrappers.<SafeAlert>lambdaQuery()
                .eq(SafeAlert::getStatus, SafeAlert.Status.PENDING));
        return String.format("整体态势:今日任务 %d 个,未闭环隐患 %d 起,进行中应急事件 %d 起,待处理飞行预警 %d 条。",
                todayTask, hazardOpen, eventActive, alertPending);
    }

    private String answerHelp() {
        return "我是 AI 值班助手,可以查询平台实时态势,例如:%n- 今天有多少巡检任务?%n- 当前有什么隐患?%n- 机场和无人机在线情况%n- 最近有飞行安全预警吗?%n- 电子围栏有哪些?"
                .replace("%n", "\n");
    }

    @Data
    public static class AskForm {
        private String q;
        private String route;
    }
}
