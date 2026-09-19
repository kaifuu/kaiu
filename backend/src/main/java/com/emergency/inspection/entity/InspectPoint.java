package com.emergency.inspection.entity;

import com.baomidou.mybatisplus.annotation.IEnum;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/** 巡检点位 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("inspect_point")
public class InspectPoint extends BaseEntity {

    /** 点位类型(生态环境巡检对象) */
    public enum Category implements IEnum<String> {
        OUTFALL,        // 入河排污口
        RIVER,          // 河道断面
        AIR,            // 空气自动站
        WATER_SOURCE,   // 饮用水源地
        SOLID_WASTE,    // 固废堆场
        FOREST,         // 林地 / 自然保护区
        OTHER;

        @Override
        public String getValue() {
            return name();
        }
    }

    /** 风险等级 */
    public enum RiskLevel implements IEnum<String> {
        LOW, MEDIUM, HIGH, EXTREME;

        @Override
        public String getValue() {
            return name();
        }
    }

    public enum Status implements IEnum<String> {
        ENABLED, DISABLED;

        @Override
        public String getValue() {
            return name();
        }
    }

    private String name;

    /** 点位编码,全局唯一 */
    private String code;

    private Category category = Category.OTHER;

    private RiskLevel riskLevel = RiskLevel.LOW;

    /** 所属区域/网格 */
    private String area;

    private String address;

    private BigDecimal longitude;

    private BigDecimal latitude;

    /** 责任人 */
    private String manager;

    private String managerPhone;

    private Status status = Status.ENABLED;

    private String remark;
}
