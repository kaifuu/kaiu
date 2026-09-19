package com.emergency.inspection.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.emergency.inspection.dto.MenuDto;
import com.emergency.inspection.dto.MenuNode;
import com.emergency.inspection.entity.SysMenu;
import com.emergency.inspection.entity.SysRole;
import com.emergency.inspection.entity.SysUser;
import com.emergency.inspection.mapper.SysMenuMapper;
import com.emergency.inspection.mapper.SysRoleMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/** 菜单下发与菜单树组装 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MenuService {

    private final SysMenuMapper menuMapper;
    private final SysRoleMapper roleMapper;
    private final ObjectMapper objectMapper;

    /** 分组(BIZ 在前) + 同级 sort 排序 */
    private static final Comparator<SysMenu> MENU_ORDER =
            Comparator.comparing((SysMenu m) -> m.getMenuGroup() == null ? 9 : m.getMenuGroup().ordinal())
                    .thenComparing(m -> m.getSort() == null ? 0 : m.getSort())
                    .thenComparing(SysMenu::getId);

    /** 当前用户可见菜单(登录后/刷新后拉取) */
    public List<MenuDto> mine(SysUser user) {
        Set<Long> allowed = allowedMenuIds(user);
        return menuMapper.selectList(Wrappers.<SysMenu>lambdaQuery()
                        .eq(SysMenu::getEnabled, true))
                .stream()
                .filter(m -> allowed == null || allowed.contains(m.getId()))
                .sorted(MENU_ORDER)
                .map(m -> new MenuDto(m.getId(), m.getName(), m.getPath(), m.getIcon(),
                        m.getMenuGroup() == null ? null : m.getMenuGroup().name(), m.getSort()))
                .toList();
    }

    /** 全量菜单(扁平,角色授权树与菜单管理页共用) */
    public List<SysMenu> listAll() {
        return menuMapper.selectList(Wrappers.<SysMenu>lambdaQuery()).stream()
                .sorted(MENU_ORDER)
                .toList();
    }

    /** 菜单树(菜单管理页左树 / 上级菜单候选) */
    public List<MenuNode> tree() {
        List<SysMenu> all = listAll();
        Map<Long, MenuNode> nodeMap = new LinkedHashMap<>();
        for (SysMenu m : all) {
            nodeMap.put(m.getId(), MenuNode.of(m));
        }
        List<MenuNode> roots = new ArrayList<>();
        for (SysMenu m : all) {
            MenuNode node = nodeMap.get(m.getId());
            MenuNode parent = m.getParentId() == null ? null : nodeMap.get(m.getParentId());
            if (parent != null) {
                parent.getChildren().add(node);
            } else {
                roots.add(node);
            }
        }
        return roots;
    }

    /** 节点自身 + 全部子孙 id(删除校验 / 防成环) */
    public Set<Long> descendantIds(Long rootId, List<SysMenu> all) {
        Set<Long> ids = new java.util.LinkedHashSet<>();
        ids.add(rootId);
        boolean grew = true;
        while (grew) {
            grew = false;
            for (SysMenu m : all) {
                if (m.getParentId() != null && ids.contains(m.getParentId()) && ids.add(m.getId())) {
                    grew = true;
                }
            }
        }
        return ids;
    }

    /** null 表示不限制(ADMIN);否则为授权菜单 id 集合 */
    private Set<Long> allowedMenuIds(SysUser user) {
        if (user == null || user.getRoleId() == null) {
            return Set.of();
        }
        SysRole role = roleMapper.selectById(user.getRoleId());
        if (role == null || SysRole.CODE_ADMIN.equals(role.getCode())) {
            return null;
        }
        return parseMenuIds(role.getMenuIdsJson());
    }

    /** 解析 menuIdsJson;格式非法按「无授权」处理,避免一个脏数据放行全部菜单 */
    public Set<Long> parseMenuIds(String json) {
        if (json == null || json.isBlank()) {
            return Set.of();
        }
        try {
            List<Long> ids = objectMapper.readValue(json,
                    objectMapper.getTypeFactory().constructCollectionType(List.class, Long.class));
            return ids.stream().filter(java.util.Objects::nonNull).collect(Collectors.toSet());
        } catch (Exception e) {
            log.warn("角色 menuIdsJson 解析失败,按无授权处理: {}", json);
            return Set.of();
        }
    }
}
