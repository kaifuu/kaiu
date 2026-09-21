package com.emergency.inspection.entity;

import com.baomidou.mybatisplus.annotation.IEnum;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * 电子围栏:CIRCLE(圆心 + radius)与 POLYGON(points_json 顶点串)两种形状。
 * 坐标一律存裸经纬度(与设备 OSD / 巡检点位同源),空间判定走纯 Java(GeoUtils)。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("geo_fence")
public class GeoFence extends BaseEntity {

    public enum FenceType implements IEnum<String> {
        NO_FLY,   // 禁飞区
        LIMIT,    // 限飞区(可配 max_altitude 限高)
        WORK;     // 作业区

        @Override
        public String getValue() {
            return name();
        }
    }

    public enum Shape implements IEnum<String> {
        CIRCLE, POLYGON;

        @Override
        public String getValue() {
            return name();
        }
    }

    private String name;

    private FenceType fenceType;

    private Shape shape;

    /** 顶点/圆心串:[{"lng":116.39,"lat":39.90},..],保留 6 位小数 */
    private String pointsJson;

    /** 圆半径(米),CIRCLE 专用 */
    private BigDecimal radius;

    /** 限高(米),LIMIT 专用,null 表示不限 */
    private BigDecimal maxAltitude;

    private Boolean enabled = Boolean.TRUE;

    private String remark;
}
