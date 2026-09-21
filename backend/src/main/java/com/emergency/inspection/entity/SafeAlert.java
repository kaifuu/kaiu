package com.emergency.inspection.entity;

import com.baomidou.mybatisplus.annotation.IEnum;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 飞行安全预警:规则引擎(围栏闯入/预测/电量骤降/高度突变/信号弱)生成,处置闭环 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("safe_alert")
public class SafeAlert extends BaseEntity {

    public enum AlertType implements IEnum<String> {
        FENCE_BREACH,       // 围栏闯入(实时)
        PREDICTED_BREACH,   // 预测闯入(60s 外推)
        BATTERY_ANOMALY,    // 电量骤降
        ALTITUDE_JUMP,      // 高度突变
        SIGNAL_WEAK;        // 信号弱

        @Override
        public String getValue() {
            return name();
        }
    }

    public enum Level implements IEnum<String> {
        WARN, ERROR;

        @Override
        public String getValue() {
            return name();
        }
    }

    public enum Status implements IEnum<String> {
        PENDING, HANDLED;

        @Override
        public String getValue() {
            return name();
        }
    }

    private AlertType alertType;

    private Level level;

    private String deviceSn;

    private String title;

    private String message;

    private BigDecimal longitude;

    private BigDecimal latitude;

    private BigDecimal height;

    private LocalDateTime occurredAt;

    private Status status = Status.PENDING;

    private String handler;

    private LocalDateTime handleTime;

    private String handleRemark;
}
