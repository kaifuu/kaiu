package com.emergency.inspection.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.emergency.inspection.common.ApiResponse;
import com.emergency.inspection.common.OpLog;
import com.emergency.inspection.common.PageUtil;
import com.emergency.inspection.dto.query.SafeAlertQuery;
import com.emergency.inspection.entity.SafeAlert;
import com.emergency.inspection.mapper.SafeAlertMapper;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.Map;

/** 飞行安全预警:围栏闯入 / 预测闯入 / 电量骤降 / 高度突变记录分页 + 处置闭环 */
@RestController
@RequestMapping("/api/safe-alerts")
@RequiredArgsConstructor
public class SafeAlertController {

    private final SafeAlertMapper alertMapper;

    @GetMapping("/page")
    public ApiResponse<Map<String, Object>> page(SafeAlertQuery query) {
        String kw = query.getKeyword();
        IPage<SafeAlert> page = alertMapper.selectPage(
                PageUtil.build(query, "occurred_at",
                        PageUtil.allowedCamel("occurredAt", "createTime", "deviceSn", "level", "alertType")),
                Wrappers.<SafeAlert>lambdaQuery()
                        .eq(query.getAlertType() != null, SafeAlert::getAlertType, query.getAlertType())
                        .eq(query.getLevel() != null, SafeAlert::getLevel, query.getLevel())
                        .eq(query.getStatus() != null, SafeAlert::getStatus, query.getStatus())
                        .and(kw != null && !kw.isBlank(), w -> w.like(SafeAlert::getTitle, kw.trim())
                                .or().like(SafeAlert::getDeviceSn, kw.trim()))
                        .orderByDesc(SafeAlert::getOccurredAt));
        return ApiResponse.ok(PageUtil.result(page));
    }

    /** 处置:PENDING → HANDLED */
    @PostMapping("/{id}/handle")
    @OpLog(module = "安全预警", action = "处置预警")
    public ApiResponse<SafeAlert> handle(@PathVariable Long id, @RequestBody HandleForm form) {
        SafeAlert alert = alertMapper.selectById(id);
        if (alert == null) {
            return ApiResponse.error(404, "预警不存在");
        }
        alert.setStatus(SafeAlert.Status.HANDLED);
        alert.setHandler(form.getHandler());
        alert.setHandleRemark(form.getRemark());
        alert.setHandleTime(LocalDateTime.now());
        alertMapper.updateById(alert);
        return ApiResponse.ok(alert);
    }

    @Data
    public static class HandleForm {
        private String handler;
        private String remark;
    }
}
