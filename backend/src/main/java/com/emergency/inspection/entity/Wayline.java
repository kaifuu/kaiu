package com.emergency.inspection.entity;

import com.baomidou.mybatisplus.annotation.IEnum;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * 航线库:大疆 WPML 航线的平台侧管理单元。
 * 真实 WPML 是 KMZ 压缩包,平台以「航点序列 + 飞行参数」承载,下发时由云侧组装成文件。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("wayline")
public class Wayline extends BaseEntity {

    public enum TemplateTypes implements IEnum<String> {
        WAYPOINT,   // 航点模板
        POI,        // 兴趣点
        INSPECT,    // 巡查拍照
        STRIP,      // 航带作业
        SOLID;      // 立体作业

        @Override
        public String getValue() {
            return name();
        }
    }

    private String code;

    private String name;

    private TemplateTypes templateTypes;

    /** 默认航线高度 m */
    private BigDecimal alt;

    /** 默认飞行速度 m/s */
    private BigDecimal speed;

    /** 航点数组 JSON:[{longitude,latitude,height,speed}] */
    private String waypointsJson;

    private String remark;

    // ---------- 展示字段,不落库 ----------

    /** 航点数量(由 waypointsJson 解析) */
    @TableField(exist = false)
    private Integer waypointCount;
}
