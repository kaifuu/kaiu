package com.emergency.inspection.controller;

import com.emergency.inspection.common.ApiResponse;
import com.emergency.inspection.common.OpLog;
import com.emergency.inspection.common.PageUtil;
import com.emergency.inspection.dto.query.AlgoAlarmQuery;
import com.emergency.inspection.entity.AiAlgorithmAlarm;
import com.emergency.inspection.service.AiAlarmService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/** 算法告警:识别命中记录分页 / 详情 / 处置闭环 */
@RestController
@RequestMapping("/api/algo-alarms")
@RequiredArgsConstructor
public class AiAlarmController {

    private final AiAlarmService alarmService;

    @GetMapping("/page")
    public ApiResponse<Map<String, Object>> page(AlgoAlarmQuery query) {
        return ApiResponse.ok(PageUtil.result(alarmService.page(query)));
    }

    /** 详情:payload 已解析为键值对(payloadLabel 见前端字典) */
    @GetMapping("/{id}")
    public ApiResponse<AiAlgorithmAlarm> detail(@PathVariable Long id) {
        return ApiResponse.ok(alarmService.detail(id));
    }

    /** 处置:PENDING → HANDLED */
    @PostMapping("/{id}/handle")
    @OpLog(module = "算法告警", action = "处置告警")
    public ApiResponse<AiAlgorithmAlarm> handle(@PathVariable Long id, @RequestBody HandleForm form) {
        return ApiResponse.ok(alarmService.handle(id, form.getRemark()));
    }

    @Data
    public static class HandleForm {
        private String remark;
    }
}
