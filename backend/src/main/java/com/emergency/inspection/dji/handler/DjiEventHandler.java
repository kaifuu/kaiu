package com.emergency.inspection.dji.handler;

import com.emergency.inspection.dji.DjiMessage;
import com.emergency.inspection.dji.DjiTopics;
import com.emergency.inspection.entity.DeviceEvent;
import com.emergency.inspection.gateway.mqtt.MqttMessageListener;
import com.emergency.inspection.gateway.mqtt.MqttPublisher;
import com.emergency.inspection.service.AiRecognitionService;
import com.emergency.inspection.service.DeviceLogService;
import com.emergency.inspection.service.DeviceService;
import com.emergency.inspection.service.FirmwareService;
import com.emergency.inspection.service.WaylineJobService;
import com.fasterxml.jackson.databind.JsonNode;
import io.netty.handler.codec.mqtt.MqttQoS;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 设备事件与请求:
 * - events   HMS 健康告警、航线任务进度、OTA 进度、日志上传进度、AI 目标识别等,
 *            落 device_event 后路由到对应业务服务,并回 events_reply
 * - requests 设备向云端要资源(如航线文件),本期统一回「不支持」避免设备一直重试
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DjiEventHandler implements MqttMessageListener {

    private final DeviceService deviceService;
    private final WaylineJobService waylineJobService;
    private final FirmwareService firmwareService;
    private final DeviceLogService deviceLogService;
    private final AiRecognitionService aiRecognitionService;
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
            case "ota_progress" -> DeviceEvent.EventType.OTA;
            case "logs_file_upload_progress" -> DeviceEvent.EventType.FILE_UPLOAD;
            case "ai_target" -> DeviceEvent.EventType.AI;
            default -> DeviceEvent.EventType.OTHER;
        };
        DeviceEvent.Level level = switch (method) {
            case "hms" -> DeviceEvent.Level.WARN;
            default -> DeviceEvent.Level.INFO;
        };
        deviceService.recordEvent(sn, type, method, level, summarize(method, message.data()), message.data());
        route(sn, method, message.data());
        log.info("设备事件 sn={} method={}", sn, method);
    }

    /** 事件驱动业务:按 method 分发到对应服务的设备侧回调 */
    private void route(String sn, String method, JsonNode data) {
        if (data == null) {
            return;
        }
        try {
            switch (method) {
                case "flighttask_progress" -> waylineJobService.onProgress(sn, data);
                case "ota_progress" -> firmwareService.onProgress(sn, data);
                case "logs_file_upload_progress" -> deviceLogService.onUploadProgress(sn, data);
                case "ai_target" -> aiRecognitionService.onTarget(sn, data);
                default -> { /* 其余事件仅落库展示 */ }
            }
        } catch (Exception e) {
            // 事件路由异常不影响事件落库与回执
            log.error("事件路由失败: sn={} method={} {}", sn, method, e.getMessage());
        }
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
            case "ota_progress" -> {
                String status = text(data, "status");
                Integer percent = data.hasNonNull("progress") ? data.get("progress").asInt() : null;
                yield "固件升级: " + (status == null ? "进行中" : status)
                        + (percent == null ? "" : "(" + percent + "%)");
            }
            case "logs_file_upload_progress" -> {
                String fileId = text(data, "file_id");
                Integer percent = data.hasNonNull("progress") ? data.get("progress").asInt() : null;
                yield "日志文件上传: " + (fileId == null ? "" : fileId)
                        + (percent == null ? "" : "(" + percent + "%)");
            }
            case "ai_target" -> {
                String t = text(data, "type");
                Integer confidence = data.hasNonNull("confidence") ? data.get("confidence").asInt() : null;
                yield "AI 识别目标: " + (t == null ? "未知" : t)
                        + (confidence == null ? "" : "(置信度 " + confidence + "%)");
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
