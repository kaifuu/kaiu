package com.emergency.inspection.controller;

import com.emergency.inspection.common.ApiResponse;
import com.emergency.inspection.common.OpLog;
import com.emergency.inspection.common.PageUtil;
import com.emergency.inspection.dto.query.WorkOrderQuery;
import com.emergency.inspection.entity.WorkOrder;
import com.emergency.inspection.service.WorkOrderService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.Map;

/** 工单管理:问题派发 → 处理 → 结案 */
@RestController
@RequestMapping("/api/work-orders")
@RequiredArgsConstructor
public class WorkOrderController {

    private final WorkOrderService orderService;

    @GetMapping("/page")
    public ApiResponse<Map<String, Object>> page(WorkOrderQuery query) {
        return ApiResponse.ok(PageUtil.result(orderService.page(query)));
    }

    @GetMapping("/{id}")
    public ApiResponse<WorkOrder> detail(@PathVariable Long id) {
        return ApiResponse.ok(orderService.require(id));
    }

    @PostMapping
    @OpLog(module = "工单管理", action = "新增")
    public ApiResponse<WorkOrder> create(@RequestBody WorkOrder body) {
        return ApiResponse.ok(orderService.create(body));
    }

    /** 由巡检问题生成工单(问题状态回写为「已生成工单」) */
    @PostMapping("/from-issue/{issueId}")
    @OpLog(module = "工单管理", action = "问题派单")
    public ApiResponse<WorkOrder> fromIssue(@PathVariable Long issueId, @RequestBody(required = false) WorkOrder body) {
        return ApiResponse.ok(orderService.createFromIssue(issueId, body));
    }

    @PutMapping("/{id}")
    @OpLog(module = "工单管理", action = "修改")
    public ApiResponse<WorkOrder> update(@PathVariable Long id, @RequestBody WorkOrder body) {
        return ApiResponse.ok(orderService.update(id, body));
    }

    /** 派发:指定处理人 */
    @PostMapping("/{id}/dispatch")
    @OpLog(module = "工单管理", action = "派发")
    public ApiResponse<WorkOrder> dispatch(@PathVariable Long id, @RequestBody DispatchForm body) {
        return ApiResponse.ok(orderService.dispatch(id, body.getHandler(), body.getHandlerPhone(),
                body.getHandleDept(), body.getDeadline()));
    }

    /** 处理:填写处理结果 */
    @PostMapping("/{id}/handle")
    @OpLog(module = "工单管理", action = "处理")
    public ApiResponse<WorkOrder> handle(@PathVariable Long id, @RequestBody HandleBody body) {
        return ApiResponse.ok(orderService.handle(id, body.getResult()));
    }

    /** 结案:同步关闭来源问题 */
    @PostMapping("/{id}/close")
    @OpLog(module = "工单管理", action = "结案")
    public ApiResponse<WorkOrder> close(@PathVariable Long id) {
        return ApiResponse.ok(orderService.close(id));
    }

    @DeleteMapping("/{id}")
    @OpLog(module = "工单管理", action = "删除")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        orderService.delete(id);
        return ApiResponse.ok();
    }

    @GetMapping("/stats")
    public ApiResponse<Map<String, Long>> stats() {
        return ApiResponse.ok(orderService.countByStatus());
    }

    @Data
    public static class DispatchForm {
        private String handler;
        private String handlerPhone;
        private String handleDept;
        private LocalDateTime deadline;
    }

    @Data
    public static class HandleBody {
        private String result;
    }
}
