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
            WaylineJob.Status.QUEUED, WaylineJob.Status.RUNNING);
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
     * IMMEDIATE:机场确认后自动 execute;TIMED:机场就绪,由定时器到点触发 execute。
     */
    public WaylineJob create(Long dockId, Long waylineId, WaylineJob.JobType jobType, LocalDateTime executeTime) {
        Device dock = deviceService.require(dockId);
        if (dock.getDeviceType() != Device.DeviceType.DOCK) {
            throw BizException.of("航线任务只能下发给机场");
        }
        if (jobType == null) {
            throw BizException.of("请选择任务类型(立即 / 定时)");
        }
        if (jobType == WaylineJob.JobType.TIMED && executeTime == null) {
            throw BizException.of("定时任务必须指定执行时间");
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
        job.setExecuteTime(executeTime);
        job.setStatus(WaylineJob.Status.SENT);
        job.setProgress(0);
        job.setMediaCount(0);
        jobMapper.insert(job);

        // prepare 载荷:航线文件句柄 + 失控保护参数
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("file", Map.of(
                "url", "http://oss.local/waylines/" + wayline.getCode() + ".kmz",
                "fingerprint", md5Of(wayline)));
        params.put("out_of_control_action", 0);      // 失控返航
        params.put("exit_wayline_when_lost", 0);     // 信号丢失不退出航线
        params.put("progress_level", 2);             // 全量进度上报

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("flight_id", job.getFlightId());
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
        log.info("航线任务下发 prepare: flightId={} dock={} wayline={}",
                job.getFlightId(), dock.getDeviceSn(), wayline.getName());
        return require(job.getId());
    }

    /** 取消任务:下发 undo,指令送达即置 CANCELED(设备随后也会回 progress cancel 事件) */
    public WaylineJob undo(Long id) {
        WaylineJob job = require(id);
        if (!ACTIVE.contains(job.getStatus())) {
            throw BizException.of("任务已结束,无法取消");
        }
        commandService.send(deviceIdOf(job.getDockSn()), "flighttask_undo",
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
        } else {
            execute(job, false);
        }
    }

    /** flighttask_progress 事件:任务状态机的主驱动 */
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
