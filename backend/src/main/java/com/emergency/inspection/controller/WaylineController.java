package com.emergency.inspection.controller;

import com.emergency.inspection.common.ApiResponse;
import com.emergency.inspection.common.OpLog;
import com.emergency.inspection.entity.Wayline;
import com.emergency.inspection.service.WaylineService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/** 航线库管理:航点序列 + 飞行参数,供航线任务下发引用 */
@RestController
@RequestMapping("/api/waylines")
@RequiredArgsConstructor
public class WaylineController {

    private final WaylineService waylineService;

    @GetMapping
    public ApiResponse<List<Wayline>> list() {
        return ApiResponse.ok(waylineService.listAll());
    }

    @GetMapping("/{id}")
    public ApiResponse<Wayline> detail(@PathVariable Long id) {
        return ApiResponse.ok(waylineService.require(id));
    }

    @PostMapping
    @OpLog(module = "航线管理", action = "新增航线")
    public ApiResponse<Wayline> create(@RequestBody WaylineForm form) {
        return ApiResponse.ok(waylineService.create(form.toEntity(), form.waypoints()));
    }

    @PutMapping("/{id}")
    @OpLog(module = "航线管理", action = "修改航线")
    public ApiResponse<Wayline> update(@PathVariable Long id, @RequestBody WaylineForm form) {
        return ApiResponse.ok(waylineService.update(id, form.toEntity(), form.waypoints()));
    }

    @DeleteMapping("/{id}")
    @OpLog(module = "航线管理", action = "删除航线")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        waylineService.delete(id);
        return ApiResponse.ok();
    }

    /** 航线出入参:航点支持数组或 JSON 字符串两种形态 */
    @Data
    public static class WaylineForm {
        private String name;
        private Wayline.TemplateTypes templateTypes;
        private BigDecimal alt;
        private BigDecimal speed;
        private String remark;
        private List<Map<String, Object>> waypoints;
        /** 兼容:前端直接传航点 JSON 字符串 */
        private String waypointsJson;

        Wayline toEntity() {
            Wayline wayline = new Wayline();
            wayline.setName(name);
            wayline.setTemplateTypes(templateTypes);
            wayline.setAlt(alt);
            wayline.setSpeed(speed);
            wayline.setRemark(remark);
            return wayline;
        }

        List<Map<String, Object>> waypoints() {
            if (waypoints != null) {
                return waypoints;
            }
            if (waypointsJson == null || waypointsJson.isBlank()) {
                return null;
            }
            try {
                return new com.fasterxml.jackson.databind.ObjectMapper()
                        .readValue(waypointsJson, List.class);
            } catch (Exception e) {
                return null;
            }
        }
    }
}
