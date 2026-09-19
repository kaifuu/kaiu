package com.emergency.inspection.controller;

import com.emergency.inspection.common.ApiResponse;
import com.emergency.inspection.common.OpLog;
import com.emergency.inspection.common.PageUtil;
import com.emergency.inspection.dto.query.VideoQuery;
import com.emergency.inspection.entity.VideoChannel;
import com.emergency.inspection.service.VideoChannelService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/** 视频通道管理(台账与在线状态;实际拉流需外部流媒体服务) */
@RestController
@RequestMapping("/api/videos")
@RequiredArgsConstructor
public class VideoChannelController {

    private final VideoChannelService channelService;

    @GetMapping("/page")
    public ApiResponse<Map<String, Object>> page(VideoQuery query) {
        return ApiResponse.ok(PageUtil.result(channelService.page(query)));
    }

    @GetMapping
    public ApiResponse<List<VideoChannel>> list() {
        return ApiResponse.ok(channelService.listAll());
    }

    /** 在线通道(大屏实时画面区) */
    @GetMapping("/online")
    public ApiResponse<List<VideoChannel>> online(@RequestParam(defaultValue = "6") int limit) {
        return ApiResponse.ok(channelService.online(limit));
    }

    @GetMapping("/{id}")
    public ApiResponse<VideoChannel> detail(@PathVariable Long id) {
        return ApiResponse.ok(channelService.require(id));
    }

    @PostMapping
    @OpLog(module = "视频管理", action = "新增")
    public ApiResponse<VideoChannel> create(@RequestBody VideoChannel body) {
        return ApiResponse.ok(channelService.create(body));
    }

    @PutMapping("/{id}")
    @OpLog(module = "视频管理", action = "修改")
    public ApiResponse<VideoChannel> update(@PathVariable Long id, @RequestBody VideoChannel body) {
        return ApiResponse.ok(channelService.update(id, body));
    }

    /** 手动切换在线状态(对接流媒体服务后可改为自动探测) */
    @PostMapping("/{id}/status")
    @OpLog(module = "视频管理", action = "切换状态")
    public ApiResponse<VideoChannel> switchStatus(@PathVariable Long id, @RequestParam boolean online) {
        return ApiResponse.ok(channelService.switchStatus(id, online));
    }

    @DeleteMapping("/{id}")
    @OpLog(module = "视频管理", action = "删除")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        channelService.delete(id);
        return ApiResponse.ok();
    }

    @GetMapping("/stats")
    public ApiResponse<Map<String, Long>> stats() {
        return ApiResponse.ok(channelService.countByStatus());
    }
}
