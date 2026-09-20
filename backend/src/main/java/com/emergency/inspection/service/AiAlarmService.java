package com.emergency.inspection.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.emergency.inspection.common.BizException;
import com.emergency.inspection.common.PageUtil;
import com.emergency.inspection.dto.query.AlgoAlarmQuery;
import com.emergency.inspection.entity.AiAlgorithm;
import com.emergency.inspection.entity.AiAlgorithmAlarm;
import com.emergency.inspection.mapper.AiAlgorithmAlarmMapper;
import com.emergency.inspection.mapper.AiAlgorithmMapper;
import com.emergency.inspection.security.LoginContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 算法告警:识别命中记录的分页 / 详情(payload 解析) / 处置闭环。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiAlarmService {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final AiAlgorithmAlarmMapper alarmMapper;
    private final AiAlgorithmMapper algorithmMapper;

    public IPage<AiAlgorithmAlarm> page(AlgoAlarmQuery query) {
        IPage<AiAlgorithmAlarm> page = alarmMapper.selectPage(
                PageUtil.build(query, "occurred_at",
                        PageUtil.allowedCamel("occurredAt", "createTime", "confidence")),
                Wrappers.<AiAlgorithmAlarm>lambdaQuery()
                        .eq(query.getAlgorithmCode() != null, AiAlgorithmAlarm::getAlgorithmCode, query.getAlgorithmCode())
                        .eq(query.getLevel() != null, AiAlgorithmAlarm::getLevel, query.getLevel())
                        .eq(query.getStatus() != null, AiAlgorithmAlarm::getStatus, query.getStatus())
                        .ge(query.getStartTime() != null, AiAlgorithmAlarm::getOccurredAt, query.getStartTime())
                        .le(query.getEndTime() != null, AiAlgorithmAlarm::getOccurredAt, query.getEndTime())
                        .and(query.getKeyword() != null && !query.getKeyword().isBlank(),
                                w -> w.like(AiAlgorithmAlarm::getTitle, query.getKeyword())
                                        .or().like(AiAlgorithmAlarm::getAddress, query.getKeyword())));
        fillAlgorithmNames(page.getRecords());
        return page;
    }

    public AiAlgorithmAlarm detail(Long id) {
        AiAlgorithmAlarm alarm = require(id);
        alarm.setPayload(parsePayload(alarm.getPayloadJson()));
        fillAlgorithmNames(List.of(alarm));
        return alarm;
    }

    /** 处置:PENDING → HANDLED,处理人与时间留痕 */
    @Transactional
    public AiAlgorithmAlarm handle(Long id, String remark) {
        if (remark == null || remark.isBlank()) {
            throw BizException.of("处置说明必填");
        }
        AiAlgorithmAlarm alarm = require(id);
        if (alarm.getStatus() == AiAlgorithmAlarm.Status.HANDLED) {
            throw BizException.of("该告警已处置,无需重复操作");
        }
        alarm.setStatus(AiAlgorithmAlarm.Status.HANDLED);
        String username = LoginContext.getUsername();
        alarm.setHandler(username == null ? "系统" : username);
        alarm.setHandleRemark(remark);
        alarm.setHandleTime(java.time.LocalDateTime.now());
        alarmMapper.updateById(alarm);
        return alarm;
    }

    public AiAlgorithmAlarm require(Long id) {
        AiAlgorithmAlarm alarm = alarmMapper.selectById(id);
        if (alarm == null) {
            throw BizException.of("告警记录不存在");
        }
        return alarm;
    }

    /* ==================== 内部 ==================== */

    /** 回填算法名称(注册表仅 6 行,一次拉全量在内存对齐) */
    public void fillAlgorithmNames(List<AiAlgorithmAlarm> alarms) {
        if (alarms == null || alarms.isEmpty()) {
            return;
        }
        Map<AiAlgorithm.Code, String> names = algorithmMapper.selectList(Wrappers.<AiAlgorithm>lambdaQuery())
                .stream().collect(Collectors.toMap(AiAlgorithm::getCode,
                        AiAlgorithm::getName, (a, b) -> a));
        alarms.forEach(a -> a.setAlgorithmName(names.get(a.getAlgorithmCode())));
    }

    public static Map<String, Object> parsePayload(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            return MAPPER.readValue(json, Map.class);
        } catch (Exception e) {
            return Map.of("raw", json);
        }
    }

    /** payload 键 → 中文标签(详情页直接按键值展示) */
    public static final Map<String, String> PAYLOAD_LABELS = Map.ofEntries(
            Map.entry("fireType", "烟火类型"),
            Map.entry("irMaxTempC", "红外最高温(℃)"),
            Map.entry("ambientTempC", "环境温度(℃)"),
            Map.entry("tempPointLng", "高温点经度"),
            Map.entry("tempPointLat", "高温点纬度"),
            Map.entry("dumpType", "倾倒类型"),
            Map.entry("areaM2", "面积(㎡)"),
            Map.entry("vehiclePlate", "涉案车牌"),
            Map.entry("dwellMinutes", "停留时长(分)"),
            Map.entry("anomalyType", "覆盖膜异常"),
            Map.entry("membraneZone", "所在膜区"),
            Map.entry("smokeColor", "烟羽颜色"),
            Map.entry("opacityPercent", "不透光度(%)"),
            Map.entry("plumeHeightM", "烟羽抬升高度(m)"),
            Map.entry("chimneyId", "排气筒"),
            Map.entry("leakType", "泄漏类型"),
            Map.entry("leakLocation", "泄漏位置"),
            Map.entry("spreadAreaM2", "扩散面积(㎡)"),
            Map.entry("flowRateM3h", "估算流速(m³/h)"),
            Map.entry("h2sPpm", "H2S(ppm)"),
            Map.entry("nh3Ppm", "NH3(ppm)"),
            Map.entry("odorUnit", "臭气浓度(无量纲)"),
            Map.entry("stationName", "超标站点"),
            Map.entry("sourceLng", "溯源点经度"),
            Map.entry("sourceLat", "溯源点纬度"),
            Map.entry("backtrackM", "回溯距离(m)"),
            Map.entry("trajectoryPoints", "扩散轨迹点数"),
            Map.entry("thumbObjectKey", "截图对象键"));
}
