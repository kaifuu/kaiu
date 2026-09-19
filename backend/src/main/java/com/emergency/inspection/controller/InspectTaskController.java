package com.emergency.inspection.controller;

import com.emergency.inspection.common.ApiResponse;
import com.emergency.inspection.common.OpLog;
import com.emergency.inspection.common.PageUtil;
import com.emergency.inspection.dto.query.TaskQuery;
import com.emergency.inspection.entity.Hazard;
import com.emergency.inspection.entity.InspectTask;
import com.emergency.inspection.service.HazardService;
import com.emergency.inspection.service.InspectTaskService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/** 巡检任务管理:下发、执行、完成、取消 */
@RestController
@RequestMapping("/api/tasks")
@RequiredArgsConstructor
public class InspectTaskController {

    private final InspectTaskService taskService;
    private final HazardService hazardService;

    @GetMapping("/page")
    public ApiResponse<Map<String, Object>> page(TaskQuery query) {
        return ApiResponse.ok(PageUtil.result(taskService.page(query)));
    }

    /** 最近任务(工作台) */
    @GetMapping("/recent")
    public ApiResponse<List<InspectTask>> recent(@RequestParam(defaultValue = "10") int limit) {
        return ApiResponse.ok(taskService.listRecent(limit));
    }

    @GetMapping("/{id}")
    public ApiResponse<InspectTask> detail(@PathVariable Long id) {
        return ApiResponse.ok(taskService.require(id));
    }

    /** 任务下已上报的隐患 */
    @GetMapping("/{id}/hazards")
    public ApiResponse<List<Hazard>> hazards(@PathVariable Long id) {
        return ApiResponse.ok(hazardService.listByTask(id));
    }

    @PostMapping
    @OpLog(module = "巡检任务", action = "新增")
    public ApiResponse<InspectTask> create(@RequestBody InspectTask body) {
        return ApiResponse.ok(taskService.create(body));
    }

    @PutMapping("/{id}")
    @OpLog(module = "巡检任务", action = "修改")
    public ApiResponse<InspectTask> update(@PathVariable Long id, @RequestBody InspectTask body) {
        return ApiResponse.ok(taskService.update(id, body));
    }

    @DeleteMapping("/{id}")
    @OpLog(module = "巡检任务", action = "删除")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        taskService.delete(id);
        return ApiResponse.ok();
    }

    /** 开始执行 */
    @PostMapping("/{id}/start")
    @OpLog(module = "巡检任务", action = "开始执行")
    public ApiResponse<InspectTask> start(@PathVariable Long id) {
        return ApiResponse.ok(taskService.start(id));
    }

    /** 完成:必须给出巡检结论 */
    @PostMapping("/{id}/finish")
    @OpLog(module = "巡检任务", action = "完成任务")
    public ApiResponse<InspectTask> finish(@PathVariable Long id, @RequestBody FinishForm body) {
        return ApiResponse.ok(taskService.finish(id, body.getResult(), body.getRemark()));
    }

    /** 取消 */
    @PostMapping("/{id}/cancel")
    @OpLog(module = "巡检任务", action = "取消任务")
    public ApiResponse<InspectTask> cancel(@PathVariable Long id) {
        return ApiResponse.ok(taskService.cancel(id));
    }

    @Data
    public static class FinishForm {
        /** NORMAL 正常 / ABNORMAL 异常 */
        private InspectTask.Result result;
        private String remark;
    }
}
