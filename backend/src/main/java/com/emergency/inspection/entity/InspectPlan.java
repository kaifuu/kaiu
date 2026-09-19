package com.emergency.inspection.entity;

import com.baomidou.mybatisplus.annotation.IEnum;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;

/** 巡检计划:定义周期性巡检的范围(覆盖点位)与周期 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("inspect_plan")
public class InspectPlan extends BaseEntity {

    /** 计划类别 */
    public enum Category implements IEnum<String> {
        DAILY, SPECIAL, EMERGENCY;

        @Override
        public String getValue() {
            return name();
        }
    }

    /** 周期单位 */
    public enum CycleType implements IEnum<String> {
        DAY, WEEK, MONTH, ONCE;

        @Override
        public String getValue() {
            return name();
        }
    }

    public enum Status implements IEnum<String> {
        DRAFT, ENABLED, DISABLED;

        @Override
        public String getValue() {
            return name();
        }
    }

    private String name;

    /** 计划编码,全局唯一 */
    private String code;

    private Category category = Category.DAILY;

    private CycleType cycleType = CycleType.DAY;

    /** 每 N 个周期单位执行一次 */
    private Integer cycleValue = 1;

    private LocalDate startDate;

    private LocalDate endDate;

    /** 计划负责人 */
    private String owner;

    private String ownerPhone;

    /** 覆盖点位 id 数组 JSON,如 [1,2,3] */
    private String pointIds = "[]";

    private Status status = Status.DRAFT;

    private String remark;
}
