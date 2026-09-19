package com.emergency.inspection.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.emergency.inspection.common.BizException;
import com.emergency.inspection.common.PageUtil;
import com.emergency.inspection.dto.query.DemandQuery;
import com.emergency.inspection.entity.InspectDemand;
import com.emergency.inspection.mapper.InspectDemandMapper;
import com.emergency.inspection.security.LoginContext;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 巡检需求:业务部门提报 → 待执行 → 已执行 */
@Service
@RequiredArgsConstructor
public class DemandService {

    private final InspectDemandMapper demandMapper;

    public IPage<InspectDemand> page(DemandQuery query) {
        String kw = query.getKeyword();
        String dept = query.getSourceDept();
        String category = query.getCategory();
        return demandMapper.selectPage(
                PageUtil.build(query, "submitted_at",
                        PageUtil.allowedCamel("title", "sourceDept", "category", "status",
                                "expectDate", "submittedAt", "createTime")),
                Wrappers.<InspectDemand>lambdaQuery()
                        .and(kw != null && !kw.isBlank(), w -> w.like(InspectDemand::getTitle, kw.trim())
                                .or().like(InspectDemand::getCode, kw.trim())
                                .or().like(InspectDemand::getSourceDept, kw.trim()))
                        .eq(query.getStatus() != null, InspectDemand::getStatus, query.getStatus())
                        .eq(dept != null && !dept.isBlank(), InspectDemand::getSourceDept, dept)
                        .eq(category != null && !category.isBlank(), InspectDemand::getCategory, category));
    }

    public InspectDemand require(Long id) {
        InspectDemand demand = demandMapper.selectById(id);
        if (demand == null) {
            throw BizException.of("需求不存在: " + id);
        }
        return demand;
    }

    @Transactional
    public InspectDemand create(InspectDemand body) {
        if (body.getTitle() == null || body.getTitle().isBlank()) {
            throw BizException.of("需求名称不能为空");
        }
        if (body.getSourceDept() == null || body.getSourceDept().isBlank()) {
            throw BizException.of("请选择需求来源部门");
        }
        body.setId(null);
        if (body.getCode() == null || body.getCode().isBlank()) {
            body.setCode(nextCode());
        }
        if (body.getStatus() == null) body.setStatus(InspectDemand.Status.PENDING);
        if (body.getSubmittedAt() == null) body.setSubmittedAt(LocalDateTime.now());
        if (body.getCategory() == null) body.setCategory("DAILY");
        demandMapper.insert(body);
        return body;
    }

    public InspectDemand update(Long id, InspectDemand body) {
        InspectDemand demand = require(id);
        if (demand.getStatus() == InspectDemand.Status.EXECUTED) {
            throw BizException.of("已执行的需求不可修改");
        }
        if (body.getTitle() != null) demand.setTitle(body.getTitle());
        if (body.getSourceDept() != null) demand.setSourceDept(body.getSourceDept());
        if (body.getCategory() != null) demand.setCategory(body.getCategory());
        if (body.getDescription() != null) demand.setDescription(body.getDescription());
        if (body.getExpectDate() != null) demand.setExpectDate(body.getExpectDate());
        if (body.getRemark() != null) demand.setRemark(body.getRemark());
        demandMapper.updateById(demand);
        return demand;
    }

    /** 执行:登记执行人与执行时间 */
    public InspectDemand execute(Long id, String executor) {
        InspectDemand demand = require(id);
        if (demand.getStatus() == InspectDemand.Status.EXECUTED) {
            throw BizException.of("需求已执行");
        }
        if (demand.getStatus() == InspectDemand.Status.CANCELED) {
            throw BizException.of("已取消的需求不能执行");
        }
        demand.setStatus(InspectDemand.Status.EXECUTED);
        demand.setExecutor(executor == null || executor.isBlank() ? LoginContext.getOperator() : executor);
        demand.setExecutedAt(LocalDateTime.now());
        demandMapper.updateById(demand);
        return demand;
    }

    public InspectDemand cancel(Long id, String remark) {
        InspectDemand demand = require(id);
        if (demand.getStatus() == InspectDemand.Status.EXECUTED) {
            throw BizException.of("已执行的需求不能取消");
        }
        demand.setStatus(InspectDemand.Status.CANCELED);
        if (remark != null && !remark.isBlank()) {
            demand.setRemark(remark);
        }
        demandMapper.updateById(demand);
        return demand;
    }

    public void delete(Long id) {
        require(id);
        demandMapper.deleteById(id);
    }

    /**
     * 逾期标记:期望执行日期已过且仍未执行的,置为已超时。
     * 每小时扫一次,避免大屏「已超时」长期不更新。
     */
    @Scheduled(fixedDelay = 3600_000, initialDelay = 30_000)
    @Transactional
    public void markOverdue() {
        demandMapper.update(null, Wrappers.<InspectDemand>lambdaUpdate()
                .set(InspectDemand::getStatus, InspectDemand.Status.OVERDUE)
                .set(InspectDemand::getUpdateTime, LocalDateTime.now())
                .eq(InspectDemand::getStatus, InspectDemand.Status.PENDING)
                .isNotNull(InspectDemand::getExpectDate)
                .lt(InspectDemand::getExpectDate, LocalDate.now()));
    }

    public Map<String, Long> countByStatus() {
        Map<String, Long> out = new LinkedHashMap<>();
        for (InspectDemand.Status s : InspectDemand.Status.values()) {
            out.put(s.name(), demandMapper.selectCount(
                    Wrappers.<InspectDemand>lambdaQuery().eq(InspectDemand::getStatus, s)));
        }
        return out;
    }

    public List<InspectDemand> recent(int limit) {
        return demandMapper.selectList(Wrappers.<InspectDemand>lambdaQuery()
                .orderByDesc(InspectDemand::getSubmittedAt)
                .last("limit " + Math.max(1, Math.min(limit, 100))));
    }

    private String nextCode() {
        long n = demandMapper.selectCount(Wrappers.<InspectDemand>lambdaQuery()) + 1;
        return String.format("DM%06d", n);
    }
}
