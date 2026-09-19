package com.emergency.inspection.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.emergency.inspection.common.BizException;
import com.emergency.inspection.common.PageUtil;
import com.emergency.inspection.dto.query.CommandQuery;
import com.emergency.inspection.dji.DjiMessage;
import com.emergency.inspection.dji.DjiServiceCatalog;
import com.emergency.inspection.dji.DjiTopics;
import com.emergency.inspection.entity.Device;
import com.emergency.inspection.entity.DeviceCommand;
import com.emergency.inspection.gateway.mqtt.MqttPublisher;
import com.emergency.inspection.gateway.mqtt.MqttSessionManager;
import com.emergency.inspection.mapper.DeviceCommandMapper;
import io.netty.handler.codec.mqtt.MqttQoS;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 指令下发与回复闭环。
 *
 * 上云 API 用 tid 做事务关联:下发时生成 tid 并入库(SENT),
 * 设备在 services_reply 里原样带回 tid,据此把结果写回同一条记录(OK/FAILED)。
 * 超时未回复的由定时任务标记为 TIMEOUT —— 不依赖内存中的等待队列,重启也不丢状态。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DeviceCommandService {

    private final DeviceCommandMapper commandMapper;
    private final DeviceService deviceService;
    private final MqttPublisher publisher;
    private final MqttSessionManager sessionManager;

    @Value("${dji.command.timeout-seconds:15}")
    private long timeoutSeconds;

    @Value("${dji.command.history-keep:2000}")
    private int historyKeep;

    /**
     * 下发指令:校验 → 落库 → MQTT 下行。
     *
     * 这里**刻意不加 @Transactional**:设备回复可能在毫秒级到达,
     * 若下行发生在事务提交之前,handleReply 在另一个线程上按 tid 查不到记录,
     * 结果就是「设备明明回了、平台却记成超时」。先提交再下行才能保证顺序。
     */
    public DeviceCommand send(Long deviceId, String method, Map<String, Object> data) {
        Device device = deviceService.require(deviceId);
        if (method == null || method.isBlank()) {
            throw BizException.of("请选择要下发的指令");
        }
        if (!DjiServiceCatalog.isAllowed(device.getDeviceType(), method)) {
            throw BizException.of("指令 " + method + " 不适用于" + typeLabel(device.getDeviceType()));
        }

        // 上云 API 中指令下发到网关(机场)的 services 主题;无人机经其所属机场转发
        String gatewaySn = device.getDeviceType() == Device.DeviceType.DOCK
                ? device.getDeviceSn() : device.getGatewaySn();
        if (gatewaySn == null || gatewaySn.isBlank()) {
            throw BizException.of("无人机未绑定机场,无法下发指令");
        }
        if (!sessionManager.isOnline(gatewaySn)) {
            throw BizException.of("网关设备不在线,无法下发指令: " + gatewaySn);
        }

        // 面向飞行器的指令需带上目标 SN,机场据此决定操作对象
        Map<String, Object> payload = new LinkedHashMap<>();
        if (data != null) {
            payload.putAll(data);
        }
        if (device.getDeviceType() == Device.DeviceType.DRONE) {
            payload.put("sn", device.getDeviceSn());
        }

        String tid = UUID.randomUUID().toString().replace("-", "");
        String topic = DjiTopics.services(gatewaySn);
        String body = DjiMessage.build(tid, method, payload);

        DeviceCommand command = new DeviceCommand();
        command.setDeviceSn(device.getDeviceSn());
        command.setTid(tid);
        command.setMethod(method);
        command.setStatus(DeviceCommand.Status.SENT);
        command.setRequestJson(body);
        command.setSentAt(LocalDateTime.now());
        commandMapper.insert(command);   // 先落库提交,再下行

        int delivered = publisher.publish(topic, body, MqttQoS.AT_LEAST_ONCE);
        if (delivered == 0) {
            // 没投出去就直接结案,不留在 SENT 等超时 —— 否则用户要等 15 秒才知道没发出去
            command.setStatus(DeviceCommand.Status.FAILED);
            command.setReplyJson("{\"reason\":\"网关未订阅指令主题\"}");
            command.setReplyAt(LocalDateTime.now());
            commandMapper.updateById(command);
            throw BizException.of("网关未订阅指令主题,下发失败: " + topic);
        }

        log.info("指令下发 device={} method={} tid={}", device.getDeviceSn(), method, tid);
        return command;
    }

    /** 设备回复:按 tid 找回记录并落结果 */
    @Transactional
    public void handleReply(String gatewaySn, DjiMessage message) {
        String tid = message.tid();
        if (tid == null) {
            log.warn("services_reply 缺少 tid,无法关联: {}", message.data());
            return;
        }
        DeviceCommand command = commandMapper.selectOne(
                Wrappers.<DeviceCommand>lambdaQuery().eq(DeviceCommand::getTid, tid).last("limit 1"));
        if (command == null) {
            log.warn("收到未知 tid 的指令回复: {}", tid);
            return;
        }
        if (command.getStatus() != DeviceCommand.Status.SENT) {
            return;   // 已超时结算过,不覆盖
        }
        command.setReplyJson(message.data() == null ? "{}" : message.data().toString());
        command.setReplyAt(LocalDateTime.now());
        command.setStatus(message.success() ? DeviceCommand.Status.OK : DeviceCommand.Status.FAILED);
        commandMapper.updateById(command);
        log.info("指令回复 device={} method={} status={}", command.getDeviceSn(), command.getMethod(), command.getStatus());
    }

    public IPage<DeviceCommand> page(CommandQuery query) {
        var from = query.getStartDate() == null ? null : query.getStartDate().atStartOfDay();
        var to = query.getEndDate() == null ? null : query.getEndDate().atTime(java.time.LocalTime.MAX);
        return commandMapper.selectPage(
                PageUtil.build(query, "sent_at", PageUtil.allowedCamel("method", "status", "sentAt", "replyAt")),
                Wrappers.<DeviceCommand>lambdaQuery()
                        .eq(query.getDeviceSn() != null && !query.getDeviceSn().isBlank(),
                                DeviceCommand::getDeviceSn, query.getDeviceSn())
                        .eq(query.getStatus() != null, DeviceCommand::getStatus, query.getStatus())
                        .eq(query.getMethod() != null && !query.getMethod().isBlank(),
                                DeviceCommand::getMethod, query.getMethod())
                        .ge(from != null, DeviceCommand::getSentAt, from)
                        .le(to != null, DeviceCommand::getSentAt, to));
    }

    public List<DeviceCommand> recent(String deviceSn, int limit) {
        return commandMapper.selectList(Wrappers.<DeviceCommand>lambdaQuery()
                .eq(deviceSn != null && !deviceSn.isBlank(), DeviceCommand::getDeviceSn, deviceSn)
                .orderByDesc(DeviceCommand::getId)
                .last("limit " + Math.max(1, Math.min(limit, 100))));
    }

    /** 每 5 秒结算一次超时指令:设备没回 services_reply 的记为 TIMEOUT */
    @Scheduled(fixedDelay = 5_000, initialDelay = 10_000)
    @Transactional
    public void settleTimeout() {
        LocalDateTime deadline = LocalDateTime.now().minusSeconds(timeoutSeconds);
        int updated = commandMapper.update(null, Wrappers.<DeviceCommand>lambdaUpdate()
                .set(DeviceCommand::getStatus, DeviceCommand.Status.TIMEOUT)
                .set(DeviceCommand::getReplyAt, LocalDateTime.now())
                .set(DeviceCommand::getUpdateTime, LocalDateTime.now())
                .eq(DeviceCommand::getStatus, DeviceCommand.Status.SENT)
                .lt(DeviceCommand::getSentAt, deadline));
        if (updated > 0) {
            log.info("指令超时结算: {} 条", updated);
        }
    }

    private static String typeLabel(Device.DeviceType type) {
        return type == Device.DeviceType.DOCK ? "机场" : "无人机";
    }
}
