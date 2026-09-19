package com.emergency.inspection.gateway.mqtt;

import io.netty.channel.Channel;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** 一条 MQTT 连接会话。大疆设备以设备 SN 作为 clientId,故 clientId 即设备序列号。 */
@Getter
public class MqttSession {

    private final String clientId;
    private final Channel channel;

    /** 订阅的主题过滤器集合(可含 + / # 通配符) */
    private final Set<String> subscriptions = ConcurrentHashMap.newKeySet();

    private final LocalDateTime connectedAt = LocalDateTime.now();

    private volatile LocalDateTime lastSeenAt = LocalDateTime.now();

    public MqttSession(String clientId, Channel channel) {
        this.clientId = clientId;
        this.channel = channel;
    }

    public void touch() {
        this.lastSeenAt = LocalDateTime.now();
    }

    public boolean isActive() {
        return channel != null && channel.isActive();
    }

    public void close() {
        if (channel != null && channel.isOpen()) {
            channel.close();
        }
    }
}
