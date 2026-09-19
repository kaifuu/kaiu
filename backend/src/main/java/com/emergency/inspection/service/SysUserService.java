package com.emergency.inspection.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.emergency.inspection.common.BizException;
import com.emergency.inspection.common.PageUtil;
import com.emergency.inspection.dto.SysUserForm;
import com.emergency.inspection.dto.query.UserQuery;
import com.emergency.inspection.entity.SysOrg;
import com.emergency.inspection.entity.SysRole;
import com.emergency.inspection.entity.SysTenant;
import com.emergency.inspection.entity.SysUser;
import com.emergency.inspection.mapper.SysOrgMapper;
import com.emergency.inspection.mapper.SysRoleMapper;
import com.emergency.inspection.mapper.SysTenantMapper;
import com.emergency.inspection.mapper.SysUserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/** 人员管理 */
@Service
@RequiredArgsConstructor
public class SysUserService {

    private static final BCryptPasswordEncoder ENCODER = new BCryptPasswordEncoder();

    private final SysUserMapper userMapper;
    private final SysRoleMapper roleMapper;
    private final SysOrgMapper orgMapper;
    private final SysTenantMapper tenantMapper;

    @Value("${user.default-password:123456}")
    private String defaultPassword;

    /** 全量人员(下拉/选择器用) */
    public List<SysUser> list(String keyword) {
        String kw = keyword == null ? "" : keyword.trim();
        List<SysUser> rows = userMapper.selectList(Wrappers.<SysUser>lambdaQuery()
                .and(!kw.isEmpty(), w -> w.like(SysUser::getUsername, kw)
                        .or().like(SysUser::getNickname, kw)
                        .or().like(SysUser::getPhone, kw))
                .orderByAsc(SysUser::getId));
        fillNames(rows);
        return rows;
    }

    public IPage<SysUser> page(UserQuery query) {
        String kw = query.getKeyword();
        IPage<SysUser> page = userMapper.selectPage(
                PageUtil.build(query, "id",
                        PageUtil.allowedCamel("username", "nickname", "status", "lastLoginAt", "createTime")),
                Wrappers.<SysUser>lambdaQuery()
                        .and(kw != null && !kw.isBlank(), w -> w.like(SysUser::getUsername, kw.trim())
                                .or().like(SysUser::getNickname, kw.trim())
                                .or().like(SysUser::getPhone, kw.trim()))
                        .eq(query.getRoleId() != null, SysUser::getRoleId, query.getRoleId())
                        .eq(query.getOrgId() != null, SysUser::getOrgId, query.getOrgId())
                        .eq(query.getTenantId() != null, SysUser::getTenantId, query.getTenantId())
                        .eq(query.getStatus() != null, SysUser::getStatus, query.getStatus()));
        fillNames(page.getRecords());
        return page;
    }

    @Transactional
    public SysUser create(SysUserForm form) {
        if (form.getUsername() == null || form.getUsername().isBlank()) {
            throw BizException.of("用户名不能为空");
        }
        String username = form.getUsername().trim();
        if (findByUsername(username) != null) {
            throw BizException.of("用户名已存在: " + username);
        }
        SysUser user = new SysUser();
        user.setUsername(username);
        user.setPassword(ENCODER.encode(defaultPassword));
        applyForm(user, form);
        userMapper.insert(user);
        fillNames(List.of(user));
        return user;
    }

    @Transactional
    public SysUser update(Long id, SysUserForm form) {
        SysUser user = require(id);
        applyForm(user, form);
        userMapper.updateById(user);
        fillNames(List.of(user));
        return user;
    }

    public void delete(Long id) {
        SysUser user = require(id);
        if (SysUser.USERNAME_ADMIN.equals(user.getUsername())) {
            throw BizException.of("内置管理员不允许删除");
        }
        userMapper.deleteById(id);
    }

    /** 重置密码为默认值 */
    public void resetPassword(Long id) {
        SysUser user = require(id);
        user.setPassword(ENCODER.encode(defaultPassword));
        userMapper.updateById(user);
    }

    public SysUser findByUsername(String username) {
        if (username == null || username.isBlank()) {
            return null;
        }
        return userMapper.selectOne(Wrappers.<SysUser>lambdaQuery()
                .eq(SysUser::getUsername, username)
                .last("limit 1"));
    }

    public SysUser require(Long id) {
        SysUser user = userMapper.selectById(id);
        if (user == null) {
            throw BizException.of("用户不存在: " + id);
        }
        return user;
    }

    /** 校验并回填密码(登录/改密共用) */
    public boolean matches(String raw, String encoded) {
        return ENCODER.matches(raw, encoded);
    }

    public String encode(String raw) {
        return ENCODER.encode(raw);
    }

    public String getDefaultPassword() {
        return defaultPassword;
    }

    /** 表单 → 实体:用户名与密码不在此处变更 */
    private void applyForm(SysUser user, SysUserForm form) {
        if (form.getNickname() != null) {
            user.setNickname(form.getNickname().trim());
        }
        if (form.getPhone() != null) {
            user.setPhone(form.getPhone().trim());
        }
        if (form.getStatus() != null) {
            user.setStatus(form.getStatus());
        }
        if (form.getOrgId() != null) {
            if (orgMapper.selectById(form.getOrgId()) == null) {
                throw BizException.of("所属组织不存在");
            }
            user.setOrgId(form.getOrgId());
        }
        if (form.getTenantId() != null) {
            if (tenantMapper.selectById(form.getTenantId()) == null) {
                throw BizException.of("所属租户不存在");
            }
            user.setTenantId(form.getTenantId());
        }
        if (form.getRoleId() != null) {
            if (roleMapper.selectById(form.getRoleId()) == null) {
                throw BizException.of("角色不存在");
            }
            user.setRoleId(form.getRoleId());
        }
    }

    /** 批量回填角色/组织/租户名称,避免 N+1 */
    public void fillNames(Collection<SysUser> users) {
        if (users == null || users.isEmpty()) {
            return;
        }
        Map<Long, SysRole> roles = byId(load(ids(users, SysUser::getRoleId), roleMapper), SysRole::getId);
        Map<Long, SysOrg> orgs = byId(load(ids(users, SysUser::getOrgId), orgMapper), SysOrg::getId);
        Map<Long, SysTenant> tenants = byId(load(ids(users, SysUser::getTenantId), tenantMapper), SysTenant::getId);
        for (SysUser u : users) {
            SysRole role = u.getRoleId() == null ? null : roles.get(u.getRoleId());
            if (role != null) {
                u.setRoleName(role.getName());
                u.setRoleCode(role.getCode());
            }
            SysOrg org = u.getOrgId() == null ? null : orgs.get(u.getOrgId());
            u.setOrgName(org == null ? null : org.getName());
            SysTenant tenant = u.getTenantId() == null ? null : tenants.get(u.getTenantId());
            u.setTenantName(tenant == null ? null : tenant.getName());
        }
    }

    /** 收集非空外键 */
    private static Set<Long> ids(Collection<SysUser> users, Function<SysUser, Long> getter) {
        return users.stream().map(getter).filter(java.util.Objects::nonNull).collect(Collectors.toSet());
    }

    /** selectBatchIds 传空集合会拼出非法 SQL(IN ()),这里统一兜住 */
    private static <T> List<T> load(Set<Long> ids, com.baomidou.mybatisplus.core.mapper.BaseMapper<T> mapper) {
        return ids.isEmpty() ? List.of() : mapper.selectBatchIds(ids);
    }

    private static <T> Map<Long, T> byId(List<T> list, Function<T, Long> getter) {
        if (list == null || list.isEmpty()) {
            return Map.of();
        }
        return list.stream().collect(Collectors.toMap(getter, Function.identity(), (a, b) -> a));
    }
}
