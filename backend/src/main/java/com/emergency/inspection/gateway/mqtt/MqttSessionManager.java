package com.emergency.inspection.gateway.mqtt;

import io.netty.channel.Channel;
import io.netty.channel.ChannelId;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** MQTT 会话注册表:维护 clientId(设备 SN)与连接的映射,并提供按主题检索订阅者 */
@Slf4j
@Component
public class MqttSessionManager {

    private final Map<String, MqttSession> byClientId = new ConcurrentHashMap<>();
    private final Map<ChannelId, MqttSession> byChannel = new ConcurrentHashMap<>();

    /** 注册连接;同一 clientId 重复连接时踢掉旧连接(设备重连的常见情形) */
    public MqttSession register(String clientId, Channel channel) {
        MqttSession previous = byClientId.get(clientId);
        if (previous != null && previous.getChannel() != channel) {
            log.info("设备 {} 重复连接,断开旧会话", clientId);
            byChannel.remove(previous.getChannel().id());
            previous.close();
        }
        MqttSession session = new MqttSession(clientId, channel);
        byClientId.put(clientId, session);
        byChannel.put(channel.id(), session);
        return session;
    }

    public MqttSession remove(Channel channel) {
        MqttSession session = byChannel.remove(channel.id());
        if (session != null) {
            byClientId.remove(session.getClientId(), session);
        }
        return session;
    }

    public MqttSession get(String clientId) {
        return clientId == null ? null : byClientId.get(clientId);
    }

    public boolean isOnline(String clientId) {
        MqttSession s = get(clientId);
        return s != null && s.isActive();
    }

    public Collection<MqttSession> all() {
        return byClientId.values();
    }

    public int size() {
        return byClientId.size();
    }

    /** 找出订阅了该主题的所有活跃会话 */
    public List<MqttSession> subscribersOf(String topic) {
        return byClientId.values().stream()
                .filter(MqttSession::isActive)
                .filter(s -> s.getSubscriptions().stream()
                        .anyMatch(f -> MqttTopicMatcher.matches(f, topic)))
                .toList();
    }
}
