package com.emergency.inspection.security;

/**
 * 登录上下文:AuthInterceptor 在请求进入时写入、请求结束时清理。
 * 供 Service / 操作日志切面取当前操作人,免去逐层传参。
 */
public final class LoginContext {

    private static final ThreadLocal<LoginUser> HOLDER = new ThreadLocal<>();

    private LoginContext() {
    }

    public static void set(LoginUser user) {
        HOLDER.set(user);
    }

    public static LoginUser get() {
        return HOLDER.get();
    }

    public static void clear() {
        HOLDER.remove();
    }

    public static String getUsername() {
        LoginUser user = HOLDER.get();
        return user == null ? null : user.username();
    }

    /** 取当前操作人昵称,未登录回落 anonymous */
    public static String getOperator() {
        LoginUser user = HOLDER.get();
        if (user == null) {
            return "anonymous";
        }
        return user.nickname() == null || user.nickname().isBlank() ? user.username() : user.nickname();
    }
}
