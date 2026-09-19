package com.emergency.inspection.security;

/** 当前登录用户(取自 JWT claims) */
public record LoginUser(Long userId, String username, String nickname, String roleCode) {

    public boolean isAdmin() {
        return "ADMIN".equals(roleCode);
    }
}
