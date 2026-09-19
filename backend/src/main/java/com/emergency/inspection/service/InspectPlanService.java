package com.emergency.inspection.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.emergency.inspection.common.BizException;
import com.emergency.inspection.common.PageUtil;
import com.emergency.inspection.dto.query.PlanQuery;
import com.emergency.inspection.entity.InspectPlan;
import com.emergency.inspection.entity.InspectPoint;
import com.emergency.inspection.entity.InspectTask;
import com.emergency.inspection.mapper.InspectPlanMapper;
import com.emergency.inspection.mapper.InspectTaskMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

/** 巡检计划管理 */
@Service
@RequiredArgsConstructor
public class InspectPlanService {

    private final InspectPlanMapper planMapper;
    private final InspectTaskMapper taskMapper;
    private final InspectPointService pointService;
    private final ObjectMapper objectMapper;

    public IPage<InspectPlan> page(PlanQuery query) {
        String kw = query.getKeyword();
        String owner = query.getOwner();
        return planMapper.selectPage(
                PageUtil.build(query, "id",
                        PageUtil.allowedCamel("name", "code", "category", "status", "startDate", "createTime")),
                Wrappers.<InspectPlan>lambdaQuery()
                        .and(kw != null && !kw.isBlank(), w -> w.like(InspectPlan::getName, kw.trim())
                                .or().like(InspectPlan::getCode, kw.trim())
                                .or().like(InspectPlan::getOwner, kw.trim()))
                        .eq(query.getCategory() != null, InspectPlan::getCategory, query.getCategory())
                        .eq(query.getStatus() != null, InspectPlan::getStatus, query.getStatus())
                        .eq(owner != null && !owner.isBlank(), InspectPlan::getOwner, owner));
    }

    public List<InspectPlan> listEnabled() {
        return planMapper.selectList(Wrappers.<InspectPlan>lambdaQuery()
                .eq(InspectPlan::getStatus, InspectPlan.Status.ENABLED)
                .orderByAsc(InspectPlan::getCode));
    }

    public InspectPlan create(InspectPlan body) {
        validate(body);
        if (planMapper.selectCount(Wrappers.<InspectPlan>lambdaQuery().eq(InspectPlan::getCode, body.getCode())) > 0) {
            throw BizException.of("计划编码已存在: " + body.getCode());
        }
        body.setId(null);
        applyDefaults(body);
        planMapper.insert(body);
        return body;
    }

    public InspectPlan update(Long id, InspectPlan body) {
        InspectPlan plan = require(id);
        if (body.getName() != null) plan.setName(body.getName());
        if (body.getCategory() != null) plan.setCategory(body.getCategory());
        if (body.getCycleType() != null) plan.setCycleType(body.getCycleType());
        if (body.getCycleValue() != null) plan.setCycleValue(body.getCycleValue());
        if (body.getStartDate() != null) plan.setStartDate(body.getStartDate());
        if (body.getEndDate() != null) plan.setEndDate(body.getEndDate());
        if (body.getOwner() != null) plan.setOwner(body.getOwner());
        if (body.getOwnerPhone() != null) plan.setOwnerPhone(body.getOwnerPhone());
        if (body.getPointIds() != null) plan.setPointIds(body.getPointIds());
        if (body.getStatus() != null) plan.setStatus(body.getStatus());
        if (body.getRemark() != null) plan.setRemark(body.getRemark());
        checkDateRange(plan.getStartDate(), plan.getEndDate());
        planMapper.updateById(plan);
        return plan;
    }

    public void delete(Long id) {
        require(id);
        Long tasks = taskMapper.selectCount(Wrappers.<InspectTask>lambdaQuery().eq(InspectTask::getPlanId, id));
        if (tasks != null && tasks > 0) {
            throw BizException.of("该计划下已有 " + tasks + " 条巡检任务,先删除任务再删除计划");
        }
        planMapper.deleteById(id);
    }

    /**
     * 按计划覆盖的点位生成一批待执行任务。
     * 执行窗口取计划的起止日期(缺省为当天),每个点位一条任务。
     */
    @Transactional
    public int generateTasks(Long planId) {
        InspectPlan plan = require(planId);
        if (plan.getStatus() != InspectPlan.Status.ENABLED) {
            throw BizException.of("仅「已启用」的计划可生成任务");
        }
        List<Long> pointIds = parsePointIds(plan.getPointIds());
        if (pointIds.isEmpty()) {
            throw BizException.of("计划未覆盖任何点位,先编辑计划选择点位");
        }
        LocalDate start = plan.getStartDate() == null ? LocalDate.now() : plan.getStartDate();
        LocalDate end = plan.getEndDate() == null ? start : plan.getEndDate();
        LocalDateTime planStart = start.atTime(LocalTime.of(8, 0));
        LocalDateTime planEnd = end.atTime(LocalTime.of(18, 0));

        int created = 0;
        for (Long pointId : pointIds) {
            InspectPoint point = pointService.require(pointId);
            InspectTask task = new InspectTask();
            task.setName(plan.getName() + " - " + point.getName());
            task.setPlanId(plan.getId());
            task.setPointId(point.getId());
            task.setPointName(point.getName());
            task.setExecutor(plan.getOwner());
            task.setExecutorPhone(plan.getOwnerPhone());
            task.setPlanStart(planStart);
            task.setPlanEnd(planEnd);
            task.setStatus(InspectTask.Status.PENDING);
            taskMapper.insert(task);
            created++;
        }
        return created;
    }

    public InspectPlan require(Long id) {
        InspectPlan plan = planMapper.selectById(id);
        if (plan == null) {
            throw BizException.of("巡检计划不存在: " + id);
        }
        return plan;
    }

    /** 解析覆盖点位 id;格式非法按空处理,由调用方给出「未覆盖点位」提示 */
    public List<Long> parsePointIds(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json,
                    objectMapper.getTypeFactory().constructCollectionType(List.class, Long.class));
        } catch (Exception e) {
            return List.of();
        }
    }

    private void validate(InspectPlan body) {
        if (body.getName() == null || body.getName().isBlank()) {
            throw BizException.of("计划名称不能为空");
        }
        if (body.getCode() == null || body.getCode().isBlank()) {
            throw BizException.of("计划编码不能为空");
        }
        checkDateRange(body.getStartDate(), body.getEndDate());
    }

    private static void checkDateRange(LocalDate start, LocalDate end) {
        if (start != null && end != null && end.isBefore(start)) {
            throw BizException.of("结束日期不能早于开始日期");
        }
    }

    private void applyDefaults(InspectPlan body) {
        if (body.getCategory() == null) body.setCategory(InspectPlan.Category.DAILY);
        if (body.getCycleType() == null) body.setCycleType(InspectPlan.CycleType.DAY);
        if (body.getCycleValue() == null || body.getCycleValue() < 1) body.setCycleValue(1);
        if (body.getStatus() == null) body.setStatus(InspectPlan.Status.DRAFT);
        if (body.getPointIds() == null || body.getPointIds().isBlank()) body.setPointIds("[]");
    }
}
