package com.emergency.inspection.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 菜单下发 DTO:登录/刷新时按角色过滤后的扁平菜单项 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class MenuDto {

    private Long id;
    private String name;
    private String path;
    private String icon;

    /** BIZ | SYS */
    private String group;

    private Integer sort;
}
