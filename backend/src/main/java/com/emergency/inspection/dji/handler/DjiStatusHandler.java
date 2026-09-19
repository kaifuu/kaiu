package com.emergency.inspection.dji.handler;

import com.emergency.inspection.dji.DjiMessage;
import com.emergency.inspection.dji.DjiModel;
import com.emergency.inspection.dji.DjiTopics;
import com.emergency.inspection.entity.Device;
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
 * 设备上下线与拓扑更新:sys/product/{gateway_sn}/status。
 *
 * 机场上线时用 method=update_topo 声明自己以及挂载的无人机(sub_devices);
 * 子设备下线时 sub_devices 为空数组。平台据此维护无人机与机场的挂载关系,
 * 并回 status_reply 告知设备「已收到」。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DjiStatusHandler implements MqttMessageListener {

    private static final String UPDATE_TOPO = "update_topo";

    private final DeviceService deviceService;
    private final MqttPublisher publisher;

    @Override
    public boolean supports(String topic) {
        DjiTopics.TopicParts parts = DjiTopics.parse(topic);
        return parts != null && parts.system() && "status".equals(parts.suffix());
    }

    @Override
    public void onMessage(String topic, String payload) {
        DjiTopics.TopicParts parts = DjiTopics.parse(topic);
        String gatewaySn = parts.sn();
        DjiMessage message = DjiMessage.parse(payload);

        if (!UPDATE_TOPO.equals(message.method())) {
            log.debug("忽略非拓扑状态上报: {} {}", gatewaySn, message.method());
            reply(gatewaySn, message, 0);
            return;
        }

        JsonNode data = message.data();
        JsonNode subDevices = data.get("sub_devices");
        boolean hasSub = subDevices != null && subDevices.isArray() && !subDevices.isEmpty();

        ensureGateway(gatewaySn, data);

        if (hasSub) {
            for (JsonNode sub : subDevices) {
                String droneSn = text(sub, "sn");
                if (droneSn == null || droneSn.isBlank()) {
                    continue;
                }
                bindDrone(droneSn, gatewaySn, sub);
            }
            log.info("拓扑上报: 机场 {} 挂载 {} 台飞行器", gatewaySn, subDevices.size());
        } else {
            // 子设备下线:清空挂载关系,但不删除台账
            deviceService.clearGatewayBinding(gatewaySn);
            log.info("拓扑上报: 机场 {} 的子设备已全部下线", gatewaySn);
        }

        deviceService.recordEvent(gatewaySn, DeviceEvent.EventType.OTHER, UPDATE_TOPO,
                DeviceEvent.Level.INFO,
                hasSub ? "拓扑更新:挂载 " + subDevices.size() + " 台飞行器" : "拓扑更新:子设备已下线",
                data);
        reply(gatewaySn, message, 0);
    }

    /**
     * 网关(机场)首次上报拓扑时自动纳管。
     * 大疆官方流程要求设备先在平台登记再接入,但本平台允许设备直连,
     * 若只认预登记,第一次接入会「连上了却看不到设备」,排查成本高。
     * 这里自动建台账(机型/备注标明来自拓扑),名称等仍可在设备管理里改。
     */
    private void ensureGateway(String gatewaySn, JsonNode data) {
        if (deviceService.findBySn(gatewaySn) != null) {
            return;
        }
        Device dock = new Device();
        dock.setDeviceSn(gatewaySn);
        dock.setName("机场 " + shortSn(gatewaySn));
        dock.setDeviceType(Device.DeviceType.DOCK);
        dock.setDeviceModel(DjiModel.name(intOrNull(data, "type"), intOrNull(data, "sub_type")));
        dock.setFirmwareVersion(text(data, "thing_version"));
        dock.setRemark("由机场拓扑上报自动登记");
        deviceService.createFromTopology(dock);
        // 拓扑上报本身就说明设备在线,直接置为在线,不必等下一次重连
        deviceService.markOnline(gatewaySn);
        log.info("拓扑自动登记机场 {}", gatewaySn);
    }

    /** 无人机首次出现在拓扑里则自动建台账,已有则更新挂载关系 */
    private void bindDrone(String droneSn, String gatewaySn, JsonNode sub) {
        String model = DjiModel.name(intOrNull(sub, "type"), intOrNull(sub, "sub_type"));
        Device existing = deviceService.findBySn(droneSn);
        if (existing == null) {
            Device drone = new Device();
            drone.setDeviceSn(droneSn);
            drone.setName("飞行器 " + shortSn(droneSn));
            drone.setDeviceType(Device.DeviceType.DRONE);
            drone.setDeviceModel(model);
            drone.setGatewaySn(gatewaySn);
            drone.setFirmwareVersion(text(sub, "thing_version"));
            deviceService.createFromTopology(drone);
            log.info("拓扑自动登记飞行器 {} → 机场 {}", droneSn, gatewaySn);
        } else if (!gatewaySn.equals(existing.getGatewaySn())) {
            deviceService.bindGateway(droneSn, gatewaySn);
            log.info("飞行器 {} 挂载关系更新为机场 {}", droneSn, gatewaySn);
        }
    }

    private void reply(String gatewaySn, DjiMessage message, int result) {
        String body = DjiMessage.buildReply(message.tid(), message.bid(), UPDATE_TOPO,
                Map.of("result", result));
        publisher.publish(DjiTopics.statusReply(gatewaySn), body, MqttQoS.AT_MOST_ONCE);
    }

    private static String shortSn(String sn) {
        return sn.length() <= 8 ? sn : "…" + sn.substring(sn.length() - 8);
    }

    private static String text(JsonNode node, String field) {
        return node != null && node.hasNonNull(field) ? node.get(field).asText() : null;
    }

    private static Integer intOrNull(JsonNode node, String field) {
        return node != null && node.hasNonNull(field) ? node.get(field).asInt() : null;
    }
}
