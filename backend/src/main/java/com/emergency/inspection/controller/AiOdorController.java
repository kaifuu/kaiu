package com.emergency.inspection.controller;

import com.emergency.inspection.common.ApiResponse;
import com.emergency.inspection.common.OpLog;
import com.emergency.inspection.service.AiOdorService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/** 臭气检测与扩散溯源:分布图 / 扩散模拟溯源 / 改风重算 */
@RestController
@RequestMapping("/api/odor")
@RequiredArgsConstructor
public class AiOdorController {

    private final AiOdorService odorService;

    /** 臭气告警分布图:站点 + 最新读数 + 超标分级 + 区域风 */
    @GetMapping("/map")
    public ApiResponse<Map<String, Object>> map() {
        return ApiResponse.ok(odorService.map());
    }

    /** 扩散模拟与溯源:上风向回溯定源 + 下风向扩散轨迹 + 受影响站点 */
    @GetMapping("/dispersion")
    public ApiResponse<Map<String, Object>> dispersion() {
        return ApiResponse.ok(odorService.dispersion());
    }

    /** 手动改风(立即重算一批读数,观察扩散轨迹变化) */
    @PutMapping("/wind")
    @OpLog(module = "臭气溯源", action = "调整风向风速")
    public ApiResponse<Map<String, Object>> setWind(@RequestBody WindForm form) {
        return ApiResponse.ok(odorService.setWind(form.getSpeed(), form.getDirection()));
    }

    @Data
    public static class WindForm {
        /** 风速 m/s */
        private Double speed;
        /** 风向(度,0=北,顺时针,风的来向) */
        private Integer direction;
    }
}
