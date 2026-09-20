package com.emergency.inspection.controller;

import com.emergency.inspection.common.ApiResponse;
import com.emergency.inspection.common.BizException;
import com.emergency.inspection.common.OpLog;
import com.emergency.inspection.common.PageUtil;
import com.emergency.inspection.dto.query.WaylineJobQuery;
import com.emergency.inspection.entity.WaylineJob;
import com.emergency.inspection.service.WaylineJobService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;

/** 航线飞行任务:flighttask prepare/execute/undo 的云端编排入口 */
@RestController
@RequestMapping("/api/wayline-jobs")
@RequiredArgsConstructor
public class WaylineJobController {

    private static final DateTimeFormatter TIME =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final WaylineJobService jobService;

    @GetMapping("/page")
    public ApiResponse<Map<String, Object>> page(WaylineJobQuery query) {
        return ApiResponse.ok(PageUtil.result(jobService.page(query)));
    }

    @GetMapping("/{id}")
    public ApiResponse<WaylineJob> detail(@PathVariable Long id) {
        return ApiResponse.ok(jobService.require(id));
    }

    @PostMapping
    @OpLog(module = "航线任务", action = "创建下发")
    public ApiResponse<WaylineJob> create(@RequestBody JobForm form) {
        return ApiResponse.ok(jobService.create(
                form.getDockId(), form.getWaylineId(), form.getJobType(), parseTime(form.getExecuteTime())));
    }

    @PostMapping("/{id}/undo")
    @OpLog(module = "航线任务", action = "取消任务")
    public ApiResponse<WaylineJob> undo(@PathVariable Long id) {
        return ApiResponse.ok(jobService.undo(id));
    }

    @PostMapping("/{id}/resume")
    @OpLog(module = "航线任务", action = "断点续飞")
    public ApiResponse<WaylineJob> resume(@PathVariable Long id) {
        return ApiResponse.ok(jobService.resume(id));
    }

    /** 兼容 'yyyy-MM-dd HH:mm:ss' 与 ISO 的 'T' 分隔两种格式 */
    private static LocalDateTime parseTime(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        String normalized = text.trim().replace('T', ' ');
        if (normalized.length() == 16) {
            normalized += ":00";
        }
        try {
            return LocalDateTime.parse(normalized, TIME);
        } catch (Exception e) {
            throw BizException.of("执行时间格式应为 yyyy-MM-dd HH:mm:ss");
        }
    }

    /** 任务创建入参 */
    @Data
    public static class JobForm {
        private Long dockId;
        private Long waylineId;
        private WaylineJob.JobType jobType;
        private String executeTime;
    }
}
