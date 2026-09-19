package com.emergency.inspection.gateway.mqtt;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.mqtt.*;
import io.netty.handler.timeout.IdleState;
import io.netty.handler.timeout.IdleStateEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * MQTT Broker 报文处理(MQTT 3.1.1)。
 * 支持 CONNECT / PUBLISH(QoS0-1) / SUBSCRIBE / UNSUBSCRIBE / PINGREQ / DISCONNECT,
 * 足够覆盖大疆上云 API 的设备侧行为;QoS2 与持久会话不在本期范围内。
 */
@Slf4j
@Component
@ChannelHandler.Sharable
@RequiredArgsConstructor
public class MqttBrokerHandler extends SimpleChannelInboundHandler<MqttMessage> {

    private final MqttSessionManager sessionManager;
    private final MqttMessageDispatcher dispatcher;

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, MqttMessage msg) {
        switch (msg.fixedHeader().messageType()) {
            case CONNECT -> handleConnect(ctx, (MqttConnectMessage) msg);
            case PUBLISH -> handlePublish(ctx, (MqttPublishMessage) msg);
            case SUBSCRIBE -> handleSubscribe(ctx, (MqttSubscribeMessage) msg);
            case UNSUBSCRIBE -> handleUnsubscribe(ctx, (MqttUnsubscribeMessage) msg);
            case PINGREQ -> {
                touch(ctx);
                ctx.writeAndFlush(simple(MqttMessageType.PINGRESP));
            }
            case PUBACK, PUBREC, PUBREL, PUBCOMP -> touch(ctx);   // 云端下行 QoS1 的确认,忽略即可
            case DISCONNECT -> {
                log.debug("设备 {} 主动断开", clientId(ctx));
                ctx.close();
            }
            default -> log.debug("忽略未处理的报文类型 {}", msg.fixedHeader().messageType());
        }
    }

    private void handleConnect(ChannelHandlerContext ctx, MqttConnectMessage msg) {
        String clientId = msg.payload().clientIdentifier();
        if (clientId == null || clientId.isBlank()) {
            // MQTT 3.1.1 允许空 clientId(服务端分配),但大疆设备始终用 SN,这里直接拒绝便于排查
            log.warn("拒绝无 clientId 的连接: {}", ctx.channel().remoteAddress());
            ctx.writeAndFlush(MqttMessageBuilders.connAck()
                    .returnCode(MqttConnectReturnCode.CONNECTION_REFUSED_IDENTIFIER_REJECTED)
                    .sessionPresent(false).build());
            ctx.close();
            return;
        }
        MqttSession session = sessionManager.register(clientId, ctx.channel());
        ctx.channel().attr(MqttBrokerServer.CLIENT_ID).set(clientId);
        ctx.writeAndFlush(MqttMessageBuilders.connAck()
                .returnCode(MqttConnectReturnCode.CONNECTION_ACCEPTED)
                .sessionPresent(false).build());
        log.info("设备接入: sn={} remote={} keepAlive={}s 当前在线 {}",
                clientId, ctx.channel().remoteAddress(),
                msg.variableHeader().keepAliveTimeSeconds(), sessionManager.size());
        dispatcher.onConnected(session);
    }

    private void handlePublish(ChannelHandlerContext ctx, MqttPublishMessage msg) {
        touch(ctx);
        String topic = msg.variableHeader().topicName();
        ByteBuf buf = msg.payload();
        String payload = buf.toString(StandardCharsets.UTF_8);

        MqttQoS qos = msg.fixedHeader().qosLevel();
        if (qos == MqttQoS.AT_LEAST_ONCE) {
            // QoS1:先回 PUBACK,再处理业务,避免设备因等确认而重发
            ctx.writeAndFlush(MqttMessageBuilders.pubAck()
                    .packetId(msg.variableHeader().packetId()).build());
        }
        try {
            dispatcher.dispatch(topic, payload);
        } catch (Exception e) {
            log.error("处理上行报文失败 topic={} payload={}", topic, truncate(payload), e);
        }
    }

    private void handleSubscribe(ChannelHandlerContext ctx, MqttSubscribeMessage msg) {
        touch(ctx);
        MqttSession session = sessionManager.get(clientId(ctx));
        MqttMessageBuilders.SubAckBuilder ack = MqttMessageBuilders.subAck()
                .packetId(msg.variableHeader().messageId());
        List<MqttTopicSubscription> subs = msg.payload().topicSubscriptions();
        for (MqttTopicSubscription sub : subs) {
            String filter = sub.topicName();
            if (session != null) {
                session.getSubscriptions().add(filter);
            }
            // 只支持 QoS0/1,QoS2 降级为 1 并在 SUBACK 中如实回告
            MqttQoS granted = sub.qualityOfService() == MqttQoS.AT_MOST_ONCE
                    ? MqttQoS.AT_MOST_ONCE : MqttQoS.AT_LEAST_ONCE;
            ack.addGrantedQos(granted);
            log.debug("设备 {} 订阅 {}", clientId(ctx), filter);
        }
        ctx.writeAndFlush(ack.build());
    }

    private void handleUnsubscribe(ChannelHandlerContext ctx, MqttUnsubscribeMessage msg) {
        touch(ctx);
        MqttSession session = sessionManager.get(clientId(ctx));
        if (session != null) {
            session.getSubscriptions().removeAll(msg.payload().topics());
        }
        ctx.writeAndFlush(MqttMessageBuilders.unsubAck()
                .packetId(msg.variableHeader().messageId()).build());
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) {
        MqttSession session = sessionManager.remove(ctx.channel());
        if (session != null) {
            log.info("设备断开: sn={} 当前在线 {}", session.getClientId(), sessionManager.size());
            dispatcher.onDisconnected(session);
        }
    }

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) {
        // 读空闲:设备未按 keepAlive 发 PINGREQ,判定链路已死
        if (evt instanceof IdleStateEvent e && e.state() == IdleState.READER_IDLE) {
            log.warn("设备 {} 读空闲超时,断开连接", clientId(ctx));
            ctx.close();
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        log.warn("设备 {} 连接异常: {}", clientId(ctx), cause.getMessage());
        ctx.close();
    }

    private void touch(ChannelHandlerContext ctx) {
        MqttSession session = sessionManager.get(clientId(ctx));
        if (session != null) {
            session.touch();
        }
    }

    private static String clientId(ChannelHandlerContext ctx) {
        Object v = ctx.channel().attr(MqttBrokerServer.CLIENT_ID).get();
        return v == null ? "unknown" : v.toString();
    }

    private static MqttMessage simple(MqttMessageType type) {
        return new MqttMessage(new MqttFixedHeader(type, false, MqttQoS.AT_MOST_ONCE, false, 0));
    }

    private static String truncate(String s) {
        return s == null ? null : (s.length() > 300 ? s.substring(0, 300) + "..." : s);
    }
}
