package com.emergency.inspection.common;

import com.baomidou.mybatisplus.core.metadata.OrderItem;
import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

import java.util.Map;

/**
 * 分页与动态排序构造:排序字段走白名单(防排序注入实体外字段),非法列/方向回退默认列倒序。
 */
public final class PageUtil {

    private PageUtil() {
    }

    /**
     * @param query      分页入参(page 1 起)
     * @param defaultCol 默认排序列(数据库列名,如 create_time)
     * @param allowed    可排序白名单:key = 前端字段(实体属性名),value = 数据库列名
     */
    public static <T> Page<T> build(PageQuery query, String defaultCol, Map<String, String> allowed) {
        Page<T> page = new Page<>(query.safePage(), query.safeSize());
        String sortBy = query.getSortBy();
        String column = sortBy == null ? null : allowed.get(sortBy);
        if (column == null) {
            column = defaultCol;
        }
        boolean asc = "asc".equalsIgnoreCase(query.getDirection());
        page.addOrder(asc ? OrderItem.asc(column) : OrderItem.desc(column));
        return page;
    }

    /** 白名单便捷构造:前端字段名与数据库列名同名(下划线写法)时用 */
    public static Map<String, String> allowed(String... columns) {
        java.util.Map<String, String> map = new java.util.LinkedHashMap<>();
        for (String c : columns) {
            map.put(c, c);
        }
        return map;
    }

    /**
     * 白名单便捷构造:前端传驼峰属性名,自动转下划线列名。
     * 例:allowedCamel("createTime", "lastLoginAt") → {createTime: create_time, lastLoginAt: last_login_at}
     */
    public static Map<String, String> allowedCamel(String... props) {
        java.util.Map<String, String> map = new java.util.LinkedHashMap<>();
        for (String p : props) {
            map.put(p, StringUtils.camelToUnderline(p));
        }
        return map;
    }

    /** 分页结果包装:与前端约定的 { rows, total } 结构 */
    public static <T> java.util.Map<String, Object> result(com.baomidou.mybatisplus.core.metadata.IPage<T> page) {
        java.util.Map<String, Object> out = new java.util.LinkedHashMap<>();
        out.put("rows", page.getRecords());
        out.put("total", page.getTotal());
        return out;
    }
}
