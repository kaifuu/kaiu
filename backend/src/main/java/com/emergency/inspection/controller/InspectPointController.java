package com.emergency.inspection.controller;

import com.emergency.inspection.common.ApiResponse;
import com.emergency.inspection.common.OpLog;
import com.emergency.inspection.common.PageUtil;
import com.emergency.inspection.dto.query.PointQuery;
import com.emergency.inspection.entity.InspectPoint;
import com.emergency.inspection.service.InspectPointService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/** 巡检点位管理 */
@RestController
@RequestMapping("/api/points")
@RequiredArgsConstructor
public class InspectPointController {

    private final InspectPointService pointService;

    /** 全量点位(计划选点/隐患关联用) */
    @GetMapping
    public ApiResponse<List<InspectPoint>> list(@RequestParam(required = false) String keyword,
                                                @RequestParam(required = false) InspectPoint.Status status) {
        return ApiResponse.ok(pointService.list(keyword, status));
    }

    @GetMapping("/page")
    public ApiResponse<Map<String, Object>> page(PointQuery query) {
        return ApiResponse.ok(PageUtil.result(pointService.page(query)));
    }

    @GetMapping("/{id}")
    public ApiResponse<InspectPoint> detail(@PathVariable Long id) {
        return ApiResponse.ok(pointService.require(id));
    }

    @PostMapping
    @OpLog(module = "巡检点位", action = "新增")
    public ApiResponse<InspectPoint> create(@RequestBody InspectPoint body) {
        return ApiResponse.ok(pointService.create(body));
    }

    @PutMapping("/{id}")
    @OpLog(module = "巡检点位", action = "修改")
    public ApiResponse<InspectPoint> update(@PathVariable Long id, @RequestBody InspectPoint body) {
        return ApiResponse.ok(pointService.update(id, body));
    }

    @DeleteMapping("/{id}")
    @OpLog(module = "巡检点位", action = "删除")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        pointService.delete(id);
        return ApiResponse.ok();
    }
}
