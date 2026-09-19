package com.emergency.inspection.controller;

import com.emergency.inspection.common.ApiResponse;
import com.emergency.inspection.common.OpLog;
import com.emergency.inspection.common.PageUtil;
import com.emergency.inspection.dto.HandleForm;
import com.emergency.inspection.dto.query.EventQuery;
import com.emergency.inspection.entity.EmergencyEvent;
import com.emergency.inspection.service.EmergencyEventService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/** 应急事件接报与处置 */
@RestController
@RequestMapping("/api/events")
@RequiredArgsConstructor
public class EmergencyEventController {

    private final EmergencyEventService eventService;

    @GetMapping("/page")
    public ApiResponse<Map<String, Object>> page(EventQuery query) {
        return ApiResponse.ok(PageUtil.result(eventService.page(query)));
    }

    /** 进行中事件(工作台) */
    @GetMapping("/active")
    public ApiResponse<List<EmergencyEvent>> active(@RequestParam(defaultValue = "10") int limit) {
        return ApiResponse.ok(eventService.listActive(limit));
    }

    @GetMapping("/{id}")
    public ApiResponse<EmergencyEvent> detail(@PathVariable Long id) {
        return ApiResponse.ok(eventService.require(id));
    }

    @PostMapping
    @OpLog(module = "应急事件", action = "接报")
    public ApiResponse<EmergencyEvent> create(@RequestBody EmergencyEvent body) {
        return ApiResponse.ok(eventService.create(body));
    }

    @PutMapping("/{id}")
    @OpLog(module = "应急事件", action = "修改")
    public ApiResponse<EmergencyEvent> update(@PathVariable Long id, @RequestBody EmergencyEvent body) {
        return ApiResponse.ok(eventService.update(id, body));
    }

    /** 处置流转:RESPONDING 响应中 / HANDLED 已处置 / ARCHIVED 已归档 */
    @PostMapping("/{id}/handle")
    @OpLog(module = "应急事件", action = "处置流转")
    public ApiResponse<EmergencyEvent> handle(@PathVariable Long id, @RequestBody HandleForm body) {
        return ApiResponse.ok(eventService.handle(id, body));
    }

    @DeleteMapping("/{id}")
    @OpLog(module = "应急事件", action = "删除")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        eventService.delete(id);
        return ApiResponse.ok();
    }
}
