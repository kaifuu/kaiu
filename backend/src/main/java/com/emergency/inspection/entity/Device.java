package com.emergency.inspection.entity;

import com.baomidou.mybatisplus.annotation.IEnum;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

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

    // ---------- 以下为联表展示字段,不落库 ----------

    /** 无人机所属机场名称 */
    @TableField(exist = false)
    private String gatewayName;

    /** 机场下挂载的无人机数量 */
    @TableField(exist = false)
    private Integer subDeviceCount;
}
