package com.emergency.inspection.controller;

import com.emergency.inspection.common.ApiResponse;
import com.emergency.inspection.common.OpLog;
import com.emergency.inspection.common.PageUtil;
import com.emergency.inspection.dto.query.DemandQuery;
import com.emergency.inspection.entity.InspectDemand;
import com.emergency.inspection.service.DemandService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/** 需求管理:业务部门提报的巡检需求 */
@RestController
@RequestMapping("/api/demands")
@RequiredArgsConstructor
public class DemandController {

    private final DemandService demandService;

    @GetMapping("/page")
    public ApiResponse<Map<String, Object>> page(DemandQuery query) {
        return ApiResponse.ok(PageUtil.result(demandService.page(query)));
    }

    @GetMapping("/recent")
    public ApiResponse<List<InspectDemand>> recent(@RequestParam(defaultValue = "20") int limit) {
        return ApiResponse.ok(demandService.recent(limit));
    }

    @GetMapping("/{id}")
    public ApiResponse<InspectDemand> detail(@PathVariable Long id) {
        return ApiResponse.ok(demandService.require(id));
    }

    @PostMapping
    @OpLog(module = "需求管理", action = "提报")
    public ApiResponse<InspectDemand> create(@RequestBody InspectDemand body) {
        return ApiResponse.ok(demandService.create(body));
    }

    @PutMapping("/{id}")
    @OpLog(module = "需求管理", action = "修改")
    public ApiResponse<InspectDemand> update(@PathVariable Long id, @RequestBody InspectDemand body) {
        return ApiResponse.ok(demandService.update(id, body));
    }

    @PostMapping("/{id}/execute")
    @OpLog(module = "需求管理", action = "执行")
    public ApiResponse<InspectDemand> execute(@PathVariable Long id, @RequestBody(required = false) ExecForm body) {
        return ApiResponse.ok(demandService.execute(id, body == null ? null : body.getExecutor()));
    }

    @PostMapping("/{id}/cancel")
    @OpLog(module = "需求管理", action = "取消")
    public ApiResponse<InspectDemand> cancel(@PathVariable Long id, @RequestBody(required = false) ExecForm body) {
        return ApiResponse.ok(demandService.cancel(id, body == null ? null : body.getRemark()));
    }

    @DeleteMapping("/{id}")
    @OpLog(module = "需求管理", action = "删除")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        demandService.delete(id);
        return ApiResponse.ok();
    }

    @GetMapping("/stats")
    public ApiResponse<Map<String, Long>> stats() {
        return ApiResponse.ok(demandService.countByStatus());
    }

    @Data
    public static class ExecForm {
        private String executor;
        private String remark;
    }
}
