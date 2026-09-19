package com.emergency.inspection.common;

import com.emergency.inspection.entity.SysLog;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** 操作日志注解:打在写操作接口上,由 OpLogAspect 自动记录 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface OpLog {

    /** 模块名,如「巡检计划」 */
    String module();

    /** 动作,如「新增」「删除」 */
    String action();

    /** 日志归类:设备接入相关归 DEVICE,其余业务默认 OPERATE 操作日志 */
    SysLog.Type type() default SysLog.Type.OPERATE;
}
