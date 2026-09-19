package com.emergency.inspection.entity;

import com.baomidou.mybatisplus.annotation.IEnum;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/** 系统日志:操作(OPERATE)/登录(LOGIN)/设备(DEVICE) 三类 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_log")
@NoArgsConstructor
public class SysLog extends BaseEntity {

    public enum Type implements IEnum<String> {
        OPERATE, LOGIN, DEVICE;

        @Override
        public String getValue() {
            return name();
        }
    }

    private Type type;

    /** 账号名;设备日志存设备编码 */
    private String username;

    private String action;

    private String detail;

    private String ip;

    private Boolean success = true;

    public SysLog(Type type, String username, String action, String detail, String ip, boolean success) {
        this.type = type;
        this.username = username;
        this.action = action;
        this.detail = detail;
        this.ip = ip;
        this.success = success;
    }
}
