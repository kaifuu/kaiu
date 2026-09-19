package com.emergency.inspection.security;

import com.emergency.inspection.entity.SysUser;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

/** JWT 签发与校验(HS256 无状态令牌,服务端不存会话) */
@Component
@RequiredArgsConstructor
public class JwtUtils {

    private final JwtProperties properties;

    private SecretKey key;

    private SecretKey key() {
        if (key == null) {
            byte[] bytes = properties.getSecret().getBytes(StandardCharsets.UTF_8);
            if (bytes.length < 32) {
                throw new IllegalStateException("jwt.secret 至少需要 32 字节(HS256 要求 256 位)");
            }
            key = Keys.hmacShaKeyFor(bytes);
        }
        return key;
    }

    public String create(SysUser user) {
        long now = System.currentTimeMillis();
        return Jwts.builder()
                .subject(user.getUsername())
                .claim("uid", user.getId())
                .claim("nickname", user.getNickname() == null ? user.getUsername() : user.getNickname())
                .claim("roleCode", user.getRoleCode() == null ? "" : user.getRoleCode())
                .issuer(properties.getIssuer())
                .issuedAt(new Date(now))
                .expiration(new Date(now + properties.getTtlHours() * 3600_000L))
                // 显式指定 HS256:signWith(Key) 会按密钥长度自选 HS384/HS512,算法不固定
                .signWith(key(), Jwts.SIG.HS256)
                .compact();
    }

    /** 校验签名与有效期;无效返回 null(不抛异常,交由拦截器统一处理) */
    public LoginUser parse(String token) {
        if (token == null || token.isBlank()) {
            return null;
        }
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(key())
                    .requireIssuer(properties.getIssuer())
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            Number uid = claims.get("uid", Number.class);
            return new LoginUser(
                    uid == null ? null : uid.longValue(),
                    claims.getSubject(),
                    claims.get("nickname", String.class),
                    claims.get("roleCode", String.class));
        } catch (Exception e) {
            return null;
        }
    }
}
