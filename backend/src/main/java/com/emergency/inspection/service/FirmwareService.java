package com.emergency.inspection.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.emergency.inspection.common.BizException;
import com.emergency.inspection.common.PageUtil;
import com.emergency.inspection.dto.query.FirmwareTaskQuery;
import com.emergency.inspection.entity.Device;
import com.emergency.inspection.entity.DeviceCommand;
import com.emergency.inspection.entity.Firmware;
import com.emergency.inspection.entity.FirmwareTask;
import com.emergency.inspection.mapper.DeviceMapper;
import com.emergency.inspection.mapper.FirmwareMapper;
import com.emergency.inspection.mapper.FirmwareTaskMapper;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 固件升级:固件库管理 + OTA 下发。
 * 云端把固件包句柄(file_url / md5 / size)经 ota_create 交给设备,
 * 设备侧下载升级并按 ota_progress 事件回报进度;升级成功后设备固件版本随之刷新。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FirmwareService {

    private static final List<FirmwareTask.Status> ACTIVE =
            List.of(FirmwareTask.Status.SENT, FirmwareTask.Status.DOWNLOADING, FirmwareTask.Status.UPGRADING);

    private final FirmwareMapper firmwareMapper;
    private final FirmwareTaskMapper taskMapper;
    private final DeviceService deviceService;
    private final DeviceMapper deviceMapper;
    private final DeviceCommandService commandService;

    /* ==================== 固件库 ==================== */

    public List<Firmware> listAll() {
        return firmwareMapper.selectList(Wrappers.<Firmware>lambdaQuery().orderByDesc(Firmware::getId));
    }

    public Firmware require(Long id) {
        Firmware firmware = firmwareMapper.selectById(id);
        if (firmware == null) {
            throw BizException.of("固件不存在: " + id);
        }
        return firmware;
    }

    @Transactional
    public Firmware create(Firmware body) {
        validate(body);
        body.setId(null);
        firmwareMapper.insert(body);
        return body;
    }

    @Transactional
    public Firmware update(Long id, Firmware body) {
        Firmware firmware = require(id);
        // 部分字段修改:仅校验显式传入的版本号
        if (body.getVersion() != null && body.getVersion().isBlank()) {
            throw BizException.of("固件版本号不能为空");
        }
        if (body.getProductType() != null) {
            firmware.setProductType(body.getProductType());
        }
        if (body.getDeviceModel() != null) {
            firmware.setDeviceModel(body.getDeviceModel());
        }
        if (body.getVersion() != null) {
            firmware.setVersion(body.getVersion());
        }
        if (body.getFileName() != null) {
            firmware.setFileName(body.getFileName());
        }
        if (body.getFileSize() != null) {
            firmware.setFileSize(body.getFileSize());
        }
        if (body.getFileMd5() != null) {
            firmware.setFileMd5(body.getFileMd5());
        }
        if (body.getFileUrl() != null) {
            firmware.setFileUrl(body.getFileUrl());
        }
        if (body.getRemark() != null) {
            firmware.setRemark(body.getRemark());
        }
        firmwareMapper.updateById(firmware);
        return firmware;
    }

    public void delete(Long id) {
        firmwareMapper.deleteById(require(id).getId());
    }

    /* ==================== OTA 下发 ==================== */

    /** 批量下发升级:逐台创建任务并 ota_create;离线设备任务直接结案为 FAILED */
    public List<FirmwareTask> deploy(Long firmwareId, List<Long> deviceIds) {
        Firmware firmware = require(firmwareId);
        if (deviceIds == null || deviceIds.isEmpty()) {
            throw BizException.of("请选择要升级的设备");
        }
        List<FirmwareTask> tasks = new ArrayList<>();
        for (Long deviceId : deviceIds) {
            Device device = deviceService.require(deviceId);
            FirmwareTask task = new FirmwareTask();
            task.setFirmwareId(firmware.getId());
            task.setFirmwareVersion(firmware.getVersion());
            task.setDeviceSn(device.getDeviceSn());
            task.setDeviceName(device.getName());
            task.setStatus(FirmwareTask.Status.SENT);
            task.setProgress(0);
            taskMapper.insert(task);

            Map<String, Object> data = new LinkedHashMap<>();
            data.put("file_url", firmware.getFileUrl());
            data.put("file_md5", firmware.getFileMd5());
            data.put("file_size", firmware.getFileSize());
            data.put("product_type", firmware.getProductType().name());
            try {
                commandService.send(device.getId(), "ota_create", data);
            } catch (BizException e) {
                task.setStatus(FirmwareTask.Status.FAILED);
                task.setMessage(e.getMessage());
                task.setFinishedAt(LocalDateTime.now());
                taskMapper.updateById(task);
            }
            tasks.add(task);
        }
        return tasks;
    }

    public IPage<FirmwareTask> taskPage(FirmwareTaskQuery query) {
        return taskMapper.selectPage(
                PageUtil.build(query, "create_time",
                        PageUtil.allowedCamel("status", "progress", "createTime")),
                Wrappers.<FirmwareTask>lambdaQuery()
                        .eq(query.getDeviceSn() != null && !query.getDeviceSn().isBlank(),
                                FirmwareTask::getDeviceSn, query.getDeviceSn())
                        .eq(query.getStatus() != null, FirmwareTask::getStatus, query.getStatus()));
    }

    /* ==================== 设备侧回调 ==================== */

    /** ota_progress 事件:同一设备的最新活跃升级任务承接进度 */
    @Transactional
    public void onProgress(String deviceSn, JsonNode data) {
        if (data == null) {
            return;
        }
        FirmwareTask task = taskMapper.selectOne(Wrappers.<FirmwareTask>lambdaQuery()
                .eq(FirmwareTask::getDeviceSn, deviceSn).in(FirmwareTask::getStatus, ACTIVE)
                .orderByDesc(FirmwareTask::getId).last("limit 1"));
        if (task == null) {
            log.warn("未找到设备 {} 的活跃升级任务,忽略 ota_progress", deviceSn);
            return;
        }
        String status = data.hasNonNull("status") ? data.get("status").asText() : "";
        int progress = data.hasNonNull("progress") ? data.get("progress").asInt() : task.getProgress();
        switch (status) {
            case "sent" -> task.setStatus(FirmwareTask.Status.SENT);
            case "downloading" -> task.setStatus(FirmwareTask.Status.DOWNLOADING);
            case "upgrading" -> task.setStatus(FirmwareTask.Status.UPGRADING);
            case "success" -> {
                task.setStatus(FirmwareTask.Status.SUCCESS);
                task.setProgress(100);
                task.setFinishedAt(LocalDateTime.now());
                applyNewVersion(deviceSn, task.getFirmwareVersion());
            }
            case "failed" -> {
                task.setStatus(FirmwareTask.Status.FAILED);
                task.setFinishedAt(LocalDateTime.now());
            }
            default -> {
                return;
            }
        }
        if (task.getStatus() != FirmwareTask.Status.SUCCESS) {
            task.setProgress(Math.max(Math.min(progress, 100), 0));
        }
        if (data.hasNonNull("message")) {
            task.setMessage(data.get("message").asText());
        }
        taskMapper.updateById(task);
        log.info("固件升级进度: device={} status={} progress={}%", deviceSn, status, task.getProgress());
    }

    /* ==================== 内部 ==================== */

    private void validate(Firmware body) {
        if (body.getVersion() == null || body.getVersion().isBlank()) {
            throw BizException.of("固件版本号不能为空");
        }
        if (body.getProductType() == null) {
            throw BizException.of("请选择固件类型(机场 / 飞行器)");
        }
    }

    /** 升级成功后把设备台账的固件版本刷成新版本 */
    private void applyNewVersion(String deviceSn, String version) {
        if (version == null) {
            return;
        }
        Device device = deviceService.findBySn(deviceSn);
        if (device != null) {
            device.setFirmwareVersion(version);
            deviceMapper.updateById(device);
        }
    }
}
