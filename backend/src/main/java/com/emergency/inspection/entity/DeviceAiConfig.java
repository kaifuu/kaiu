package com.emergency.inspection.entity;

import com.baomidou.mybatisplus.annotation.IEnum;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

/** 机场 AI 目标识别配置:识别开关 / 跟随 / 置信度模式 / 目标过滤,经 property/set 下发 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("device_ai_config")
public class DeviceAiConfig extends BaseEntity {

    public enum ConfidenceMode implements IEnum<String> {
        COUNT,      // 计数模式
        RESCUE,     // 搜救模式
        CUSTOM;     // 自定义置信度

        @Override
        public String getValue() {
            return name();
        }
    }

    private String deviceSn;

    /** AI 识别开关 */
    private Boolean enabled;

    /** AI 跟随开关(识别到目标后云台跟随) */
    private Boolean followEnabled;

    /** 识别模型名称 */
    private String model;

    private ConfidenceMode confidenceMode;

    /** 自定义模式的置信度阈值 % */
    private Integer confidenceValue;

    /** 目标过滤类型 JSON 数组,如 ["PERSON","CAR","BOAT"] */
    private String filterTypesJson;

    // ---------- 展示字段,不落库 ----------

    /** 保存时是否已同步下发到在线设备 */
    @TableField(exist = false)
    private Boolean synced;

    /** 目标过滤类型(出入参用数组,库内存 JSON 字符串) */
    @TableField(exist = false)
    private List<String> filterTypes;
}
