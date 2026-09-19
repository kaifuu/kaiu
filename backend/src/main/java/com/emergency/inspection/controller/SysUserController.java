package com.emergency.inspection.controller;

import com.emergency.inspection.common.ApiResponse;
import com.emergency.inspection.common.OpLog;
import com.emergency.inspection.common.PageUtil;
import com.emergency.inspection.dto.SysUserForm;
import com.emergency.inspection.dto.query.UserQuery;
import com.emergency.inspection.entity.SysUser;
import com.emergency.inspection.service.SysUserService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/** 人员管理 */
@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class SysUserController {

    private final SysUserService userService;

    /** 全量人员(执行人下拉) */
    @GetMapping
    public ApiResponse<List<SysUser>> list(@RequestParam(required = false) String keyword) {
        return ApiResponse.ok(userService.list(keyword));
    }

    /** 分页查询(page 1 起) */
    @GetMapping("/page")
    public ApiResponse<Map<String, Object>> page(UserQuery query) {
        return ApiResponse.ok(PageUtil.result(userService.page(query)));
    }

    /** 新增:初始密码由服务端统一设定(user.default-password) */
    @PostMapping
    @OpLog(module = "人员管理", action = "新增")
    public ApiResponse<SysUser> create(@RequestBody SysUserForm body) {
        return ApiResponse.ok(userService.create(body));
    }

    @PutMapping("/{id}")
    @OpLog(module = "人员管理", action = "修改")
    public ApiResponse<SysUser> update(@PathVariable Long id, @RequestBody SysUserForm body) {
        return ApiResponse.ok(userService.update(id, body));
    }

    @DeleteMapping("/{id}")
    @OpLog(module = "人员管理", action = "删除")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        userService.delete(id);
        return ApiResponse.ok();
    }

    /** 重置密码为默认值 */
    @PostMapping("/{id}/reset-password")
    @OpLog(module = "人员管理", action = "重置密码")
    public ApiResponse<Map<String, String>> resetPassword(@PathVariable Long id) {
        userService.resetPassword(id);
        return ApiResponse.ok(Map.of("password", userService.getDefaultPassword()));
    }
}
