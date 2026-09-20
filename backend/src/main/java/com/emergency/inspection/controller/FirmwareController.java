package com.emergency.inspection.controller;

import com.emergency.inspection.common.ApiResponse;
import com.emergency.inspection.common.OpLog;
import com.emergency.inspection.entity.Firmware;
import com.emergency.inspection.entity.FirmwareTask;
import com.emergency.inspection.service.FirmwareService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** 固件库管理 + OTA 批量下发 */
@RestController
@RequestMapping("/api/firmwares")
@RequiredArgsConstructor
public class FirmwareController {

    private final FirmwareService firmwareService;

    @GetMapping
    public ApiResponse<List<Firmware>> list() {
        return ApiResponse.ok(firmwareService.listAll());
    }

    @PostMapping
    @OpLog(module = "固件升级", action = "新增固件")
    public ApiResponse<Firmware> create(@RequestBody Firmware body) {
        return ApiResponse.ok(firmwareService.create(body));
    }

    @PutMapping("/{id}")
    @OpLog(module = "固件升级", action = "修改固件")
    public ApiResponse<Firmware> update(@PathVariable Long id, @RequestBody Firmware body) {
        return ApiResponse.ok(firmwareService.update(id, body));
    }

    @DeleteMapping("/{id}")
    @OpLog(module = "固件升级", action = "删除固件")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        firmwareService.delete(id);
        return ApiResponse.ok();
    }

    /** 批量下发升级:逐台建任务并 ota_create */
    @PostMapping("/{id}/deploy")
    @OpLog(module = "固件升级", action = "下发升级")
    public ApiResponse<List<FirmwareTask>> deploy(@PathVariable Long id, @RequestBody DeployForm form) {
        return ApiResponse.ok(firmwareService.deploy(id, form.getDeviceIds()));
    }

    /** 升级下发入参 */
    @Data
    public static class DeployForm {
        private List<Long> deviceIds;
    }
}
