package com.emergency.inspection.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.emergency.inspection.common.BizException;
import com.emergency.inspection.dto.OrgNode;
import com.emergency.inspection.entity.SysOrg;
import com.emergency.inspection.entity.SysUser;
import com.emergency.inspection.mapper.SysOrgMapper;
import com.emergency.inspection.mapper.SysUserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 组织管理(树形) */
@Service
@RequiredArgsConstructor
public class SysOrgService {

    private final SysOrgMapper orgMapper;
    private final SysUserMapper userMapper;

    private static final Comparator<SysOrg> ORG_ORDER =
            Comparator.comparing((SysOrg o) -> o.getSort() == null ? 0 : o.getSort())
                    .thenComparing(SysOrg::getId);

    /** 组织树 */
    public List<OrgNode> tree() {
        List<SysOrg> all = listAll();
        Map<Long, OrgNode> nodeMap = new LinkedHashMap<>();
        for (SysOrg org : all) {
            nodeMap.put(org.getId(), OrgNode.of(org));
        }
        List<OrgNode> roots = new ArrayList<>();
        for (SysOrg org : all) {
            OrgNode node = nodeMap.get(org.getId());
            OrgNode parent = org.getParentId() == null ? null : nodeMap.get(org.getParentId());
            if (parent != null) {
                parent.getChildren().add(node);
            } else {
                roots.add(node);
            }
        }
        return roots;
    }

    public List<SysOrg> listAll() {
        return orgMapper.selectList(Wrappers.<SysOrg>lambdaQuery()).stream().sorted(ORG_ORDER).toList();
    }

    public SysOrg create(SysOrg body) {
        if (body.getName() == null || body.getName().isBlank()) {
            throw BizException.of("组织名称不能为空");
        }
        if (body.getParentId() != null && orgMapper.selectById(body.getParentId()) == null) {
            throw BizException.of("上级组织不存在");
        }
        body.setId(null);
        if (body.getSort() == null) {
            body.setSort(0);
        }
        if (body.getEnabled() == null) {
            body.setEnabled(true);
        }
        orgMapper.insert(body);
        return body;
    }

    public SysOrg update(Long id, SysOrg body) {
        SysOrg org = require(id);
        if (body.getName() != null) {
            org.setName(body.getName());
        }
        if (body.getOrgCode() != null) {
            org.setOrgCode(body.getOrgCode());
        }
        if (body.getSort() != null) {
            org.setSort(body.getSort());
        }
        if (body.getEnabled() != null) {
            org.setEnabled(body.getEnabled());
        }
        if (body.getParentId() != null) {
            if (body.getParentId().equals(id)) {
                throw BizException.of("上级组织不能是自己");
            }
            // 上级不能落在自己的子树里,否则树断开成环
            if (descendantIds(id).contains(body.getParentId())) {
                throw BizException.of("上级组织不能是自己的下级");
            }
            org.setParentId(body.getParentId());
        }
        orgMapper.updateById(org);
        return org;
    }

    public void delete(Long id) {
        require(id);
        Long children = orgMapper.selectCount(Wrappers.<SysOrg>lambdaQuery().eq(SysOrg::getParentId, id));
        if (children != null && children > 0) {
            throw BizException.of("存在下级组织,先删除子组织");
        }
        Long bound = userMapper.selectCount(Wrappers.<SysUser>lambdaQuery().eq(SysUser::getOrgId, id));
        if (bound != null && bound > 0) {
            throw BizException.of("仍有 " + bound + " 个用户归属该组织,先调整再删除");
        }
        orgMapper.deleteById(id);
    }

    /** 自身 + 全部子孙 id */
    public java.util.Set<Long> descendantIds(Long rootId) {
        List<SysOrg> all = listAll();
        java.util.Set<Long> ids = new java.util.LinkedHashSet<>();
        ids.add(rootId);
        boolean grew = true;
        while (grew) {
            grew = false;
            for (SysOrg o : all) {
                if (o.getParentId() != null && ids.contains(o.getParentId()) && ids.add(o.getId())) {
                    grew = true;
                }
            }
        }
        return ids;
    }

    public SysOrg require(Long id) {
        SysOrg org = orgMapper.selectById(id);
        if (org == null) {
            throw BizException.of("组织不存在: " + id);
        }
        return org;
    }
}
