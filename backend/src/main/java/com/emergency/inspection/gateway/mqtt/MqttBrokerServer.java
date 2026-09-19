package com.emergency.inspection.gateway.mqtt;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.mqtt.MqttDecoder;
import io.netty.handler.codec.mqtt.MqttEncoder;
import io.netty.handler.timeout.IdleStateHandler;
import io.netty.util.AttributeKey;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * 基于 Netty 的 MQTT Broker:大疆机场与无人机直接连到本端口,
 * 不需要额外部署 EMQX 等中间件。
 *
 * 在 ApplicationReadyEvent 启动 —— 此时 Web 容器与数据源均已就绪,
 * 设备一连上来就能落库,不会出现「连上了但写不进」的窗口。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MqttBrokerServer {

    /** 连接建立后把 clientId(设备 SN)挂到 channel 上,便于各回调取用 */
    public static final AttributeKey<String> CLIENT_ID = AttributeKey.valueOf("mqtt.clientId");

    private final MqttProperties properties;
    private final MqttBrokerHandler brokerHandler;

    private EventLoopGroup bossGroup;
    private EventLoopGroup workerGroup;
    private Channel serverChannel;

    @EventListener(ApplicationReadyEvent.class)
    public void start() {
        if (!properties.isEnabled()) {
            log.info("MQTT Broker 已关闭(dji.mqtt.enabled=false)");
            return;
        }
        bossGroup = new NioEventLoopGroup(1);
        workerGroup = new NioEventLoopGroup();

        ServerBootstrap bootstrap = new ServerBootstrap()
                .group(bossGroup, workerGroup)
                .channel(NioServerSocketChannel.class)
                .option(ChannelOption.SO_BACKLOG, 256)
                .childOption(ChannelOption.SO_KEEPALIVE, true)
                .childOption(ChannelOption.TCP_NODELAY, true)
                .childHandler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel ch) {
                        ch.pipeline()
                                // 读空闲超过阈值即判链路已死(设备会按 keepAlive 发 PINGREQ 续命)
                                .addLast(new IdleStateHandler(properties.getIdleTimeoutSeconds(), 0, 0,
                                        TimeUnit.SECONDS))
                                .addLast(new MqttDecoder(properties.getMaxBytesInMessage()))
                                .addLast(MqttEncoder.INSTANCE)
                                .addLast(brokerHandler);
                    }
                });

        try {
            serverChannel = bootstrap.bind(properties.getPort()).sync().channel();
            log.info("MQTT Broker 已启动,端口 {}", properties.getPort());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("MQTT Broker 启动被中断", e);
            shutdown();
        } catch (Exception e) {
            log.error("MQTT Broker 启动失败(端口 {}): {}", properties.getPort(), e.getMessage());
            shutdown();
        }
    }

    @PreDestroy
    public void stop() {
        shutdown();
    }

    private void shutdown() {
        if (serverChannel != null) {
            serverChannel.close();
            serverChannel = null;
        }
        if (bossGroup != null) {
            bossGroup.shutdownGracefully();
            bossGroup = null;
        }
        if (workerGroup != null) {
            workerGroup.shutdownGracefully();
            workerGroup = null;
        }
        log.info("MQTT Broker 已停止");
    }
}
