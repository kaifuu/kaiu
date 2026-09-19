package com.emergency.inspection.controller;

import com.emergency.inspection.common.ApiResponse;
import com.emergency.inspection.common.OpLog;
import com.emergency.inspection.common.PageUtil;
import com.emergency.inspection.dto.HandleForm;
import com.emergency.inspection.dto.query.HazardQuery;
import com.emergency.inspection.entity.Hazard;
import com.emergency.inspection.service.HazardService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/** 隐患上报与整改闭环 */
@RestController
@RequestMapping("/api/hazards")
@RequiredArgsConstructor
public class HazardController {

    private final HazardService hazardService;

    @GetMapping("/page")
    public ApiResponse<Map<String, Object>> page(HazardQuery query) {
        return ApiResponse.ok(PageUtil.result(hazardService.page(query)));
    }

    @GetMapping("/{id}")
    public ApiResponse<Hazard> detail(@PathVariable Long id) {
        return ApiResponse.ok(hazardService.require(id));
    }

    @PostMapping
    @OpLog(module = "隐患上报", action = "上报")
    public ApiResponse<Hazard> create(@RequestBody Hazard body) {
        return ApiResponse.ok(hazardService.create(body));
    }

    @PutMapping("/{id}")
    @OpLog(module = "隐患上报", action = "修改")
    public ApiResponse<Hazard> update(@PathVariable Long id, @RequestBody Hazard body) {
        return ApiResponse.ok(hazardService.update(id, body));
    }

    /** 整改流转:PROCESSING 处理中 / RECTIFIED 已整改 / CLOSED 已关闭 */
    @PostMapping("/{id}/handle")
    @OpLog(module = "隐患上报", action = "整改流转")
    public ApiResponse<Hazard> handle(@PathVariable Long id, @RequestBody HandleForm body) {
        return ApiResponse.ok(hazardService.handle(id, body));
    }

    @DeleteMapping("/{id}")
    @OpLog(module = "隐患上报", action = "删除")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        hazardService.delete(id);
        return ApiResponse.ok();
    }
}
