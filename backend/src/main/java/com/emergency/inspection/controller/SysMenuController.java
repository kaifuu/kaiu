package com.emergency.inspection.controller;

import com.emergency.inspection.common.ApiResponse;
import com.emergency.inspection.common.BizException;
import com.emergency.inspection.common.OpLog;
import com.emergency.inspection.dto.MenuDto;
import com.emergency.inspection.dto.MenuNode;
import com.emergency.inspection.entity.SysMenu;
import com.emergency.inspection.entity.SysUser;
import com.emergency.inspection.mapper.SysMenuMapper;
import com.emergency.inspection.security.LoginContext;
import com.emergency.inspection.service.MenuService;
import com.emergency.inspection.service.SysUserService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Set;

/** 菜单管理 + 当前用户菜单下发 */
@RestController
@RequestMapping("/api/menus")
@RequiredArgsConstructor
public class SysMenuController {

    private final SysMenuMapper menuMapper;
    private final SysUserService userService;
    private final MenuService menuService;

    /** 当前登录用户可见菜单(登录后/刷新后拉取) */
    @GetMapping("/mine")
    public ApiResponse<List<MenuDto>> mine() {
        SysUser user = userService.findByUsername(LoginContext.getUsername());
        if (user == null) {
            throw BizException.of("用户不存在");
        }
        return ApiResponse.ok(menuService.mine(user));
    }

    /** 全量菜单(扁平):角色授权树与人员菜单下拉共用 */
    @GetMapping
    public ApiResponse<List<SysMenu>> list() {
        return ApiResponse.ok(menuService.listAll());
    }

    /** 菜单树(菜单管理页) */
    @GetMapping("/tree")
    public ApiResponse<List<MenuNode>> tree() {
        return ApiResponse.ok(menuService.tree());
    }

    @PostMapping
    @OpLog(module = "菜单管理", action = "新增")
    public ApiResponse<SysMenu> create(@RequestBody SysMenu body) {
        if (body.getName() == null || body.getName().isBlank()) {
            throw BizException.of("菜单名称不能为空");
        }
        if (body.getMenuGroup() == null) {
            body.setMenuGroup(SysMenu.Group.BIZ);
        }
        if (body.getSort() == null) {
            body.setSort(0);
        }
        if (body.getEnabled() == null) {
            body.setEnabled(true);
        }
        // parentId=0 约定为顶级(PUT 部分更新语义下,显式清空上级的唯一通道)
        body.setParentId(normalizeParent(body.getParentId(), null));
        body.setId(null);
        checkPathUnique(body.getPath(), null);
        menuMapper.insert(body);
        return ApiResponse.ok(body);
    }

    @PutMapping("/{id}")
    @OpLog(module = "菜单管理", action = "修改")
    public ApiResponse<SysMenu> update(@PathVariable Long id, @RequestBody SysMenu body) {
        SysMenu menu = require(id);
        if (body.getPath() != null && !body.getPath().equals(menu.getPath())) {
            checkPathUnique(body.getPath(), id);
            menu.setPath(body.getPath());
        }
        if (body.getName() != null) menu.setName(body.getName());
        if (body.getIcon() != null) menu.setIcon(body.getIcon());
        if (body.getMenuGroup() != null) menu.setMenuGroup(body.getMenuGroup());
        if (body.getSort() != null) menu.setSort(body.getSort());
        if (body.getEnabled() != null) menu.setEnabled(body.getEnabled());
        if (body.getParentId() != null) {
            menu.setParentId(normalizeParent(body.getParentId(), id));
        }
        menuMapper.updateById(menu);
        return ApiResponse.ok(menu);
    }

    @DeleteMapping("/{id}")
    @OpLog(module = "菜单管理", action = "删除")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        if (menuMapper.selectById(id) == null) {
            return ApiResponse.ok();
        }
        long children = menuMapper.selectCount(
                com.baomidou.mybatisplus.core.toolkit.Wrappers.<SysMenu>lambdaQuery().eq(SysMenu::getParentId, id));
        if (children > 0) {
            throw BizException.of("存在下级菜单,先删除子菜单");
        }
        menuMapper.deleteById(id);
        return ApiResponse.ok();
    }

    private SysMenu require(Long id) {
        SysMenu menu = menuMapper.selectById(id);
        if (menu == null) {
            throw BizException.of("菜单不存在: " + id);
        }
        return menu;
    }

    /**
     * 上级菜单归一化:0 表示顶级(转 null);校验存在性,并禁止把菜单挂到自己的子树下。
     */
    private Long normalizeParent(Long parentId, Long selfId) {
        if (parentId == null || parentId == 0L) {
            return null;
        }
        if (parentId.equals(selfId)) {
            throw BizException.of("上级菜单不能是自己");
        }
        if (menuMapper.selectById(parentId) == null) {
            throw BizException.of("上级菜单不存在");
        }
        if (selfId != null) {
            Set<Long> descendants = menuService.descendantIds(selfId, menuService.listAll());
            if (descendants.contains(parentId)) {
                throw BizException.of("上级菜单不能是自己的下级");
            }
        }
        return parentId;
    }

    /** path 有唯一约束,提前给出可读提示 */
    private void checkPathUnique(String path, Long excludeId) {
        if (path == null || path.isBlank()) {
            return;
        }
        long exists = menuMapper.selectCount(com.baomidou.mybatisplus.core.toolkit.Wrappers.<SysMenu>lambdaQuery()
                .eq(SysMenu::getPath, path)
                .ne(excludeId != null, SysMenu::getId, excludeId));
        if (exists > 0) {
            throw BizException.of("路由路径已存在: " + path);
        }
    }
}
