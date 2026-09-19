package com.emergency.inspection.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/** 菜单树节点(菜单管理页) */
@Data
public class MenuNode {

    private Long id;
    private String name;
    private String path;
    private String icon;
    private String group;
    private Long parentId;
    private Integer sort;
    private Boolean enabled;

    private List<MenuNode> children = new ArrayList<>();

    public static MenuNode of(com.emergency.inspection.entity.SysMenu menu) {
        MenuNode node = new MenuNode();
        node.setId(menu.getId());
        node.setName(menu.getName());
        node.setPath(menu.getPath());
        node.setIcon(menu.getIcon());
        node.setGroup(menu.getMenuGroup() == null ? null : menu.getMenuGroup().name());
        node.setParentId(menu.getParentId());
        node.setSort(menu.getSort());
        node.setEnabled(menu.getEnabled());
        return node;
    }
}
