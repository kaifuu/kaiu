package com.emergency.inspection.dto.query;

import com.emergency.inspection.common.PageQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 媒体文件分页查询 */
@Data
@EqualsAndHashCode(callSuper = true)
public class MediaFileQuery extends PageQuery {

    private String deviceSn;

    private String flightId;

    private Boolean isOriginal;
}
