package com.emergency.inspection.common;

import lombok.Data;

/**
 * 分页查询入参基类:所有列表接口的 Query DTO 继承它。
 * page 为 1 起页码(前端统一口径),size 上限 200 防全表拉取。
 */
@Data
public class PageQuery {

    private long page = 1;
    private long size = 10;

    /** 排序字段(实体属性名,须在各接口白名单内) */
    private String sortBy;

    /** asc / desc,空或非法按 desc */
    private String direction;

    /** 通用关键字模糊检索 */
    private String keyword;

    public long safePage() {
        return page < 1 ? 1 : page;
    }

    public long safeSize() {
        return size < 1 ? 10 : Math.min(size, 200);
    }
}
