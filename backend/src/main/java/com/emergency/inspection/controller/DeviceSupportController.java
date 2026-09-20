package com.emergency.inspection.controller;

import com.emergency.inspection.common.ApiResponse;
import com.emergency.inspection.common.OpLog;
import com.emergency.inspection.common.PageUtil;
import com.emergency.inspection.dto.query.AiTargetQuery;
import com.emergency.inspection.entity.DeviceAiConfig;
import com.emergency.inspection.entity.DeviceLogFile;
import com.emergency.inspection.service.AiRecognitionService;
import com.emergency.inspection.service.DeviceLogService;
import com.emergency.inspection.service.DeviceService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/** 设备扩展能力:远程日志 + AI 目标识别(挂在设备路由下,与台账控制器互补) */
@RestController
@RequestMapping("/api/devices")
@RequiredArgsConstructor
public class DeviceSupportController {

    private final DeviceLogService logService;
    private final AiRecognitionService aiService;
    private final DeviceService deviceService;

    /* ==================== 远程日志 ==================== */

    /** 拉取日志清单:异步回执落库,前端稍候调 logs 刷新 */
    @PostMapping("/{id}/logs/sync")
    @OpLog(module = "远程日志", action = "同步日志清单")
    public ApiResponse<Void> syncLogs(@PathVariable Long id) {
        logService.sync(id);
        return ApiResponse.ok();
    }

    @GetMapping("/{id}/logs")
    public ApiResponse<List<DeviceLogFile>> logs(@PathVariable Long id) {
        return ApiResponse.ok(logService.list(id));
    }

    @PostMapping("/{id}/logs/upload")
    @OpLog(module = "远程日志", action = "上传日志")
    public ApiResponse<Void> uploadLogs(@PathVariable Long id, @RequestBody FileIdsForm form) {
        logService.upload(id, form.getFileIds());
        return ApiResponse.ok();
    }

    /* ==================== AI 目标识别 ==================== */

    @GetMapping("/{id}/ai/config")
    public ApiResponse<DeviceAiConfig> aiConfig(@PathVariable Long id) {
        return ApiResponse.ok(aiService.configOf(id));
    }

    @PutMapping("/{id}/ai/config")
    @OpLog(module = "AI识别", action = "保存配置")
    public ApiResponse<DeviceAiConfig> saveAiConfig(@PathVariable Long id, @RequestBody AiConfigForm form) {
        return ApiResponse.ok(aiService.save(id, form.toConfig(), form.getFilterTypes()));
    }

    @GetMapping("/{id}/ai/targets/page")
    public ApiResponse<Map<String, Object>> aiTargets(@PathVariable Long id, AiTargetQuery query) {
        query.setDeviceSn(deviceService.require(id).getDeviceSn());
        return ApiResponse.ok(PageUtil.result(aiService.targetPage(query)));
    }

    /* ==================== 入参 ==================== */

    /** 日志上传入参 */
    @Data
    public static class FileIdsForm {
        private List<String> fileIds;
    }

    /** AI 配置入参:filterTypes 为数组,库内存 JSON */
    @Data
    public static class AiConfigForm {
        private Boolean enabled;
        private Boolean followEnabled;
        private String model;
        private DeviceAiConfig.ConfidenceMode confidenceMode;
        private Integer confidenceValue;
        private List<String> filterTypes;

        DeviceAiConfig toConfig() {
            DeviceAiConfig config = new DeviceAiConfig();
            config.setEnabled(enabled);
            config.setFollowEnabled(followEnabled);
            config.setModel(model);
            config.setConfidenceMode(confidenceMode);
            config.setConfidenceValue(confidenceValue);
            return config;
        }
    }
}
