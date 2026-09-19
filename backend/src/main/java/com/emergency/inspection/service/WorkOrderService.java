package com.emergency.inspection.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.emergency.inspection.common.BizException;
import com.emergency.inspection.common.PageUtil;
import com.emergency.inspection.dto.query.WorkOrderQuery;
import com.emergency.inspection.entity.InspectIssue;
import com.emergency.inspection.entity.WorkOrder;
import com.emergency.inspection.mapper.WorkOrderMapper;
import com.emergency.inspection.security.LoginContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/** 工单:巡检问题的派发与处置闭环 */
@Service
@RequiredArgsConstructor
public class WorkOrderService {

    private final WorkOrderMapper orderMapper;
    private final IssueService issueService;

    public IPage<WorkOrder> page(WorkOrderQuery query) {
        String kw = query.getKeyword();
        String dept = query.getDept();
        String handler = query.getHandler();
        return orderMapper.selectPage(
                PageUtil.build(query, "create_time",
                        PageUtil.allowedCamel("code", "title", "pointName", "dept", "handler",
                                "priority", "status", "dispatchedAt", "deadline", "createTime")),
                Wrappers.<WorkOrder>lambdaQuery()
                        .and(kw != null && !kw.isBlank(), w -> w.like(WorkOrder::getTitle, kw.trim())
                                .or().like(WorkOrder::getCode, kw.trim())
                                .or().like(WorkOrder::getPointName, kw.trim())
                                .or().like(WorkOrder::getHandler, kw.trim()))
                        .eq(query.getStatus() != null, WorkOrder::getStatus, query.getStatus())
                        .eq(query.getPriority() != null, WorkOrder::getPriority, query.getPriority())
                        .eq(dept != null && !dept.isBlank(), WorkOrder::getDept, dept)
                        .eq(handler != null && !handler.isBlank(), WorkOrder::getHandler, handler));
    }

    /**
     * 由巡检问题生成工单:把问题的地点/部门/类型带过来,
     * 并回写问题状态,形成「问题 → 工单」的追溯链。
     */
    @Transactional
    public WorkOrder createFromIssue(Long issueId, WorkOrder form) {
        InspectIssue issue = issueService.require(issueId);
        if (issue.getStatus() == InspectIssue.Status.CLOSED) {
            throw BizException.of("该问题已结案,无法再生成工单");
        }
        if (issue.getStatus() == InspectIssue.Status.WORK_ORDER) {
            throw BizException.of("该问题已生成过工单,请勿重复派发");
        }

        WorkOrder order = new WorkOrder();
        order.setCode(nextCode());
        order.setTitle(form != null && form.getTitle() != null && !form.getTitle().isBlank()
                ? form.getTitle() : issue.getTitle());
        order.setIssueId(issue.getId());
        order.setIssueTitle(issue.getTitle());
        order.setIssueType(issue.getIssueType() == null ? null : issue.getIssueType().name());
        order.setPointName(issue.getPointName());
        order.setAddress(issue.getAddress());
        order.setLongitude(issue.getLongitude());
        order.setLatitude(issue.getLatitude());
        order.setDept(issue.getDept());
        order.setDescription(issue.getDescription());
        order.setPriority(form != null && form.getPriority() != null
                ? form.getPriority() : WorkOrder.Priority.NORMAL);
        order.setHandler(form == null ? null : form.getHandler());
        order.setHandlerPhone(form == null ? null : form.getHandlerPhone());
        order.setHandleDept(form == null ? null : form.getHandleDept());
        order.setDeadline(form == null ? null : form.getDeadline());
        // 已指定处理人视为已派发,否则停在待派发
        if (order.getHandler() != null && !order.getHandler().isBlank()) {
            order.setStatus(WorkOrder.Status.PROCESSING);
            order.setDispatchedAt(LocalDateTime.now());
        } else {
            order.setStatus(WorkOrder.Status.PENDING);
        }
        orderMapper.insert(order);
        issueService.markWorkOrder(issueId, order.getId());
        return order;
    }

    public WorkOrder create(WorkOrder body) {
        if (body.getTitle() == null || body.getTitle().isBlank()) {
            throw BizException.of("工单标题不能为空");
        }
        body.setId(null);
        if (body.getCode() == null || body.getCode().isBlank()) {
            body.setCode(nextCode());
        }
        if (body.getPriority() == null) body.setPriority(WorkOrder.Priority.NORMAL);
        if (body.getStatus() == null) body.setStatus(WorkOrder.Status.PENDING);
        orderMapper.insert(body);
        return body;
    }

    public WorkOrder update(Long id, WorkOrder body) {
        WorkOrder order = require(id);
        if (order.getStatus() == WorkOrder.Status.CLOSED) {
            throw BizException.of("已结案的工单不可修改");
        }
        if (body.getTitle() != null) order.setTitle(body.getTitle());
        if (body.getPriority() != null) order.setPriority(body.getPriority());
        if (body.getDept() != null) order.setDept(body.getDept());
        if (body.getHandleDept() != null) order.setHandleDept(body.getHandleDept());
        if (body.getDescription() != null) order.setDescription(body.getDescription());
        if (body.getDeadline() != null) order.setDeadline(body.getDeadline());
        if (body.getHandler() != null) order.setHandler(body.getHandler());
        if (body.getHandlerPhone() != null) order.setHandlerPhone(body.getHandlerPhone());
        orderMapper.updateById(order);
        return order;
    }

    /** 派发:指定处理人与处理部门,状态进入处理中 */
    public WorkOrder dispatch(Long id, String handler, String handlerPhone, String handleDept,
                              LocalDateTime deadline) {
        WorkOrder order = require(id);
        if (order.getStatus() != WorkOrder.Status.PENDING) {
            throw BizException.of("仅「待派发」的工单可派发,当前状态: " + order.getStatus());
        }
        if (handler == null || handler.isBlank()) {
            throw BizException.of("请选择处理人");
        }
        order.setHandler(handler);
        order.setHandlerPhone(handlerPhone);
        order.setHandleDept(handleDept);
        order.setDeadline(deadline);
        order.setStatus(WorkOrder.Status.PROCESSING);
        order.setDispatchedAt(LocalDateTime.now());
        orderMapper.updateById(order);
        return order;
    }

    /** 处理:填写处理结果,状态进入已处理 */
    public WorkOrder handle(Long id, String result) {
        WorkOrder order = require(id);
        if (order.getStatus() != WorkOrder.Status.PROCESSING) {
            throw BizException.of("仅「处理中」的工单可处理,当前状态: " + order.getStatus());
        }
        if (result == null || result.isBlank()) {
            throw BizException.of("请填写处理结果");
        }
        order.setResult(result.trim());
        order.setStatus(WorkOrder.Status.HANDLED);
        order.setFinishedAt(LocalDateTime.now());
        orderMapper.updateById(order);
        return order;
    }

    /** 结案:已处理的工单才能结案,并同步关闭来源问题 */
    @Transactional
    public WorkOrder close(Long id) {
        WorkOrder order = require(id);
        if (order.getStatus() == WorkOrder.Status.CLOSED) {
            throw BizException.of("工单已结案");
        }
        if (order.getStatus() != WorkOrder.Status.HANDLED) {
            throw BizException.of("仅「已处理」的工单可结案,当前状态: " + order.getStatus());
        }
        order.setStatus(WorkOrder.Status.CLOSED);
        if (order.getFinishedAt() == null) {
            order.setFinishedAt(LocalDateTime.now());
        }
        orderMapper.updateById(order);
        if (order.getIssueId() != null) {
            issueService.closeByWorkOrder(order.getIssueId());
        }
        return order;
    }

    public void delete(Long id) {
        require(id);
        orderMapper.deleteById(id);
    }

    public WorkOrder require(Long id) {
        WorkOrder order = orderMapper.selectById(id);
        if (order == null) {
            throw BizException.of("工单不存在: " + id);
        }
        return order;
    }

    /** 按状态计数(大屏工单卡片) */
    public Map<String, Long> countByStatus() {
        Map<String, Long> out = new LinkedHashMap<>();
        for (WorkOrder.Status s : WorkOrder.Status.values()) {
            out.put(s.name(), orderMapper.selectCount(
                    Wrappers.<WorkOrder>lambdaQuery().eq(WorkOrder::getStatus, s)));
        }
        return out;
    }

    /** 按需求部门统计工单量(大屏部门排行) */
    public Map<String, Long> countByDept() {
        Map<String, Long> out = new LinkedHashMap<>();
        orderMapper.selectList(Wrappers.<WorkOrder>lambdaQuery().select(WorkOrder::getDept))
                .stream().map(WorkOrder::getDept).filter(d -> d != null && !d.isBlank())
                .distinct().sorted()
                .forEach(dept -> out.put(dept, orderMapper.selectCount(
                        Wrappers.<WorkOrder>lambdaQuery().eq(WorkOrder::getDept, dept))));
        return out;
    }

    private String nextCode() {
        long n = orderMapper.selectCount(Wrappers.<WorkOrder>lambdaQuery()) + 1;
        return String.format("WO%s%04d",
                LocalDateTime.now().toLocalDate().toString().replace("-", ""), n);
    }

    /** 当前登录人,派发时若未指定处理人可作默认 */
    public String currentOperator() {
        return LoginContext.getOperator();
    }
}
