package com.emergency.inspection.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.emergency.inspection.common.BizException;
import com.emergency.inspection.common.PageUtil;
import com.emergency.inspection.dji.DjiMessage;
import com.emergency.inspection.dto.query.WaylineJobQuery;
import com.emergency.inspection.entity.Device;
import com.emergency.inspection.entity.DeviceCommand;
import com.emergency.inspection.entity.Wayline;
import com.emergency.inspection.entity.WaylineJob;
import com.emergency.inspection.mapper.DeviceMapper;
import com.emergency.inspection.mapper.WaylineJobMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 航线飞行任务编排,对齐上云 API 的 flighttask 三段式:
 * 1. prepare —— 任务下发(定时任务带 execute_time,机场到点前保持就绪)
 * 2. execute —— 触发执行(立即任务在机场确认 prepare 后自动连发;断点续飞携带 breakpoint)
 * 3. progress —— 机场事件流驱动状态机 SENT→READY→QUEUED→RUNNING→SUCCESS/FAILED/CANCELED
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WaylineJobService {

    private static final Set<WaylineJob.Status> ACTIVE = Set.of(
            WaylineJob.Status.SENT, WaylineJob.Status.READY,
            WaylineJob.Status.QUEUED, WaylineJob.Status.RUNNING, WaylineJob.Status.PAUSED);
    private static final Set<WaylineJob.Status> RESUMABLE = Set.of(
            WaylineJob.Status.FAILED, WaylineJob.Status.CANCELED);

    private final WaylineJobMapper jobMapper;
    private final WaylineService waylineService;
    private final DeviceService deviceService;
    private final DeviceMapper deviceMapper;
    private final DeviceCommandService commandService;
    private final ObjectMapper objectMapper;

    /* ==================== 查询 ==================== */

    public IPage<WaylineJob> page(WaylineJobQuery query) {
        IPage<WaylineJob> page = jobMapper.selectPage(
                PageUtil.build(query, "create_time",
                        PageUtil.allowedCamel("status", "progress", "executeTime", "createTime")),
                Wrappers.<WaylineJob>lambdaQuery()
                        .eq(query.getDockSn() != null && !query.getDockSn().isBlank(),
                                WaylineJob::getDockSn, query.getDockSn())
                        .eq(query.getStatus() != null, WaylineJob::getStatus, query.getStatus()));
        fillDockNames(page.getRecords());
        return page;
    }

    public WaylineJob require(Long id) {
        WaylineJob job = jobMapper.selectById(id);
        if (job == null) {
            throw BizException.of("飞行任务不存在: " + id);
        }
        fillDockNames(List.of(job));
        return job;
    }

    /* ==================== 创建与下发 ==================== */

    /**
     * 创建任务并下发 prepare。
     * IMMEDIATE:机场确认后自动 execute;TIMED:机场就绪,由定时器到点触发 execute;
     * CONDITION(Dock 3):机场监听 ready_conditions,条件满足后发 flighttask_ready,云端据此 execute。
     */
    public WaylineJob create(Long dockId, Long waylineId, WaylineJob.JobType jobType, LocalDateTime executeTime,
                             Integer rthAltitude, String readyConditionsJson) {
        Device dock = deviceService.require(dockId);
        if (dock.getDeviceType() != Device.DeviceType.DOCK) {
            throw BizException.of("航线任务只能下发给机场");
        }
        if (jobType == null) {
            throw BizException.of("请选择任务类型(立即 / 定时 / 条件)");
        }
        if (jobType == WaylineJob.JobType.TIMED && executeTime == null) {
            throw BizException.of("定时任务必须指定执行时间");
        }
        Map<String, Object> readyConditions = parseReadyConditions(readyConditionsJson);
        if (jobType == WaylineJob.JobType.CONDITION && readyConditions.isEmpty()) {
            throw BizException.of("条件任务必须至少配置一个就绪条件(电量 / 时间窗)");
        }
        Wayline wayline = waylineService.require(waylineId);
        long active = jobMapper.selectCount(Wrappers.<WaylineJob>lambdaQuery()
                .eq(WaylineJob::getDockSn, dock.getDeviceSn()).in(WaylineJob::getStatus, ACTIVE));
        if (active > 0) {
            throw BizException.of("该机场已有进行中的飞行任务,请先取消或等待完成");
        }

        WaylineJob job = new WaylineJob();
        job.setFlightId(UUID.randomUUID().toString().replace("-", ""));
        job.setDockSn(dock.getDeviceSn());
        job.setDroneSn(firstDroneSn(dock.getDeviceSn()));
        job.setWaylineId(wayline.getId());
        job.setWaylineName(wayline.getName());
        job.setJobType(jobType);
        job.setJobChannel(WaylineJob.JobChannel.FLIGHTTASK);
        job.setExecuteTime(executeTime);
        job.setRthAltitude(rthAltitude);
        job.setReadyConditionsJson(readyConditions.isEmpty() ? null : readyConditionsJson.trim());
        job.setStatus(WaylineJob.Status.SENT);
        job.setProgress(0);
        job.setMediaCount(0);
        jobMapper.insert(job);

        // prepare 载荷:任务类型 + 航线文件句柄 + 失控保护 / 返航参数
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("file", Map.of(
                "url", "http://oss.local/waylines/" + wayline.getCode() + ".kmz",
                "fingerprint", md5Of(wayline)));
        params.put("out_of_control_action", 0);      // 失控返航
        params.put("exit_wayline_when_lost", 0);     // 信号丢失不退出航线
        params.put("progress_level", 2);             // 全量进度上报
        if (rthAltitude != null) {
            params.put("rth_altitude", rthAltitude);
        }
        if (!readyConditions.isEmpty()) {
            params.put("ready_conditions", readyConditions);
        }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("flight_id", job.getFlightId());
        data.put("task_type", jobType.protocolValue());
        if (jobType == WaylineJob.JobType.TIMED) {
            data.put("execute_time", executeTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli());
        }
        data.put("flighttask_params", params);

        try {
            DeviceCommand cmd = commandService.send(dock.getId(), "flighttask_prepare", data);
            job.setPrepareTid(cmd.getTid());
            job.setDispatchedAt(LocalDateTime.now());
            jobMapper.updateById(job);
        } catch (BizException e) {
            // 下发失败保留任务行作为审计,状态直接结案
            job.setStatus(WaylineJob.Status.FAILED);
            job.setErrorMsg(e.getMessage());
            job.setEndAt(LocalDateTime.now());
            jobMapper.updateById(job);
            throw e;
        }
        log.info("航线任务下发 prepare: flightId={} dock={} wayline={} type={}",
                job.getFlightId(), dock.getDeviceSn(), wayline.getName(), jobType);
        return require(job.getId());
    }

    /** 暂停执行中的任务(Dock 3 flighttask_pause),设备随后回报 pause 进度 */
    public WaylineJob pause(Long id) {
        WaylineJob job = require(id);
        if (job.getStatus() != WaylineJob.Status.RUNNING && job.getStatus() != WaylineJob.Status.QUEUED) {
            throw BizException.of("仅执行中的任务支持暂停");
        }
        commandService.send(deviceIdOf(job.getDockSn()), "flighttask_pause",
                Map.of("flight_id", job.getFlightId()));
        job.setStatus(WaylineJob.Status.PAUSED);
        jobMapper.updateById(job);
        return job;
    }

    /** 恢复已暂停的任务(Dock 3 flighttask_recovery) */
    public WaylineJob recovery(Long id) {
        WaylineJob job = require(id);
        if (job.getStatus() != WaylineJob.Status.PAUSED) {
            throw BizException.of("仅已暂停的任务支持恢复");
        }
        commandService.send(deviceIdOf(job.getDockSn()), "flighttask_recovery",
                Map.of("flight_id", job.getFlightId()));
        job.setStatus(WaylineJob.Status.RUNNING);
        jobMapper.updateById(job);
        return job;
    }

    /** 一键返航:对任务所属机场下发 return_home(与指令单元的 RTH 同源,挂在任务上便于操作) */
    public WaylineJob returnHome(Long id) {
        WaylineJob job = require(id);
        if (job.getStatus() != WaylineJob.Status.RUNNING && job.getStatus() != WaylineJob.Status.PAUSED) {
            throw BizException.of("任务不在执行中,无需返航");
        }
        commandService.send(deviceIdOf(job.getDockSn()), "return_home", Map.of());
        return job;
    }

    /**
     * 空中下发航线(Dock 3 in_flight_wayline):跳过 prepare/execute,
     * 直接向飞行中的飞行器投递新航线,进度走 in_flight_wayline_progress 事件。
     */
    public WaylineJob createInFlight(Long dockId, Long waylineId) {
        Device dock = deviceService.require(dockId);
        if (dock.getDeviceType() != Device.DeviceType.DOCK) {
            throw BizException.of("航线任务只能下发给机场");
        }
        Wayline wayline = waylineService.require(waylineId);
        long active = jobMapper.selectCount(Wrappers.<WaylineJob>lambdaQuery()
                .eq(WaylineJob::getDockSn, dock.getDeviceSn()).in(WaylineJob::getStatus, ACTIVE));
        if (active > 0) {
            throw BizException.of("该机场已有进行中的飞行任务,请先取消或等待完成");
        }

        WaylineJob job = new WaylineJob();
        job.setFlightId(UUID.randomUUID().toString().replace("-", ""));
        job.setDockSn(dock.getDeviceSn());
        job.setDroneSn(firstDroneSn(dock.getDeviceSn()));
        job.setWaylineId(wayline.getId());
        job.setWaylineName(wayline.getName());
        job.setJobType(WaylineJob.JobType.IMMEDIATE);
        job.setJobChannel(WaylineJob.JobChannel.IN_FLIGHT);
        job.setStatus(WaylineJob.Status.SENT);
        job.setProgress(0);
        job.setMediaCount(0);
        jobMapper.insert(job);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("flight_id", job.getFlightId());
        data.put("file", Map.of(
                "url", "http://oss.local/waylines/" + wayline.getCode() + ".kmz",
                "fingerprint", md5Of(wayline)));
        try {
            DeviceCommand cmd = commandService.send(dock.getId(), "in_flight_wayline_deliver", data);
            job.setPrepareTid(cmd.getTid());
            job.setDispatchedAt(LocalDateTime.now());
            jobMapper.updateById(job);
        } catch (BizException e) {
            job.setStatus(WaylineJob.Status.FAILED);
            job.setErrorMsg(e.getMessage());
            job.setEndAt(LocalDateTime.now());
            jobMapper.updateById(job);
            throw e;
        }
        log.info("空中下发航线: flightId={} dock={} wayline={}", job.getFlightId(), dock.getDeviceSn(), wayline.getName());
        return require(job.getId());
    }

    /** 空中航线任务动作:stop(暂停悬停)/ recover(恢复)/ cancel(取消并返航) */
    public WaylineJob inFlightAction(Long id, String action) {
        WaylineJob job = require(id);
        if (job.getJobChannel() != WaylineJob.JobChannel.IN_FLIGHT) {
            throw BizException.of("仅空中下发的任务支持该操作");
        }
        String method = switch (action) {
            case "stop" -> "in_flight_wayline_stop";
            case "recover" -> "in_flight_wayline_recover";
            case "cancel" -> "in_flight_wayline_cancel";
            default -> null;
        };
        if (method == null || !ACTIVE.contains(job.getStatus())) {
            throw BizException.of("任务当前状态不支持该操作");
        }
        commandService.send(deviceIdOf(job.getDockSn()), method, Map.of("flight_id", job.getFlightId()));
        if ("cancel".equals(action)) {
            job.setStatus(WaylineJob.Status.CANCELED);
            job.setEndAt(LocalDateTime.now());
            jobMapper.updateById(job);
        }
        return job;
    }

    /** 取消任务:下发 undo,指令送达即置 CANCELED(设备随后也会回 progress cancel 事件) */
    public WaylineJob undo(Long id) {
        WaylineJob job = require(id);
        if (!ACTIVE.contains(job.getStatus())) {
            throw BizException.of("任务已结束,无法取消");
        }
        String method = job.getJobChannel() == WaylineJob.JobChannel.IN_FLIGHT
                ? "in_flight_wayline_cancel" : "flighttask_undo";
        commandService.send(deviceIdOf(job.getDockSn()), method,
                Map.of("flight_id", job.getFlightId()));
        job.setStatus(WaylineJob.Status.CANCELED);
        job.setEndAt(LocalDateTime.now());
        jobMapper.updateById(job);
        return job;
    }

    /** 断点续飞:携带设备上报的 breakpoint 重新 execute;无断点则整段重飞 */
    public WaylineJob resume(Long id) {
        WaylineJob job = require(id);
        if (!RESUMABLE.contains(job.getStatus())) {
            throw BizException.of("仅失败或已取消的任务支持断点续飞");
        }
        if (job.getJobChannel() == WaylineJob.JobChannel.IN_FLIGHT) {
            throw BizException.of("空中下发的任务不支持断点续飞,请重新下发");
        }
        job.setStatus(WaylineJob.Status.SENT);
        execute(job, true);
        return require(id);
    }

    /* ==================== 设备侧回调 ==================== */

    /** prepare 的 services_reply:机场确认就绪 → 定时任务置 READY,立即任务直接 execute */
    public void onPrepareReply(String gatewaySn, DjiMessage message) {
        WaylineJob job = jobMapper.selectOne(Wrappers.<WaylineJob>lambdaQuery()
                .eq(WaylineJob::getPrepareTid, message.tid()).last("limit 1"));
        if (job == null) {
            return;
        }
        if (!message.success()) {
            job.setStatus(WaylineJob.Status.FAILED);
            job.setErrorMsg("机场拒绝任务: " + message.data());
            job.setEndAt(LocalDateTime.now());
            jobMapper.updateById(job);
            return;
        }
        if (job.getJobType() == WaylineJob.JobType.TIMED) {
            job.setStatus(WaylineJob.Status.READY);
            jobMapper.updateById(job);
            log.info("定时任务就绪: flightId={} 计划执行 {}", job.getFlightId(), job.getExecuteTime());
        } else if (job.getJobType() == WaylineJob.JobType.CONDITION) {
            // 条件任务:机场已受理,开始监听 ready_conditions;满足后设备发 flighttask_ready
            log.info("条件任务受理: flightId={} 条件 {}", job.getFlightId(), job.getReadyConditionsJson());
        } else {
            execute(job, false);
        }
    }

    /** flighttask_ready 事件(Dock 3 条件任务):设备条件满足,云端随即触发 execute */
    @Transactional
    public void onFlighttaskReady(String gatewaySn, JsonNode data) {
        if (data == null || !data.hasNonNull("flight_id")) {
            return;
        }
        WaylineJob job = jobMapper.selectOne(Wrappers.<WaylineJob>lambdaQuery()
                .eq(WaylineJob::getFlightId, data.get("flight_id").asText()).last("limit 1"));
        if (job == null || job.getStatus() != WaylineJob.Status.SENT) {
            return;
        }
        job.setStatus(WaylineJob.Status.READY);
        jobMapper.updateById(job);
        execute(job, false);
        log.info("条件任务就绪并触发执行: flightId={}", job.getFlightId());
    }

    /** return_home_info 事件:返航前的规划轨迹上报,存任务上供前端展示 */
    @Transactional
    public void onReturnHomeInfo(String gatewaySn, JsonNode data) {
        if (data == null || !data.hasNonNull("flight_id") || !data.has("planning_path")) {
            return;
        }
        WaylineJob job = jobMapper.selectOne(Wrappers.<WaylineJob>lambdaQuery()
                .eq(WaylineJob::getFlightId, data.get("flight_id").asText()).last("limit 1"));
        if (job == null) {
            return;
        }
        job.setReturnHomeJson(data.get("planning_path").toString());
        jobMapper.updateById(job);
        log.info("返航轨迹上报: flightId={} {} 个规划点", job.getFlightId(), data.get("planning_path").size());
    }

    /** flighttask_progress / in_flight_wayline_progress 事件:任务状态机的主驱动 */
    @Transactional
    public void onProgress(String gatewaySn, JsonNode data) {
        if (data == null || !data.hasNonNull("flight_id")) {
            return;
        }
        WaylineJob job = jobMapper.selectOne(Wrappers.<WaylineJob>lambdaQuery()
                .eq(WaylineJob::getFlightId, data.get("flight_id").asText()).last("limit 1"));
        if (job == null) {
            log.warn("未知 flight_id 的任务进度: {}", data.get("flight_id").asText());
            return;
        }
        String status = data.hasNonNull("status") ? data.get("status").asText() : "";
        int progress = data.hasNonNull("progress") ? data.get("progress").asInt() : job.getProgress();
        switch (status) {
            case "sent", "queued" -> {
                job.setStatus(WaylineJob.Status.QUEUED);
                job.setProgress(Math.min(progress, 100));
            }
            case "in_progress" -> {
                job.setStatus(WaylineJob.Status.RUNNING);
                job.setProgress(Math.min(progress, 100));
                if (data.hasNonNull("current_step")) {
                    job.setCurrentStep(data.get("current_step").asInt());
                }
                if (job.getBeginAt() == null) {
                    job.setBeginAt(LocalDateTime.now());
                }
            }
            case "pause" -> {
                job.setStatus(WaylineJob.Status.PAUSED);
                job.setProgress(Math.min(progress, 100));
            }
            case "recovering" -> {
                job.setStatus(WaylineJob.Status.RUNNING);
                job.setProgress(Math.min(progress, 100));
            }
            case "ok" -> {
                job.setStatus(WaylineJob.Status.SUCCESS);
                job.setProgress(100);
                job.setEndAt(LocalDateTime.now());
                if (data.hasNonNull("media_count")) {
                    job.setMediaCount(job.getMediaCount() + data.get("media_count").asInt());
                }
            }
            case "failed" -> {
                job.setStatus(WaylineJob.Status.FAILED);
                job.setProgress(Math.min(progress, 100));
                job.setErrorMsg(data.hasNonNull("message") ? data.get("message").asText() : "设备报告任务失败");
                job.setEndAt(LocalDateTime.now());
            }
            case "cancel" -> {
                job.setStatus(WaylineJob.Status.CANCELED);
                job.setEndAt(LocalDateTime.now());
            }
            default -> {
                return;
            }
        }
        if (data.hasNonNull("breakpoint")) {
            job.setBreakpointJson(data.get("breakpoint").toString());
        }
        jobMapper.updateById(job);
        log.info("航线任务进度: flightId={} status={} progress={}%", job.getFlightId(), status, job.getProgress());
    }

    /* ==================== 设备请求应答(requests) ==================== */

    /**
     * flighttask_resource_get 请求:设备要航线文件句柄(条件任务就绪后主动拉取)。
     * 返回 requests_reply 的 data 载荷;找不到任务时返回 null,由上层回拒绝。
     */
    public Map<String, Object> onResourceGet(String gatewaySn, JsonNode data) {
        String flightId = data == null || !data.hasNonNull("flight_id") ? null : data.get("flight_id").asText();
        WaylineJob job = flightId == null ? null : jobMapper.selectOne(
                Wrappers.<WaylineJob>lambdaQuery().eq(WaylineJob::getFlightId, flightId).last("limit 1"));
        if (job == null || job.getWaylineId() == null) {
            return null;
        }
        Wayline wayline = waylineService.require(job.getWaylineId());
        return Map.of("file", Map.of(
                "url", "http://oss.local/waylines/" + wayline.getCode() + ".kmz",
                "fingerprint", md5Of(wayline)));
    }

    /** flighttask_progress_get 请求(蛙跳多机场):回该机场当前活跃任务概况 */
    public Map<String, Object> onProgressGet(String gatewaySn) {
        WaylineJob job = jobMapper.selectOne(Wrappers.<WaylineJob>lambdaQuery()
                .eq(WaylineJob::getDockSn, gatewaySn).in(WaylineJob::getStatus, ACTIVE)
                .orderByDesc(WaylineJob::getId).last("limit 1"));
        if (job == null) {
            return Map.of("job", Map.of());
        }
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("flight_id", job.getFlightId());
        summary.put("status", job.getStatus().name());
        summary.put("progress", job.getProgress());
        return Map.of("job", summary);
    }

    /* ==================== 定时触发 ==================== */

    /** 定时任务到点触发 execute;设备暂不在线则下个周期自然重试 */
    @Scheduled(fixedDelay = 5_000, initialDelay = 15_000)
    public void triggerTimed() {
        List<WaylineJob> due = jobMapper.selectList(Wrappers.<WaylineJob>lambdaQuery()
                .eq(WaylineJob::getStatus, WaylineJob.Status.READY)
                .le(WaylineJob::getExecuteTime, LocalDateTime.now()));
        for (WaylineJob job : due) {
            try {
                execute(job, false);
                log.info("定时任务到点触发: flightId={}", job.getFlightId());
            } catch (Exception e) {
                log.warn("定时任务触发失败(下周期重试): flightId={} {}", job.getFlightId(), e.getMessage());
            }
        }
    }

    /* ==================== 内部 ==================== */

    /** 解析条件任务就绪条件 JSON;非法内容直接拒单,不让坏条件流到设备 */
    @SuppressWarnings("unchecked")
    private Map<String, Object> parseReadyConditions(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            Map<String, Object> parsed = objectMapper.readValue(json.trim(), Map.class);
            return parsed == null ? Map.of() : parsed;
        } catch (Exception e) {
            throw BizException.of("就绪条件不是合法 JSON:{\"battery_capacity\":80,\"begin_time\":ms,\"end_time\":ms}");
        }
    }

    /** 下发 execute(resume=true 时携带断点);成功送达即置 QUEUED,后续由进度事件驱动 */
    private void execute(WaylineJob job, boolean resume) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("flight_id", job.getFlightId());
        if (resume && job.getBreakpointJson() != null && !job.getBreakpointJson().isBlank()) {
            try {
                data.put("breakpoint", objectMapper.readValue(job.getBreakpointJson(), Map.class));
            } catch (Exception ignored) {
                // 断点损坏则整段重飞
            }
        }
        try {
            commandService.send(deviceIdOf(job.getDockSn()), "flighttask_execute", data);
            job.setStatus(WaylineJob.Status.QUEUED);
            jobMapper.updateById(job);
        } catch (BizException e) {
            if (resume) {
                throw e;   // 续飞是用户动作,失败要如实报出
            }
            log.warn("execute 下发失败: flightId={} {}", job.getFlightId(), e.getMessage());
        }
    }

    private Long deviceIdOf(String dockSn) {
        Device dock = deviceService.findBySn(dockSn);
        if (dock == null) {
            throw BizException.of("机场不存在: " + dockSn);
        }
        return dock.getId();
    }

    private String firstDroneSn(String dockSn) {
        Device child = deviceMapper.selectOne(Wrappers.<Device>lambdaQuery()
                .eq(Device::getGatewaySn, dockSn).orderByAsc(Device::getId).last("limit 1"));
        return child == null ? null : child.getDeviceSn();
    }

    private String md5Of(Wayline wayline) {
        // 简化指纹:航线标识 + 航点数,设备侧只做一致性比对
        return Integer.toHexString((wayline.getCode() + wayline.getWaypointsJson()).hashCode());
    }

    private void fillDockNames(List<WaylineJob> jobs) {
        if (jobs == null || jobs.isEmpty()) {
            return;
        }
        Map<String, Device> docks = deviceMapper.selectList(Wrappers.<Device>lambdaQuery()
                        .in(Device::getDeviceSn, jobs.stream().map(WaylineJob::getDockSn).toList()))
                .stream().collect(Collectors.toMap(Device::getDeviceSn, Function.identity(), (a, b) -> a));
        for (WaylineJob job : jobs) {
            Device dock = docks.get(job.getDockSn());
            job.setDockName(dock == null ? job.getDockSn() : dock.getName());
        }
    }
}
