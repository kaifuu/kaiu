package com.emergency.inspection.common;

import com.emergency.inspection.entity.SysLog;
import com.emergency.inspection.mapper.SysLogMapper;
import com.emergency.inspection.security.LoginContext;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/** 操作日志切面:成功/失败均记录,操作人取登录上下文(失败时回退匿名) */
@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class OpLogAspect {

    private final SysLogMapper logMapper;

    @Around("@annotation(opLog)")
    public Object around(ProceedingJoinPoint pjp, OpLog opLog) throws Throwable {
        Throwable error = null;
        try {
            return pjp.proceed();
        } catch (Throwable t) {
            error = t;
            throw t;
        } finally {
            try {
                record(opLog, error);
            } catch (Exception e) {
                // 日志失败不能影响主流程
                log.warn("操作日志写入失败: {}", e.getMessage());
            }
        }
    }

    private void record(OpLog opLog, Throwable error) {
        String username = LoginContext.getUsername();
        String ip = "";
        ServletRequestAttributes attrs =
                (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attrs != null) {
            ip = clientIp(attrs.getRequest());
        }
        SysLog entity = new SysLog();
        entity.setType(opLog.type());
        entity.setUsername(username == null ? "anonymous" : username);
        entity.setAction(opLog.module() + "·" + opLog.action());
        entity.setDetail(error == null ? "成功"
                : "失败: " + error.getClass().getSimpleName() + " " + error.getMessage());
        entity.setIp(ip);
        entity.setSuccess(error == null);
        logMapper.insert(entity);
    }

    /** X-Forwarded-For 取首个(反代场景),否则用 remoteAddr */
    public static String clientIp(HttpServletRequest req) {
        String ip = req.getHeader("X-Forwarded-For");
        if (ip != null && !ip.isBlank()) {
            return ip.split(",")[0].trim();
        }
        return req.getRemoteAddr();
    }
}
