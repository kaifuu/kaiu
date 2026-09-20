package com.emergency.inspection.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 臭气读数:按批次刷新,同批共享区域风向风速(溯源与扩散模拟的驱动量) */
@Data
@TableName("ai_odor_reading")
public class AiOdorReading {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long stationId;

    /** 硫化氢 ppm(恶臭特征气体) */
    private BigDecimal h2sPpm;

    /** 氨气 ppm */
    private BigDecimal nh3Ppm;

    /** 臭气浓度(无量纲,GB 14554 三点比较式臭袋法口径) */
    private Integer odorUnit;

    /** 风速 m/s */
    private BigDecimal windSpeed;

    /** 风向(度,风的来向):0=北,顺时针 */
    private Integer windDirection;

    private LocalDateTime readTime;

    private LocalDateTime createTime;

    /** 分布图回填:站点信息(联查,不落库) */
    @TableField(exist = false)
    private AiOdorStation station;
}
