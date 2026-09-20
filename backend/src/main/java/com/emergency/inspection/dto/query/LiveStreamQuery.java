package com.emergency.inspection.dto.query;

import com.emergency.inspection.common.PageQuery;
import com.emergency.inspection.entity.DeviceLiveStream;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 直播会话分页查询 */
@Data
@EqualsAndHashCode(callSuper = true)
public class LiveStreamQuery extends PageQuery {

    private String deviceSn;

    private DeviceLiveStream.Status status;
}
