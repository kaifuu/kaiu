package com.emergency.inspection.controller;

import com.emergency.inspection.common.ApiResponse;
import com.emergency.inspection.common.OpLog;
import com.emergency.inspection.common.PageUtil;
import com.emergency.inspection.dto.query.TenantQuery;
import com.emergency.inspection.entity.SysTenant;
import com.emergency.inspection.service.SysTenantService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/** 租户管理 */
@RestController
@RequestMapping("/api/tenants")
@RequiredArgsConstructor
public class SysTenantController {

    private final SysTenantService tenantService;

    /** 全量租户(人员管理的租户下拉) */
    @GetMapping
    public ApiResponse<List<SysTenant>> list() {
        return ApiResponse.ok(tenantService.list());
    }

    /** 分页查询(page 1 起) */
    @GetMapping("/page")
    public ApiResponse<Map<String, Object>> page(TenantQuery query) {
        return ApiResponse.ok(PageUtil.result(tenantService.page(query)));
    }

    @PostMapping
    @OpLog(module = "租户管理", action = "新增")
    public ApiResponse<SysTenant> create(@RequestBody SysTenant body) {
        return ApiResponse.ok(tenantService.create(body));
    }

    @PutMapping("/{id}")
    @OpLog(module = "租户管理", action = "修改")
    public ApiResponse<SysTenant> update(@PathVariable Long id, @RequestBody SysTenant body) {
        return ApiResponse.ok(tenantService.update(id, body));
    }

    @DeleteMapping("/{id}")
    @OpLog(module = "租户管理", action = "删除")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        tenantService.delete(id);
        return ApiResponse.ok();
    }
}
