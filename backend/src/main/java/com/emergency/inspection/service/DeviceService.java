package com.emergency.inspection.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.emergency.inspection.common.BizException;
import com.emergency.inspection.common.PageUtil;
import com.emergency.inspection.dto.query.DeviceQuery;
import com.emergency.inspection.entity.Device;
import com.emergency.inspection.entity.DeviceAiConfig;
import com.emergency.inspection.entity.DeviceAiTarget;
import com.emergency.inspection.entity.DeviceEvent;
import com.emergency.inspection.entity.DeviceLogFile;
import com.emergency.inspection.entity.DeviceOsd;
import com.emergency.inspection.entity.DeviceTrackPoint;
import com.emergency.inspection.entity.Pilot;
import com.emergency.inspection.mapper.DeviceAiConfigMapper;
import com.emergency.inspection.mapper.DeviceAiTargetMapper;
import com.emergency.inspection.mapper.DeviceEventMapper;
import com.emergency.inspection.mapper.DeviceLogFileMapper;
import com.emergency.inspection.mapper.DeviceMapper;
import com.emergency.inspection.mapper.DeviceOsdMapper;
import com.emergency.inspection.mapper.DeviceTrackPointMapper;
import com.emergency.inspection.mapper.PilotMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/** 设备管理:台账 CRUD + 在线状态维护 + 遥测落库 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DeviceService {

    private final DeviceMapper deviceMapper;
    private final DeviceOsdMapper osdMapper;
    private final DeviceEventMapper eventMapper;
    private final DeviceAiConfigMapper aiConfigMapper;
    private final DeviceAiTargetMapper aiTargetMapper;
    private final DeviceLogFileMapper logFileMapper;
    private final DeviceTrackPointMapper trackMapper;
    private final PilotMapper pilotMapper;
    private final SafeAlertService safeAlertService;
    private final ObjectMapper objectMapper;

    /* ==================== 台账 ==================== */

    public IPage<Device> page(DeviceQuery query) {
        String kw = query.getKeyword();
        IPage<Device> page = deviceMapper.selectPage(
                PageUtil.build(query, "id",
                        PageUtil.allowedCamel("name", "deviceSn", "deviceType", "deviceModel",
                                "status", "lastOnlineAt", "createTime")),
                Wrappers.<Device>lambdaQuery()
                        .and(kw != null && !kw.isBlank(), w -> w.like(Device::getName, kw.trim())
                                .or().like(Device::getDeviceSn, kw.trim())
                                .or().like(Device::getDeviceModel, kw.trim()))
                        .eq(query.getDeviceType() != null, Device::getDeviceType, query.getDeviceType())
                        .eq(query.getStatus() != null, Device::getStatus, query.getStatus())
                        .eq(query.getGatewaySn() != null && !query.getGatewaySn().isBlank(),
                                Device::getGatewaySn, query.getGatewaySn()));
        fillNames(page.getRecords());
        return page;
    }

    public List<Device> listAll() {
        List<Device> list = deviceMapper.selectList(Wrappers.<Device>lambdaQuery().orderByAsc(Device::getId));
        fillNames(list);
        return list;
    }

    /** 按类型取全量(机场管理 / 无人机管理各自的下拉与统计) */
    public List<Device> listByType(Device.DeviceType type) {
        List<Device> list = deviceMapper.selectList(Wrappers.<Device>lambdaQuery()
                .eq(Device::getDeviceType, type)
                .orderByAsc(Device::getId));
        fillNames(list);
        return list;
    }

    public Device require(Long id) {
        Device device = deviceMapper.selectById(id);
        if (device == null) {
            throw BizException.of("设备不存在: " + id);
        }
        fillNames(List.of(device));
        return device;
    }

    public Device findBySn(String sn) {
        return sn == null ? null : deviceMapper.selectOne(
                Wrappers.<Device>lambdaQuery().eq(Device::getDeviceSn, sn).last("limit 1"));
    }

    @Transactional
    public Device create(Device body) {
        validate(body);
        if (findBySn(body.getDeviceSn()) != null) {
            throw BizException.of("设备序列号已存在: " + body.getDeviceSn());
        }
        // 无人机必须挂到某台机场下 —— 上云 API 中无人机是机场的子设备,没有网关就无从下发指令
        if (body.getDeviceType() == Device.DeviceType.DRONE) {
            requireGateway(body.getGatewaySn());
        } else {
            body.setGatewaySn(null);
        }
        body.setId(null);
        body.setStatus(Device.Status.OFFLINE);
        body.setBoundAt(LocalDateTime.now());
        applyLedgerDefaults(body);
        deviceMapper.insert(body);
        fillNames(List.of(body));
        return body;
    }

    @Transactional
    public Device update(Long id, Device body) {
        Device device = require(id);
        validateLedger(body);
        if (body.getName() != null) {
            device.setName(body.getName());
        }
        if (body.getDeviceModel() != null) {
            device.setDeviceModel(body.getDeviceModel());
        }
        if (body.getFirmwareVersion() != null) {
            device.setFirmwareVersion(body.getFirmwareVersion());
        }
        if (body.getRemark() != null) {
            device.setRemark(body.getRemark());
        }
        // ---------- 台账字段(null = 未提交,不动) ----------
        if (body.getManufacturer() != null) {
            device.setManufacturer(body.getManufacturer());
        }
        if (body.getUsage() != null) {
            device.setUsage(body.getUsage());
        }
        if (body.getHomeLng() != null) {
            device.setHomeLng(body.getHomeLng());
        }
        if (body.getHomeLat() != null) {
            device.setHomeLat(body.getHomeLat());
        }
        if (body.getMaxAltitude() != null) {
            device.setMaxAltitude(body.getMaxAltitude());
        }
        if (body.getMaxEndurance() != null) {
            device.setMaxEndurance(body.getMaxEndurance());
        }
        if (body.getEnabled() != null) {
            device.setEnabled(body.getEnabled());
        }
        if (body.getVirtual() != null) {
            device.setVirtual(body.getVirtual());
        }
        if (body.getIcon() != null) {
            device.setIcon(body.getIcon());
        }
        // pilotId:>0 绑定 / 0 显式解绑 / null 未提交
        if (body.getPilotId() != null) {
            device.setPilotId(body.getPilotId() > 0 ? body.getPilotId() : null);
        }
        if (body.getGatewaySn() != null && !body.getGatewaySn().isBlank()) {
            if (device.getDeviceType() != Device.DeviceType.DRONE) {
                throw BizException.of("机场自身不能挂载到其他网关");
            }
            requireGateway(body.getGatewaySn());
            device.setGatewaySn(body.getGatewaySn());
        }
        deviceMapper.updateById(device);
        fillNames(List.of(device));
        return device;
    }

    public void delete(Long id) {
        Device device = require(id);
        long children = deviceMapper.selectCount(
                Wrappers.<Device>lambdaQuery().eq(Device::getGatewaySn, device.getDeviceSn()));
        if (children > 0) {
            throw BizException.of("该机场下仍挂载 " + children + " 台无人机,先解绑再删除");
        }
        String sn = device.getDeviceSn();
        deviceMapper.deleteById(id);
        osdMapper.delete(Wrappers.<DeviceOsd>lambdaQuery().eq(DeviceOsd::getDeviceSn, sn));
        // 扩展数据随之清理:AI 配置 / 识别记录 / 远程日志
        aiConfigMapper.delete(Wrappers.<DeviceAiConfig>lambdaQuery().eq(DeviceAiConfig::getDeviceSn, sn));
        aiTargetMapper.delete(Wrappers.<DeviceAiTarget>lambdaQuery().eq(DeviceAiTarget::getDeviceSn, sn));
        logFileMapper.delete(Wrappers.<DeviceLogFile>lambdaQuery().eq(DeviceLogFile::getDeviceSn, sn));
    }

    /**
     * 拓扑上报自动登记:机场在 update_topo 里声明了挂载的飞行器,平台据此建台账。
     * 与手工新增的区别是不做「必须绑定机场」校验 —— 挂载关系正是本次上报带进来的。
     */
    @Transactional
    public Device createFromTopology(Device device) {
        device.setId(null);
        device.setStatus(Device.Status.OFFLINE);
        device.setBoundAt(LocalDateTime.now());
        if (device.getRemark() == null) {
            device.setRemark("由机场拓扑上报自动登记");
        }
        applyLedgerDefaults(device);
        deviceMapper.insert(device);
        return device;
    }

    /** 更新飞行器的挂载机场(拓扑变化时) */
    @Transactional
    public void bindGateway(String droneSn, String gatewaySn) {
        Device drone = findBySn(droneSn);
        if (drone == null || drone.getDeviceType() != Device.DeviceType.DRONE) {
            return;
        }
        drone.setGatewaySn(gatewaySn);
        deviceMapper.updateById(drone);
    }

    /** 机场上报子设备全部下线:解除挂载关系并置离线,台账保留 */
    @Transactional
    public void clearGatewayBinding(String gatewaySn) {
        List<Device> children = deviceMapper.selectList(
                Wrappers.<Device>lambdaQuery().eq(Device::getGatewaySn, gatewaySn));
        for (Device child : children) {
            child.setStatus(Device.Status.OFFLINE);
            deviceMapper.updateById(child);
        }
    }

    private void validate(Device body) {
        if (body.getName() == null || body.getName().isBlank()) {
            throw BizException.of("设备名称不能为空");
        }
        if (body.getDeviceSn() == null || body.getDeviceSn().isBlank()) {
            throw BizException.of("设备序列号不能为空");
        }
        if (body.getDeviceType() == null) {
            throw BizException.of("请选择设备类型(无人机 / 机场)");
        }
        validateLedger(body);
    }

    /**
     * 台账字段默认值。手工新增与拓扑自动登记共用。
     *
     * <p>建表虽有 DEFAULT,但实体 insert 时 null 会被显式写入,必须在应用层补齐。
     * 模拟器设备(SN 含 SIM)自动标记为虚拟设备,便于台账上与真机区分。
     */
    private void applyLedgerDefaults(Device d) {
        if (d.getEnabled() == null) {
            d.setEnabled(true);
        }
        if (d.getVirtual() == null) {
            d.setVirtual(d.getDeviceSn() != null && d.getDeviceSn().toUpperCase().contains("SIM"));
        }
        if (d.getIcon() == null) {
            d.setIcon("");
        }
        if (d.getPilotId() != null && d.getPilotId() <= 0) {
            d.setPilotId(null);
        }
        if (d.getManufacturer() == null || d.getManufacturer().isBlank()) {
            d.setManufacturer("大疆");
        }
    }

    /** 台账字段范围校验:只在提交了值时才校验,未提交(null)一律放行 */
    private void validateLedger(Device body) {
        if (body.getHomeLng() != null && (body.getHomeLng().doubleValue() < -180 || body.getHomeLng().doubleValue() > 180)) {
            throw BizException.of("经度应在 -180 ~ 180 之间");
        }
        if (body.getHomeLat() != null && (body.getHomeLat().doubleValue() < -90 || body.getHomeLat().doubleValue() > 90)) {
            throw BizException.of("纬度应在 -90 ~ 90 之间");
        }
        if (body.getMaxAltitude() != null
                && (body.getMaxAltitude().doubleValue() < 30 || body.getMaxAltitude().doubleValue() > 1000)) {
            throw BizException.of("最大航高应在 30 ~ 1000 m 之间");
        }
        if (body.getMaxEndurance() != null
                && (body.getMaxEndurance().doubleValue() < 10 || body.getMaxEndurance().doubleValue() > 360)) {
            throw BizException.of("续航应在 10 ~ 360 min 之间");
        }
        if (body.getPilotId() != null && body.getPilotId() > 0
                && pilotMapper.selectById(body.getPilotId()) == null) {
            throw BizException.of("绑定的飞手不存在: " + body.getPilotId());
        }
    }

    private void requireGateway(String gatewaySn) {
        if (gatewaySn == null || gatewaySn.isBlank()) {
            throw BizException.of("无人机必须绑定所属机场");
        }
        Device dock = findBySn(gatewaySn);
        if (dock == null) {
            throw BizException.of("所属机场不存在: " + gatewaySn);
        }
        if (dock.getDeviceType() != Device.DeviceType.DOCK) {
            throw BizException.of("所属网关必须是机场: " + gatewaySn);
        }
    }

    /** 回填机场名称与下挂无人机数量 */
    private void fillNames(Collection<Device> devices) {
        if (devices == null || devices.isEmpty()) {
            return;
        }
        Set<String> gatewaySns = devices.stream()
                .map(Device::getGatewaySn).filter(java.util.Objects::nonNull).collect(Collectors.toSet());
        Map<String, Device> docks = gatewaySns.isEmpty() ? Map.of()
                : deviceMapper.selectList(Wrappers.<Device>lambdaQuery().in(Device::getDeviceSn, gatewaySns))
                        .stream().collect(Collectors.toMap(Device::getDeviceSn, Function.identity(), (a, b) -> a));

        // 绑定飞手姓名:一次 in() 批量取,避免逐行 selectById
        Set<Long> pilotIds = devices.stream()
                .map(Device::getPilotId).filter(java.util.Objects::nonNull).collect(Collectors.toSet());
        Map<Long, String> pilotNames = pilotIds.isEmpty() ? Map.of()
                : pilotMapper.selectList(Wrappers.<Pilot>lambdaQuery().in(Pilot::getId, pilotIds))
                        .stream().collect(Collectors.toMap(Pilot::getId, Pilot::getName, (a, b) -> a));

        // 最新遥测模式码:前端据此把 ONLINE 派生为「飞行中/待命」,同样批量取
        Set<String> sns = devices.stream()
                .map(Device::getDeviceSn).filter(java.util.Objects::nonNull).collect(Collectors.toSet());
        Map<String, Integer> modeCodes = sns.isEmpty() ? Map.of()
                : osdMapper.selectList(Wrappers.<DeviceOsd>lambdaQuery()
                        .select(DeviceOsd::getDeviceSn, DeviceOsd::getModeCode)
                        .in(DeviceOsd::getDeviceSn, sns))
                .stream().filter(o -> o.getModeCode() != null)
                .collect(Collectors.toMap(DeviceOsd::getDeviceSn, DeviceOsd::getModeCode, (a, b) -> a));

        for (Device d : devices) {
            if (d.getGatewaySn() != null) {
                Device dock = docks.get(d.getGatewaySn());
                d.setGatewayName(dock == null ? null : dock.getName());
            }
            if (d.getPilotId() != null) {
                d.setPilotName(pilotNames.get(d.getPilotId()));
            }
            d.setModeCode(modeCodes.get(d.getDeviceSn()));
            if (d.getDeviceType() == Device.DeviceType.DOCK) {
                Long count = deviceMapper.selectCount(
                        Wrappers.<Device>lambdaQuery().eq(Device::getGatewaySn, d.getDeviceSn()));
                d.setSubDeviceCount(count == null ? 0 : count.intValue());
            }
        }
    }

    /* ==================== 在线状态 ==================== */

    /** MQTT 连接建立:标记在线。未登记的 SN 只记日志,不自动建台账(避免脏 SN 灌库) */
    @Transactional
    public void markOnline(String sn) {
        Device device = findBySn(sn);
        if (device == null) {
            log.warn("未登记的设备接入: {} —— 请先在设备管理中添加该序列号", sn);
            return;
        }
        // 已停用的设备拒绝接入:保持 OFFLINE,不刷新 lastOnlineAt(台账与历史数据保留)
        if (Boolean.FALSE.equals(device.getEnabled())) {
            log.warn("已停用的设备尝试接入,已拒绝: {} —— 如需恢复请在设备管理启用", sn);
            recordEvent(sn, DeviceEvent.EventType.OFFLINE, "device_disabled", DeviceEvent.Level.WARN,
                    "设备已停用,接入被拒绝", null);
            return;
        }
        // 每次连接都刷新「最近在线」:后端重启后库里的状态仍是 ONLINE,
        // 若只在状态跃迁时更新,台账上的最近在线会一直停在重启前的时间。
        device.setLastOnlineAt(LocalDateTime.now());
        if (device.getStatus() != Device.Status.ONLINE) {
            device.setStatus(Device.Status.ONLINE);
            deviceMapper.updateById(device);
            recordEvent(sn, DeviceEvent.EventType.ONLINE, "device_online", DeviceEvent.Level.INFO,
                    "设备已接入平台", null);
        } else {
            deviceMapper.updateById(device);
        }
        // 机场在线,其挂载的无人机随之在线 —— 无人机没有独立 MQTT 连接,经机场转发可达。
        // 与 markOffline 的下线联动对称,否则台账上会出现「机场在线、无人机离线」的矛盾态。
        List<Device> children = deviceMapper.selectList(
                Wrappers.<Device>lambdaQuery().eq(Device::getGatewaySn, sn));
        for (Device child : children) {
            boolean wasOffline = child.getStatus() != Device.Status.ONLINE;
            child.setStatus(Device.Status.ONLINE);
            child.setLastOnlineAt(LocalDateTime.now());
            deviceMapper.updateById(child);
            if (wasOffline) {
                recordEvent(child.getDeviceSn(), DeviceEvent.EventType.ONLINE, "device_online",
                        DeviceEvent.Level.INFO, "所属机场在线,飞行器随之在线", null);
            }
        }
    }

    @Transactional
    public void markOffline(String sn) {
        Device device = findBySn(sn);
        if (device == null) {
            return;
        }
        if (device.getStatus() != Device.Status.OFFLINE) {
            device.setStatus(Device.Status.OFFLINE);
            deviceMapper.updateById(device);
            recordEvent(sn, DeviceEvent.EventType.OFFLINE, "device_offline", DeviceEvent.Level.WARN,
                    "设备与平台的连接已断开", null);
        }
        // 机场离线,其挂载的无人机随之离线(无人机没有独立连接)
        List<Device> children = deviceMapper.selectList(
                Wrappers.<Device>lambdaQuery().eq(Device::getGatewaySn, sn)
                        .eq(Device::getStatus, Device.Status.ONLINE));
        for (Device child : children) {
            child.setStatus(Device.Status.OFFLINE);
            deviceMapper.updateById(child);
            recordEvent(child.getDeviceSn(), DeviceEvent.EventType.OFFLINE, "device_offline",
                    DeviceEvent.Level.WARN, "所属机场离线,飞行器随之下线", null);
        }
    }

    /* ==================== 遥测 ==================== */

    /** 保存最新遥测:原始报文整体留存,常用字段抽列 */
    @Transactional
    public void saveOsd(String sn, JsonNode osd) {
        // 已停用的设备不落遥测:否则轨迹表会被停用设备持续写入(每帧一行,无清理策略)
        Device dev = findBySn(sn);
        if (dev != null && Boolean.FALSE.equals(dev.getEnabled())) {
            return;
        }
        DeviceOsd row = osdMapper.selectOne(
                Wrappers.<DeviceOsd>lambdaQuery().eq(DeviceOsd::getDeviceSn, sn).last("limit 1"));
        boolean isNew = row == null;
        if (isNew) {
            row = new DeviceOsd();
            row.setDeviceSn(sn);
        }
        row.setOsdJson(osd.toString());
        row.setModeCode(intOrNull(osd, "mode_code"));
        row.setLongitude(decimalOrNull(osd, "longitude"));
        row.setLatitude(decimalOrNull(osd, "latitude"));
        row.setHeight(decimalOrNull(osd, "height"));
        // 电量位置随机型不同:飞行器在 battery.capacity_percent,机场在 charging_state.capacity_percent
        Integer battery = capacityOf(osd.get("battery"));
        if (battery == null) {
            battery = capacityOf(osd.get("charging_state"));
        }
        if (battery != null) {
            row.setBatteryPercent(battery);
        }
        if (isNew) {
            osdMapper.insert(row);
        } else {
            osdMapper.updateById(row);
        }
        // 飞行器(带经纬度)追加轨迹点供回放,并喂给安全预警规则引擎
        if (row.getLongitude() != null && row.getLatitude() != null) {
            DeviceTrackPoint point = new DeviceTrackPoint();
            point.setDeviceSn(sn);
            point.setTs(LocalDateTime.now());
            point.setLongitude(row.getLongitude());
            point.setLatitude(row.getLatitude());
            point.setHeight(row.getHeight());
            point.setSpeed(decimalOrNull(osd, "horizontal_speed"));
            point.setHeading(decimalOrNull(osd, "attitude_head"));
            point.setBattery(battery);
            trackMapper.insert(point);
            safeAlertService.onOsd(sn, osd);
        }
    }

    /** 航迹回放:近 N 分钟内时间升序轨迹点 */
    public List<DeviceTrackPoint> track(Long id, int minutes, int limit) {
        String sn = require(id).getDeviceSn();
        return trackMapper.selectList(Wrappers.<DeviceTrackPoint>lambdaQuery()
                .eq(DeviceTrackPoint::getDeviceSn, sn)
                .ge(DeviceTrackPoint::getTs, LocalDateTime.now().minusMinutes(Math.max(minutes, 1)))
                .orderByAsc(DeviceTrackPoint::getTs)
                .last("limit " + Math.min(Math.max(limit, 1), 5000)));
    }

    public DeviceOsd osdOf(String sn) {
        return osdMapper.selectOne(Wrappers.<DeviceOsd>lambdaQuery().eq(DeviceOsd::getDeviceSn, sn).last("limit 1"));
    }

    /* ==================== 事件 ==================== */

    @Transactional
    public void recordEvent(String sn, DeviceEvent.EventType type, String method, DeviceEvent.Level level,
                            String message, JsonNode data) {
        DeviceEvent event = new DeviceEvent();
        event.setDeviceSn(sn);
        event.setEventType(type);
        event.setMethod(method);
        event.setLevel(level == null ? DeviceEvent.Level.INFO : level);
        event.setMessage(message == null ? null : truncate(message, 500));
        event.setDataJson(data == null ? "{}" : truncate(data.toString(), 4000));
        eventMapper.insert(event);
    }

    public List<DeviceEvent> recentEvents(String sn, int limit) {
        return eventMapper.selectList(Wrappers.<DeviceEvent>lambdaQuery()
                .eq(sn != null && !sn.isBlank(), DeviceEvent::getDeviceSn, sn)
                .orderByDesc(DeviceEvent::getId)
                .last("limit " + Math.max(1, Math.min(limit, 200))));
    }

    /** 事件分页查询(控制页「设备事件」TAB 用) */
    public com.baomidou.mybatisplus.core.metadata.IPage<DeviceEvent> pageEvents(
            com.emergency.inspection.dto.query.DeviceEventQuery query) {
        String kw = query.getKeyword();
        String method = query.getMethod();
        return eventMapper.selectPage(
                PageUtil.build(query, "create_time",
                        PageUtil.allowedCamel("eventType", "method", "level", "createTime")),
                Wrappers.<DeviceEvent>lambdaQuery()
                        .eq(query.getDeviceSn() != null && !query.getDeviceSn().isBlank(),
                                DeviceEvent::getDeviceSn, query.getDeviceSn())
                        .eq(query.getEventType() != null, DeviceEvent::getEventType, query.getEventType())
                        .eq(query.getLevel() != null, DeviceEvent::getLevel, query.getLevel())
                        .eq(method != null && !method.isBlank(), DeviceEvent::getMethod, method)
                        .and(kw != null && !kw.isBlank(), w -> w.like(DeviceEvent::getMessage, kw.trim())
                                .or().like(DeviceEvent::getMethod, kw.trim())));
    }

    /* ==================== 工具 ==================== */

    private static Integer capacityOf(JsonNode node) {
        return node != null && node.hasNonNull("capacity_percent") ? node.get("capacity_percent").asInt() : null;
    }

    private Integer intOrNull(JsonNode node, String field) {
        return node.hasNonNull(field) ? node.get(field).asInt() : null;
    }

    private BigDecimal decimalOrNull(JsonNode node, String field) {
        return node.hasNonNull(field) ? BigDecimal.valueOf(node.get(field).asDouble()) : null;
    }

    private static String truncate(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max);
    }

    /** 供指令下发层复用:把对象序列化成 JSON 文本 */
    public String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            return "{}";
        }
    }
}
