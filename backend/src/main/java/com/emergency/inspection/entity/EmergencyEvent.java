package com.emergency.inspection.entity;

import com.baomidou.mybatisplus.annotation.IEnum;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 应急事件:突发事件的接报、响应与处置归档 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("emergency_event")
public class EmergencyEvent extends BaseEntity {

    /** 环境应急事件类型 */
    public enum Category implements IEnum<String> {
        WATER_POLLUTION,   // 水污染
        AIR_POLLUTION,     // 大气污染
        SOIL_POLLUTION,    // 土壤污染
        CHEMICAL,          // 危化品泄漏
        ECOLOGY,           // 生态破坏
        OTHER;

        @Override
        public String getValue() {
            return name();
        }
    }

    /** 事件等级:Ⅰ 特别重大 / Ⅱ 重大 / Ⅲ 较大 / Ⅳ 一般 */
    public enum Level implements IEnum<String> {
        I, II, III, IV;

        @Override
        public String getValue() {
            return name();
        }
    }

    public enum Status implements IEnum<String> {
        PENDING, RESPONDING, HANDLED, ARCHIVED;

        @Override
        public String getValue() {
            return name();
        }
    }

    private String title;

    private Category category = Category.OTHER;

    private Level level = Level.IV;

    private String address;

    private BigDecimal longitude;

    private BigDecimal latitude;

    private LocalDateTime occurTime;

    private String reporter;

    private String reporterPhone;

    private String description;

    private Status status = Status.PENDING;

    /** 现场指挥 */
    private String commander;

    /** 处置措施 */
    private String measure;

    private LocalDateTime finishTime;
}
