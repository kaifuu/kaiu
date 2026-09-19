package com.emergency.inspection.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.emergency.inspection.common.BizException;
import com.emergency.inspection.common.PageUtil;
import com.emergency.inspection.dto.HandleForm;
import com.emergency.inspection.dto.query.EventQuery;
import com.emergency.inspection.entity.EmergencyEvent;
import com.emergency.inspection.mapper.EmergencyEventMapper;
import com.emergency.inspection.security.LoginContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/** 应急事件接报、响应与归档 */
@Service
@RequiredArgsConstructor
public class EmergencyEventService {

    /** 未归档事件的状态集合(工作台「进行中事件」口径) */
    private static final List<EmergencyEvent.Status> ACTIVE_STATUS =
            List.of(EmergencyEvent.Status.PENDING, EmergencyEvent.Status.RESPONDING);

    private final EmergencyEventMapper eventMapper;

    public IPage<EmergencyEvent> page(EventQuery query) {
        String kw = query.getKeyword();
        return eventMapper.selectPage(
                PageUtil.build(query, "occur_time",
                        PageUtil.allowedCamel("title", "category", "level", "status", "occurTime", "createTime")),
                Wrappers.<EmergencyEvent>lambdaQuery()
                        .and(kw != null && !kw.isBlank(), w -> w.like(EmergencyEvent::getTitle, kw.trim())
                                .or().like(EmergencyEvent::getAddress, kw.trim())
                                .or().like(EmergencyEvent::getReporter, kw.trim()))
                        .eq(query.getCategory() != null, EmergencyEvent::getCategory, query.getCategory())
                        .eq(query.getLevel() != null, EmergencyEvent::getLevel, query.getLevel())
                        .eq(query.getStatus() != null, EmergencyEvent::getStatus, query.getStatus()));
    }

    public List<EmergencyEvent> listActive(int limit) {
        return eventMapper.selectList(Wrappers.<EmergencyEvent>lambdaQuery()
                .in(EmergencyEvent::getStatus, ACTIVE_STATUS)
                .orderByDesc(EmergencyEvent::getOccurTime)
                .last("limit " + Math.max(1, Math.min(limit, 50))));
    }

    public EmergencyEvent create(EmergencyEvent body) {
        if (body.getTitle() == null || body.getTitle().isBlank()) {
            throw BizException.of("事件标题不能为空");
        }
        checkCoordinate(body);
        body.setId(null);
        if (body.getCategory() == null) body.setCategory(EmergencyEvent.Category.OTHER);
        if (body.getLevel() == null) body.setLevel(EmergencyEvent.Level.IV);
        if (body.getStatus() == null) body.setStatus(EmergencyEvent.Status.PENDING);
        if (body.getOccurTime() == null) body.setOccurTime(LocalDateTime.now());
        if (body.getReporter() == null || body.getReporter().isBlank()) {
            body.setReporter(LoginContext.getOperator());
        }
        eventMapper.insert(body);
        return body;
    }

    public EmergencyEvent update(Long id, EmergencyEvent body) {
        EmergencyEvent event = require(id);
        if (event.getStatus() == EmergencyEvent.Status.ARCHIVED) {
            throw BizException.of("已归档事件不可修改");
        }
        if (body.getTitle() != null) event.setTitle(body.getTitle());
        if (body.getCategory() != null) event.setCategory(body.getCategory());
        if (body.getLevel() != null) event.setLevel(body.getLevel());
        if (body.getAddress() != null) event.setAddress(body.getAddress());
        if (body.getLongitude() != null) event.setLongitude(body.getLongitude());
        if (body.getLatitude() != null) event.setLatitude(body.getLatitude());
        if (body.getOccurTime() != null) event.setOccurTime(body.getOccurTime());
        if (body.getReporter() != null) event.setReporter(body.getReporter());
        if (body.getReporterPhone() != null) event.setReporterPhone(body.getReporterPhone());
        if (body.getDescription() != null) event.setDescription(body.getDescription());
        checkCoordinate(event);
        eventMapper.updateById(event);
        return event;
    }

    /**
     * 处置流转:RESPONDING 响应中 / HANDLED 已处置 / ARCHIVED 已归档。
     * 归档要求先有处置措施,避免空档。
     */
    public EmergencyEvent handle(Long id, HandleForm form) {
        EmergencyEvent event = require(id);
        EmergencyEvent.Status target = parseStatus(form.getStatus());
        if (event.getStatus() == EmergencyEvent.Status.ARCHIVED) {
            throw BizException.of("事件已归档,不可再流转");
        }
        if (form.getContent() == null || form.getContent().isBlank()) {
            throw BizException.of("请填写处置措施");
        }
        event.setStatus(target);
        event.setCommander(form.getOperator() == null || form.getOperator().isBlank()
                ? LoginContext.getOperator() : form.getOperator());
        event.setMeasure(form.getContent().trim());
        if (target == EmergencyEvent.Status.HANDLED || target == EmergencyEvent.Status.ARCHIVED) {
            event.setFinishTime(LocalDateTime.now());
        }
        eventMapper.updateById(event);
        return event;
    }

    public void delete(Long id) {
        EmergencyEvent event = require(id);
        if (event.getStatus() == EmergencyEvent.Status.RESPONDING) {
            throw BizException.of("响应中的事件不可删除,先处置或归档");
        }
        eventMapper.deleteById(id);
    }

    public EmergencyEvent require(Long id) {
        EmergencyEvent event = eventMapper.selectById(id);
        if (event == null) {
            throw BizException.of("应急事件不存在: " + id);
        }
        return event;
    }

    private static void checkCoordinate(EmergencyEvent event) {
        InspectPointService.checkCoordinate(event.getLongitude(), event.getLatitude());
    }

    private static EmergencyEvent.Status parseStatus(String status) {
        if (status == null || status.isBlank()) {
            throw BizException.of("请选择目标状态");
        }
        try {
            return EmergencyEvent.Status.valueOf(status.trim());
        } catch (IllegalArgumentException e) {
            throw BizException.of("非法的事件状态: " + status);
        }
    }
}
