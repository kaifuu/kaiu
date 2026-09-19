package com.emergency.inspection.controller;

import com.emergency.inspection.common.ApiResponse;
import com.emergency.inspection.common.OpLog;
import com.emergency.inspection.common.PageUtil;
import com.emergency.inspection.dto.query.RoleQuery;
import com.emergency.inspection.entity.SysRole;
import com.emergency.inspection.service.SysRoleService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/** 角色管理 */
@RestController
@RequestMapping("/api/roles")
@RequiredArgsConstructor
public class SysRoleController {

    private final SysRoleService roleService;

    /** 全量角色(人员管理的角色下拉) */
    @GetMapping
    public ApiResponse<List<SysRole>> list() {
        return ApiResponse.ok(roleService.list());
    }

    /** 分页查询(page 1 起) */
    @GetMapping("/page")
    public ApiResponse<Map<String, Object>> page(RoleQuery query) {
        return ApiResponse.ok(PageUtil.result(roleService.page(query)));
    }

    @PostMapping
    @OpLog(module = "角色管理", action = "新增")
    public ApiResponse<SysRole> create(@RequestBody SysRole body) {
        return ApiResponse.ok(roleService.create(body));
    }

    @PutMapping("/{id}")
    @OpLog(module = "角色管理", action = "修改")
    public ApiResponse<SysRole> update(@PathVariable Long id, @RequestBody SysRole body) {
        return ApiResponse.ok(roleService.update(id, body));
    }

    @DeleteMapping("/{id}")
    @OpLog(module = "角色管理", action = "删除")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        roleService.delete(id);
        return ApiResponse.ok();
    }
}
