package com.emergency.inspection.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 组织(树形,parentId 平列不建实体关联) */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_org")
public class SysOrg extends BaseEntity {

    private String name;

    /** 上级组织 id;null = 顶级 */
    private Long parentId;

    private String orgCode;

    private Integer sort = 0;

    private Boolean enabled = true;
}
