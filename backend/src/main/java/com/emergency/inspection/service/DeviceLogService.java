package com.emergency.inspection.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.emergency.inspection.common.BizException;
import com.emergency.inspection.entity.DeviceLogFile;
import com.emergency.inspection.mapper.DeviceLogFileMapper;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 远程日志:设备侧日志文件清单的拉取与上传。
 * logs_file_list 的回复报文里带全量文件清单(经 services_reply 承载),
 * 上传由 logs_file_upload 触发,进度经 file_upload_progress 事件回报。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DeviceLogService {

    private final DeviceLogFileMapper logFileMapper;
    private final DeviceService deviceService;
    private final DeviceCommandService commandService;

    /** 拉取日志列表:指令异步回复后由 onFileListReply 落库,前端稍候刷新即可 */
    public void sync(Long deviceId) {
        commandService.send(deviceId, "logs_file_list", Map.of());
    }

    public List<DeviceLogFile> list(Long deviceId) {
        String sn = deviceService.require(deviceId).getDeviceSn();
        return logFileMapper.selectList(Wrappers.<DeviceLogFile>lambdaQuery()
                .eq(DeviceLogFile::getDeviceSn, sn)
                .orderByAsc(DeviceLogFile::getModule)
                .orderByDesc(DeviceLogFile::getFileTime));
    }

    /** 上传所选日志文件:逐个置 UPLOADING,一条指令批量触发 */
    @Transactional
    public void upload(Long deviceId, List<String> fileIds) {
        if (fileIds == null || fileIds.isEmpty()) {
            throw BizException.of("请选择要上传的日志文件");
        }
        String sn = deviceService.require(deviceId).getDeviceSn();
        List<Map<String, Object>> files = new ArrayList<>();
        for (String fileId : fileIds) {
            DeviceLogFile row = require(sn, fileId);
            if (row.getStatus() == DeviceLogFile.Status.UPLOADING) {
                throw BizException.of("文件上传中,请勿重复触发: " + row.getName());
            }
            if (row.getStatus() == DeviceLogFile.Status.UPLOADED) {
                continue;   // 已上传的静默跳过
            }
            row.setStatus(DeviceLogFile.Status.UPLOADING);
            row.setPercent(0);
            logFileMapper.updateById(row);
            files.add(Map.of("file_id", fileId));
        }
        if (files.isEmpty()) {
            throw BizException.of("所选文件均已上传");
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("files", files);
        commandService.send(deviceId, "logs_file_upload", data);
    }

    /* ==================== 设备侧回调 ==================== */

    /** logs_file_list 的 services_reply:全量文件清单 upsert(保留既有上传状态) */
    @Transactional
    public void onFileListReply(String gatewaySn, JsonNode data) {
        JsonNode files = data == null ? null : data.get("files");
        if (files == null || !files.isArray() || files.isEmpty()) {
            log.info("设备 {} 日志清单为空", gatewaySn);
            return;
        }
        for (JsonNode file : files) {
            String fileId = text(file, "file_id");
            if (fileId == null) {
                continue;
            }
            DeviceLogFile row = logFileMapper.selectOne(Wrappers.<DeviceLogFile>lambdaQuery()
                    .eq(DeviceLogFile::getDeviceSn, gatewaySn)
                    .eq(DeviceLogFile::getFileId, fileId).last("limit 1"));
            boolean isNew = row == null;
            if (isNew) {
                row = new DeviceLogFile();
                row.setDeviceSn(gatewaySn);
                row.setFileId(fileId);
                row.setStatus(DeviceLogFile.Status.FOUND);
                row.setPercent(0);
            }
            row.setName(text(file, "name"));
            row.setModule(moduleOf(text(file, "module")));
            row.setSize(file.hasNonNull("size") ? file.get("size").asLong() : null);
            row.setFileTime(timeOf(file));
            if (isNew) {
                logFileMapper.insert(row);
            } else {
                logFileMapper.updateById(row);
            }
        }
        log.info("设备 {} 日志清单已同步: {} 个文件", gatewaySn, files.size());
    }

    /** 上传进度事件:按 (设备, file_id) 回写百分比与状态 */
    @Transactional
    public void onUploadProgress(String gatewaySn, JsonNode data) {
        if (data == null || !data.hasNonNull("file_id")) {
            return;
        }
        DeviceLogFile row = require(gatewaySn, data.get("file_id").asText());
        int progress = data.hasNonNull("progress") ? data.get("progress").asInt() : row.getPercent();
        String status = text(data, "status");
        if ("failed".equals(status)) {
            row.setStatus(DeviceLogFile.Status.FAILED);
        } else if (progress >= 100) {
            row.setStatus(DeviceLogFile.Status.UPLOADED);
            row.setPercent(100);
            row.setObjectKey("logs/" + gatewaySn + "/" + row.getFileId() + ".tar.gz");
            logFileMapper.updateById(row);
            log.info("日志上传完成: {} → {}", row.getName(), row.getObjectKey());
            return;
        } else {
            row.setStatus(DeviceLogFile.Status.UPLOADING);
        }
        row.setPercent(Math.max(Math.min(progress, 100), 0));
        logFileMapper.updateById(row);
    }

    /* ==================== 内部 ==================== */

    private DeviceLogFile require(String sn, String fileId) {
        DeviceLogFile row = logFileMapper.selectOne(Wrappers.<DeviceLogFile>lambdaQuery()
                .eq(DeviceLogFile::getDeviceSn, sn)
                .eq(DeviceLogFile::getFileId, fileId).last("limit 1"));
        if (row == null) {
            throw BizException.of("日志文件不存在: " + fileId);
        }
        return row;
    }

    private static DeviceLogFile.Module moduleOf(String module) {
        return "DRONE".equals(module) ? DeviceLogFile.Module.DRONE : DeviceLogFile.Module.DOCK;
    }

    private static LocalDateTime timeOf(JsonNode file) {
        if (!file.hasNonNull("time")) {
            return null;
        }
        try {
            return LocalDateTime.ofInstant(Instant.ofEpochMilli(file.get("time").asLong()), ZoneId.systemDefault());
        } catch (Exception e) {
            return null;
        }
    }

    private static String text(JsonNode node, String field) {
        return node != null && node.hasNonNull(field) ? node.get(field).asText() : null;
    }
}
