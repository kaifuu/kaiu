package com.emergency.inspection.dji;

import com.emergency.inspection.dto.DeviceTelemetry;
import com.emergency.inspection.entity.Device;
import com.emergency.inspection.entity.DeviceOsd;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * osd 原始报文 → 结构化遥测。
 * 所有取值都做存在性判断,机型缺字段时留空而不是抛异常。
 */
@Slf4j
public final class DjiTelemetryMapper {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private DjiTelemetryMapper() {
    }

    public static DeviceTelemetry map(Device device, DeviceOsd osd) {
        DeviceTelemetry t = new DeviceTelemetry();
        t.setDeviceSn(device.getDeviceSn());
        t.setDeviceType(device.getDeviceType() == null ? null : device.getDeviceType().name());
        t.setStatus(device.getStatus() == null ? null : device.getStatus().name());
        if (osd == null) {
            return t;
        }
        t.setUpdateTime(osd.getUpdateTime());
        t.setModeCode(osd.getModeCode());
        t.setModeLabel(modeLabel(device.getDeviceType(), osd.getModeCode()));
        t.setBatteryPercent(osd.getBatteryPercent());
        t.setLongitude(osd.getLongitude());
        t.setLatitude(osd.getLatitude());
        t.setHeight(osd.getHeight());

        JsonNode root = parse(osd.getOsdJson());
        t.setRaw(root);
        if (root == null) {
            return t;
        }

        if (device.getDeviceType() == Device.DeviceType.DOCK) {
            t.setCoverState(intOf(root, "cover_state"));
            t.setPutterState(intOf(root, "putter_state"));
            t.setDroneInDock(intOf(root, "drone_in_dock"));
            t.setEnvironmentTemperature(doubleOf(root, "environment_temperature"));
            t.setWindSpeed(doubleOf(root, "wind_speed"));
            t.setRainfall(doubleOf(root, "rainfall"));
            t.setMediaFileCount(intOf(root, "media_file_count"));
            t.setChargingState(mapOf(root, "charging_state"));
            t.setNetworkState(mapOf(root, "network_state"));
            t.setSubDevice(mapOf(root, "sub_device"));
        } else {
            t.setElevation(decimalOf(root, "elevation"));
            t.setAttitudeHead(doubleOf(root, "attitude_head"));
            t.setHorizontalSpeed(doubleOf(root, "horizontal_speed"));
            t.setVerticalSpeed(doubleOf(root, "vertical_speed"));
            t.setGear(intOf(root, "gear"));
            t.setPositionState(mapOf(root, "position_state"));
            t.setStorage(mapOf(root, "storage"));
            t.setGimbal(mapOf(root, "gimbal"));
            t.setBattery(mapOf(root, "battery"));
        }
        return t;
    }

    /** 状态码 → 中文;字典与前端 dict.js 保持一致 */
    public static String modeLabel(Device.DeviceType type, Integer code) {
        if (code == null) {
            return null;
        }
        String[] dock = {"空闲中", "现场调试", "远程调试", "固件升级中", "作业中", "待标定"};
        String[] drone = {"待机", "起飞准备", "起飞准备完毕", "手动飞行", "自动起飞", "航线飞行",
                "返航中", "降落中", "降落完成", "上电中", "已开机", "已关机"};
        String[] dict = type == Device.DeviceType.DOCK ? dock : drone;
        return code >= 0 && code < dict.length ? dict[code] : String.valueOf(code);
    }

    private static JsonNode parse(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return MAPPER.readTree(json);
        } catch (Exception e) {
            log.warn("遥测报文解析失败: {}", e.getMessage());
            return null;
        }
    }

    private static Integer intOf(JsonNode node, String field) {
        return node.hasNonNull(field) ? node.get(field).asInt() : null;
    }

    private static Double doubleOf(JsonNode node, String field) {
        return node.hasNonNull(field) ? node.get(field).asDouble() : null;
    }

    private static BigDecimal decimalOf(JsonNode node, String field) {
        return node.hasNonNull(field) ? BigDecimal.valueOf(node.get(field).asDouble()) : null;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> mapOf(JsonNode node, String field) {
        if (!node.hasNonNull(field) || !node.get(field).isObject()) {
            return null;
        }
        return MAPPER.convertValue(node.get(field), LinkedHashMap.class);
    }
}
