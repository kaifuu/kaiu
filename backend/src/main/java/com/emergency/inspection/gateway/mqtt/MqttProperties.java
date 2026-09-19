package com.emergency.inspection.gateway.mqtt;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "dji.mqtt")
public class MqttProperties {

    private boolean enabled = true;

    /** 设备接入端口 */
    private int port = 1883;

    private int maxConnections = 500;

    /** 读空闲超时(秒):设备按 keepAlive 发 PINGREQ,超时即判定掉线并断开 */
    private int idleTimeoutSeconds = 180;

    /** 单帧最大字节数(航线任务报文可能较大) */
    private int maxBytesInMessage = 8 * 1024 * 1024;
}
