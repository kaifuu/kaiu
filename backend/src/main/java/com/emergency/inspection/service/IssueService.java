package com.emergency.inspection.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.emergency.inspection.common.BizException;
import com.emergency.inspection.common.PageUtil;
import com.emergency.inspection.dto.query.IssueQuery;
import com.emergency.inspection.entity.InspectIssue;
import com.emergency.inspection.entity.InspectPoint;
import com.emergency.inspection.mapper.InspectIssueMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 巡检问题清单 */
@Service
@RequiredArgsConstructor
public class IssueService {

    private final InspectIssueMapper issueMapper;
    private final InspectPointService pointService;

    public IPage<InspectIssue> page(IssueQuery query) {
        String kw = query.getKeyword();
        String dept = query.getDept();
        return issueMapper.selectPage(
                PageUtil.build(query, "found_at",
                        PageUtil.allowedCamel("title", "issueType", "pointName", "deviceName",
                                "dept", "source", "status", "foundAt", "createTime")),
                Wrappers.<InspectIssue>lambdaQuery()
                        .and(kw != null && !kw.isBlank(), w -> w.like(InspectIssue::getTitle, kw.trim())
                                .or().like(InspectIssue::getPointName, kw.trim())
                                .or().like(InspectIssue::getDeviceName, kw.trim())
                                .or().like(InspectIssue::getAddress, kw.trim()))
                        .eq(query.getIssueType() != null, InspectIssue::getIssueType, query.getIssueType())
                        .eq(query.getStatus() != null, InspectIssue::getStatus, query.getStatus())
                        .eq(query.getSource() != null, InspectIssue::getSource, query.getSource())
                        .eq(dept != null && !dept.isBlank(), InspectIssue::getDept, dept));
    }

    public List<InspectIssue> recent(int limit) {
        return issueMapper.selectList(Wrappers.<InspectIssue>lambdaQuery()
                .orderByDesc(InspectIssue::getFoundAt)
                .last("limit " + Math.max(1, Math.min(limit, 100))));
    }

    public InspectIssue require(Long id) {
        InspectIssue issue = issueMapper.selectById(id);
        if (issue == null) {
            throw BizException.of("巡检问题不存在: " + id);
        }
        return issue;
    }

    public InspectIssue create(InspectIssue body) {
        if (body.getTitle() == null || body.getTitle().isBlank()) {
            throw BizException.of("问题标题不能为空");
        }
        if (body.getCode() == null || body.getCode().isBlank()) {
            body.setCode(nextCode());
        } else if (existsCode(body.getCode(), null)) {
            throw BizException.of("问题编码已存在: " + body.getCode());
        }
        if (body.getPointId() != null) {
            InspectPoint point = pointService.require(body.getPointId());
            body.setPointName(point.getName());
            if (body.getAddress() == null) {
                body.setAddress(point.getAddress());
            }
            if (body.getLongitude() == null) {
                body.setLongitude(point.getLongitude());
                body.setLatitude(point.getLatitude());
            }
        }
        body.setId(null);
        if (body.getIssueType() == null) body.setIssueType(InspectIssue.IssueType.OTHER);
        if (body.getSource() == null) body.setSource(InspectIssue.Source.AI);
        if (body.getStatus() == null) body.setStatus(InspectIssue.Status.PENDING);
        if (body.getFoundAt() == null) body.setFoundAt(LocalDateTime.now());
        if (body.getImagesJson() == null || body.getImagesJson().isBlank()) body.setImagesJson("[]");
        issueMapper.insert(body);
        return body;
    }

    public InspectIssue update(Long id, InspectIssue body) {
        InspectIssue issue = require(id);
        if (issue.getStatus() == InspectIssue.Status.CLOSED) {
            throw BizException.of("已结案的问题不可修改");
        }
        if (body.getTitle() != null) issue.setTitle(body.getTitle());
        if (body.getIssueType() != null) issue.setIssueType(body.getIssueType());
        if (body.getDept() != null) issue.setDept(body.getDept());
        if (body.getDescription() != null) issue.setDescription(body.getDescription());
        if (body.getAddress() != null) issue.setAddress(body.getAddress());
        if (body.getDeviceSn() != null) issue.setDeviceSn(body.getDeviceSn());
        if (body.getDeviceName() != null) issue.setDeviceName(body.getDeviceName());
        if (body.getImagesJson() != null) issue.setImagesJson(body.getImagesJson());
        if (body.getFoundAt() != null) issue.setFoundAt(body.getFoundAt());
        issueMapper.updateById(issue);
        return issue;
    }

    /** 结案:已生成工单的问题不允许直接结案,须由工单结案后回写 */
    public InspectIssue close(Long id) {
        InspectIssue issue = require(id);
        if (issue.getStatus() == InspectIssue.Status.WORK_ORDER) {
            throw BizException.of("该问题已生成工单,请在工单中结案");
        }
        issue.setStatus(InspectIssue.Status.CLOSED);
        issueMapper.updateById(issue);
        return issue;
    }

    public void delete(Long id) {
        require(id);
        issueMapper.deleteById(id);
    }

    /** 问题转工单后回写状态与关联 */
    public void markWorkOrder(Long id, Long workOrderId) {
        updateStatus(id, InspectIssue.Status.WORK_ORDER);
    }

    /**
     * 工单结案后回写问题状态。
     *
     * 刻意不走 {@link #update} —— 那里有「已结案不可修改」的守卫,
     * 而 MyBatis 一级缓存会让同一事务内的 selectById 返回**同一个实例**:
     * 调用方先 setStatus(CLOSED) 再进来,守卫读到的就是已被改过的状态,必然误拒。
     * 这里用条件更新直接落库,不读回对象,绕开缓存与守卫。
     */
    public void closeByWorkOrder(Long id) {
        updateStatus(id, InspectIssue.Status.CLOSED);
    }

    private void updateStatus(Long id, InspectIssue.Status status) {
        issueMapper.update(null, Wrappers.<InspectIssue>lambdaUpdate()
                .set(InspectIssue::getStatus, status)
                .set(InspectIssue::getUpdateTime, LocalDateTime.now())
                .eq(InspectIssue::getId, id));
    }

    /** 按问题类型统计(大屏「算法识别类型」) */
    public Map<String, Long> countByType() {
        Map<String, Long> out = new LinkedHashMap<>();
        for (InspectIssue.IssueType type : InspectIssue.IssueType.values()) {
            long n = issueMapper.selectCount(Wrappers.<InspectIssue>lambdaQuery()
                    .eq(InspectIssue::getIssueType, type));
            if (n > 0) {
                out.put(type.name(), n);
            }
        }
        return out;
    }

    private boolean existsCode(String code, Long excludeId) {
        return issueMapper.selectCount(Wrappers.<InspectIssue>lambdaQuery()
                .eq(InspectIssue::getCode, code)
                .ne(excludeId != null, InspectIssue::getId, excludeId)) > 0;
    }

    private String nextCode() {
        long n = issueMapper.selectCount(Wrappers.<InspectIssue>lambdaQuery()) + 1;
        return String.format("IS%06d", n);
    }
}
