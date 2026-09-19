package com.emergency.inspection.controller;

import com.emergency.inspection.common.ApiResponse;
import com.emergency.inspection.common.OpLog;
import com.emergency.inspection.common.PageUtil;
import com.emergency.inspection.dto.query.LogQuery;
import com.emergency.inspection.service.SysLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/** 日志管理:操作/登录/设备 三类 */
@RestController
@RequestMapping("/api/logs")
@RequiredArgsConstructor
public class SysLogController {

    private final SysLogService logService;

    /** 分页查询(page 1 起) */
    @GetMapping
    public ApiResponse<Map<String, Object>> page(LogQuery query) {
        return ApiResponse.ok(PageUtil.result(logService.page(query)));
    }

    /** 各类日志条数(页签徽标) */
    @GetMapping("/count")
    public ApiResponse<Map<String, Long>> count() {
        return ApiResponse.ok(logService.count());
    }

    /** 一键清空全部日志;返回删除条数(清空动作本身会留一条审计) */
    @DeleteMapping
    @OpLog(module = "日志管理", action = "清空日志")
    public ApiResponse<Long> clear() {
        return ApiResponse.ok(logService.clear());
    }
}
