package com.emergency.inspection.dto.query;

import com.emergency.inspection.common.PageQuery;
import com.emergency.inspection.entity.VideoChannel;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 视频通道分页查询:keyword 模糊名称/编码/设备 */
@Data
@EqualsAndHashCode(callSuper = true)
public class VideoQuery extends PageQuery {

    private VideoChannel.Status status;
    private VideoChannel.Protocol protocol;
    private VideoChannel.ChannelType channelType;
}
