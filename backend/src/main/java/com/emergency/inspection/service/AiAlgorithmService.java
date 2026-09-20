package com.emergency.inspection.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.emergency.inspection.common.BizException;
import com.emergency.inspection.entity.AiAlgorithm;
import com.emergency.inspection.entity.AiAlgorithmAlarm;
import com.emergency.inspection.mapper.AiAlgorithmMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 算法管理:算法注册表的读取与运行配置(启停 / 置信度门槛 / 告警等级),
 * 手动执行经生成器立即合成一次识别命中(演示与联调用)。
 */
@Service
@RequiredArgsConstructor
public class AiAlgorithmService {

    private final AiAlgorithmMapper algorithmMapper;
    private final AiAlarmGenerator generator;

    /** 全量算法(仅 6 条,直接返回列表) */
    public List<AiAlgorithm> list() {
        return algorithmMapper.selectList(Wrappers.<AiAlgorithm>lambdaQuery()
                .orderByAsc(AiAlgorithm::getId));
    }

    public AiAlgorithm require(Long id) {
        AiAlgorithm algo = algorithmMapper.selectById(id);
        if (algo == null) {
            throw BizException.of("算法不存在");
        }
        return algo;
    }

    /** 保存运行配置 */
    @Transactional
    public AiAlgorithm config(Long id, AiAlgorithm body) {
        AiAlgorithm algo = require(id);
        if (body.getEnabled() != null) {
            algo.setEnabled(body.getEnabled());
        }
        if (body.getConfidenceValue() != null) {
            algo.setConfidenceValue(Math.max(50, Math.min(99, body.getConfidenceValue())));
        }
        if (body.getAlarmLevel() != null) {
            algo.setAlarmLevel(body.getAlarmLevel());
        }
        algo.setUpdateTime(LocalDateTime.now());
        algorithmMapper.updateById(algo);
        return algo;
    }

    /** 手动执行一次识别:立即合成命中并刷新执行时刻与次数 */
    @Transactional
    public AiAlgorithmAlarm run(Long id) {
        AiAlgorithm algo = require(id);
        if (!Boolean.TRUE.equals(algo.getEnabled())) {
            throw BizException.of("算法已停用,请先启用再执行");
        }
        AiAlgorithmAlarm alarm = generator.generate(algo, null, null);
        algo.setLastRunAt(alarm.getOccurredAt());
        algo.setRunCount((algo.getRunCount() == null ? 0 : algo.getRunCount()) + 1);
        algo.setUpdateTime(LocalDateTime.now());
        algorithmMapper.updateById(algo);
        return alarm;
    }
}
