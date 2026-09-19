package com.emergency.inspection.dji;

import com.emergency.inspection.gateway.mqtt.MqttConnectionListener;
import com.emergency.inspection.gateway.mqtt.MqttSession;
import com.emergency.inspection.service.DeviceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * MQTT 连接 ↔ 设备在线状态。
 * 连接建立/断开即刷新设备状态;未登记的 SN 只记日志不建台账,
 * 由后续的 update_topo 拓扑上报决定是否纳管。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DjiConnectionListener implements MqttConnectionListener {

    private final DeviceService deviceService;

    @Override
    public void onConnected(MqttSession session) {
        safe(() -> deviceService.markOnline(session.getClientId()), "上线", session.getClientId());
    }

    @Override
    public void onDisconnected(MqttSession session) {
        safe(() -> deviceService.markOffline(session.getClientId()), "离线", session.getClientId());
    }

    private void safe(Runnable action, String phase, String sn) {
        try {
            action.run();
        } catch (Exception e) {
            // 设备接入不能因为一次落库失败而中断连接处理
            log.error("设备{}状态更新失败: {}", phase, sn, e);
        }
    }
}
