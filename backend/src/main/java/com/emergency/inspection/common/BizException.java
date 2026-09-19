package com.emergency.inspection.common;

/** 业务校验失败:由 GlobalExceptionHandler 转为 400 + 原始文案 */
public class BizException extends RuntimeException {

    public BizException(String message) {
        super(message);
    }

    public static BizException of(String message) {
        return new BizException(message);
    }
}
