package com.emergency.inspection.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.emergency.inspection.common.BizException;
import com.emergency.inspection.common.PageUtil;
import com.emergency.inspection.dto.query.MediaFileQuery;
import com.emergency.inspection.entity.Device;
import com.emergency.inspection.entity.DeviceMediaFile;
import com.emergency.inspection.entity.DeviceMediaPriority;
import com.emergency.inspection.entity.WaylineJob;
import com.emergency.inspection.mapper.DeviceMediaFileMapper;
import com.emergency.inspection.mapper.DeviceMediaPriorityMapper;
import com.emergency.inspection.mapper.WaylineJobMapper;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 媒体管理(对齐 Dock 3 media 协议):
 * - 设备任务结束后经 requests 要存储配置(storage_config_get),本服务回模拟对象存储句柄
 * - 设备上传完成逐个发 file_upload_callback 事件 → 落 device_media_file,并回填任务媒体数
 * - 设备上报当前优先上传任务(highest_priority 事件),云端可经 upload_flighttask_media_prioritize 改写
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MediaService {

    private final DeviceMediaFileMapper fileMapper;
    private final DeviceMediaPriorityMapper priorityMapper;
    private final WaylineJobMapper jobMapper;
    private final DeviceService deviceService;
    private final DeviceCommandService commandService;

    /* ==================== 查询 ==================== */

    public IPage<DeviceMediaFile> page(MediaFileQuery query) {
        IPage<DeviceMediaFile> page = fileMapper.selectPage(
                PageUtil.build(query, "create_time",
                        PageUtil.allowedCamel("isOriginal", "takenAt", "createTime")),
                Wrappers.<DeviceMediaFile>lambdaQuery()
                        .eq(query.getDeviceSn() != null && !query.getDeviceSn().isBlank(),
                                DeviceMediaFile::getDeviceSn, query.getDeviceSn())
                        .eq(query.getFlightId() != null && !query.getFlightId().isBlank(),
                                DeviceMediaFile::getFlightId, query.getFlightId())
                        .eq(query.getIsOriginal() != null, DeviceMediaFile::getIsOriginal, query.getIsOriginal()));
        fillFlightNames(page.getRecords());
        return page;
    }

    /** 设备当前优先上传媒体的任务 */
    public DeviceMediaPriority priorityOf(String deviceSn) {
        return priorityMapper.selectOne(Wrappers.<DeviceMediaPriority>lambdaQuery()
                .eq(DeviceMediaPriority::getDeviceSn, deviceSn).last("limit 1"));
    }

    /* ==================== 优先上传 ==================== */

    /** 指定某任务的媒体优先上传:下发 upload_flighttask_media_prioritize 并同步优先级台账 */
    public DeviceMediaPriority prioritize(Long deviceId, String flightId) {
        Device dock = deviceService.require(deviceId);
        if (dock.getDeviceType() != Device.DeviceType.DOCK) {
            throw BizException.of("媒体指令只能下发给机场");
        }
        if (flightId == null || flightId.isBlank()) {
            throw BizException.of("请选择要优先上传的任务");
        }
        commandService.send(dock.getId(), "upload_flighttask_media_prioritize",
                Map.of("flight_id", flightId));
        return upsertPriority(dock.getDeviceSn(), flightId);
    }

    /* ==================== 设备侧回调 ==================== */

    /** file_upload_callback 事件:单个媒体文件上传完成 */
    @Transactional
    public void onFileUploadCallback(String sn, JsonNode data) {
        if (data == null || !data.has("file")) {
            return;
        }
        JsonNode file = data.get("file");
        if (data.hasNonNull("result") && data.get("result").asInt() != 0) {
            log.warn("媒体文件上传失败回调: sn={} name={}", sn, text(file, "name"));
            return;
        }
        DeviceMediaFile row = new DeviceMediaFile();
        row.setDeviceSn(sn);
        row.setFlightId(text(file, "flight_id"));
        row.setObjectKey(text(file, "object_key"));
        row.setPath(text(file, "path"));
        row.setName(text(file, "name") == null ? "未命名媒体" : text(file, "name"));
        row.setSubFileType(intOrNull(file, "sub_file_type"));
        row.setIsOriginal(!file.hasNonNull("is_original") || file.get("is_original").asBoolean());
        row.setDroneModelKey(text(file, "drone_model_key"));
        row.setPayloadModelKey(text(file, "payload_model_key"));

        JsonNode meta = file.get("metadata");
        if (meta != null && meta.isObject()) {
            row.setLongitude(decimalOrNull(meta, "longitude"));
            row.setLatitude(decimalOrNull(meta, "latitude"));
            row.setAbsoluteAltitude(decimalOrNull(meta, "absolute_altitude"));
            row.setRelativeAltitude(decimalOrNull(meta, "relative_altitude"));
            row.setGimbalYawDegree(decimalOrNull(meta, "gimbal_yaw_degree"));
            row.setTakenAt(timeOrNull(meta, "create_time"));
        }
        fileMapper.insert(row);
        syncMediaCount(row.getFlightId());
        log.info("媒体文件入库: sn={} flight={} {} (原始={} {}KB级对象键 {})",
                sn, row.getFlightId(), row.getName(), row.getIsOriginal(), row.getObjectKey());
    }

    /** highest_priority_upload_flighttask_media 事件:设备告知当前优先上传的任务 */
    @Transactional
    public void onHighestPriority(String sn, JsonNode data) {
        String flightId = data == null ? null : text(data, "flight_id");
        if (flightId == null || flightId.isBlank()) {
            return;
        }
        upsertPriority(sn, flightId);
        log.info("媒体优先上传任务上报: sn={} flight={}", sn, flightId);
    }

    /**
     * storage_config_get 请求的回复载荷。
     * 平台未接真实对象存储,回一套本地模拟句柄 —— 设备侧拿它拼 object_key 并「上传」。
     */
    public Map<String, Object> storageConfig(String sn) {
        long expire = Instant.now().plusSeconds(3600).toEpochMilli();
        Map<String, Object> credentials = new LinkedHashMap<>();
        credentials.put("access_key", "emergency-inspect-ak");
        credentials.put("secret_key", "simulated-secret-key");
        credentials.put("expire", expire);

        Map<String, Object> config = new LinkedHashMap<>();
        config.put("bucket", "emergency-media");
        config.put("credentials", credentials);
        config.put("expire", expire);
        config.put("object_key_prefix", "media/" + sn);
        config.put("region", "cn-north-1");
        config.put("event_routing", "");
        return config;
    }

    /* ==================== 内部 ==================== */

    private DeviceMediaPriority upsertPriority(String sn, String flightId) {
        DeviceMediaPriority row = priorityOf(sn);
        boolean isNew = row == null;
        if (isNew) {
            row = new DeviceMediaPriority();
            row.setDeviceSn(sn);
        }
        row.setFlightId(flightId);
        if (isNew) {
            priorityMapper.insert(row);
        } else {
            priorityMapper.updateById(row);
        }
        return row;
    }

    /** 媒体数以实际入库文件数为准,覆盖任务进度事件里的预估值 */
    private void syncMediaCount(String flightId) {
        if (flightId == null || flightId.isBlank()) {
            return;
        }
        WaylineJob job = jobMapper.selectOne(Wrappers.<WaylineJob>lambdaQuery()
                .eq(WaylineJob::getFlightId, flightId).last("limit 1"));
        if (job == null) {
            return;
        }
        Long count = fileMapper.selectCount(Wrappers.<DeviceMediaFile>lambdaQuery()
                .eq(DeviceMediaFile::getFlightId, flightId).eq(DeviceMediaFile::getIsOriginal, true));
        job.setMediaCount(count == null ? 0 : count.intValue());
        jobMapper.updateById(job);
    }

    private void fillFlightNames(List<DeviceMediaFile> rows) {
        if (rows == null || rows.isEmpty()) {
            return;
        }
        List<String> flightIds = rows.stream().map(DeviceMediaFile::getFlightId)
                .filter(f -> f != null && !f.isBlank()).distinct().toList();
        Map<String, WaylineJob> jobs = flightIds.isEmpty() ? Map.of()
                : jobMapper.selectList(Wrappers.<WaylineJob>lambdaQuery()
                                .in(WaylineJob::getFlightId, flightIds))
                        .stream().collect(Collectors.toMap(WaylineJob::getFlightId, Function.identity(), (a, b) -> a));
        for (DeviceMediaFile row : rows) {
            WaylineJob job = jobs.get(row.getFlightId());
            row.setFlightName(job == null ? null : job.getWaylineName());
        }
    }

    private static String text(JsonNode node, String field) {
        return node != null && node.hasNonNull(field) ? node.get(field).asText() : null;
    }

    private static Integer intOrNull(JsonNode node, String field) {
        return node.hasNonNull(field) ? node.get(field).asInt() : null;
    }

    private static BigDecimal decimalOrNull(JsonNode node, String field) {
        return node.hasNonNull(field) ? BigDecimal.valueOf(node.get(field).asDouble()) : null;
    }

    private static LocalDateTime timeOrNull(JsonNode node, String field) {
        if (!node.hasNonNull(field)) {
            return null;
        }
        try {
            return LocalDateTime.ofInstant(
                    Instant.ofEpochMilli(node.get(field).asLong()), ZoneId.systemDefault());
        } catch (Exception e) {
            return null;
        }
    }
}
