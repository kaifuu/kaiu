package com.emergency.inspection.gateway.mqtt;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 上行报文分发:把主题分派给所有声明关心它的监听器。
 * 监听器由 Spring 注入(DJI 协议层的各 handler),MQTT 层不认识任何业务语义。
 */
@Slf4j
@Component
public class MqttMessageDispatcher {

    private final List<MqttMessageListener> listeners;
    private final List<MqttConnectionListener> connectionListeners;

    public MqttMessageDispatcher(List<MqttMessageListener> listeners,
                                 List<MqttConnectionListener> connectionListeners) {
        this.listeners = listeners;
        this.connectionListeners = connectionListeners;
        log.info("MQTT 上行监听器 {} 个,连接监听器 {} 个", listeners.size(), connectionListeners.size());
    }

    public void dispatch(String topic, String payload) {
        boolean handled = false;
        for (MqttMessageListener listener : listeners) {
            if (listener.supports(topic)) {
                listener.onMessage(topic, payload);
                handled = true;
            }
        }
        if (!handled) {
            log.debug("无监听器处理主题 {}", topic);
        }
    }

    public void onConnected(MqttSession session) {
        connectionListeners.forEach(l -> safe(() -> l.onConnected(session)));
    }

    public void onDisconnected(MqttSession session) {
        connectionListeners.forEach(l -> safe(() -> l.onDisconnected(session)));
    }

    private static void safe(Runnable r) {
        try {
            r.run();
        } catch (Exception e) {
            log.error("连接事件回调异常", e);
        }
    }
}
