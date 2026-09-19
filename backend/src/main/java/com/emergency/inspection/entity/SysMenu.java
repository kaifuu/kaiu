package com.emergency.inspection.entity;

import com.baomidou.mybatisplus.annotation.IEnum;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 菜单:树形结构(parentId 空=顶级),menuGroup 决定归属「业务菜单/系统管理」分组,同级按 sort 排序 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_menu")
public class SysMenu extends BaseEntity {

    /** 菜单分组:BIZ 巡检业务 / SVC 巡检服务 / SYS 系统管理 */
    public enum Group implements IEnum<String> {
        BIZ, SVC, SYS;

        @Override
        public String getValue() {
            return name();
        }
    }

    private String name;

    /** 前端路由路径,全局唯一 */
    private String path;

    /** Element Plus 图标名(已全局注册,前端 component :is 渲染) */
    private String icon;

    /** 列名 menu_group:group 是 SQL 保留字 */
    private Group menuGroup = Group.BIZ;

    /** 上级菜单 id(平列,不建实体关联);null = 顶级 */
    private Long parentId;

    private Integer sort = 0;

    private Boolean enabled = true;

    public SysMenu() {
    }

    public SysMenu(String name, String path, String icon, Group menuGroup, int sort) {
        this.name = name;
        this.path = path;
        this.icon = icon;
        this.menuGroup = menuGroup;
        this.sort = sort;
    }
}
