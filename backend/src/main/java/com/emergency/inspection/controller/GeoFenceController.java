package com.emergency.inspection.controller;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.emergency.inspection.common.ApiResponse;
import com.emergency.inspection.common.OpLog;
import com.emergency.inspection.entity.GeoFence;
import com.emergency.inspection.mapper.GeoFenceMapper;
import com.emergency.inspection.utils.GeoUtils;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** 电子围栏:CRUD + 点包含判定(供安全预警与前端地图使用) */
@RestController
@RequestMapping("/api/fences")
@RequiredArgsConstructor
public class GeoFenceController {

    private final GeoFenceMapper fenceMapper;
    private final ObjectMapper objectMapper;

    @GetMapping
    public ApiResponse<List<GeoFence>> list() {
        return ApiResponse.ok(fenceMapper.selectList(
                Wrappers.<GeoFence>lambdaQuery().orderByDesc(GeoFence::getId)));
    }

    @PostMapping
    @OpLog(module = "电子围栏", action = "新建围栏")
    public ApiResponse<GeoFence> create(@RequestBody GeoFence fence) {
        normalize(fence);
        fenceMapper.insert(fence);
        return ApiResponse.ok(fence);
    }

    /** 逐字段非空覆盖:支持只传 {enabled} 的开关场景 */
    @PutMapping("/{id}")
    @OpLog(module = "电子围栏", action = "更新围栏")
    public ApiResponse<GeoFence> update(@PathVariable Long id, @RequestBody GeoFence form) {
        GeoFence fence = fenceMapper.selectById(id);
        if (fence == null) {
            return ApiResponse.error(404, "围栏不存在");
        }
        if (form.getName() != null) fence.setName(form.getName());
        if (form.getFenceType() != null) fence.setFenceType(form.getFenceType());
        if (form.getShape() != null) fence.setShape(form.getShape());
        if (form.getPointsJson() != null) fence.setPointsJson(form.getPointsJson());
        if (form.getRadius() != null) fence.setRadius(form.getRadius());
        if (form.getMaxAltitude() != null) fence.setMaxAltitude(form.getMaxAltitude());
        if (form.getEnabled() != null) fence.setEnabled(form.getEnabled());
        if (form.getRemark() != null) fence.setRemark(form.getRemark());
        normalize(fence);
        fenceMapper.updateById(fence);
        return ApiResponse.ok(fence);
    }

    @DeleteMapping("/{id}")
    @OpLog(module = "电子围栏", action = "删除围栏")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        fenceMapper.deleteById(id);
        return ApiResponse.ok();
    }

    /** 点包含判定:返回该坐标命中的全部启用围栏 */
    @GetMapping("/contains")
    public ApiResponse<List<GeoFence>> contains(@RequestParam double lng, @RequestParam double lat) {
        List<GeoFence> hits = new ArrayList<>();
        for (GeoFence fence : enabledFences()) {
            if (GeoUtils.contains(fence, lng, lat, points(fence))) {
                hits.add(fence);
            }
        }
        return ApiResponse.ok(hits);
    }

    /** 全部启用围栏 */
    private List<GeoFence> enabledFences() {
        return fenceMapper.selectList(Wrappers.<GeoFence>lambdaQuery().eq(GeoFence::getEnabled, true));
    }

    private List<Map<String, Object>> points(GeoFence fence) {
        try {
            return objectMapper.readValue(fence.getPointsJson(), new TypeReference<>() {
            });
        } catch (Exception e) {
            return List.of();
        }
    }

    private void normalize(GeoFence fence) {
        if (fence.getFenceType() == null) fence.setFenceType(GeoFence.FenceType.WORK);
        if (fence.getShape() == null) fence.setShape(GeoFence.Shape.POLYGON);
        if (fence.getEnabled() == null) fence.setEnabled(Boolean.TRUE);
    }
}
