package com.emergency.inspection.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.emergency.inspection.entity.AiAlgorithm;
import com.emergency.inspection.entity.AiAlgorithmAlarm;
import com.emergency.inspection.entity.AiOdorReading;
import com.emergency.inspection.entity.Device;
import com.emergency.inspection.entity.DeviceCommand;
import com.emergency.inspection.entity.DeviceEvent;
import com.emergency.inspection.entity.DeviceOsd;
import com.emergency.inspection.entity.EmergencyEvent;
import com.emergency.inspection.entity.GeoFence;
import com.emergency.inspection.entity.Hazard;
import com.emergency.inspection.entity.InspectDemand;
import com.emergency.inspection.entity.InspectIssue;
import com.emergency.inspection.entity.InspectPoint;
import com.emergency.inspection.entity.InspectTask;
import com.emergency.inspection.entity.Pilot;
import com.emergency.inspection.entity.SafeAlert;
import com.emergency.inspection.entity.VideoChannel;
import com.emergency.inspection.entity.Wayline;
import com.emergency.inspection.entity.WaylineJob;
import com.emergency.inspection.entity.WorkOrder;
import com.emergency.inspection.mapper.AiAlgorithmAlarmMapper;
import com.emergency.inspection.mapper.AiOdorReadingMapper;
import com.emergency.inspection.mapper.DeviceCommandMapper;
import com.emergency.inspection.mapper.DeviceEventMapper;
import com.emergency.inspection.mapper.DeviceMapper;
import com.emergency.inspection.mapper.DeviceOsdMapper;
import com.emergency.inspection.mapper.EmergencyEventMapper;
import com.emergency.inspection.mapper.GeoFenceMapper;
import com.emergency.inspection.mapper.HazardMapper;
import com.emergency.inspection.mapper.InspectDemandMapper;
import com.emergency.inspection.mapper.InspectIssueMapper;
import com.emergency.inspection.mapper.InspectPointMapper;
import com.emergency.inspection.mapper.InspectTaskMapper;
import com.emergency.inspection.mapper.PilotMapper;
import com.emergency.inspection.mapper.SafeAlertMapper;
import com.emergency.inspection.mapper.VideoChannelMapper;
import com.emergency.inspection.mapper.WaylineJobMapper;
import com.emergency.inspection.mapper.WaylineMapper;
import com.emergency.inspection.mapper.WorkOrderMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashMap;
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
    private final EmergencyEventMapper eventMapper;
    private final GeoFenceMapper fenceMapper;
    private final AiAlgorithmAlarmMapper algoAlarmMapper;
    private final AiOdorReadingMapper odorReadingMapper;
    private final com.emergency.inspection.mapper.AiOdorStationMapper odorStationMapper;
    private final WaylineMapper waylineMapper;
    private final WaylineJobMapper waylineJobMapper;
    private final DeviceOsdMapper osdMapper;
    private final DeviceCommandMapper commandMapper;
    private final DeviceEventMapper deviceEventMapper;

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

    /* ==================== 子屏聚合 ==================== */

    /** 应急专题:事件等级/类别分布 + 进行中事件 + 安全预警 + 空域保障围栏 */
    public Map<String, Object> emergency() {
        Map<String, Object> data = new LinkedHashMap<>();

        Map<String, Long> byLevel = new LinkedHashMap<>();
        for (EmergencyEvent.Level lv : EmergencyEvent.Level.values()) {
            byLevel.put(lv.name(), eventMapper.selectCount(
                    Wrappers.<EmergencyEvent>lambdaQuery().eq(EmergencyEvent::getLevel, lv)));
        }
        Map<String, Long> byCategory = new LinkedHashMap<>();
        for (EmergencyEvent.Category c : EmergencyEvent.Category.values()) {
            byCategory.put(c.name(), eventMapper.selectCount(
                    Wrappers.<EmergencyEvent>lambdaQuery().eq(EmergencyEvent::getCategory, c)));
        }
        List<EmergencyEvent> active = eventMapper.selectList(Wrappers.<EmergencyEvent>lambdaQuery()
                .in(EmergencyEvent::getStatus, List.of(EmergencyEvent.Status.PENDING, EmergencyEvent.Status.RESPONDING))
                .orderByDesc(EmergencyEvent::getOccurTime).last("limit 8"));
        List<EmergencyEvent> recent = eventMapper.selectList(Wrappers.<EmergencyEvent>lambdaQuery()
                .orderByDesc(EmergencyEvent::getOccurTime).last("limit 10"));

        Map<String, Long> fenceByType = new LinkedHashMap<>();
        for (GeoFence.FenceType t : GeoFence.FenceType.values()) {
            fenceByType.put(t.name(), fenceMapper.selectCount(
                    Wrappers.<GeoFence>lambdaQuery().eq(GeoFence::getFenceType, t)));
        }

        // 近 7 日事件趋势:当日发生 / 当日处置完结
        List<Map<String, Object>> trend = new ArrayList<>();
        for (int i = 6; i >= 0; i--) {
            LocalDate day = LocalDate.now().minusDays(i);
            LocalDateTime from = day.atStartOfDay(), to = day.atTime(LocalTime.MAX);
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("date", day.toString());
            item.put("total", eventMapper.selectCount(Wrappers.<EmergencyEvent>lambdaQuery()
                    .between(EmergencyEvent::getOccurTime, from, to)));
            item.put("done", eventMapper.selectCount(Wrappers.<EmergencyEvent>lambdaQuery()
                    .isNotNull(EmergencyEvent::getFinishTime)
                    .between(EmergencyEvent::getFinishTime, from, to)));
            trend.add(item);
        }

        Map<String, Object> kpi = new LinkedHashMap<>();
        kpi.put("activeCount", active.size());
        kpi.put("todayNew", eventMapper.selectCount(Wrappers.<EmergencyEvent>lambdaQuery()
                .between(EmergencyEvent::getOccurTime, LocalDate.now().atStartOfDay(), LocalDateTime.now())));
        kpi.put("alertPending", safeAlertMapper.selectCount(
                Wrappers.<SafeAlert>lambdaQuery().eq(SafeAlert::getStatus, SafeAlert.Status.PENDING)));
        kpi.put("fenceCount", fenceMapper.selectCount(Wrappers.<GeoFence>lambdaQuery()));

        data.put("kpi", kpi);
        data.put("eventByLevel", byLevel);
        data.put("eventByCategory", byCategory);
        data.put("activeEvents", active);
        data.put("recentEvents", recent);
        data.put("fenceByType", fenceByType);
        data.put("fences", fenceMapper.selectList(Wrappers.<GeoFence>lambdaQuery()));
        data.put("safeAlertPending", kpi.get("alertPending"));
        data.put("recentSafeAlerts", safeAlertMapper.selectList(
                Wrappers.<SafeAlert>lambdaQuery().orderByDesc(SafeAlert::getOccurredAt).last("limit 8")));
        data.put("eventTrend", trend);
        return data;
    }

    /** 生态专题:点位类别/风险分布 + 六算法命中 + 臭气站最新读数排行 */
    public Map<String, Object> ecology() {
        Map<String, Object> data = new LinkedHashMap<>();

        Map<String, Long> pointByCategory = new LinkedHashMap<>();
        for (InspectPoint.Category c : InspectPoint.Category.values()) {
            pointByCategory.put(c.name(), pointMapper.selectCount(
                    Wrappers.<InspectPoint>lambdaQuery().eq(InspectPoint::getCategory, c)));
        }
        Map<String, Long> pointByRisk = new LinkedHashMap<>();
        for (InspectPoint.RiskLevel r : InspectPoint.RiskLevel.values()) {
            pointByRisk.put(r.name(), pointMapper.selectCount(
                    Wrappers.<InspectPoint>lambdaQuery().eq(InspectPoint::getRiskLevel, r)));
        }
        Map<String, Long> alarmByCode = new LinkedHashMap<>();
        for (AiAlgorithm.Code code : AiAlgorithm.Code.values()) {
            alarmByCode.put(code.name(), algoAlarmMapper.selectCount(
                    Wrappers.<AiAlgorithmAlarm>lambdaQuery().eq(AiAlgorithmAlarm::getAlgorithmCode, code)));
        }
        long alarmToday = algoAlarmMapper.selectCount(Wrappers.<AiAlgorithmAlarm>lambdaQuery()
                .between(AiAlgorithmAlarm::getOccurredAt, LocalDate.now().atStartOfDay(), LocalDateTime.now()));

        data.put("kpi", Map.of(
                "pointTotal", pointMapper.selectCount(Wrappers.<InspectPoint>lambdaQuery()),
                "highRisk", pointMapper.selectCount(Wrappers.<InspectPoint>lambdaQuery()
                        .in(InspectPoint::getRiskLevel, List.of(
                                InspectPoint.RiskLevel.HIGH, InspectPoint.RiskLevel.EXTREME))),
                "alarmToday", alarmToday,
                "odorOver", odorOverStations()));
        data.put("pointByCategory", pointByCategory);
        data.put("pointByRisk", pointByRisk);
        data.put("alarmByCode", alarmByCode);
        data.put("recentAlarms", algoAlarmMapper.selectList(Wrappers.<AiAlgorithmAlarm>lambdaQuery()
                .orderByDesc(AiAlgorithmAlarm::getOccurredAt).last("limit 10")));
        data.put("odorTop", odorTop());
        return data;
    }

    /** 臭气超标站数(最新一批读数里硫化氢 ≥ 预警阈值 0.05ppm) */
    private long odorOverStations() {
        return odorTop().stream().filter(s -> {
            Object h2s = ((Map<?, ?>) s).get("h2sPpm");
            return h2s != null && new java.math.BigDecimal(h2s.toString())
                    .compareTo(new java.math.BigDecimal("0.05")) >= 0;
        }).count();
    }

    /** 每站取最新一条读数,按臭气浓度倒序(大屏排行 + 3D 站点用) */
    private List<Map<String, Object>> odorTop() {
        // 读数按 180s 批次落库,取最近若干条在内存里按站去重即可
        List<AiOdorReading> readings = odorReadingMapper.selectList(
                Wrappers.<AiOdorReading>lambdaQuery().orderByDesc(AiOdorReading::getReadTime).last("limit 400"));
        Map<Long, AiOdorReading> latest = new LinkedHashMap<>();
        for (AiOdorReading r : readings) {
            latest.putIfAbsent(r.getStationId(), r);
        }
        Map<Long, com.emergency.inspection.entity.AiOdorStation> stations = new HashMap<>();
        for (com.emergency.inspection.entity.AiOdorStation st : odorStationMapper.selectList(
                Wrappers.<com.emergency.inspection.entity.AiOdorStation>lambdaQuery())) {
            stations.put(st.getId(), st);
        }
        List<Map<String, Object>> list = new ArrayList<>();
        latest.forEach((stationId, r) -> {
            com.emergency.inspection.entity.AiOdorStation st = stations.get(stationId);
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("stationId", stationId);
            m.put("stationName", st == null ? null : st.getStationName());
            m.put("area", st == null ? null : st.getArea());
            m.put("longitude", st == null ? null : st.getLongitude());
            m.put("latitude", st == null ? null : st.getLatitude());
            m.put("h2sPpm", r.getH2sPpm());
            m.put("nh3Ppm", r.getNh3Ppm());
            m.put("odorUnit", r.getOdorUnit());
            m.put("readTime", r.getReadTime());
            list.add(m);
        });
        list.sort((a, b) -> {
            Integer x = (Integer) a.get("odorUnit"), y = (Integer) b.get("odorUnit");
            return y == null || x == null ? 0 : y - x;
        });
        return list;
    }

    /** 工单管理:状态/优先级/部门分布 + 近期工单 + 趋势 */
    public Map<String, Object> orders() {
        Map<String, Object> data = new LinkedHashMap<>();
        Map<String, Long> byPriority = new LinkedHashMap<>();
        for (WorkOrder.Priority p : WorkOrder.Priority.values()) {
            byPriority.put(p.name(), orderMapper.selectCount(
                    Wrappers.<WorkOrder>lambdaQuery().eq(WorkOrder::getPriority, p)));
        }
        // 近 7 日工单趋势:当日新建 / 当日办结
        List<Map<String, Object>> trend = new ArrayList<>();
        for (int i = 6; i >= 0; i--) {
            LocalDate day = LocalDate.now().minusDays(i);
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("date", day.toString());
            item.put("total", orderMapper.selectCount(Wrappers.<WorkOrder>lambdaQuery()
                    .between(WorkOrder::getCreateTime, day.atStartOfDay(), day.atTime(LocalTime.MAX))));
            item.put("done", orderMapper.selectCount(Wrappers.<WorkOrder>lambdaQuery()
                    .isNotNull(WorkOrder::getFinishedAt)
                    .between(WorkOrder::getFinishedAt, day.atStartOfDay(), day.atTime(LocalTime.MAX))));
            trend.add(item);
        }
        long total = orderMapper.selectCount(Wrappers.<WorkOrder>lambdaQuery());
        long done = orderMapper.selectCount(Wrappers.<WorkOrder>lambdaQuery()
                .in(WorkOrder::getStatus, List.of(WorkOrder.Status.HANDLED, WorkOrder.Status.CLOSED)));

        Map<String, Object> kpi = new LinkedHashMap<>();
        kpi.put("total", total);
        kpi.put("pending", byStatusCount(WorkOrder.Status.PENDING));
        kpi.put("processing", byStatusCount(WorkOrder.Status.PROCESSING));
        kpi.put("done", done);

        data.put("kpi", kpi);
        data.put("orderByStatus", orderByStatus());
        data.put("orderByPriority", byPriority);
        data.put("orderByDept", orderByDept());
        data.put("recentOrders", recentOrders(10));
        data.put("demandByStatus", demandByStatus());
        data.put("orderTrend", trend);
        return data;
    }

    private long byStatusCount(WorkOrder.Status s) {
        return orderMapper.selectCount(Wrappers.<WorkOrder>lambdaQuery().eq(WorkOrder::getStatus, s));
    }

    /** 飞手管理:状态/证照/片区分布 + 台账 */
    public Map<String, Object> pilots() {
        Map<String, Object> data = new LinkedHashMap<>();
        Map<String, Long> byCert = new LinkedHashMap<>();
        for (Pilot.CertType c : Pilot.CertType.values()) {
            byCert.put(c.name(), pilotMapper.selectCount(
                    Wrappers.<Pilot>lambdaQuery().eq(Pilot::getCertType, c)));
        }
        List<Pilot> pilots = pilotMapper.selectList(Wrappers.<Pilot>lambdaQuery());
        Map<String, Long> byArea = new LinkedHashMap<>();
        pilots.stream().map(Pilot::getArea).filter(a -> a != null && !a.isBlank())
                .forEach(a -> byArea.merge(a, 1L, Long::sum));
        double avgExp = pilots.stream().map(Pilot::getExperienceYears)
                .filter(y -> y != null).mapToInt(Integer::intValue).average().orElse(0);

        Map<String, Object> kpi = new LinkedHashMap<>();
        kpi.put("total", pilots.size());
        kpi.put("available", pilotByStatus().getOrDefault(Pilot.Status.AVAILABLE.name(), 0L));
        kpi.put("onTask", pilotByStatus().getOrDefault(Pilot.Status.ON_TASK.name(), 0L));
        kpi.put("avgExperience", Math.round(avgExp * 10) / 10.0);

        data.put("kpi", kpi);
        data.put("pilotByStatus", pilotByStatus());
        data.put("pilotByCert", byCert);
        data.put("pilotByArea", byArea);
        data.put("pilots", pilots);
        return data;
    }

    /** 设备监控:设备台账联 OSD 快照(避免前端 N+1)+ 指令/事件流水 */
    public Map<String, Object> devices() {
        Map<String, Object> data = new LinkedHashMap<>();
        List<Device> devices = deviceMapper.selectList(Wrappers.<Device>lambdaQuery()
                .orderByDesc(Device::getLastOnlineAt));
        Map<String, DeviceOsd> osdBySn = new HashMap<>();
        for (DeviceOsd osd : osdMapper.selectList(Wrappers.<DeviceOsd>lambdaQuery())) {
            osdBySn.put(osd.getDeviceSn(), osd);
        }
        Map<String, String> nameBySn = new HashMap<>();
        for (Device d : devices) {
            nameBySn.put(d.getDeviceSn(), d.getName());
        }
        List<Map<String, Object>> list = new ArrayList<>();
        for (Device d : devices) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", d.getId());
            m.put("name", d.getName());
            m.put("deviceSn", d.getDeviceSn());
            m.put("deviceType", d.getDeviceType() == null ? null : d.getDeviceType().name());
            m.put("deviceModel", d.getDeviceModel());
            m.put("gatewayName", d.getGatewaySn() == null ? null : nameBySn.get(d.getGatewaySn()));
            m.put("status", d.getStatus() == null ? null : d.getStatus().name());
            m.put("firmwareVersion", d.getFirmwareVersion());
            DeviceOsd osd = osdBySn.get(d.getDeviceSn());
            if (osd != null) {
                m.put("modeCode", osd.getModeCode());
                m.put("batteryPercent", osd.getBatteryPercent());
                m.put("height", osd.getHeight());
                m.put("longitude", osd.getLongitude());
                m.put("latitude", osd.getLatitude());
            }
            list.add(m);
        }
        data.put("devices", list);
        data.put("deviceStats", deviceStats());
        data.put("recentCommands", commandMapper.selectList(Wrappers.<DeviceCommand>lambdaQuery()
                .orderByDesc(DeviceCommand::getSentAt).last("limit 10")));
        data.put("recentEvents", deviceEventMapper.selectList(Wrappers.<DeviceEvent>lambdaQuery()
                .orderByDesc(DeviceEvent::getCreateTime).last("limit 10")));
        return data;
    }

    /** 航线管理:航线库(含航点 JSON,3D 绘制用)+ 任务状态/每航线执行统计 */
    public Map<String, Object> waylines() {
        Map<String, Object> data = new LinkedHashMap<>();
        List<Wayline> waylines = waylineMapper.selectList(Wrappers.<Wayline>lambdaQuery());
        List<WaylineJob> jobs = waylineJobMapper.selectList(Wrappers.<WaylineJob>lambdaQuery());

        Map<String, Long> jobByStatus = new LinkedHashMap<>();
        for (WaylineJob.Status s : WaylineJob.Status.values()) {
            jobByStatus.put(s.name(), jobs.stream().filter(j -> j.getStatus() == s).count());
        }
        // 每航线执行次数 / 成功次数
        Map<Long, List<WaylineJob>> byWayline = new HashMap<>();
        for (WaylineJob j : jobs) {
            if (j.getWaylineId() != null) {
                byWayline.computeIfAbsent(j.getWaylineId(), k -> new ArrayList<>()).add(j);
            }
        }
        List<Map<String, Object>> runs = new ArrayList<>();
        byWayline.forEach((wid, js) -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("waylineId", wid);
            m.put("runs", js.size());
            m.put("success", js.stream().filter(j -> j.getStatus() == WaylineJob.Status.SUCCESS).count());
            runs.add(m);
        });

        long success = jobByStatus.getOrDefault(WaylineJob.Status.SUCCESS.name(), 0L);
        Map<String, Object> kpi = new LinkedHashMap<>();
        kpi.put("waylineTotal", waylines.size());
        kpi.put("jobTotal", jobs.size());
        kpi.put("successRate", jobs.isEmpty() ? 0
                : Math.round(success * 1000.0 / jobs.size()) / 10.0);
        kpi.put("active", jobs.stream().filter(j -> List.of(WaylineJob.Status.SENT, WaylineJob.Status.READY,
                WaylineJob.Status.QUEUED, WaylineJob.Status.RUNNING).contains(j.getStatus())).count());

        data.put("kpi", kpi);
        data.put("waylines", waylines);
        data.put("jobByStatus", jobByStatus);
        data.put("runsByWayline", runs);
        data.put("recentJobs", waylineJobMapper.selectList(Wrappers.<WaylineJob>lambdaQuery()
                .orderByDesc(WaylineJob::getCreateTime).last("limit 12")));
        return data;
    }

    /** 飞行记录:wayline_job 即事实架次,联告警数与设备名,含总计与趋势 */
    public Map<String, Object> flights() {
        Map<String, Object> data = new LinkedHashMap<>();
        List<WaylineJob> jobs = waylineJobMapper.selectList(Wrappers.<WaylineJob>lambdaQuery()
                .isNotNull(WaylineJob::getBeginAt)
                .orderByDesc(WaylineJob::getBeginAt).last("limit 40"));

        Map<String, Long> alarmByFlight = new HashMap<>();
        for (AiAlgorithmAlarm a : algoAlarmMapper.selectList(Wrappers.<AiAlgorithmAlarm>lambdaQuery()
                .isNotNull(AiAlgorithmAlarm::getFlightId))) {
            alarmByFlight.merge(a.getFlightId(), 1L, Long::sum);
        }
        Map<String, String> nameBySn = new HashMap<>();
        for (Device d : deviceMapper.selectList(Wrappers.<Device>lambdaQuery())) {
            nameBySn.put(d.getDeviceSn(), d.getName());
        }

        List<Map<String, Object>> flights = new ArrayList<>();
        for (WaylineJob j : jobs) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("flightId", j.getFlightId());
            m.put("waylineName", j.getWaylineName());
            m.put("droneSn", j.getDroneSn());
            m.put("droneName", nameBySn.get(j.getDroneSn()));
            m.put("dockSn", j.getDockSn());
            m.put("status", j.getStatus() == null ? null : j.getStatus().name());
            m.put("beginAt", j.getBeginAt());
            m.put("endAt", j.getEndAt());
            m.put("durationMin", j.getEndAt() == null ? null
                    : Duration.between(j.getBeginAt(), j.getEndAt()).toMinutes());
            m.put("mediaCount", j.getMediaCount());
            m.put("alarmCount", alarmByFlight.getOrDefault(j.getFlightId(), 0L));
            flights.add(m);
        }

        long total = waylineJobMapper.selectCount(Wrappers.<WaylineJob>lambdaQuery()
                .isNotNull(WaylineJob::getBeginAt));
        long today = waylineJobMapper.selectCount(Wrappers.<WaylineJob>lambdaQuery()
                .isNotNull(WaylineJob::getBeginAt)
                .ge(WaylineJob::getBeginAt, LocalDate.now().atStartOfDay()));
        long success = waylineJobMapper.selectCount(Wrappers.<WaylineJob>lambdaQuery()
                .eq(WaylineJob::getStatus, WaylineJob.Status.SUCCESS));
        long totalMin = waylineJobMapper.selectList(Wrappers.<WaylineJob>lambdaQuery()
                        .isNotNull(WaylineJob::getEndAt).isNotNull(WaylineJob::getBeginAt))
                .stream().mapToLong(j -> Duration.between(j.getBeginAt(), j.getEndAt()).toMinutes()).sum();
        long mediaTotal = waylineJobMapper.selectList(Wrappers.<WaylineJob>lambdaQuery()
                        .isNotNull(WaylineJob::getMediaCount))
                .stream().mapToLong(j -> j.getMediaCount() == null ? 0 : j.getMediaCount()).sum();

        List<Map<String, Object>> trend = new ArrayList<>();
        for (int i = 6; i >= 0; i--) {
            LocalDate day = LocalDate.now().minusDays(i);
            trend.add(Map.of("date", day.toString(),
                    "total", waylineJobMapper.selectCount(Wrappers.<WaylineJob>lambdaQuery()
                            .isNotNull(WaylineJob::getBeginAt)
                            .between(WaylineJob::getBeginAt, day.atStartOfDay(), day.atTime(LocalTime.MAX)))));
        }

        Map<String, Object> kpi = new LinkedHashMap<>();
        kpi.put("total", total);
        kpi.put("today", today);
        kpi.put("totalMinutes", totalMin);
        kpi.put("successRate", total == 0 ? 0 : Math.round(success * 1000.0 / total) / 10.0);
        kpi.put("mediaTotal", mediaTotal);

        data.put("kpi", kpi);
        data.put("flights", flights);
        data.put("trend", trend);
        return data;
    }
}
