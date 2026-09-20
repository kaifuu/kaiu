package com.emergency.inspection.dto;

import com.emergency.inspection.entity.DeviceLiveStream;
import lombok.Data;

/** 开启直播入参 */
@Data
public class LiveStartForm {

    /** 视频源标识:{sn}/{camera_index}/{video_index} */
    private String videoId;

    private DeviceLiveStream.UrlType urlType;

    /** 推流地址(RTMP / GB28181 必填) */
    private String url;

    /** 0 自适应 / 1 流畅 / 2 标清 / 3 高清 / 4 超清 */
    private Integer videoQuality;

    /** 镜头:normal / wide / zoom / ir */
    private String videoType;
}
