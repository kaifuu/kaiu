package com.emergency.inspection.controller;

import com.emergency.inspection.common.ApiResponse;
import com.emergency.inspection.common.OpLog;
import com.emergency.inspection.common.PageUtil;
import com.emergency.inspection.dto.query.PlanQuery;
import com.emergency.inspection.entity.InspectPlan;
import com.emergency.inspection.service.InspectPlanService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/** 巡检计划管理 */
@RestController
@RequestMapping("/api/plans")
@RequiredArgsConstructor
public class InspectPlanController {

    private final InspectPlanService planService;

    /** 已启用计划(任务/统计下拉) */
    @GetMapping
    public ApiResponse<List<InspectPlan>> list() {
        return ApiResponse.ok(planService.listEnabled());
    }

    @GetMapping("/page")
    public ApiResponse<Map<String, Object>> page(PlanQuery query) {
        return ApiResponse.ok(PageUtil.result(planService.page(query)));
    }

    @GetMapping("/{id}")
    public ApiResponse<InspectPlan> detail(@PathVariable Long id) {
        return ApiResponse.ok(planService.require(id));
    }

    @PostMapping
    @OpLog(module = "巡检计划", action = "新增")
    public ApiResponse<InspectPlan> create(@RequestBody InspectPlan body) {
        return ApiResponse.ok(planService.create(body));
    }

    @PutMapping("/{id}")
    @OpLog(module = "巡检计划", action = "修改")
    public ApiResponse<InspectPlan> update(@PathVariable Long id, @RequestBody InspectPlan body) {
        return ApiResponse.ok(planService.update(id, body));
    }

    @DeleteMapping("/{id}")
    @OpLog(module = "巡检计划", action = "删除")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        planService.delete(id);
        return ApiResponse.ok();
    }

    /** 按计划覆盖点位一键生成待执行任务,返回生成条数 */
    @PostMapping("/{id}/generate-tasks")
    @OpLog(module = "巡检计划", action = "生成任务")
    public ApiResponse<Map<String, Object>> generateTasks(@PathVariable Long id) {
        int created = planService.generateTasks(id);
        return ApiResponse.ok(Map.of("created", created));
    }
}
