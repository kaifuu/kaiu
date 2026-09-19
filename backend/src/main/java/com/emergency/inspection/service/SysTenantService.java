package com.emergency.inspection.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.emergency.inspection.common.BizException;
import com.emergency.inspection.common.PageUtil;
import com.emergency.inspection.dto.query.TenantQuery;
import com.emergency.inspection.entity.SysTenant;
import com.emergency.inspection.entity.SysUser;
import com.emergency.inspection.mapper.SysTenantMapper;
import com.emergency.inspection.mapper.SysUserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/** 租户管理 */
@Service
@RequiredArgsConstructor
public class SysTenantService {

    private final SysTenantMapper tenantMapper;
    private final SysUserMapper userMapper;

    public List<SysTenant> list() {
        return tenantMapper.selectList(Wrappers.<SysTenant>lambdaQuery()
                .orderByDesc(SysTenant::getId));
    }

    public IPage<SysTenant> page(TenantQuery query) {
        String kw = query.getKeyword();
        return tenantMapper.selectPage(
                PageUtil.build(query, "id", PageUtil.allowedCamel("name", "code", "enabled", "createTime")),
                Wrappers.<SysTenant>lambdaQuery()
                        .and(kw != null && !kw.isBlank(),
                                w -> w.like(SysTenant::getName, kw.trim()).or().like(SysTenant::getCode, kw.trim()))
                        .eq(query.getEnabled() != null, SysTenant::getEnabled, query.getEnabled()));
    }

    public SysTenant create(SysTenant body) {
        if (isBlank(body.getName())) {
            throw BizException.of("租户名称不能为空");
        }
        if (isBlank(body.getCode())) {
            throw BizException.of("租户编码不能为空");
        }
        if (existsCode(body.getCode(), null)) {
            throw BizException.of("租户编码已存在: " + body.getCode());
        }
        body.setId(null);
        if (body.getEnabled() == null) {
            body.setEnabled(true);
        }
        tenantMapper.insert(body);
        return body;
    }

    public SysTenant update(Long id, SysTenant body) {
        SysTenant tenant = require(id);
        if (body.getName() != null) {
            tenant.setName(body.getName());
        }
        if (body.getRemark() != null) {
            tenant.setRemark(body.getRemark());
        }
        if (body.getEnabled() != null) {
            tenant.setEnabled(body.getEnabled());
        }
        tenantMapper.updateById(tenant);
        return tenant;
    }

    public void delete(Long id) {
        require(id);
        Long bound = userMapper.selectCount(Wrappers.<SysUser>lambdaQuery().eq(SysUser::getTenantId, id));
        if (bound != null && bound > 0) {
            throw BizException.of("仍有 " + bound + " 个用户归属该租户,先调整再删除");
        }
        tenantMapper.deleteById(id);
    }

    public SysTenant require(Long id) {
        SysTenant tenant = tenantMapper.selectById(id);
        if (tenant == null) {
            throw BizException.of("租户不存在: " + id);
        }
        return tenant;
    }

    private boolean existsCode(String code, Long excludeId) {
        return tenantMapper.selectCount(Wrappers.<SysTenant>lambdaQuery()
                .eq(SysTenant::getCode, code)
                .ne(excludeId != null, SysTenant::getId, excludeId)) > 0;
    }

    private static boolean isBlank(String v) {
        return v == null || v.isBlank();
    }
}
