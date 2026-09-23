package com.emergency.inspection.security;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "jwt")
public class JwtProperties {

    /**
     * HS256 签名密钥,至少 32 字节。
     *
     * <p>刻意不给字段默认值:该属性经 @ConfigurationProperties 绑定,
     * 占位符解析失败时不会抛异常,而是把字面量当值——留下默认值会让应用
     * 带着可预测密钥静默启动。缺失/未解析由 {@link JwtSecretValidator} 兜底拦截。
     */
    private String secret;

    private String issuer = "emergency-inspection";

    /** 令牌有效期(小时) */
    private long ttlHours = 12;
}
