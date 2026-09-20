package com.emergency.inspection.controller;

import com.emergency.inspection.common.ApiResponse;
import com.emergency.inspection.common.PageUtil;
import com.emergency.inspection.dto.query.FirmwareTaskQuery;
import com.emergency.inspection.service.FirmwareService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** 固件升级任务记录(全局视图) */
@RestController
@RequestMapping("/api/firmware-tasks")
@RequiredArgsConstructor
public class FirmwareTaskController {

    private final FirmwareService firmwareService;

    @GetMapping("/page")
    public ApiResponse<Map<String, Object>> page(FirmwareTaskQuery query) {
        return ApiResponse.ok(PageUtil.result(firmwareService.taskPage(query)));
    }
}
