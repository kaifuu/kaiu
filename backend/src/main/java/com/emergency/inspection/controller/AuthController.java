package com.emergency.inspection.controller;

import com.emergency.inspection.common.ApiResponse;
import com.emergency.inspection.common.OpLog;
import com.emergency.inspection.dto.LoginRequest;
import com.emergency.inspection.dto.PasswordForm;
import com.emergency.inspection.dto.ProfileForm;
import com.emergency.inspection.service.AuthService;
import com.emergency.inspection.common.OpLogAspect;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/** 登录(验证码 + JWT)与个人信息 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    /** 图形验证码:返回 SVG 与校验用 cid(免登录) */
    @GetMapping("/captcha")
    public ApiResponse<Map<String, String>> captcha() {
        return ApiResponse.ok(authService.captcha());
    }

    /** 登录:成功返回 token + 昵称 + 角色 + 可见菜单(免登录) */
    @PostMapping("/login")
    public ApiResponse<Map<String, Object>> login(@RequestBody LoginRequest body, HttpServletRequest request) {
        return ApiResponse.ok(authService.login(body, OpLogAspect.clientIp(request)));
    }

    @PostMapping("/logout")
    public ApiResponse<Void> logout(HttpServletRequest request) {
        authService.logout(OpLogAspect.clientIp(request));
        return ApiResponse.ok();
    }

    /** 当前用户资料 + 菜单(刷新页面后重建导航) */
    @GetMapping("/profile")
    public ApiResponse<Map<String, Object>> profile() {
        return ApiResponse.ok(authService.profile());
    }

    /** 个人信息-修改基础资料(仅昵称/手机号) */
    @PutMapping("/profile")
    @OpLog(module = "个人信息", action = "修改资料")
    public ApiResponse<Map<String, Object>> updateProfile(@RequestBody ProfileForm body) {
        return ApiResponse.ok(authService.updateProfile(body));
    }

    /** 个人信息-修改密码 */
    @PostMapping("/profile/password")
    @OpLog(module = "个人信息", action = "修改密码")
    public ApiResponse<Void> changePassword(@RequestBody PasswordForm body) {
        authService.changePassword(body);
        return ApiResponse.ok();
    }
}
