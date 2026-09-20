package com.emergency.inspection.entity;

import com.baomidou.mybatisplus.annotation.IEnum;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 算法管理:平台侧识别算法的注册与运行配置。
 * 算法本身跑在平台(视频流 / 传感数据分析),命中后落 {@link AiAlgorithmAlarm}。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("ai_algorithm")
public class AiAlgorithm extends BaseEntity {

    /** 算法编码:业务语义唯一键,告警与生成器都按它路由 */
    public enum Code implements IEnum<String> {
        /** 厂区烟火识别与异常高温点定位 */
        SMOKE_FIRE,
        /** 臭气检测与扩散溯源 */
        ODOR_TRACE,
        /** 非法倾倒识别 */
        ILLEGAL_DUMP,
        /** 覆盖膜异常状态识别 */
        COVER_MEMBRANE,
        /** 烟囱排放视觉监测 */
        CHIMNEY_EMISSION,
        /** 厂区泄漏检测 */
        LEAK_DETECT;

        @Override
        public String getValue() {
            return name();
        }
    }

    /** 告警等级与 HMS 同口径,便于前端复用一套字典习惯 */
    public enum Level implements IEnum<String> {
        NOTICE, WARN, ERROR;

        @Override
        public String getValue() {
            return name();
        }
    }

    private Code code;

    private String name;

    private String description;

    /** 适用场景,如「厂区 / 填埋库区 / 罐区」 */
    private String scene;

    /** 是否依赖红外镜头(异常高温点定位) */
    private Boolean requiresIr;

    /** 命中后是否自动录像取证 */
    private Boolean videoRecord;

    private Boolean enabled;

    /** 置信度门槛(50-99),低于该值的识别结果不上报告警 */
    private Integer confidenceValue;

    /** 告警等级基线(严重命中可升级为 ERROR) */
    private Level alarmLevel;

    /** 最近一次执行(手动触发或飞行中自动识别)时刻 */
    private LocalDateTime lastRunAt;

    /** 累计执行次数 */
    private Integer runCount;
}
