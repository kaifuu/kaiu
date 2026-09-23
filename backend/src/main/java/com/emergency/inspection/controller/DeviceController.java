package com.emergency.inspection.controller;

import com.emergency.inspection.common.ApiResponse;
import com.emergency.inspection.common.OpLog;
import com.emergency.inspection.common.PageUtil;
import com.emergency.inspection.dji.DjiServiceCatalog;
import com.emergency.inspection.dto.DeviceTelemetry;
import com.emergency.inspection.dto.query.CommandQuery;
import com.emergency.inspection.dto.query.DeviceQuery;
import com.emergency.inspection.dji.DjiTelemetryMapper;
import com.emergency.inspection.entity.Device;
import com.emergency.inspection.entity.DeviceCommand;
import com.emergency.inspection.entity.DeviceEvent;
import com.emergency.inspection.entity.DeviceOsd;
import com.emergency.inspection.gateway.mqtt.MqttSessionManager;
import com.emergency.inspection.service.DeviceCommandService;
import com.emergency.inspection.service.DeviceService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 设备管理:无人机 / 机场台账、遥测、指令下发与事件 */
@RestController
@RequestMapping("/api/devices")
@RequiredArgsConstructor
public class DeviceController {

    private final DeviceService deviceService;
    private final DeviceCommandService commandService;
    private final MqttSessionManager sessionManager;

    /* ==================== 台账 ==================== */

    @GetMapping("/page")
    public ApiResponse<Map<String, Object>> page(DeviceQuery query) {
        return ApiResponse.ok(PageUtil.result(deviceService.page(query)));
    }

    /** 全量设备(无人机绑定机场时的下拉) */
    @GetMapping
    public ApiResponse<List<Device>> list() {
        return ApiResponse.ok(deviceService.listAll());
    }

    /** 全量机场(机场管理模块) */
    @GetMapping("/docks")
    public ApiResponse<List<Device>> docks() {
        return ApiResponse.ok(deviceService.listByType(Device.DeviceType.DOCK));
    }

    /** 全量无人机(无人机管理模块) */
    @GetMapping("/drones")
    public ApiResponse<List<Device>> drones() {
        return ApiResponse.ok(deviceService.listByType(Device.DeviceType.DRONE));
    }

    @GetMapping("/{id}")
    public ApiResponse<Device> detail(@PathVariable Long id) {
        return ApiResponse.ok(deviceService.require(id));
    }

    @PostMapping
    @OpLog(module = "设备管理", action = "新增")
    public ApiResponse<Device> create(@RequestBody Device body) {
        return ApiResponse.ok(deviceService.create(body));
    }

    @PutMapping("/{id}")
    @OpLog(module = "设备管理", action = "修改")
    public ApiResponse<Device> update(@PathVariable Long id, @RequestBody Device body) {
        return ApiResponse.ok(deviceService.update(id, body));
    }

    @DeleteMapping("/{id}")
    @OpLog(module = "设备管理", action = "删除")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        deviceService.delete(id);
        return ApiResponse.ok();
    }

    /* ==================== 运行数据 ==================== */

    /** 最新遥测快照(原始行) */
    @GetMapping("/{id}/osd")
    public ApiResponse<DeviceOsd> osd(@PathVariable Long id) {
        return ApiResponse.ok(deviceService.osdOf(deviceService.require(id).getDeviceSn()));
    }

    /** 航迹回放:近 minutes 分钟的飞行轨迹点(时间升序) */
    @GetMapping("/{id}/track")
    public ApiResponse<List<com.emergency.inspection.entity.DeviceTrackPoint>> track(
            @PathVariable Long id,
            @RequestParam(defaultValue = "120") int minutes,
            @RequestParam(defaultValue = "2000") int limit) {
        return ApiResponse.ok(deviceService.track(id, minutes, limit));
    }

    /**
     * 结构化遥测:控制页直接渲染用。
     * 机场返回舱盖/推杆/环境/充电等区块,飞行器返回姿态/定位/负载等区块。
     */
    @GetMapping("/{id}/telemetry")
    public ApiResponse<DeviceTelemetry> telemetry(@PathVariable Long id) {
        Device device = deviceService.require(id);
        DeviceOsd osd = deviceService.osdOf(device.getDeviceSn());
        return ApiResponse.ok(DjiTelemetryMapper.map(device, osd));
    }

    /** 设备事件(上下线 / 健康告警 / 任务进度) */
    @GetMapping("/{id}/events")
    public ApiResponse<List<DeviceEvent>> events(@PathVariable Long id,
                                                 @RequestParam(defaultValue = "30") int limit) {
        return ApiResponse.ok(deviceService.recentEvents(deviceService.require(id).getDeviceSn(), limit));
    }

    /** 设备事件分页:控制页「设备事件」TAB 用,支持类型/级别筛选 */
    @GetMapping("/{id}/events/page")
    public ApiResponse<Map<String, Object>> eventsPage(
            @PathVariable Long id,
            @RequestParam(required = false) com.emergency.inspection.entity.DeviceEvent.EventType eventType,
            @RequestParam(required = false) com.emergency.inspection.entity.DeviceEvent.Level level,
            @RequestParam(required = false) String method,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String sortBy,
            @RequestParam(required = false) String direction,
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "10") long size) {
        var query = new com.emergency.inspection.dto.query.DeviceEventQuery();
        query.setDeviceSn(deviceService.require(id).getDeviceSn());
        query.setEventType(eventType);
        query.setLevel(level);
        query.setMethod(method);
        query.setKeyword(keyword);
        query.setSortBy(sortBy);
        query.setDirection(direction);
        query.setPage(page);
        query.setSize(size);
        return ApiResponse.ok(PageUtil.result(deviceService.pageEvents(query)));
    }

    /** 指令下发记录 */
    @GetMapping("/{id}/commands")
    public ApiResponse<List<DeviceCommand>> commands(@PathVariable Long id,
                                                     @RequestParam(defaultValue = "20") int limit) {
        return ApiResponse.ok(commandService.recent(deviceService.require(id).getDeviceSn(), limit));
    }

    /** 该设备类型可下发的指令目录 */
    @GetMapping("/{id}/services")
    public ApiResponse<List<DjiServiceCatalog.ServiceDef>> services(@PathVariable Long id) {
        return ApiResponse.ok(DjiServiceCatalog.of(deviceService.require(id).getDeviceType()));
    }

    /* ==================== 指令下发 ==================== */

    /** 下发指令:走 MQTT 到设备,回复按 tid 关联 */
    @PostMapping("/{id}/commands")
    @OpLog(module = "设备管理", action = "下发指令")
    public ApiResponse<DeviceCommand> send(@PathVariable Long id, @RequestBody CommandForm body) {
        return ApiResponse.ok(commandService.send(id, body.getMethod(), body.getData()));
    }

    /** 指令记录分页(全局) */
    @GetMapping("/commands/page")
    public ApiResponse<Map<String, Object>> commandPage(CommandQuery query) {
        return ApiResponse.ok(PageUtil.result(commandService.page(query)));
    }

    /* ==================== 概览 ==================== */

    /** 设备统计 + MQTT 接入状态(工作台/设备页顶部用) */
    @GetMapping("/stats")
    public ApiResponse<Map<String, Object>> stats() {
        List<Device> all = deviceService.listAll();
        long dockTotal = all.stream().filter(d -> d.getDeviceType() == Device.DeviceType.DOCK).count();
        long droneTotal = all.stream().filter(d -> d.getDeviceType() == Device.DeviceType.DRONE).count();
        long online = all.stream().filter(d -> d.getStatus() == Device.Status.ONLINE).count();
        // 无人机挂载口径:上云 API 中无人机是机场子设备,未挂载则无从下发指令
        long droneMounted = all.stream()
                .filter(d -> d.getDeviceType() == Device.DeviceType.DRONE)
                .filter(d -> d.getGatewaySn() != null && !d.getGatewaySn().isBlank())
                .count();

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("total", all.size());
        out.put("dockTotal", dockTotal);
        out.put("droneTotal", droneTotal);
        out.put("droneMounted", droneMounted);
        out.put("droneUnmounted", droneTotal - droneMounted);
        out.put("online", online);
        out.put("offline", all.size() - online);
        out.put("mqttOnline", sessionManager.size());
        return ApiResponse.ok(out);
    }

    /** 指令下发入参 */
    @Data
    public static class CommandForm {
        private String method;
        private Map<String, Object> data;
    }
}
