package com.emergency.inspection.dji.handler;

import com.emergency.inspection.dji.DjiMessage;
import com.emergency.inspection.dji.DjiTopics;
import com.emergency.inspection.gateway.mqtt.MqttMessageListener;
import com.emergency.inspection.service.DeviceCommandService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 设备对下行报文的回复:
 * - services_reply      指令执行结果(按 tid 关联到 device_command)
 * - property/set_reply  属性设置结果(本期仅记录)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DjiReplyHandler implements MqttMessageListener {

    private final DeviceCommandService commandService;

    @Override
    public boolean supports(String topic) {
        DjiTopics.TopicParts parts = DjiTopics.parse(topic);
        if (parts == null || parts.system()) {
            return false;
        }
        return "services_reply".equals(parts.suffix()) || "property/set_reply".equals(parts.suffix());
    }

    @Override
    public void onMessage(String topic, String payload) {
        DjiTopics.TopicParts parts = DjiTopics.parse(topic);
        DjiMessage message = DjiMessage.parse(payload);

        if ("services_reply".equals(parts.suffix())) {
            commandService.handleReply(parts.sn(), message);
        } else {
            log.info("属性设置回复: sn={} method={} data={}", parts.sn(), message.method(), message.data());
        }
    }
}
