package com.emergency.inspection.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import org.apache.ibatis.reflection.MetaObject;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.LocalDateTime;

@Configuration
public class MybatisPlusConfig {

    /** 分页插件(PostgreSQL 方言)+ 单页上限,防前端传超大 size 拖库 */
    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        PaginationInnerInterceptor pagination = new PaginationInnerInterceptor(DbType.POSTGRE_SQL);
        pagination.setMaxLimit(200L);
        interceptor.addInnerInterceptor(pagination);
        return interceptor;
    }

    /** create_time / update_time 自动填充 */
    @Bean
    public MetaObjectHandler metaObjectHandler() {
        return new MetaObjectHandler() {
            @Override
            public void insertFill(MetaObject metaObject) {
                LocalDateTime now = LocalDateTime.now();
                strictInsertFill(metaObject, "createTime", LocalDateTime.class, now);
                strictInsertFill(metaObject, "updateTime", LocalDateTime.class, now);
            }

            @Override
            public void updateFill(MetaObject metaObject) {
                // 用 setFieldValByName 而非 strictUpdateFill:后者只在字段为 null 时填充,
                // 而"先 select 出实体、改几个字段再 updateById"是本项目的常见写法,
                // 此时 updateTime 已被读出来的旧值占位,strict 版本会直接跳过 ——
                // 表现为 update_time 永远停在插入时刻(如 device_osd 的最新遥测时间)。
                setFieldValByName("updateTime", LocalDateTime.now(), metaObject);
            }
        };
    }
}
