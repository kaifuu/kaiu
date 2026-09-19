package com.emergency.inspection.gateway.mqtt;

/**
 * 上行报文监听器:MQTT 层只负责协议与路由,具体业务(大疆上云 API 的各主题)
 * 由实现类自行声明关心的主题并处理,避免协议层耦合业务。
 */
public interface MqttMessageListener {

    /** 是否关心该主题 */
    boolean supports(String topic);

    /** 处理上行报文;payload 为 UTF-8 文本 */
    void onMessage(String topic, String payload);
}
