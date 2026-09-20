package com.emergency.inspection.entity;

import com.baomidou.mybatisplus.annotation.IEnum;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 航线飞行任务:云端经 flighttask_prepare / flighttask_execute 下发到机场,
 * 进度由设备 flighttask_progress 事件驱动,失败/取消后可凭断点信息续飞。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("wayline_job")
public class WaylineJob extends BaseEntity {

    public enum JobType implements IEnum<String> {
        IMMEDIATE,  // 立即任务(task_type=0)
        TIMED,      // 定时任务(task_type=1)
        CONDITION;  // 条件任务(task_type=2):ready_conditions 满足后设备发 flighttask_ready

        @Override
        public String getValue() {
            return name();
        }

        /** 上云 API 协议里的 task_type 数值 */
        public int protocolValue() {
            return ordinal();
        }
    }

    /** 任务下发通道:常规三段式 / Dock 3 空中下发航线 */
    public enum JobChannel implements IEnum<String> {
        FLIGHTTASK,     // 地面任务:prepare → execute → progress
        IN_FLIGHT;      // 空中下发:in_flight_wayline_deliver → progress

        @Override
        public String getValue() {
            return name();
        }
    }

    public enum Status implements IEnum<String> {
        SENT,       // 已下发 prepare,等机场确认
        READY,      // 机场就绪:定时任务等待到点 / 条件任务等待设备就绪
        QUEUED,     // 已下发 execute,任务入队
        RUNNING,    // 执行中
        PAUSED,     // 已暂停(flighttask_pause,可 recovery 恢复)
        SUCCESS,    // 完成
        FAILED,     // 失败
        CANCELED;   // 已取消

        @Override
        public String getValue() {
            return name();
        }
    }

    /** 任务事务 id:云端生成,设备进度上报时原样带回 */
    private String flightId;

    /** prepare 指令的 tid:机场确认就绪后据此触发 execute */
    private String prepareTid;

    private String dockSn;

    private String droneSn;

    private Long waylineId;

    /** 航线名快照:航线库删除后任务仍可读 */
    private String waylineName;

    private JobType jobType;

    /** 下发通道:FLIGHTTASK 常规三段式 / IN_FLIGHT 空中下发航线 */
    private JobChannel jobChannel;

    /** 定时任务的计划执行时刻 */
    private LocalDateTime executeTime;

    private Status status;

    private Integer progress;

    private Integer currentStep;

    /** 断点信息 JSON:{index,progress,remain_margin},失败/取消后可续飞 */
    private String breakpointJson;

    /** 条件任务就绪条件 JSON:{battery_capacity,begin_time,end_time}(毫秒时间戳) */
    private String readyConditionsJson;

    /** 返航高度 m */
    private Integer rthAltitude;

    /** return_home_info 事件上报的返航轨迹点 JSON */
    private String returnHomeJson;

    private Integer mediaCount;

    private String errorMsg;

    private LocalDateTime dispatchedAt;

    private LocalDateTime beginAt;

    private LocalDateTime endAt;

    // ---------- 展示字段,不落库 ----------

    @TableField(exist = false)
    private String dockName;
}
