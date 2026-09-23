package com.emergency.inspection.entity;

import com.baomidou.mybatisplus.annotation.IEnum;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 接入设备:无人机 / 机场。
 * 与大疆上云 API 对应 —— 机场是网关设备(自带 MQTT 连接),无人机是挂载在机场下的子设备,
 * 因此无人机行记录 gateway_sn 指向所属机场,而它自身的在线状态由拓扑上报维护而非独立连接。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("device")
public class Device extends BaseEntity {

    public enum DeviceType implements IEnum<String> {
        DRONE,   // 无人机
        DOCK;    // 机场(网关)

        @Override
        public String getValue() {
            return name();
        }
    }

    public enum Status implements IEnum<String> {
        ONLINE, OFFLINE;

        @Override
        public String getValue() {
            return name();
        }
    }

    /** 设备序列号,与设备上报的 SN 一致 */
    private String deviceSn;

    private String name;

    private DeviceType deviceType;

    /** 机型,如 DJI Dock 2 / M3D */
    private String deviceModel;

    /** 所属网关(机场)SN;机场自身为空 */
    private String gatewaySn;

    private String firmwareVersion;

    private Status status = Status.OFFLINE;

    private LocalDateTime boundAt;

    private LocalDateTime lastOnlineAt;

    private String remark;

    // ---------- 设备台账:规格与归属 ----------

    /** 厂商,如 大疆 */
    private String manufacturer;

    /** 用途分类(无人机):巡检 / 航拍 / 测绘 / 应急 等 */
    private String usage;

    /** 归航坐标(无人机)或部署坐标(机场) */
    private BigDecimal homeLng;

    private BigDecimal homeLat;

    /** 最大航高 m */
    private BigDecimal maxAltitude;

    /** 续航 min */
    private BigDecimal maxEndurance;

    /** 绑定飞手 id */
    private Long pilotId;

    /** 启停:停用后拒绝该设备接入(仍保留台账与历史数据) */
    private Boolean enabled = true;

    /** 虚拟设备(模拟器接入),用于与真机区分 */
    private Boolean virtual = false;

    /**
     * 地图图标,三种取值:
     * {@code ''} 按设备类型用默认 SVG / {@code preset:xxx} 预设图标 / dataURL 用户上传。
     */
    private String icon;

    // ---------- 以下为联表展示字段,不落库 ----------

    /** 无人机所属机场名称 */
    @TableField(exist = false)
    private String gatewayName;

    /** 机场下挂载的无人机数量 */
    @TableField(exist = false)
    private Integer subDeviceCount;

    /** 绑定飞手姓名 */
    @TableField(exist = false)
    private String pilotName;

    /**
     * 最新遥测的工作模式码(取自 device_osd,不落 device 表)。
     * 前端据此把 ONLINE 派生为「飞行中 / 待命」——状态机仍只维护 ONLINE/OFFLINE 两态。
     */
    @TableField(exist = false)
    private Integer modeCode;
}
