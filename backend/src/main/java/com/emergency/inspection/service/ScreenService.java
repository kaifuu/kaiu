package com.emergency.inspection.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.emergency.inspection.entity.Device;
import com.emergency.inspection.entity.Hazard;
import com.emergency.inspection.entity.InspectDemand;
import com.emergency.inspection.entity.InspectIssue;
import com.emergency.inspection.entity.InspectPoint;
import com.emergency.inspection.entity.InspectTask;
import com.emergency.inspection.entity.Pilot;
import com.emergency.inspection.entity.SafeAlert;
import com.emergency.inspection.entity.VideoChannel;
import com.emergency.inspection.entity.WorkOrder;
import com.emergency.inspection.mapper.DeviceMapper;
import com.emergency.inspection.mapper.HazardMapper;
import com.emergency.inspection.mapper.InspectDemandMapper;
import com.emergency.inspection.mapper.InspectIssueMapper;
import com.emergency.inspection.mapper.InspectPointMapper;
import com.emergency.inspection.mapper.InspectTaskMapper;
import com.emergency.inspection.mapper.PilotMapper;
import com.emergency.inspection.mapper.SafeAlertMapper;
import com.emergency.inspection.mapper.VideoChannelMapper;
import com.emergency.inspection.mapper.WorkOrderMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 大屏聚合:一次请求返回整屏所需的全部数据。
 *
 * 大屏每 30 秒整体刷新,若拆成十几个接口,一屏要发十几个请求且时序不一致
 * (各卡片可能来自不同时刻)。这里统一聚合,保证同屏数据取自同一时刻。
 */
@Service
@RequiredArgsConstructor
public class ScreenService {

    private final DeviceMapper deviceMapper;
    private final InspectPointMapper pointMapper;
    private final InspectTaskMapper taskMapper;
    private final InspectIssueMapper issueMapper;
    private final WorkOrderMapper orderMapper;
    private final InspectDemandMapper demandMapper;
    private final PilotMapper pilotMapper;
    private final VideoChannelMapper channelMapper;
    private final HazardMapper hazardMapper;
    private final SafeAlertMapper safeAlertMapper;

    public Map<String, Object> overview() {
        Map<String, Object> data = new LinkedHashMap<>();

        data.put("kpi", kpi());
        data.put("device", deviceStats());
        data.put("issueByType", issueByType());
        data.put("orderByStatus", orderByStatus());
        data.put("demandByStatus", demandByStatus());
        data.put("pilotByStatus", pilotByStatus());
        data.put("videoByStatus", videoByStatus());
        data.put("orderByDept", orderByDept());
        data.put("recentIssues", recentIssues(12));
        data.put("recentOrders", recentOrders(10));
        data.put("safeAlertPending", safeAlertMapper.selectCount(
                Wrappers.<SafeAlert>lambdaQuery().eq(SafeAlert::getStatus, SafeAlert.Status.PENDING)));
        data.put("recentSafeAlerts", safeAlertMapper.selectList(
                Wrappers.<SafeAlert>lambdaQuery().orderByDesc(SafeAlert::getOccurredAt).last("limit 8")));
        data.put("onlineVideos", onlineVideos(6));
        data.put("mapPoints", mapPoints());
        data.put("taskTrend", taskTrend());

        return data;
    }

    /** 底部五个环形指标 */
    private Map<String, Object> kpi() {
        Map<String, Object> kpi = new LinkedHashMap<>();
        long dockOnline = deviceMapper.selectCount(Wrappers.<Device>lambdaQuery()
                .eq(Device::getDeviceType, Device.DeviceType.DOCK)
                .eq(Device::getStatus, Device.Status.ONLINE));
        long dockTotal = deviceMapper.selectCount(Wrappers.<Device>lambdaQuery()
                .eq(Device::getDeviceType, Device.DeviceType.DOCK));

        // 已完结工单:已处理 + 已结案
        long doneOrders = orderMapper.selectCount(Wrappers.<WorkOrder>lambdaQuery()
                .in(WorkOrder::getStatus, List.of(WorkOrder.Status.HANDLED, WorkOrder.Status.CLOSED)));

        kpi.put("pointTotal", pointMapper.selectCount(Wrappers.<InspectPoint>lambdaQuery()));
        kpi.put("dockOnline", dockOnline);
        kpi.put("dockTotal", dockTotal);
        kpi.put("taskTotal", taskMapper.selectCount(Wrappers.<InspectTask>lambdaQuery()));
        kpi.put("taskDone", taskMapper.selectCount(Wrappers.<InspectTask>lambdaQuery()
                .eq(InspectTask::getStatus, InspectTask.Status.DONE)));
        kpi.put("issueTotal", issueMapper.selectCount(Wrappers.<InspectIssue>lambdaQuery()));
        kpi.put("orderTotal", orderMapper.selectCount(Wrappers.<WorkOrder>lambdaQuery()));
        kpi.put("orderDone", doneOrders);
        kpi.put("hazardOpen", hazardMapper.selectCount(Wrappers.<Hazard>lambdaQuery()
                .ne(Hazard::getStatus, Hazard.Status.CLOSED)));
        kpi.put("demandPending", demandMapper.selectCount(Wrappers.<InspectDemand>lambdaQuery()
                .eq(InspectDemand::getStatus, InspectDemand.Status.PENDING)));
        kpi.put("pilotAvailable", pilotMapper.selectCount(Wrappers.<Pilot>lambdaQuery()
                .eq(Pilot::getStatus, Pilot.Status.AVAILABLE)));
        return kpi;
    }

    private Map<String, Object> deviceStats() {
        Map<String, Object> out = new LinkedHashMap<>();
        for (Device.DeviceType type : Device.DeviceType.values()) {
            long total = deviceMapper.selectCount(Wrappers.<Device>lambdaQuery()
                    .eq(Device::getDeviceType, type));
            long online = deviceMapper.selectCount(Wrappers.<Device>lambdaQuery()
                    .eq(Device::getDeviceType, type)
                    .eq(Device::getStatus, Device.Status.ONLINE));
            out.put(type.name(), Map.of("total", total, "online", online));
        }
        return out;
    }

    /** 算法识别类型分布(大屏右侧) */
    private List<Map<String, Object>> issueByType() {
        List<Map<String, Object>> list = new ArrayList<>();
        for (InspectIssue.IssueType type : InspectIssue.IssueType.values()) {
            long n = issueMapper.selectCount(Wrappers.<InspectIssue>lambdaQuery()
                    .eq(InspectIssue::getIssueType, type));
            if (n > 0) {
                list.add(Map.of("type", type.name(), "count", n));
            }
        }
        list.sort((a, b) -> Long.compare((Long) b.get("count"), (Long) a.get("count")));
        return list;
    }

    private Map<String, Long> orderByStatus() {
        Map<String, Long> out = new LinkedHashMap<>();
        for (WorkOrder.Status s : WorkOrder.Status.values()) {
            out.put(s.name(), orderMapper.selectCount(
                    Wrappers.<WorkOrder>lambdaQuery().eq(WorkOrder::getStatus, s)));
        }
        return out;
    }

    private Map<String, Long> demandByStatus() {
        Map<String, Long> out = new LinkedHashMap<>();
        for (InspectDemand.Status s : InspectDemand.Status.values()) {
            out.put(s.name(), demandMapper.selectCount(
                    Wrappers.<InspectDemand>lambdaQuery().eq(InspectDemand::getStatus, s)));
        }
        return out;
    }

    private Map<String, Long> pilotByStatus() {
        Map<String, Long> out = new LinkedHashMap<>();
        for (Pilot.Status s : Pilot.Status.values()) {
            out.put(s.name(), pilotMapper.selectCount(
                    Wrappers.<Pilot>lambdaQuery().eq(Pilot::getStatus, s)));
        }
        return out;
    }

    private Map<String, Long> videoByStatus() {
        Map<String, Long> out = new LinkedHashMap<>();
        for (VideoChannel.Status s : VideoChannel.Status.values()) {
            out.put(s.name(), channelMapper.selectCount(
                    Wrappers.<VideoChannel>lambdaQuery().eq(VideoChannel::getStatus, s)));
        }
        return out;
    }

    /** 需求部门工单量排行 */
    private List<Map<String, Object>> orderByDept() {
        List<Map<String, Object>> list = new ArrayList<>();
        orderMapper.selectList(Wrappers.<WorkOrder>lambdaQuery().select(WorkOrder::getDept))
                .stream().map(WorkOrder::getDept).filter(d -> d != null && !d.isBlank()).distinct()
                .forEach(dept -> {
                    long n = orderMapper.selectCount(
                            Wrappers.<WorkOrder>lambdaQuery().eq(WorkOrder::getDept, dept));
                    list.add(Map.of("dept", dept, "count", n));
                });
        list.sort((a, b) -> Long.compare((Long) b.get("count"), (Long) a.get("count")));
        return list.size() > 8 ? list.subList(0, 8) : list;
    }

    private List<InspectIssue> recentIssues(int limit) {
        return issueMapper.selectList(Wrappers.<InspectIssue>lambdaQuery()
                .orderByDesc(InspectIssue::getFoundAt).last("limit " + limit));
    }

    private List<WorkOrder> recentOrders(int limit) {
        return orderMapper.selectList(Wrappers.<WorkOrder>lambdaQuery()
                .orderByDesc(WorkOrder::getCreateTime).last("limit " + limit));
    }

    private List<VideoChannel> onlineVideos(int limit) {
        return channelMapper.selectList(Wrappers.<VideoChannel>lambdaQuery()
                .eq(VideoChannel::getStatus, VideoChannel.Status.ONLINE)
                .orderByDesc(VideoChannel::getLastFrameAt).last("limit " + limit));
    }

    /** 地图标记:点位 + 已定位的问题 */
    private List<Map<String, Object>> mapPoints() {
        List<Map<String, Object>> list = new ArrayList<>();
        for (InspectPoint p : pointMapper.selectList(Wrappers.<InspectPoint>lambdaQuery())) {
            if (p.getLongitude() == null || p.getLatitude() == null) {
                continue;
            }
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("name", p.getName());
            m.put("kind", "POINT");
            m.put("riskLevel", p.getRiskLevel() == null ? null : p.getRiskLevel().name());
            m.put("longitude", p.getLongitude());
            m.put("latitude", p.getLatitude());
            list.add(m);
        }
        for (InspectIssue i : issueMapper.selectList(Wrappers.<InspectIssue>lambdaQuery()
                .ne(InspectIssue::getStatus, InspectIssue.Status.CLOSED))) {
            if (i.getLongitude() == null || i.getLatitude() == null) {
                continue;
            }
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("name", i.getTitle());
            m.put("kind", "ISSUE");
            m.put("issueType", i.getIssueType() == null ? null : i.getIssueType().name());
            m.put("longitude", i.getLongitude());
            m.put("latitude", i.getLatitude());
            list.add(m);
        }
        return list;
    }

    /** 近 7 日任务量趋势(大屏底部折线) */
    private List<Map<String, Object>> taskTrend() {
        List<Map<String, Object>> trend = new ArrayList<>();
        for (int i = 6; i >= 0; i--) {
            LocalDate day = LocalDate.now().minusDays(i);
            LocalDateTime from = day.atStartOfDay();
            LocalDateTime to = day.atTime(LocalTime.MAX);
            long total = taskMapper.selectCount(Wrappers.<InspectTask>lambdaQuery()
                    .between(InspectTask::getPlanStart, from, to));
            long done = taskMapper.selectCount(Wrappers.<InspectTask>lambdaQuery()
                    .eq(InspectTask::getStatus, InspectTask.Status.DONE)
                    .between(InspectTask::getActualEnd, from, to));
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("date", day.toString());
            item.put("total", total);
            item.put("done", done);
            trend.add(item);
        }
        return trend;
    }
}
