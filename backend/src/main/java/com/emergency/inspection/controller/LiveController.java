package com.emergency.inspection.controller;

import com.emergency.inspection.common.ApiResponse;
import com.emergency.inspection.common.OpLog;
import com.emergency.inspection.common.PageUtil;
import com.emergency.inspection.dto.LiveStartForm;
import com.emergency.inspection.dto.query.LiveStreamQuery;
import com.emergency.inspection.entity.DeviceLiveCapacity;
import com.emergency.inspection.entity.DeviceLiveStream;
import com.emergency.inspection.service.DeviceService;
import com.emergency.inspection.service.LiveStreamService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/** 机场直播:能力查询 + 开停流 + 直播中调整清晰度 / 镜头 / 相机位 */
@RestController
@RequestMapping("/api/devices/{id}/live")
@RequiredArgsConstructor
public class LiveController {

    private final LiveStreamService liveService;
    private final DeviceService deviceService;

    /** 设备直播能力(state 上报的可用视频源清单) */
    @GetMapping("/capacity")
    public ApiResponse<DeviceLiveCapacity> capacity(@PathVariable Long id) {
        return ApiResponse.ok(liveService.capacityOf(deviceService.require(id).getDeviceSn()));
    }

    @GetMapping("/streams/page")
    public ApiResponse<Map<String, Object>> page(@PathVariable Long id, LiveStreamQuery query) {
        query.setDeviceSn(deviceService.require(id).getDeviceSn());
        return ApiResponse.ok(PageUtil.result(liveService.page(query)));
    }

    @PostMapping("/start")
    @OpLog(module = "直播管理", action = "开启直播")
    public ApiResponse<DeviceLiveStream> start(@PathVariable Long id, @RequestBody LiveStartForm form) {
        return ApiResponse.ok(liveService.start(id, form));
    }

    @PostMapping("/streams/{sid}/stop")
    @OpLog(module = "直播管理", action = "停止直播")
    public ApiResponse<DeviceLiveStream> stop(@PathVariable Long id, @PathVariable Long sid) {
        return ApiResponse.ok(liveService.stop(sid));
    }

    @PostMapping("/streams/{sid}/quality")
    @OpLog(module = "直播管理", action = "切换清晰度")
    public ApiResponse<DeviceLiveStream> quality(@PathVariable Long id, @PathVariable Long sid,
                                                 @RequestBody QualityForm form) {
        return ApiResponse.ok(liveService.setQuality(sid, form.getVideoQuality()));
    }

    /** 切镜头作用于该机场当前推流会话(normal/wide/zoom/ir) */
    @PostMapping("/lens")
    @OpLog(module = "直播管理", action = "切换镜头")
    public ApiResponse<Object> lens(@PathVariable Long id, @RequestBody LensForm form) {
        return ApiResponse.ok(Map.of("updated", liveService.changeLens(id, form.getVideoType()).size()));
    }

    @PostMapping("/streams/{sid}/camera")
    @OpLog(module = "直播管理", action = "切换相机位")
    public ApiResponse<DeviceLiveStream> camera(@PathVariable Long id, @PathVariable Long sid,
                                                @RequestBody CameraForm form) {
        return ApiResponse.ok(liveService.changeCamera(sid, form.getCameraPosition()));
    }

    /* ==================== 入参 ==================== */

    @Data
    public static class QualityForm {
        private Integer videoQuality;
    }

    @Data
    public static class LensForm {
        private String videoType;
    }

    @Data
    public static class CameraForm {
        /** 0 舱内 / 1 舱外 */
        private Integer cameraPosition;
    }
}
