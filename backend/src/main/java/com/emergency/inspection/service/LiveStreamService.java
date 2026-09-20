package com.emergency.inspection.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.emergency.inspection.common.BizException;
import com.emergency.inspection.common.PageUtil;
import com.emergency.inspection.dto.LiveStartForm;
import com.emergency.inspection.dto.query.LiveStreamQuery;
import com.emergency.inspection.entity.Device;
import com.emergency.inspection.entity.DeviceLiveCapacity;
import com.emergency.inspection.entity.DeviceLiveStream;
import com.emergency.inspection.mapper.DeviceLiveCapacityMapper;
import com.emergency.inspection.mapper.DeviceLiveStreamMapper;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 机场直播(对齐 Dock 3 live 服务集):
 * - live_start_push / live_stop_push 开停流,会话留痕
 * - live_set_quality 切清晰度,live_lens_change 切镜头,live_camera_change 切舱内外相机
 * - 设备经 state 主题上报 live_capacity(可用视频源清单),直播入口据此拼 video_id
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LiveStreamService {

    private static final Set<String> VIDEO_TYPES = Set.of("normal", "wide", "zoom", "ir");

    private final DeviceLiveStreamMapper streamMapper;
    private final DeviceLiveCapacityMapper capacityMapper;
    private final DeviceService deviceService;
    private final DeviceCommandService commandService;

    /* ==================== 查询 ==================== */

    public IPage<DeviceLiveStream> page(LiveStreamQuery query) {
        return streamMapper.selectPage(
                PageUtil.build(query, "create_time",
                        PageUtil.allowedCamel("status", "videoQuality", "createTime")),
                Wrappers.<DeviceLiveStream>lambdaQuery()
                        .eq(query.getDeviceSn() != null && !query.getDeviceSn().isBlank(),
                                DeviceLiveStream::getDeviceSn, query.getDeviceSn())
                        .eq(query.getStatus() != null, DeviceLiveStream::getStatus, query.getStatus()));
    }

    /** 设备直播能力(state 上报的 live_capacity 原文) */
    public DeviceLiveCapacity capacityOf(String deviceSn) {
        return capacityMapper.selectOne(Wrappers.<DeviceLiveCapacity>lambdaQuery()
                .eq(DeviceLiveCapacity::getDeviceSn, deviceSn).last("limit 1"));
    }

    /* ==================== 开流 / 停流 ==================== */

    /** 开启直播:落会话后下发 live_start_push,设备不在线则如实报错并将会话结案为 FAILED */
    public DeviceLiveStream start(Long deviceId, LiveStartForm form) {
        Device dock = requireDock(deviceId);
        if (form.getVideoId() == null || form.getVideoId().isBlank()) {
            throw BizException.of("请选择直播视频源(video_id)");
        }
        DeviceLiveStream.UrlType urlType = form.getUrlType() == null ? DeviceLiveStream.UrlType.RTMP : form.getUrlType();
        if (urlType != DeviceLiveStream.UrlType.WEBRTC
                && (form.getUrl() == null || form.getUrl().isBlank())) {
            throw BizException.of("RTMP / GB28181 推流必须填写推流地址");
        }
        String[] parts = form.getVideoId().split("/");
        String videoType = form.getVideoType() == null ? "normal" : form.getVideoType();
        if (!VIDEO_TYPES.contains(videoType)) {
            throw BizException.of("不支持的镜头类型: " + videoType);
        }

        DeviceLiveStream stream = new DeviceLiveStream();
        stream.setDeviceSn(dock.getDeviceSn());
        stream.setVideoId(form.getVideoId());
        stream.setCameraIndex(parts.length > 1 ? parts[1] : null);
        stream.setVideoIndex(parts.length > 2 ? parts[2] : null);
        stream.setVideoType(videoType);
        stream.setUrlType(urlType);
        stream.setUrl(form.getUrl());
        stream.setVideoQuality(form.getVideoQuality() == null ? 0 : form.getVideoQuality());
        stream.setStatus(DeviceLiveStream.Status.PUSHING);
        stream.setStartedAt(LocalDateTime.now());
        streamMapper.insert(stream);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("url_type", urlType.protocolValue());
        data.put("url", form.getUrl() == null ? "" : form.getUrl());
        data.put("video_id", form.getVideoId());
        data.put("video_quality", stream.getVideoQuality());
        try {
            commandService.send(dock.getId(), "live_start_push", data);
        } catch (BizException e) {
            stream.setStatus(DeviceLiveStream.Status.FAILED);
            stream.setErrorMsg(e.getMessage());
            stream.setStoppedAt(LocalDateTime.now());
            streamMapper.updateById(stream);
            throw e;
        }
        log.info("直播开流: dock={} video_id={} {} quality={}",
                dock.getDeviceSn(), form.getVideoId(), urlType, stream.getVideoQuality());
        return stream;
    }

    /** 停止直播 */
    public DeviceLiveStream stop(Long streamId) {
        DeviceLiveStream stream = require(streamId);
        if (stream.getStatus() != DeviceLiveStream.Status.PUSHING) {
            throw BizException.of("该直播会话已结束");
        }
        commandService.send(deviceService.findBySn(stream.getDeviceSn()).getId(), "live_stop_push",
                Map.of("video_id", stream.getVideoId()));
        stream.setStatus(DeviceLiveStream.Status.STOPPED);
        stream.setStoppedAt(LocalDateTime.now());
        streamMapper.updateById(stream);
        return stream;
    }

    /* ==================== 直播中调整 ==================== */

    /** 切清晰度:0 自适应 / 1 流畅 / 2 标清 / 3 高清 / 4 超清 */
    public DeviceLiveStream setQuality(Long streamId, Integer videoQuality) {
        DeviceLiveStream stream = requirePushing(streamId);
        if (videoQuality == null || videoQuality < 0 || videoQuality > 4) {
            throw BizException.of("清晰度取值 0-4(自适应/流畅/标清/高清/超清)");
        }
        commandService.send(deviceService.findBySn(stream.getDeviceSn()).getId(), "live_set_quality",
                Map.of("video_id", stream.getVideoId(), "video_quality", videoQuality));
        stream.setVideoQuality(videoQuality);
        streamMapper.updateById(stream);
        return stream;
    }

    /** 切镜头:normal 广角 / wide 超广角 / zoom 变焦 / ir 红外(作用于该机场当前推流会话) */
    public List<DeviceLiveStream> changeLens(Long deviceId, String videoType) {
        Device dock = requireDock(deviceId);
        if (!VIDEO_TYPES.contains(videoType)) {
            throw BizException.of("不支持的镜头类型: " + videoType);
        }
        commandService.send(dock.getId(), "live_lens_change", Map.of("video_type", videoType));
        List<DeviceLiveStream> pushing = streamMapper.selectList(Wrappers.<DeviceLiveStream>lambdaQuery()
                .eq(DeviceLiveStream::getDeviceSn, dock.getDeviceSn())
                .eq(DeviceLiveStream::getStatus, DeviceLiveStream.Status.PUSHING));
        for (DeviceLiveStream stream : pushing) {
            stream.setVideoType(videoType);
            streamMapper.updateById(stream);
        }
        return pushing;
    }

    /** 切换直播相机位(Dock 3 舱内 / 舱外两个 FPV 相机位):0 舱内 / 1 舱外 */
    public DeviceLiveStream changeCamera(Long streamId, Integer cameraPosition) {
        DeviceLiveStream stream = requirePushing(streamId);
        if (cameraPosition == null || (cameraPosition != 0 && cameraPosition != 1)) {
            throw BizException.of("camera_position:0 舱内 / 1 舱外");
        }
        commandService.send(deviceService.findBySn(stream.getDeviceSn()).getId(), "live_camera_change",
                Map.of("video_id", stream.getVideoId(), "camera_position", cameraPosition));
        return stream;
    }

    /* ==================== 设备侧回调 ==================== */

    /** state 主题 live_capacity 上报:覆盖式留存 */
    @Transactional
    public void onCapacity(String sn, JsonNode capacity) {
        if (capacity == null) {
            return;
        }
        DeviceLiveCapacity row = capacityOf(sn);
        boolean isNew = row == null;
        if (isNew) {
            row = new DeviceLiveCapacity();
            row.setDeviceSn(sn);
        }
        row.setCapacityJson(capacity.toString());
        if (isNew) {
            capacityMapper.insert(row);
        } else {
            capacityMapper.updateById(row);
        }
    }

    /* ==================== 内部 ==================== */

    private Device requireDock(Long deviceId) {
        Device dock = deviceService.require(deviceId);
        if (dock.getDeviceType() != Device.DeviceType.DOCK) {
            throw BizException.of("直播指令只能下发给机场");
        }
        return dock;
    }

    private DeviceLiveStream require(Long streamId) {
        DeviceLiveStream stream = streamMapper.selectById(streamId);
        if (stream == null) {
            throw BizException.of("直播会话不存在: " + streamId);
        }
        return stream;
    }

    private DeviceLiveStream requirePushing(Long streamId) {
        DeviceLiveStream stream = require(streamId);
        if (stream.getStatus() != DeviceLiveStream.Status.PUSHING) {
            throw BizException.of("仅推流中的会话支持该操作");
        }
        return stream;
    }
}
