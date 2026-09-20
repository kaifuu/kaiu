package com.emergency.inspection.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/** 臭气监测点位:厂区/填埋库区/罐区布设的恶臭传感站,分布图与溯源的输入 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("ai_odor_station")
public class AiOdorStation extends BaseEntity {

    private String stationCode;

    private String stationName;

    /** 所在区域:填埋库区 / 罐区 / 厂界 ... */
    private String area;

    private BigDecimal longitude;

    private BigDecimal latitude;
}
