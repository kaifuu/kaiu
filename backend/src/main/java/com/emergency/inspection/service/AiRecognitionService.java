package com.emergency.inspection.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.emergency.inspection.common.BizException;
import com.emergency.inspection.common.PageUtil;
import com.emergency.inspection.dji.DjiMessage;
import com.emergency.inspection.dji.DjiTopics;
import com.emergency.inspection.dto.query.AiTargetQuery;
import com.emergency.inspection.entity.Device;
import com.emergency.inspection.entity.DeviceAiConfig;
import com.emergency.inspection.entity.DeviceAiTarget;
import com.emergency.inspection.gateway.mqtt.MqttPublisher;
import com.emergency.inspection.gateway.mqtt.MqttSessionManager;
import com.emergency.inspection.mapper.DeviceAiConfigMapper;
import com.emergency.inspection.mapper.DeviceAiTargetMapper;
import com.fasterxml.jackson.databind.JsonNode;
import io.netty.handler.codec.mqtt.MqttQoS;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * AI 目标识别:识别配置经 property/set 下发到机场(识别开关 / 跟随 / 置信度模式 / 目标过滤),
 * 设备识别到目标后以事件流上报,平台落识别记录。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiRecognitionService {

    private final DeviceAiConfigMapper configMapper;
    private final DeviceAiTargetMapper targetMapper;
    private final DeviceService deviceService;
    private final MqttPublisher publisher;
    private final MqttSessionManager sessionManager;

    /* ==================== 配置 ==================== */

    /** 读取配置;从未保存过则返回默认值(不落库) */
    public DeviceAiConfig configOf(Long deviceId) {
        String sn = deviceService.require(deviceId).getDeviceSn();
        DeviceAiConfig config = configMapper.selectOne(Wrappers.<DeviceAiConfig>lambdaQuery()
                .eq(DeviceAiConfig::getDeviceSn, sn).last("limit 1"));
        if (config == null) {
            config = new DeviceAiConfig();
            config.setDeviceSn(sn);
            config.setEnabled(false);
            config.setFollowEnabled(false);
            config.setModel("通用目标检测");
            config.setConfidenceMode(DeviceAiConfig.ConfidenceMode.CUSTOM);
            config.setConfidenceValue(80);
            config.setFilterTypesJson("[\"PERSON\",\"CAR\",\"BOAT\"]");
        }
        config.setFilterTypes(parseTypes(config.getFilterTypesJson()));
        return config;
    }

    /** 保存配置并尽量同步到在线设备;返回值带 synced 标记是否已下发 */
    @Transactional
    public DeviceAiConfig save(Long deviceId, DeviceAiConfig body, List<String> filterTypes) {
        Device device = deviceService.require(deviceId);
        if (device.getDeviceType() != Device.DeviceType.DOCK) {
            throw BizException.of("AI 识别配置归属机场(识别能力在机场挂载的飞行器上)");
        }
        DeviceAiConfig config = configMapper.selectOne(Wrappers.<DeviceAiConfig>lambdaQuery()
                .eq(DeviceAiConfig::getDeviceSn, device.getDeviceSn()).last("limit 1"));
        boolean isNew = config == null;
        if (isNew) {
            config = new DeviceAiConfig();
            config.setDeviceSn(device.getDeviceSn());
        }
        config.setEnabled(Boolean.TRUE.equals(body.getEnabled()));
        config.setFollowEnabled(Boolean.TRUE.equals(body.getFollowEnabled()));
        config.setModel(body.getModel() == null || body.getModel().isBlank() ? "通用目标检测" : body.getModel());
        config.setConfidenceMode(body.getConfidenceMode() == null
                ? DeviceAiConfig.ConfidenceMode.CUSTOM : body.getConfidenceMode());
        int value = body.getConfidenceValue() == null ? 80 : body.getConfidenceValue();
        config.setConfidenceValue(Math.max(50, Math.min(99, value)));
        List<String> types = filterTypes == null || filterTypes.isEmpty()
                ? List.of("PERSON", "CAR", "BOAT") : filterTypes;
        config.setFilterTypesJson(toJson(types));
        if (isNew) {
            configMapper.insert(config);
        } else {
            configMapper.updateById(config);
        }

        config.setFilterTypes(types);
        config.setSynced(syncToDevice(device.getDeviceSn(), config));
        return config;
    }

    /** property/set 下发 AI 配置;设备离线时返回 false(配置已存,设备上线后可再保存同步) */
    private boolean syncToDevice(String gatewaySn, DeviceAiConfig config) {
        if (!sessionManager.isOnline(gatewaySn)) {
            return false;
        }
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("ai_switch", config.getEnabled() ? 1 : 0);
        props.put("ai_follow_switch", config.getFollowEnabled() ? 1 : 0);
        props.put("ai_model", config.getModel());
        props.put("ai_confidence_mode", switch (config.getConfidenceMode()) {
            case COUNT -> 0;
            case RESCUE -> 1;
            default -> 2;
        });
        props.put("ai_confidence_value", config.getConfidenceValue());
        props.put("ai_target_filter_list", parseTypes(config.getFilterTypesJson()));
        String tid = UUID.randomUUID().toString().replace("-", "");
        int delivered = publisher.publish(DjiTopics.propertySet(gatewaySn),
                DjiMessage.buildProperty(tid, props), MqttQoS.AT_LEAST_ONCE);
        return delivered > 0;
    }

    /* ==================== 识别记录 ==================== */

    public IPage<DeviceAiTarget> targetPage(AiTargetQuery query) {
        return targetMapper.selectPage(
                PageUtil.build(query, "event_time",
                        PageUtil.allowedCamel("confidence", "eventTime", "createTime")),
                Wrappers.<DeviceAiTarget>lambdaQuery()
                        .eq(query.getDeviceSn() != null && !query.getDeviceSn().isBlank(),
                                DeviceAiTarget::getDeviceSn, query.getDeviceSn())
                        .eq(query.getType() != null, DeviceAiTarget::getTargetType, query.getType()));
    }

    /* ==================== 设备侧回调 ==================== */

    /** AI 目标识别事件:落一条识别记录 */
    @Transactional
    public void onTarget(String gatewaySn, JsonNode data) {
        if (data == null || !data.hasNonNull("type")) {
            return;
        }
        DeviceAiTarget target = new DeviceAiTarget();
        target.setDeviceSn(gatewaySn);
        try {
            target.setTargetType(DeviceAiTarget.TargetType.valueOf(data.get("type").asText()));
        } catch (IllegalArgumentException e) {
            return;
        }
        if (data.hasNonNull("confidence")) {
            target.setConfidence(data.get("confidence").asInt());
        }
        if (data.hasNonNull("longitude")) {
            target.setLongitude(BigDecimal.valueOf(data.get("longitude").asDouble()));
        }
        if (data.hasNonNull("latitude")) {
            target.setLatitude(BigDecimal.valueOf(data.get("latitude").asDouble()));
        }
        if (data.hasNonNull("time")) {
            target.setEventTime(LocalDateTime.ofInstant(
                    Instant.ofEpochMilli(data.get("time").asLong()), ZoneId.systemDefault()));
        } else {
            target.setEventTime(LocalDateTime.now());
        }
        targetMapper.insert(target);
    }

    /* ==================== 内部 ==================== */

    private static List<String> parseTypes(String json) {
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper().readValue(json, List.class);
        } catch (Exception e) {
            return List.of("PERSON", "CAR", "BOAT");
        }
    }

    private static String toJson(List<String> types) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < types.size(); i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append('"').append(types.get(i)).append('"');
        }
        return sb.append(']').toString();
    }
}
