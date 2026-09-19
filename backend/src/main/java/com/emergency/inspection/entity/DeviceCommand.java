package com.emergency.inspection.entity;

import com.baomidou.mybatisplus.annotation.IEnum;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 云端下发的指令记录。
 * 上云 API 用 tid 做事务关联:云端下发时生成 tid,设备在 services_reply 中原样带回,
 * 据此把回复写回对应记录,从而得到「已下发 → 成功/失败/超时」的闭环。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("device_command")
public class DeviceCommand extends BaseEntity {

    public enum Status implements IEnum<String> {
        SENT,     // 已下发,等待回复
        OK,       // 设备执行成功
        FAILED,   // 设备拒绝或执行失败
        TIMEOUT;  // 超时未回复

        @Override
        public String getValue() {
            return name();
        }
    }

    private String deviceSn;

    /** 报文事务 id,云端生成 */
    private String tid;

    /** 上云 API 的服务方法名,如 cover_open / return_home */
    private String method;

    private Status status = Status.SENT;

    private String requestJson = "{}";

    private String replyJson;

    private LocalDateTime sentAt = LocalDateTime.now();

    private LocalDateTime replyAt;
}
