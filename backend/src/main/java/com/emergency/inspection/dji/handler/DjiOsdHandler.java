package com.emergency.inspection.dji.handler;

import com.emergency.inspection.dji.DjiMessage;
import com.emergency.inspection.dji.DjiTopics;
import com.emergency.inspection.entity.DeviceEvent;
import com.emergency.inspection.gateway.mqtt.MqttMessageListener;
import com.emergency.inspection.service.DeviceService;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 设备属性上报:
 * - osd   定频遥测(0.5Hz)→ 覆盖式写入 device_osd
 * - state 状态变化 → 记录事件
 *
 * 报文形态有两种:多数版本把属性放在 data 里,也有版本直接平铺在根节点,
 * 这里统一取「根节点里有 data 对象就用 data,否则用根节点」。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DjiOsdHandler implements MqttMessageListener {

    private final DeviceService deviceService;

    @Override
    public boolean supports(String topic) {
        DjiTopics.TopicParts parts = DjiTopics.parse(topic);
        if (parts == null || parts.system()) {
            return false;
        }
        return "osd".equals(parts.suffix()) || "state".equals(parts.suffix());
    }

    @Override
    public void onMessage(String topic, String payload) {
        DjiTopics.TopicParts parts = DjiTopics.parse(topic);
        String sn = parts.sn();
        JsonNode props = propertiesOf(payload);

        if ("osd".equals(parts.suffix())) {
            deviceService.saveOsd(sn, props);
        } else {
            deviceService.recordEvent(sn, DeviceEvent.EventType.OTHER, "state", DeviceEvent.Level.INFO,
                    "设备状态变更", props);
        }
    }

    /**
     * 取属性节点:多数版本把属性放在 data 里,也有版本直接平铺在根节点上。
     * 因此这里回读原始报文 —— 只看 DjiMessage.data() 会把平铺形态的遥测丢成空对象。
     */
    private JsonNode propertiesOf(String payload) {
        try {
            JsonNode root = MAPPER.readTree(payload);
            JsonNode data = root.get("data");
            if (data != null && data.isObject() && !data.isEmpty()) {
                return data;
            }
            return root;
        } catch (Exception e) {
            log.warn("遥测报文解析失败: {}", e.getMessage());
            return MAPPER.createObjectNode();
        }
    }

    private static final com.fasterxml.jackson.databind.ObjectMapper MAPPER =
            new com.fasterxml.jackson.databind.ObjectMapper();
}
