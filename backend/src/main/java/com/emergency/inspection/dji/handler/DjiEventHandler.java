package com.emergency.inspection.dji.handler;

import com.emergency.inspection.dji.DjiMessage;
import com.emergency.inspection.dji.DjiTopics;
import com.emergency.inspection.entity.DeviceEvent;
import com.emergency.inspection.gateway.mqtt.MqttMessageListener;
import com.emergency.inspection.gateway.mqtt.MqttPublisher;
import com.emergency.inspection.service.DeviceService;
import com.fasterxml.jackson.databind.JsonNode;
import io.netty.handler.codec.mqtt.MqttQoS;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 设备事件与请求:
 * - events   HMS 健康告警、航线任务进度、媒体上传回调等,落 device_event 并回 events_reply
 * - requests 设备向云端要资源(如航线文件),本期统一回「不支持」避免设备一直重试
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DjiEventHandler implements MqttMessageListener {

    private final DeviceService deviceService;
    private final MqttPublisher publisher;

    @Override
    public boolean supports(String topic) {
        DjiTopics.TopicParts parts = DjiTopics.parse(topic);
        if (parts == null || parts.system()) {
            return false;
        }
        return "events".equals(parts.suffix()) || "requests".equals(parts.suffix());
    }

    @Override
    public void onMessage(String topic, String payload) {
        DjiTopics.TopicParts parts = DjiTopics.parse(topic);
        String sn = parts.sn();
        DjiMessage message = DjiMessage.parse(payload);
        String method = message.method() == null ? "unknown" : message.method();

        if ("events".equals(parts.suffix())) {
            handleEvent(sn, method, message);
            // 事件必须回执,否则设备会持续重推
            publisher.publish(DjiTopics.eventsReply(sn),
                    DjiMessage.buildReply(message.tid(), message.bid(), method, Map.of("result", 0)),
                    MqttQoS.AT_MOST_ONCE);
        } else {
            log.info("设备请求(本期未支持): sn={} method={}", sn, method);
            publisher.publish(DjiTopics.requestsReply(sn),
                    DjiMessage.buildReply(message.tid(), message.bid(), method,
                            Map.of("result", 1, "message", "平台未支持该请求")),
                    MqttQoS.AT_MOST_ONCE);
        }
    }

    private void handleEvent(String sn, String method, DjiMessage message) {
        DeviceEvent.EventType type = switch (method) {
            case "hms" -> DeviceEvent.EventType.HMS;
            case "flighttask_progress" -> DeviceEvent.EventType.FLIGHTTASK;
            case "file_upload_callback" -> DeviceEvent.EventType.FILE_UPLOAD;
            default -> DeviceEvent.EventType.OTHER;
        };
        DeviceEvent.Level level = switch (method) {
            case "hms" -> DeviceEvent.Level.WARN;
            default -> DeviceEvent.Level.INFO;
        };
        deviceService.recordEvent(sn, type, method, level, summarize(method, message.data()), message.data());
        log.info("设备事件 sn={} method={}", sn, method);
    }

    /** 把事件数据压成一句可读摘要,便于列表页直接展示 */
    private String summarize(String method, JsonNode data) {
        if (data == null) {
            return method;
        }
        return switch (method) {
            case "flighttask_progress" -> {
                String status = text(data, "status");
                Integer percent = data.hasNonNull("progress") ? data.get("progress").asInt() : null;
                yield "航线任务进度: " + (status == null ? "执行中" : status)
                        + (percent == null ? "" : "(" + percent + "%)");
            }
            case "hms" -> "健康告警:" + hmsSummary(data);
            case "file_upload_callback" -> "媒体文件上传回调";
            default -> method;
        };
    }

    private String hmsSummary(JsonNode data) {
        JsonNode list = data.has("list") ? data.get("list") : data.get("hms");
        if (list == null || !list.isArray() || list.isEmpty()) {
            return "无明细";
        }
        StringBuilder sb = new StringBuilder();
        for (JsonNode item : list) {
            if (sb.length() > 0) {
                sb.append("; ");
            }
            String code = text(item, "code");
            String msg = text(item, "message");
            sb.append(code == null ? "未知" : code).append(msg == null ? "" : " " + msg);
            if (sb.length() > 300) {
                break;
            }
        }
        return sb.toString();
    }

    private static String text(JsonNode node, String field) {
        return node != null && node.hasNonNull(field) ? node.get(field).asText() : null;
    }
}
