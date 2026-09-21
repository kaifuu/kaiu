package com.emergency.inspection.controller;

import com.emergency.inspection.common.ApiResponse;
import com.emergency.inspection.service.ScreenService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 应急巡检服务大屏:首页总览 + 七个子屏,每屏一次请求返回整屏数据
 * (30 秒刷新,拆多个接口会造成同屏时序不一致)。
 */
@RestController
@RequestMapping("/api/screen")
@RequiredArgsConstructor
public class ScreenController {

    private final ScreenService screenService;

    /** 首页总览 */
    @GetMapping("/overview")
    public ApiResponse<Map<String, Object>> overview() {
        return ApiResponse.ok(screenService.overview());
    }

    /** 应急专题:事件分布 + 进行中事件 + 安全预警 + 空域围栏 */
    @GetMapping("/emergency")
    public ApiResponse<Map<String, Object>> emergency() {
        return ApiResponse.ok(screenService.emergency());
    }

    /** 生态专题:点位分布 + 算法命中 + 臭气站读数 */
    @GetMapping("/ecology")
    public ApiResponse<Map<String, Object>> ecology() {
        return ApiResponse.ok(screenService.ecology());
    }

    /** 工单管理:状态/优先级/部门 + 近期工单 */
    @GetMapping("/orders")
    public ApiResponse<Map<String, Object>> orders() {
        return ApiResponse.ok(screenService.orders());
    }

    /** 飞手管理:状态/证照/片区 + 台账 */
    @GetMapping("/pilots")
    public ApiResponse<Map<String, Object>> pilots() {
        return ApiResponse.ok(screenService.pilots());
    }

    /** 设备监控:台账联 OSD 遥测 + 指令/事件流水 */
    @GetMapping("/devices")
    public ApiResponse<Map<String, Object>> devices() {
        return ApiResponse.ok(screenService.devices());
    }

    /** 航线管理:航线库 + 任务统计 */
    @GetMapping("/waylines")
    public ApiResponse<Map<String, Object>> waylines() {
        return ApiResponse.ok(screenService.waylines());
    }

    /** 飞行记录:架次列表 + 总计与趋势 */
    @GetMapping("/flights")
    public ApiResponse<Map<String, Object>> flights() {
        return ApiResponse.ok(screenService.flights());
    }
}
