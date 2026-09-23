package com.emergency.inspection.security;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/**
 * 启动时校验 JWT 密钥可用性,避免带着弱密钥/未解析占位符静默上线。
 *
 * <p>背景:{@code jwt.secret: ${JWT_SECRET}} 走 @ConfigurationProperties 绑定,
 * 占位符解析失败时 Spring 不抛异常,而是把字面量 {@code "${JWT_SECRET}"} 当作密钥值
 * (对比 {@code @Value} 注入会抛 Could not resolve placeholder)。
 * 若再叠加字段默认值,应用就会带着**可预测密钥**正常启动,任何人都能伪造令牌绕过鉴权。
 *
 * <p>用 @PostConstruct 而非 ApplicationRunner:后者在容器刷新完成、Web 端口已绑定、
 * 且已打印 "Started ...Application" 之后才执行,失败时日志会误导运维以为服务起过。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtSecretValidator {

    /** 开发环境默认密钥:仅 dev 允许使用 */
    private static final String DEV_DEFAULT = "emergency-inspection-platform-default-secret-key-2026";

    /** HS256 要求 256 位密钥 */
    private static final int MIN_BYTES = 32;

    private final JwtProperties props;
    private final Environment environment;

    @PostConstruct
    void validate() {
        String secret = props.getSecret();

        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException(
                    "jwt.secret 未配置。请设置环境变量 JWT_SECRET(至少 " + MIN_BYTES + " 字节)");
        }
        // 占位符未解析:值里仍带 ${...} 字面量
        if (secret.contains("${")) {
            throw new IllegalStateException(
                    "jwt.secret 占位符未解析(当前值: " + secret + ")。请设置环境变量 JWT_SECRET");
        }
        int bytes = secret.getBytes(StandardCharsets.UTF_8).length;
        if (bytes < MIN_BYTES) {
            throw new IllegalStateException(
                    "jwt.secret 长度不足:" + bytes + " 字节,HS256 要求至少 " + MIN_BYTES + " 字节");
        }
        boolean prod = Arrays.asList(environment.getActiveProfiles()).contains("prod");
        if (prod && DEV_DEFAULT.equals(secret)) {
            throw new IllegalStateException(
                    "生产环境禁止使用开发默认密钥。请通过环境变量 JWT_SECRET 覆盖");
        }

        log.info("JWT 密钥校验通过({} 字节{})", bytes, prod ? ",生产环境" : "");
    }
}
