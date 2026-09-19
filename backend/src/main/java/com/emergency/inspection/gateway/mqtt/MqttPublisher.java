package com.emergency.inspection.gateway.mqtt;

import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.handler.codec.mqtt.MqttMessageBuilders;
import io.netty.handler.codec.mqtt.MqttQoS;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/** 云端 → 设备的下行发布 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MqttPublisher {

    private final MqttSessionManager sessionManager;

    /** MQTT 报文标识符,1..65535 循环 */
    private final AtomicInteger packetId = new AtomicInteger(1);

    /**
     * 发布到指定主题。
     * QoS1 只发给「订阅了该主题且在线」的会话,返回实际投递的连接数;
     * 为 0 说明设备不在线或未订阅该主题。
     */
    public int publish(String topic, String payload, MqttQoS qos) {
        List<MqttSession> targets = sessionManager.subscribersOf(topic);
        if (targets.isEmpty()) {
            log.warn("下行报文无订阅者 topic={}", topic);
            return 0;
        }
        int sent = 0;
        for (MqttSession session : targets) {
            Channel ch = session.getChannel();
            if (ch == null || !ch.isActive()) {
                continue;
            }
            MqttMessageBuilders.PublishBuilder builder = MqttMessageBuilders.publish()
                    .topicName(topic)
                    .retained(false)
                    .qos(qos)
                    .payload(Unpooled.copiedBuffer(payload, StandardCharsets.UTF_8));
            if (qos != MqttQoS.AT_MOST_ONCE) {
                builder.messageId(nextPacketId());
            }
            ch.writeAndFlush(builder.build());
            sent++;
        }
        log.debug("下行 topic={} 投递 {} 个连接", topic, sent);
        return sent;
    }

    /** 直接向某个设备 SN 发布(设备以 SN 作为 clientId) */
    public boolean publishTo(String deviceSn, String topic, String payload, MqttQoS qos) {
        MqttSession session = sessionManager.get(deviceSn);
        if (session == null || !session.isActive()) {
            return false;
        }
        MqttMessageBuilders.PublishBuilder builder = MqttMessageBuilders.publish()
                .topicName(topic)
                .retained(false)
                .qos(qos)
                .payload(Unpooled.copiedBuffer(payload, StandardCharsets.UTF_8));
        if (qos != MqttQoS.AT_MOST_ONCE) {
            builder.messageId(nextPacketId());
        }
        session.getChannel().writeAndFlush(builder.build());
        return true;
    }

    private int nextPacketId() {
        return packetId.updateAndGet(v -> v >= 65535 ? 1 : v + 1);
    }
}
