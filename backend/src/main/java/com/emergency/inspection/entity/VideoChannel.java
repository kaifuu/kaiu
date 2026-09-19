package com.emergency.inspection.entity;

import com.baomidou.mybatisplus.annotation.IEnum;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 视频通道:机场 / 无人机的实时画面。
 * 本模块只维护通道台账与在线状态 —— 实际拉流需要 RTMP/GB28181 流媒体服务
 * (如 SRS、ZLMediaKit),平台侧不内嵌播放服务。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("video_channel")
public class VideoChannel extends BaseEntity {

    public enum ChannelType implements IEnum<String> {
        LIVE, PLAYBACK;

        @Override
        public String getValue() {
            return name();
        }
    }

    public enum Protocol implements IEnum<String> {
        RTMP, FLV, HLS, GB28181, WEBRTC;

        @Override
        public String getValue() {
            return name();
        }
    }

    public enum Status implements IEnum<String> {
        ONLINE, OFFLINE;

        @Override
        public String getValue() {
            return name();
        }
    }

    private String code;

    private String name;

    private String deviceSn;

    private String deviceName;

    private ChannelType channelType = ChannelType.LIVE;

    private Protocol protocol = Protocol.FLV;

    /** 拉流地址,如 http://media.example.com/live/dock01.flv */
    private String streamUrl;

    private String resolution;

    private Status status = Status.OFFLINE;

    private LocalDateTime lastFrameAt;

    private String remark;
}
