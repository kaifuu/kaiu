package com.emergency.inspection.entity;

import com.baomidou.mybatisplus.annotation.IEnum;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 固件库:机场 / 飞行器 OTA 升级包 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("firmware")
public class Firmware extends BaseEntity {

    public enum ProductType implements IEnum<String> {
        DOCK,   // 机场固件
        DRONE;  // 飞行器固件

        @Override
        public String getValue() {
            return name();
        }
    }

    private ProductType productType;

    /** 适用机型,如 DJI Dock 2 / Matrice 3D */
    private String deviceModel;

    private String version;

    private String fileName;

    /** 字节 */
    private Long fileSize;

    private String fileMd5;

    private String fileUrl;

    private String remark;
}
