package com.emergency.inspection.controller;

import com.emergency.inspection.common.ApiResponse;
import com.emergency.inspection.common.OpLog;
import com.emergency.inspection.common.PageUtil;
import com.emergency.inspection.dto.query.PilotQuery;
import com.emergency.inspection.entity.Pilot;
import com.emergency.inspection.service.PilotService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/** 飞手管理 */
@RestController
@RequestMapping("/api/pilots")
@RequiredArgsConstructor
public class PilotController {

    private final PilotService pilotService;

    @GetMapping("/page")
    public ApiResponse<Map<String, Object>> page(PilotQuery query) {
        return ApiResponse.ok(PageUtil.result(pilotService.page(query)));
    }

    /** 全量飞手(执行人下拉) */
    @GetMapping
    public ApiResponse<List<Pilot>> list() {
        return ApiResponse.ok(pilotService.listAll());
    }

    @GetMapping("/{id}")
    public ApiResponse<Pilot> detail(@PathVariable Long id) {
        return ApiResponse.ok(pilotService.require(id));
    }

    @PostMapping
    @OpLog(module = "飞手管理", action = "新增")
    public ApiResponse<Pilot> create(@RequestBody Pilot body) {
        return ApiResponse.ok(pilotService.create(body));
    }

    @PutMapping("/{id}")
    @OpLog(module = "飞手管理", action = "修改")
    public ApiResponse<Pilot> update(@PathVariable Long id, @RequestBody Pilot body) {
        return ApiResponse.ok(pilotService.update(id, body));
    }

    @DeleteMapping("/{id}")
    @OpLog(module = "飞手管理", action = "删除")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        pilotService.delete(id);
        return ApiResponse.ok();
    }

    @GetMapping("/stats")
    public ApiResponse<Map<String, Long>> stats() {
        return ApiResponse.ok(pilotService.countByStatus());
    }
}
