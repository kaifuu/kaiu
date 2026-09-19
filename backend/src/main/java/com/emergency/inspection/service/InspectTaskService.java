package com.emergency.inspection.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.emergency.inspection.common.BizException;
import com.emergency.inspection.common.PageUtil;
import com.emergency.inspection.dto.query.TaskQuery;
import com.emergency.inspection.entity.InspectPoint;
import com.emergency.inspection.entity.InspectTask;
import com.emergency.inspection.mapper.InspectTaskMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

/** 巡检任务管理:任务执行状态流转 + 逾期自动标记 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InspectTaskService {

    private final InspectTaskMapper taskMapper;
    private final InspectPointService pointService;

    public IPage<InspectTask> page(TaskQuery query) {
        String kw = query.getKeyword();
        String executor = query.getExecutor();
        LocalDateTime from = query.getStartDate() == null ? null : query.getStartDate().atStartOfDay();
        LocalDateTime to = query.getEndDate() == null ? null : query.getEndDate().atTime(LocalTime.MAX);
        return taskMapper.selectPage(
                PageUtil.build(query, "plan_start",
                        PageUtil.allowedCamel("name", "pointName", "executor", "status", "result",
                                "planStart", "planEnd", "actualEnd", "createTime")),
                Wrappers.<InspectTask>lambdaQuery()
                        .and(kw != null && !kw.isBlank(), w -> w.like(InspectTask::getName, kw.trim())
                                .or().like(InspectTask::getPointName, kw.trim())
                                .or().like(InspectTask::getExecutor, kw.trim()))
                        .eq(query.getPlanId() != null, InspectTask::getPlanId, query.getPlanId())
                        .eq(query.getPointId() != null, InspectTask::getPointId, query.getPointId())
                        .eq(query.getStatus() != null, InspectTask::getStatus, query.getStatus())
                        .eq(query.getResult() != null, InspectTask::getResult, query.getResult())
                        .eq(executor != null && !executor.isBlank(), InspectTask::getExecutor, executor)
                        .ge(from != null, InspectTask::getPlanStart, from)
                        .le(to != null, InspectTask::getPlanStart, to));
    }

    public List<InspectTask> listRecent(int limit) {
        return taskMapper.selectList(Wrappers.<InspectTask>lambdaQuery()
                .orderByDesc(InspectTask::getPlanStart)
                .last("limit " + Math.max(1, Math.min(limit, 50))));
    }

    public InspectTask create(InspectTask body) {
        if (body.getName() == null || body.getName().isBlank()) {
            throw BizException.of("任务名称不能为空");
        }
        if (body.getPointId() != null) {
            InspectPoint point = pointService.require(body.getPointId());
            body.setPointName(point.getName());
        }
        if (body.getPlanStart() != null && body.getPlanEnd() != null
                && body.getPlanEnd().isBefore(body.getPlanStart())) {
            throw BizException.of("计划结束时间不能早于开始时间");
        }
        body.setId(null);
        if (body.getStatus() == null) {
            body.setStatus(InspectTask.Status.PENDING);
        }
        taskMapper.insert(body);
        return body;
    }

    public InspectTask update(Long id, InspectTask body) {
        InspectTask task = require(id);
        if (task.getStatus() == InspectTask.Status.DONE) {
            throw BizException.of("已完成的任务不可修改,如需调整请先取消任务");
        }
        if (body.getName() != null) task.setName(body.getName());
        if (body.getPointId() != null) {
            InspectPoint point = pointService.require(body.getPointId());
            task.setPointId(point.getId());
            task.setPointName(point.getName());
        }
        if (body.getExecutor() != null) task.setExecutor(body.getExecutor());
        if (body.getExecutorPhone() != null) task.setExecutorPhone(body.getExecutorPhone());
        if (body.getPlanStart() != null) task.setPlanStart(body.getPlanStart());
        if (body.getPlanEnd() != null) task.setPlanEnd(body.getPlanEnd());
        if (body.getRemark() != null) task.setRemark(body.getRemark());
        if (task.getPlanStart() != null && task.getPlanEnd() != null
                && task.getPlanEnd().isBefore(task.getPlanStart())) {
            throw BizException.of("计划结束时间不能早于开始时间");
        }
        taskMapper.updateById(task);
        return task;
    }

    public void delete(Long id) {
        require(id);
        taskMapper.deleteById(id);
    }

    /** 开始执行:PENDING / OVERDUE → RUNNING */
    public InspectTask start(Long id) {
        InspectTask task = require(id);
        if (task.getStatus() != InspectTask.Status.PENDING && task.getStatus() != InspectTask.Status.OVERDUE) {
            throw BizException.of("仅「待执行 / 已逾期」的任务可开始执行,当前状态: " + task.getStatus());
        }
        task.setStatus(InspectTask.Status.RUNNING);
        task.setActualStart(LocalDateTime.now());
        taskMapper.updateById(task);
        return task;
    }

    /**
     * 完成:必须给出巡检结论 NORMAL / ABNORMAL。
     * 接受 RUNNING 与 OVERDUE——执行中的任务超过计划结束时间会被定时任务标记为逾期,
     * 若只认 RUNNING,这些任务将永远无法收尾。
     */
    public InspectTask finish(Long id, InspectTask.Result result, String remark) {
        if (result == null) {
            throw BizException.of("请选择巡检结论(正常 / 异常)");
        }
        InspectTask task = require(id);
        if (task.getStatus() != InspectTask.Status.RUNNING && task.getStatus() != InspectTask.Status.OVERDUE) {
            throw BizException.of("仅「执行中 / 已逾期」的任务可完成,当前状态: " + task.getStatus());
        }
        if (task.getActualStart() == null) {
            // 逾期但从未开始:补一个开始时间,保证耗时统计不出现空档
            task.setActualStart(task.getPlanStart() == null ? LocalDateTime.now() : task.getPlanStart());
        }
        task.setStatus(InspectTask.Status.DONE);
        task.setResult(result);
        task.setActualEnd(LocalDateTime.now());
        if (remark != null && !remark.isBlank()) {
            task.setRemark(remark);
        }
        taskMapper.updateById(task);
        return task;
    }

    /** 取消:已完成的任务不可取消 */
    public InspectTask cancel(Long id) {
        InspectTask task = require(id);
        if (task.getStatus() == InspectTask.Status.DONE) {
            throw BizException.of("已完成的任务不可取消");
        }
        task.setStatus(InspectTask.Status.CANCELED);
        taskMapper.updateById(task);
        return task;
    }

    public InspectTask require(Long id) {
        InspectTask task = requireOrNull(id);
        if (task == null) {
            throw BizException.of("巡检任务不存在: " + id);
        }
        return task;
    }

    public InspectTask requireOrNull(Long id) {
        return taskMapper.selectById(id);
    }

    /** 每 10 分钟把超过计划结束时间仍未开始的任务标记为逾期 */
    @Scheduled(fixedDelay = 600_000, initialDelay = 30_000)
    @Transactional
    public void markOverdue() {
        int updated = taskMapper.update(null, Wrappers.<InspectTask>lambdaUpdate()
                .set(InspectTask::getStatus, InspectTask.Status.OVERDUE)
                // entity 传 null 时自动填充不生效,update_time 需显式 set
                .set(InspectTask::getUpdateTime, LocalDateTime.now())
                .in(InspectTask::getStatus, List.of(InspectTask.Status.PENDING, InspectTask.Status.RUNNING))
                .lt(InspectTask::getPlanEnd, LocalDateTime.now()));
        if (updated > 0) {
            log.info("巡检任务逾期标记: {} 条", updated);
        }
    }

    /** 今日待执行任务数(工作台) */
    public long countTodayPending() {
        LocalDateTime from = LocalDate.now().atStartOfDay();
        LocalDateTime to = LocalDate.now().atTime(LocalTime.MAX);
        return taskMapper.selectCount(Wrappers.<InspectTask>lambdaQuery()
                .in(InspectTask::getStatus, List.of(InspectTask.Status.PENDING, InspectTask.Status.RUNNING))
                .between(InspectTask::getPlanStart, from, to));
    }
}
