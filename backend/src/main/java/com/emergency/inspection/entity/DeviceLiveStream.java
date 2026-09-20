package com.emergency.inspection.entity;

import com.baomidou.mybatisplus.annotation.IEnum;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 直播会话:云端经 live_start_push 开流,直播中可切清晰度 / 镜头 / 舱内外相机,
 * live_stop_push 停流。会话留痕便于审计「谁在什么时候推了什么流」。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("device_live_stream")
public class DeviceLiveStream extends BaseEntity {

    public enum UrlType implements IEnum<String> {
        RTMP,       // 通用 RTMP 推流(协议值 1)
        GB28181,    // 国标 GB28181(协议值 3)
        WEBRTC,     // WebRTC / WHBC(协议值 4)
        AGORA;      // 声网(协议值 0)

        @Override
        public String getValue() {
            return name();
        }

        /** 上云 API 协议里的数字取值 */
        public int protocolValue() {
            return switch (this) {
                case AGORA -> 0;
                case RTMP -> 1;
                case GB28181 -> 3;
                case WEBRTC -> 4;
            };
        }
    }

    public enum Status implements IEnum<String> {
        PUSHING,    // 推流中
        STOPPED,    // 已停止
        FAILED;     // 开流失败

        @Override
        public String getValue() {
            return name();
        }
    }

    private String deviceSn;

    /** 视频源标识:{sn}/{camera_index}/{video_index} */
    private String videoId;

    private String cameraIndex;

    private String videoIndex;

    /** 当前镜头:normal / wide / zoom / ir */
    private String videoType;

    private UrlType urlType;

    private String url;

    /** 0 自适应 / 1 流畅 / 2 标清 / 3 高清 / 4 超清 */
    private Integer videoQuality;

    private Status status;

    private String errorMsg;

    private LocalDateTime startedAt;

    private LocalDateTime stoppedAt;
}
