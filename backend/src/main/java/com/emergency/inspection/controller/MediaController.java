package com.emergency.inspection.controller;

import com.emergency.inspection.common.ApiResponse;
import com.emergency.inspection.common.OpLog;
import com.emergency.inspection.common.PageUtil;
import com.emergency.inspection.dto.query.MediaFileQuery;
import com.emergency.inspection.entity.DeviceMediaPriority;
import com.emergency.inspection.service.DeviceService;
import com.emergency.inspection.service.MediaService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/** 机场媒体管理:任务媒体文件检索 + 优先上传编排 */
@RestController
@RequestMapping("/api/devices/{id}/media")
@RequiredArgsConstructor
public class MediaController {

    private final MediaService mediaService;
    private final DeviceService deviceService;

    @GetMapping("/page")
    public ApiResponse<Map<String, Object>> page(@PathVariable Long id, MediaFileQuery query) {
        query.setDeviceSn(deviceService.require(id).getDeviceSn());
        return ApiResponse.ok(PageUtil.result(mediaService.page(query)));
    }

    /** 设备当前优先上传媒体的任务(设备上报或云端指定) */
    @GetMapping("/priority")
    public ApiResponse<DeviceMediaPriority> priority(@PathVariable Long id) {
        return ApiResponse.ok(mediaService.priorityOf(deviceService.require(id).getDeviceSn()));
    }

    /** 指定某任务的媒体优先上传 */
    @PostMapping("/prioritize")
    @OpLog(module = "媒体管理", action = "优先上传")
    public ApiResponse<DeviceMediaPriority> prioritize(@PathVariable Long id, @RequestBody PriorityForm form) {
        return ApiResponse.ok(mediaService.prioritize(id, form.getFlightId()));
    }

    /** 优先上传入参 */
    @Data
    public static class PriorityForm {
        private String flightId;
    }
}
