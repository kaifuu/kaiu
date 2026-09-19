package com.emergency.inspection.security;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "jwt")
public class JwtProperties {

    /** HS256 签名密钥,至少 32 字节 */
    private String secret = "emergency-inspection-platform-default-secret-key-2026";

    private String issuer = "emergency-inspection";

    /** 令牌有效期(小时) */
    private long ttlHours = 12;
}
