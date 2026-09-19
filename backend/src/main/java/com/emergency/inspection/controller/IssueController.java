package com.emergency.inspection.controller;

import com.emergency.inspection.common.ApiResponse;
import com.emergency.inspection.common.OpLog;
import com.emergency.inspection.common.PageUtil;
import com.emergency.inspection.dto.query.IssueQuery;
import com.emergency.inspection.entity.InspectIssue;
import com.emergency.inspection.service.IssueService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/** 问题清单:巡检发现的问题,工单的来源 */
@RestController
@RequestMapping("/api/issues")
@RequiredArgsConstructor
public class IssueController {

    private final IssueService issueService;

    @GetMapping("/page")
    public ApiResponse<Map<String, Object>> page(IssueQuery query) {
        return ApiResponse.ok(PageUtil.result(issueService.page(query)));
    }

    @GetMapping("/recent")
    public ApiResponse<List<InspectIssue>> recent(@RequestParam(defaultValue = "12") int limit) {
        return ApiResponse.ok(issueService.recent(limit));
    }

    @GetMapping("/{id}")
    public ApiResponse<InspectIssue> detail(@PathVariable Long id) {
        return ApiResponse.ok(issueService.require(id));
    }

    @PostMapping
    @OpLog(module = "问题清单", action = "新增")
    public ApiResponse<InspectIssue> create(@RequestBody InspectIssue body) {
        return ApiResponse.ok(issueService.create(body));
    }

    @PutMapping("/{id}")
    @OpLog(module = "问题清单", action = "修改")
    public ApiResponse<InspectIssue> update(@PathVariable Long id, @RequestBody InspectIssue body) {
        return ApiResponse.ok(issueService.update(id, body));
    }

    /** 结案(已生成工单的问题须在工单中结案) */
    @PostMapping("/{id}/close")
    @OpLog(module = "问题清单", action = "结案")
    public ApiResponse<InspectIssue> close(@PathVariable Long id) {
        return ApiResponse.ok(issueService.close(id));
    }

    @DeleteMapping("/{id}")
    @OpLog(module = "问题清单", action = "删除")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        issueService.delete(id);
        return ApiResponse.ok();
    }

    /** 按问题类型计数(大屏算法识别类型) */
    @GetMapping("/stats")
    public ApiResponse<Map<String, Long>> stats() {
        return ApiResponse.ok(issueService.countByType());
    }
}
