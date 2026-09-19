package com.emergency.inspection.controller;

import com.emergency.inspection.common.ApiResponse;
import com.emergency.inspection.common.OpLog;
import com.emergency.inspection.dto.OrgNode;
import com.emergency.inspection.entity.SysOrg;
import com.emergency.inspection.service.SysOrgService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** 组织管理(树形) */
@RestController
@RequestMapping("/api/orgs")
@RequiredArgsConstructor
public class SysOrgController {

    private final SysOrgService orgService;

    /** 组织树(前端本地筛选/分页) */
    @GetMapping
    public ApiResponse<List<OrgNode>> tree() {
        return ApiResponse.ok(orgService.tree());
    }

    /** 全量组织(扁平) */
    @GetMapping("/list")
    public ApiResponse<List<SysOrg>> list() {
        return ApiResponse.ok(orgService.listAll());
    }

    @PostMapping
    @OpLog(module = "组织管理", action = "新增")
    public ApiResponse<SysOrg> create(@RequestBody SysOrg body) {
        return ApiResponse.ok(orgService.create(body));
    }

    @PutMapping("/{id}")
    @OpLog(module = "组织管理", action = "修改")
    public ApiResponse<SysOrg> update(@PathVariable Long id, @RequestBody SysOrg body) {
        return ApiResponse.ok(orgService.update(id, body));
    }

    @DeleteMapping("/{id}")
    @OpLog(module = "组织管理", action = "删除")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        orgService.delete(id);
        return ApiResponse.ok();
    }
}
