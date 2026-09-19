package com.emergency.inspection.service;

import com.emergency.inspection.common.BizException;
import com.emergency.inspection.dto.LoginRequest;
import com.emergency.inspection.dto.MenuDto;
import com.emergency.inspection.dto.PasswordForm;
import com.emergency.inspection.dto.ProfileForm;
import com.emergency.inspection.entity.SysLog;
import com.emergency.inspection.entity.SysUser;
import com.emergency.inspection.mapper.SysUserMapper;
import com.emergency.inspection.security.JwtUtils;
import com.emergency.inspection.security.LoginContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** 登录(账号密码 + 图形验证码)+ 个人信息 */
@Service
@RequiredArgsConstructor
public class AuthService {

    private static final long CAPTCHA_TTL_MS = 5 * 60 * 1000L;

    /** 验证码缓存:cid -> 验证码,校验一次即失效 */
    private final Map<String, CaptchaEntry> captchaStore = new ConcurrentHashMap<>();

    private final SysUserService userService;
    private final SysLogService logService;
    private final MenuService menuService;
    private final JwtUtils jwtUtils;
    private final SysUserMapper userMapper;

    private record CaptchaEntry(String code, long createdAt) {
    }

    /** 生成验证码(SVG,免登录) */
    public Map<String, String> captcha() {
        String cid = UUID.randomUUID().toString().replace("-", "");
        String code = randomCode(4);
        captchaStore.put(cid, new CaptchaEntry(code, System.currentTimeMillis()));
        // 顺带清理过期项,避免长期运行内存堆积
        captchaStore.entrySet().removeIf(e -> System.currentTimeMillis() - e.getValue().createdAt() > CAPTCHA_TTL_MS);
        return Map.of("cid", cid, "svg", CaptchaRenderer.render(code));
    }

    @Transactional
    public Map<String, Object> login(LoginRequest req, String ip) {
        String username = req.getUsername() == null ? "" : req.getUsername().trim();
        String password = req.getPassword() == null ? "" : req.getPassword();

        verifyCaptcha(req.getCid(), req.getCaptcha());

        SysUser user = userService.findByUsername(username);
        if (user == null || !userService.matches(password, user.getPassword())) {
            logService.record(SysLog.Type.LOGIN, username, "登录失败", "用户名或密码错误", ip, false);
            throw BizException.of("用户名或密码错误");
        }
        if (user.getStatus() != SysUser.Status.ENABLED) {
            logService.record(SysLog.Type.LOGIN, username, "登录失败", "账号已停用", ip, false);
            throw BizException.of("账号已停用,请联系管理员");
        }

        user.setLastLoginAt(LocalDateTime.now());
        userMapper.updateById(user);
        logService.record(SysLog.Type.LOGIN, username, "登录成功", null, ip, true);

        // roleCode/roleName 是联表展示字段,登录响应与 JWT 都要用,必须先回填
        userService.fillNames(List.of(user));
        List<MenuDto> menus = menuService.mine(user);
        Map<String, Object> data = new HashMap<>();
        data.put("token", jwtUtils.create(user));
        data.put("username", user.getUsername());
        data.put("nickname", displayName(user));
        data.put("roleCode", roleCodeOf(user));
        data.put("menus", menus);
        return data;
    }

    public void logout(String ip) {
        String username = LoginContext.getUsername();
        logService.record(SysLog.Type.LOGIN, username, "退出登录", null, ip, true);
    }

    public Map<String, Object> profile() {
        SysUser user = currentUser();
        Map<String, Object> data = new HashMap<>();
        data.put("id", user.getId());
        data.put("username", user.getUsername());
        data.put("nickname", user.getNickname());
        data.put("phone", user.getPhone());
        data.put("roleCode", roleCodeOf(user));
        data.put("roleName", user.getRoleName());
        data.put("orgId", user.getOrgId());
        data.put("lastLoginAt", user.getLastLoginAt());
        data.put("menus", menuService.mine(user));
        return data;
    }

    /** 个人信息-修改基础资料(账号与角色走人员管理) */
    @Transactional
    public Map<String, Object> updateProfile(ProfileForm form) {
        SysUser user = currentUser();
        if (form.getNickname() != null) {
            String nickname = form.getNickname().trim();
            if (nickname.isEmpty()) {
                throw BizException.of("昵称不能为空");
            }
            if (nickname.length() > 32) {
                throw BizException.of("昵称最长 32 字");
            }
            user.setNickname(nickname);
        }
        if (form.getPhone() != null) {
            String phone = form.getPhone().trim();
            if (!phone.isEmpty() && !phone.matches("\\d{6,20}")) {
                throw BizException.of("手机号格式不正确(6-20 位数字)");
            }
            user.setPhone(phone);
        }
        userMapper.updateById(user);
        return Map.of("nickname", user.getNickname() == null ? "" : user.getNickname(),
                "phone", user.getPhone() == null ? "" : user.getPhone());
    }

    /** 个人信息-修改密码:校验原密码,新密码 ≥6 位且不得与原密码相同 */
    @Transactional
    public void changePassword(PasswordForm form) {
        SysUser user = currentUser();
        String oldPwd = form.getOldPassword();
        String newPwd = form.getNewPassword();
        if (oldPwd == null || oldPwd.isBlank()) {
            throw BizException.of("请输入原密码");
        }
        if (!userService.matches(oldPwd, user.getPassword())) {
            throw BizException.of("原密码不正确");
        }
        if (newPwd == null || newPwd.length() < 6) {
            throw BizException.of("新密码长度至少 6 位");
        }
        if (newPwd.length() > 64) {
            throw BizException.of("新密码长度最多 64 位");
        }
        if (userService.matches(newPwd, user.getPassword())) {
            throw BizException.of("新密码不能与原密码相同");
        }
        user.setPassword(userService.encode(newPwd));
        userMapper.updateById(user);
    }

    private void verifyCaptcha(String cid, String captcha) {
        CaptchaEntry entry = cid == null ? null : captchaStore.remove(cid);
        if (entry == null) {
            throw BizException.of("验证码已过期,请刷新后重试");
        }
        if (System.currentTimeMillis() - entry.createdAt() > CAPTCHA_TTL_MS) {
            throw BizException.of("验证码已过期,请刷新后重试");
        }
        if (captcha == null || !entry.code().equalsIgnoreCase(captcha.trim())) {
            throw BizException.of("验证码错误");
        }
    }

    /** 取当前登录用户(带角色名回填) */
    public SysUser currentUser() {
        String username = LoginContext.getUsername();
        SysUser user = userService.findByUsername(username);
        if (user == null) {
            throw BizException.of("用户不存在或登录已失效");
        }
        userService.fillNames(List.of(user));
        return user;
    }

    private static String displayName(SysUser user) {
        return user.getNickname() == null || user.getNickname().isBlank()
                ? user.getUsername() : user.getNickname();
    }

    private static String roleCodeOf(SysUser user) {
        return user.getRoleCode() == null ? "" : user.getRoleCode();
    }

    private static String randomCode(int len) {
        // 去掉易混淆字符 0/O/1/I
        String chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < len; i++) {
            sb.append(chars.charAt((int) (Math.random() * chars.length())));
        }
        return sb.toString();
    }
}
