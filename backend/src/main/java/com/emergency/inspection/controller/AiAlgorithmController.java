package com.emergency.inspection.controller;

import com.emergency.inspection.common.ApiResponse;
import com.emergency.inspection.common.OpLog;
import com.emergency.inspection.entity.AiAlgorithm;
import com.emergency.inspection.entity.AiAlgorithmAlarm;
import com.emergency.inspection.service.AiAlgorithmService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** 算法管理:算法注册表 / 运行配置 / 手动执行识别 */
@RestController
@RequestMapping("/api/algorithms")
@RequiredArgsConstructor
public class AiAlgorithmController {

    private final AiAlgorithmService algorithmService;

    /** 全量算法(含启停 / 置信度门槛 / 告警等级配置) */
    @GetMapping
    public ApiResponse<List<AiAlgorithm>> list() {
        return ApiResponse.ok(algorithmService.list());
    }

    /** 保存运行配置:启停 / 置信度门槛(50-99) / 告警等级 */
    @PutMapping("/{id}")
    @OpLog(module = "算法管理", action = "修改算法配置")
    public ApiResponse<AiAlgorithm> config(@PathVariable Long id, @RequestBody AiAlgorithm body) {
        return ApiResponse.ok(algorithmService.config(id, body));
    }

    /** 手动执行一次识别(演示/联调入口,立即合成一条命中) */
    @PostMapping("/{id}/run")
    @OpLog(module = "算法管理", action = "执行算法识别")
    public ApiResponse<AiAlgorithmAlarm> run(@PathVariable Long id) {
        return ApiResponse.ok(algorithmService.run(id));
    }
}
