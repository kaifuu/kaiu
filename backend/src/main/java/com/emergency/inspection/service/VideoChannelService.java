package com.emergency.inspection.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.emergency.inspection.common.BizException;
import com.emergency.inspection.common.PageUtil;
import com.emergency.inspection.dto.query.VideoQuery;
import com.emergency.inspection.entity.VideoChannel;
import com.emergency.inspection.mapper.VideoChannelMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 视频通道管理。
 * 平台只维护通道台账与在线状态:实际拉流需要外部流媒体服务(RTMP/GB28181,如 SRS / ZLMediaKit),
 * 前端播放时把 streamUrl 交给支持对应协议的播放器即可。
 */
@Service
@RequiredArgsConstructor
public class VideoChannelService {

    private final VideoChannelMapper channelMapper;

    public IPage<VideoChannel> page(VideoQuery query) {
        String kw = query.getKeyword();
        return channelMapper.selectPage(
                PageUtil.build(query, "id",
                        PageUtil.allowedCamel("code", "name", "deviceName", "protocol",
                                "status", "lastFrameAt", "createTime")),
                Wrappers.<VideoChannel>lambdaQuery()
                        .and(kw != null && !kw.isBlank(), w -> w.like(VideoChannel::getName, kw.trim())
                                .or().like(VideoChannel::getCode, kw.trim())
                                .or().like(VideoChannel::getDeviceName, kw.trim()))
                        .eq(query.getStatus() != null, VideoChannel::getStatus, query.getStatus())
                        .eq(query.getProtocol() != null, VideoChannel::getProtocol, query.getProtocol())
                        .eq(query.getChannelType() != null, VideoChannel::getChannelType, query.getChannelType()));
    }

    public List<VideoChannel> listAll() {
        return channelMapper.selectList(Wrappers.<VideoChannel>lambdaQuery().orderByAsc(VideoChannel::getId));
    }

    public List<VideoChannel> online(int limit) {
        return channelMapper.selectList(Wrappers.<VideoChannel>lambdaQuery()
                .eq(VideoChannel::getStatus, VideoChannel.Status.ONLINE)
                .orderByDesc(VideoChannel::getLastFrameAt)
                .last("limit " + Math.max(1, Math.min(limit, 50))));
    }

    public VideoChannel require(Long id) {
        VideoChannel channel = channelMapper.selectById(id);
        if (channel == null) {
            throw BizException.of("视频通道不存在: " + id);
        }
        return channel;
    }

    public VideoChannel create(VideoChannel body) {
        if (body.getName() == null || body.getName().isBlank()) {
            throw BizException.of("通道名称不能为空");
        }
        if (body.getCode() == null || body.getCode().isBlank()) {
            body.setCode(nextCode());
        } else if (existsCode(body.getCode(), null)) {
            throw BizException.of("通道编码已存在: " + body.getCode());
        }
        body.setId(null);
        if (body.getChannelType() == null) body.setChannelType(VideoChannel.ChannelType.LIVE);
        if (body.getProtocol() == null) body.setProtocol(VideoChannel.Protocol.FLV);
        if (body.getStatus() == null) body.setStatus(VideoChannel.Status.OFFLINE);
        channelMapper.insert(body);
        return body;
    }

    public VideoChannel update(Long id, VideoChannel body) {
        VideoChannel channel = require(id);
        if (body.getName() != null) channel.setName(body.getName());
        if (body.getDeviceSn() != null) channel.setDeviceSn(body.getDeviceSn());
        if (body.getDeviceName() != null) channel.setDeviceName(body.getDeviceName());
        if (body.getChannelType() != null) channel.setChannelType(body.getChannelType());
        if (body.getProtocol() != null) channel.setProtocol(body.getProtocol());
        if (body.getStreamUrl() != null) channel.setStreamUrl(body.getStreamUrl());
        if (body.getResolution() != null) channel.setResolution(body.getResolution());
        if (body.getStatus() != null) channel.setStatus(body.getStatus());
        if (body.getRemark() != null) channel.setRemark(body.getRemark());
        channelMapper.updateById(channel);
        return channel;
    }

    /** 手动置为在线/离线并记录最后一帧时间(对接流媒体服务后可改为自动探测) */
    public VideoChannel switchStatus(Long id, boolean online) {
        VideoChannel channel = require(id);
        channel.setStatus(online ? VideoChannel.Status.ONLINE : VideoChannel.Status.OFFLINE);
        if (online) {
            channel.setLastFrameAt(LocalDateTime.now());
        }
        channelMapper.updateById(channel);
        return channel;
    }

    public void delete(Long id) {
        require(id);
        channelMapper.deleteById(id);
    }

    public Map<String, Long> countByStatus() {
        Map<String, Long> out = new LinkedHashMap<>();
        for (VideoChannel.Status s : VideoChannel.Status.values()) {
            out.put(s.name(), channelMapper.selectCount(
                    Wrappers.<VideoChannel>lambdaQuery().eq(VideoChannel::getStatus, s)));
        }
        return out;
    }

    private boolean existsCode(String code, Long excludeId) {
        return channelMapper.selectCount(Wrappers.<VideoChannel>lambdaQuery()
                .eq(VideoChannel::getCode, code)
                .ne(excludeId != null, VideoChannel::getId, excludeId)) > 0;
    }

    private String nextCode() {
        long n = channelMapper.selectCount(Wrappers.<VideoChannel>lambdaQuery()) + 1;
        return String.format("VC%04d", n);
    }
}
