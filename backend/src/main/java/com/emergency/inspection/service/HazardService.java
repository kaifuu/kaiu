package com.emergency.inspection.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.emergency.inspection.common.BizException;
import com.emergency.inspection.common.PageUtil;
import com.emergency.inspection.dto.HandleForm;
import com.emergency.inspection.dto.query.HazardQuery;
import com.emergency.inspection.entity.Hazard;
import com.emergency.inspection.entity.InspectPoint;
import com.emergency.inspection.entity.InspectTask;
import com.emergency.inspection.mapper.HazardMapper;
import com.emergency.inspection.security.LoginContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/** 隐患上报与整改闭环 */
@Service
@RequiredArgsConstructor
public class HazardService {

    private final HazardMapper hazardMapper;
    private final InspectPointService pointService;
    private final InspectTaskService taskService;

    public IPage<Hazard> page(HazardQuery query) {
        String kw = query.getKeyword();
        return hazardMapper.selectPage(
                PageUtil.build(query, "report_time",
                        PageUtil.allowedCamel("title", "pointName", "level", "status", "reporter",
                                "reportTime", "deadline", "createTime")),
                Wrappers.<Hazard>lambdaQuery()
                        .and(kw != null && !kw.isBlank(), w -> w.like(Hazard::getTitle, kw.trim())
                                .or().like(Hazard::getPointName, kw.trim())
                                .or().like(Hazard::getReporter, kw.trim()))
                        .eq(query.getLevel() != null, Hazard::getLevel, query.getLevel())
                        .eq(query.getStatus() != null, Hazard::getStatus, query.getStatus())
                        .eq(query.getPointId() != null, Hazard::getPointId, query.getPointId())
                        .eq(query.getTaskId() != null, Hazard::getTaskId, query.getTaskId()));
    }

    /** 按任务反查隐患(任务详情页) */
    public List<Hazard> listByTask(Long taskId) {
        return hazardMapper.selectList(Wrappers.<Hazard>lambdaQuery()
                .eq(Hazard::getTaskId, taskId)
                .orderByDesc(Hazard::getReportTime));
    }

    public Hazard create(Hazard body) {
        if (body.getTitle() == null || body.getTitle().isBlank()) {
            throw BizException.of("隐患标题不能为空");
        }
        if (body.getPointId() != null) {
            InspectPoint point = pointService.require(body.getPointId());
            body.setPointName(point.getName());
        }
        if (body.getTaskId() != null) {
            InspectTask task = taskService.require(body.getTaskId());
            // 任务未指定点位时,隐患继承任务点位,保证空间归属一致
            if (body.getPointId() == null && task.getPointId() != null) {
                body.setPointId(task.getPointId());
                body.setPointName(task.getPointName());
            }
        }
        body.setId(null);
        if (body.getLevel() == null) body.setLevel(Hazard.Level.GENERAL);
        if (body.getStatus() == null) body.setStatus(Hazard.Status.PENDING);
        if (body.getImagesJson() == null || body.getImagesJson().isBlank()) body.setImagesJson("[]");
        if (body.getReportTime() == null) body.setReportTime(LocalDateTime.now());
        if (body.getReporter() == null || body.getReporter().isBlank()) {
            body.setReporter(LoginContext.getOperator());
        }
        hazardMapper.insert(body);
        return body;
    }

    public Hazard update(Long id, Hazard body) {
        Hazard hazard = require(id);
        if (hazard.getStatus() == Hazard.Status.CLOSED) {
            throw BizException.of("已关闭的隐患不可修改");
        }
        if (body.getTitle() != null) hazard.setTitle(body.getTitle());
        if (body.getLevel() != null) hazard.setLevel(body.getLevel());
        if (body.getDescription() != null) hazard.setDescription(body.getDescription());
        if (body.getImagesJson() != null) hazard.setImagesJson(body.getImagesJson());
        if (body.getDeadline() != null) hazard.setDeadline(body.getDeadline());
        if (body.getPointId() != null) {
            InspectPoint point = pointService.require(body.getPointId());
            hazard.setPointId(point.getId());
            hazard.setPointName(point.getName());
        }
        hazardMapper.updateById(hazard);
        return hazard;
    }

    /**
     * 整改流转:PROCESSING 处理中 / RECTIFIED 已整改 / CLOSED 已关闭。
     * 每次流转都要求给出处理说明,保证闭环留痕。
     */
    public Hazard handle(Long id, HandleForm form) {
        Hazard hazard = require(id);
        Hazard.Status target = parseStatus(form.getStatus());
        if (target == Hazard.Status.PENDING) {
            throw BizException.of("不能回退为待处理状态");
        }
        if (hazard.getStatus() == Hazard.Status.CLOSED) {
            throw BizException.of("隐患已关闭,无需再处理");
        }
        if (form.getContent() == null || form.getContent().isBlank()) {
            throw BizException.of("请填写处理说明");
        }
        hazard.setStatus(target);
        hazard.setHandler(form.getOperator() == null || form.getOperator().isBlank()
                ? LoginContext.getOperator() : form.getOperator());
        hazard.setHandleResult(form.getContent().trim());
        hazard.setHandleTime(LocalDateTime.now());
        hazardMapper.updateById(hazard);
        return hazard;
    }

    public void delete(Long id) {
        require(id);
        hazardMapper.deleteById(id);
    }

    public Hazard require(Long id) {
        Hazard hazard = hazardMapper.selectById(id);
        if (hazard == null) {
            throw BizException.of("隐患记录不存在: " + id);
        }
        return hazard;
    }

    private static Hazard.Status parseStatus(String status) {
        if (status == null || status.isBlank()) {
            throw BizException.of("请选择目标状态");
        }
        try {
            return Hazard.Status.valueOf(status.trim());
        } catch (IllegalArgumentException e) {
            throw BizException.of("非法的隐患状态: " + status);
        }
    }
}
