package com.emergency.inspection.entity;

import com.baomidou.mybatisplus.annotation.IEnum;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 算法告警:一次识别命中 = 一条告警,携带算法私有结论(payload)与录像取证,
 * 处置闭环留 handler / handleRemark / handleTime。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("ai_algorithm_alarm")
public class AiAlgorithmAlarm extends BaseEntity {

    public enum Level implements IEnum<String> {
        NOTICE, WARN, ERROR;

        @Override
        public String getValue() {
            return name();
        }
    }

    public enum Status implements IEnum<String> {
        /** 待处置 */
        PENDING,
        /** 已处置 */
        HANDLED;

        @Override
        public String getValue() {
            return name();
        }
    }

    private AiAlgorithm.Code algorithmCode;

    /** 一句话结论,如「识别到明火,红外定位异常高温点 412℃」 */
    private String title;

    private Level level;

    private Integer confidence;

    /** 执行识别的设备(机场/飞行器序列号) */
    private String deviceSn;

    private String dockName;

    /** 关联航线任务(飞行巡检中自动识别时带回) */
    private String flightId;

    private BigDecimal longitude;

    private BigDecimal latitude;

    private String address;

    /** 算法私有结论:高温点温度 / 烟羽颜色 / 泄漏类型 / 溯源结果等,JSON 字符串 */
    private String payloadJson;

    /** 自动录像取证的对象存储键(video_record 算法命中时生成) */
    private String videoObjectKey;

    private Integer videoSeconds;

    /** 识别命中时刻 */
    private LocalDateTime occurredAt;

    private Status status;

    private String handler;

    private String handleRemark;

    private LocalDateTime handleTime;

    /** 详情接口回填:payload 解析后的键值对(不落库) */
    @TableField(exist = false)
    private java.util.Map<String, Object> payload;

    /** 列表回填:算法名称(联查算法注册表) */
    @TableField(exist = false)
    private String algorithmName;
}
