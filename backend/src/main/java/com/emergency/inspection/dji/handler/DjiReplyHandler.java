package com.emergency.inspection.dji.handler;

import com.emergency.inspection.dji.DjiMessage;
import com.emergency.inspection.dji.DjiTopics;
import com.emergency.inspection.gateway.mqtt.MqttMessageListener;
import com.emergency.inspection.service.DeviceCommandService;
import com.emergency.inspection.service.DeviceLogService;
import com.emergency.inspection.service.WaylineJobService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 设备对下行报文的回复:
 * - services_reply      指令执行结果(按 tid 关联到 device_command),并按 method 分发业务回调
 * - property/set_reply  属性设置结果(本期仅记录)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DjiReplyHandler implements MqttMessageListener {

    private final DeviceCommandService commandService;
    private final WaylineJobService waylineJobService;
    private final DeviceLogService deviceLogService;

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
        String sn = parts.sn();
        DjiMessage message = DjiMessage.parse(payload);

        if ("services_reply".equals(parts.suffix())) {
            commandService.handleReply(sn, message);
            dispatch(sn, message);
        } else {
            log.info("属性设置回复: sn={} method={} data={}", sn, message.method(), message.data());
        }
    }

    /** 回复驱动的业务编排:prepare 确认后触发 execute,日志清单回复落库 */
    private void dispatch(String sn, DjiMessage message) {
        String method = message.method() == null ? "" : message.method();
        try {
            switch (method) {
                case "flighttask_prepare" -> waylineJobService.onPrepareReply(sn, message);
                case "logs_file_list" -> deviceLogService.onFileListReply(sn, message.data());
                default -> { /* 其余指令回复仅更新指令记录 */ }
            }
        } catch (Exception e) {
            log.error("回复分发失败: sn={} method={} {}", sn, method, e.getMessage());
        }
    }
}
