package com.emergency.inspection.entity;

import com.baomidou.mybatisplus.annotation.IEnum;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 飞手(无人机驾驶员) */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("pilot")
public class Pilot extends BaseEntity {

    public enum CertType implements IEnum<String> {
        CAAC,   // 民航局执照
        UTC,    // 大疆慧飞
        AOPA,
        NONE;   // 无证

        @Override
        public String getValue() {
            return name();
        }
    }

    public enum Status implements IEnum<String> {
        AVAILABLE,   // 可调度
        ON_TASK,     // 执行中
        LEAVE,       // 休假
        DISABLED;    // 停用

        @Override
        public String getValue() {
            return name();
        }
    }

    private String name;

    private String phone;

    private Integer age;

    private Integer experienceYears;

    /** 所属板块 / 责任片区 */
    private String area;

    private CertType certType = CertType.CAAC;

    /** 发证机构 */
    private String certOrg;

    private String certNo;

    private Status status = Status.AVAILABLE;

    private String remark;
}
