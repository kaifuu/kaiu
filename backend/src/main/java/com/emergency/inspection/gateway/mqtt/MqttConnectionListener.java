package com.emergency.inspection.gateway.mqtt;

/** 连接生命周期回调:业务侧据此维护设备在线状态与上下线事件 */
public interface MqttConnectionListener {

    default void onConnected(MqttSession session) {
    }

    default void onDisconnected(MqttSession session) {
    }
}
