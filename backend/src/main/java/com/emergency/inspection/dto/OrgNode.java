package com.emergency.inspection.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/** 组织树节点 */
@Data
public class OrgNode {

    private Long id;
    private String name;
    private Long parentId;
    private String orgCode;
    private Integer sort;
    private Boolean enabled;

    private List<OrgNode> children = new ArrayList<>();

    public static OrgNode of(com.emergency.inspection.entity.SysOrg org) {
        OrgNode node = new OrgNode();
        node.setId(org.getId());
        node.setName(org.getName());
        node.setParentId(org.getParentId());
        node.setOrgCode(org.getOrgCode());
        node.setSort(org.getSort());
        node.setEnabled(org.getEnabled());
        return node;
    }
}
