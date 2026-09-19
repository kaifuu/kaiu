package com.emergency.inspection.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.emergency.inspection.common.BizException;
import com.emergency.inspection.common.PageUtil;
import com.emergency.inspection.dto.query.RoleQuery;
import com.emergency.inspection.entity.SysRole;
import com.emergency.inspection.entity.SysUser;
import com.emergency.inspection.mapper.SysRoleMapper;
import com.emergency.inspection.mapper.SysUserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/** 角色管理 */
@Service
@RequiredArgsConstructor
public class SysRoleService {

    private final SysRoleMapper roleMapper;
    private final SysUserMapper userMapper;

    /** 全量角色(人员管理的角色下拉) */
    public List<SysRole> list() {
        return roleMapper.selectList(Wrappers.<SysRole>lambdaQuery().orderByAsc(SysRole::getId));
    }

    public IPage<SysRole> page(RoleQuery query) {
        String kw = query.getKeyword();
        return roleMapper.selectPage(
                PageUtil.build(query, "id", PageUtil.allowedCamel("name", "code", "enabled", "createTime")),
                Wrappers.<SysRole>lambdaQuery()
                        .and(kw != null && !kw.isBlank(),
                                w -> w.like(SysRole::getName, kw.trim()).or().like(SysRole::getCode, kw.trim()))
                        .eq(query.getEnabled() != null, SysRole::getEnabled, query.getEnabled()));
    }

    public SysRole create(SysRole body) {
        if (isBlank(body.getName())) {
            throw BizException.of("角色名称不能为空");
        }
        if (isBlank(body.getCode())) {
            throw BizException.of("角色编码不能为空");
        }
        if (roleMapper.selectCount(Wrappers.<SysRole>lambdaQuery().eq(SysRole::getCode, body.getCode())) > 0) {
            throw BizException.of("角色编码已存在: " + body.getCode());
        }
        body.setId(null);
        if (isBlank(body.getMenuIdsJson())) {
            body.setMenuIdsJson("[]");
        }
        if (body.getEnabled() == null) {
            body.setEnabled(true);
        }
        roleMapper.insert(body);
        return body;
    }

    public SysRole update(Long id, SysRole body) {
        SysRole role = require(id);
        if (body.getName() != null) {
            role.setName(body.getName());
        }
        if (body.getRemark() != null) {
            role.setRemark(body.getRemark());
        }
        if (body.getMenuIdsJson() != null) {
            role.setMenuIdsJson(body.getMenuIdsJson());
        }
        if (body.getEnabled() != null) {
            role.setEnabled(body.getEnabled());
        }
        roleMapper.updateById(role);
        return role;
    }

    public void delete(Long id) {
        SysRole role = require(id);
        if (SysRole.CODE_ADMIN.equals(role.getCode())) {
            throw BizException.of("内置管理员角色不允许删除");
        }
        Long bound = userMapper.selectCount(Wrappers.<SysUser>lambdaQuery().eq(SysUser::getRoleId, id));
        if (bound != null && bound > 0) {
            throw BizException.of("仍有 " + bound + " 个用户使用该角色,先调整再删除");
        }
        roleMapper.deleteById(id);
    }

    public SysRole require(Long id) {
        SysRole role = roleMapper.selectById(id);
        if (role == null) {
            throw BizException.of("角色不存在: " + id);
        }
        return role;
    }

    private static boolean isBlank(String v) {
        return v == null || v.isBlank();
    }
}
