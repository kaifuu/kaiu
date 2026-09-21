package com.emergency.inspection.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.emergency.inspection.entity.AiAlgorithmAlarm;
import com.emergency.inspection.entity.Device;
import com.emergency.inspection.entity.EmergencyEvent;
import com.emergency.inspection.entity.Hazard;
import com.emergency.inspection.entity.InspectPlan;
import com.emergency.inspection.entity.InspectPoint;
import com.emergency.inspection.entity.InspectTask;
import com.emergency.inspection.entity.SafeAlert;
import com.emergency.inspection.mapper.AiAlgorithmAlarmMapper;
import com.emergency.inspection.mapper.DeviceMapper;
import com.emergency.inspection.mapper.EmergencyEventMapper;
import com.emergency.inspection.mapper.HazardMapper;
import com.emergency.inspection.mapper.InspectPlanMapper;
import com.emergency.inspection.mapper.InspectPointMapper;
import com.emergency.inspection.mapper.InspectTaskMapper;
import com.emergency.inspection.mapper.SafeAlertMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 工作台统计:概览卡片 + 趋势 + 待办列表 */
@Service
@RequiredArgsConstructor
public class DashboardService {

    private final InspectPointMapper pointMapper;
    private final InspectPlanMapper planMapper;
    private final InspectTaskMapper taskMapper;
    private final HazardMapper hazardMapper;
    private final EmergencyEventMapper eventMapper;
    private final DeviceMapper deviceMapper;
    private final AiAlgorithmAlarmMapper alarmMapper;
    private final SafeAlertMapper safeAlertMapper;

    public Map<String, Object> stats() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("pointTotal", pointMapper.selectCount(Wrappers.<InspectPoint>lambdaQuery()));
        data.put("planEnabled", planMapper.selectCount(
                Wrappers.<InspectPlan>lambdaQuery().eq(InspectPlan::getStatus, InspectPlan.Status.ENABLED)));
        data.put("taskToday", countTodayTask());
        data.put("taskPending", countTask(InspectTask.Status.PENDING));
        data.put("taskRunning", countTask(InspectTask.Status.RUNNING));
        data.put("taskOverdue", countTask(InspectTask.Status.OVERDUE));
        data.put("hazardPending", countHazard(Hazard.Status.PENDING));
        data.put("hazardProcessing", countHazard(Hazard.Status.PROCESSING));
        data.put("eventActive", eventMapper.selectCount(Wrappers.<EmergencyEvent>lambdaQuery()
                .in(EmergencyEvent::getStatus, List.of(EmergencyEvent.Status.PENDING, EmergencyEvent.Status.RESPONDING))));
        data.put("eventTotal", eventMapper.selectCount(Wrappers.<EmergencyEvent>lambdaQuery()));
        // 设备与 AI 态势:机场/无人机在线数、今日 AI 告警数
        data.put("dockOnline", deviceMapper.selectCount(Wrappers.<Device>lambdaQuery()
                .eq(Device::getDeviceType, Device.DeviceType.DOCK).eq(Device::getStatus, Device.Status.ONLINE)));
        data.put("droneOnline", deviceMapper.selectCount(Wrappers.<Device>lambdaQuery()
                .eq(Device::getDeviceType, Device.DeviceType.DRONE).eq(Device::getStatus, Device.Status.ONLINE)));
        data.put("alarmToday", alarmMapper.selectCount(Wrappers.<AiAlgorithmAlarm>lambdaQuery()
                .ge(AiAlgorithmAlarm::getOccurredAt, LocalDate.now().atStartOfDay())));

        data.put("pointByRisk", groupPointByRisk());
        data.put("taskByStatus", groupTaskByStatus());
        data.put("eventByLevel", groupEventByLevel());
        data.put("taskTrend", taskTrend());

        data.put("recentTasks", taskMapper.selectList(Wrappers.<InspectTask>lambdaQuery()
                .orderByDesc(InspectTask::getPlanStart).last("limit 6")));
        data.put("activeEvents", eventMapper.selectList(Wrappers.<EmergencyEvent>lambdaQuery()
                .in(EmergencyEvent::getStatus, List.of(EmergencyEvent.Status.PENDING, EmergencyEvent.Status.RESPONDING))
                .orderByDesc(EmergencyEvent::getOccurTime).last("limit 6")));
        data.put("recentHazards", hazardMapper.selectList(Wrappers.<Hazard>lambdaQuery()
                .ne(Hazard::getStatus, Hazard.Status.CLOSED)
                .orderByDesc(Hazard::getReportTime).last("limit 6")));
        // 飞行安全预警:最近 6 条 + 待处理数(第 4 列表卡)
        data.put("safeAlerts", safeAlertMapper.selectList(Wrappers.<SafeAlert>lambdaQuery()
                .orderByDesc(SafeAlert::getOccurredAt).last("limit 6")));
        data.put("safeAlertPending", safeAlertMapper.selectCount(
                Wrappers.<SafeAlert>lambdaQuery().eq(SafeAlert::getStatus, SafeAlert.Status.PENDING)));
        return data;
    }

    private long countTodayTask() {
        return taskMapper.selectCount(Wrappers.<InspectTask>lambdaQuery()
                .between(InspectTask::getPlanStart, LocalDate.now().atStartOfDay(),
                        LocalDate.now().atTime(LocalTime.MAX)));
    }

    private long countTask(InspectTask.Status status) {
        return taskMapper.selectCount(Wrappers.<InspectTask>lambdaQuery().eq(InspectTask::getStatus, status));
    }

    private long countHazard(Hazard.Status status) {
        return hazardMapper.selectCount(Wrappers.<Hazard>lambdaQuery().eq(Hazard::getStatus, status));
    }

    /** 点位风险等级分布(饼图) */
    private Map<String, Long> groupPointByRisk() {
        Map<String, Long> out = new LinkedHashMap<>();
        for (InspectPoint.RiskLevel level : InspectPoint.RiskLevel.values()) {
            out.put(level.name(), pointMapper.selectCount(
                    Wrappers.<InspectPoint>lambdaQuery().eq(InspectPoint::getRiskLevel, level)));
        }
        return out;
    }

    /** 任务状态分布(柱图) */
    private Map<String, Long> groupTaskByStatus() {
        Map<String, Long> out = new LinkedHashMap<>();
        for (InspectTask.Status status : InspectTask.Status.values()) {
            out.put(status.name(), countTask(status));
        }
        return out;
    }

    /** 事件等级分布(柱图) */
    private Map<String, Long> groupEventByLevel() {
        Map<String, Long> out = new LinkedHashMap<>();
        for (EmergencyEvent.Level level : EmergencyEvent.Level.values()) {
            out.put(level.name(), eventMapper.selectCount(
                    Wrappers.<EmergencyEvent>lambdaQuery().eq(EmergencyEvent::getLevel, level)));
        }
        return out;
    }

    /** 近 7 日巡检任务完成数趋势(折线图),按天分组统计 actual_end 落在当天的任务 */
    private List<Map<String, Object>> taskTrend() {
        List<Map<String, Object>> trend = new ArrayList<>();
        for (int i = 6; i >= 0; i--) {
            LocalDate day = LocalDate.now().minusDays(i);
            LocalDateTime from = day.atStartOfDay();
            LocalDateTime to = day.atTime(LocalTime.MAX);
            long done = taskMapper.selectCount(Wrappers.<InspectTask>lambdaQuery()
                    .eq(InspectTask::getStatus, InspectTask.Status.DONE)
                    .between(InspectTask::getActualEnd, from, to));
            long total = taskMapper.selectCount(Wrappers.<InspectTask>lambdaQuery()
                    .between(InspectTask::getPlanStart, from, to));
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("date", day.toString());
            item.put("done", done);
            item.put("total", total);
            trend.add(item);
        }
        return trend;
    }
}
