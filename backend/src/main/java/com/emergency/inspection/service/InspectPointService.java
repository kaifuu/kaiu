package com.emergency.inspection.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.emergency.inspection.common.BizException;
import com.emergency.inspection.common.PageUtil;
import com.emergency.inspection.dto.query.PointQuery;
import com.emergency.inspection.entity.InspectPoint;
import com.emergency.inspection.mapper.InspectPointMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;

/** 巡检点位管理 */
@Service
@RequiredArgsConstructor
public class InspectPointService {

    private final InspectPointMapper pointMapper;

    /** 全量点位(计划选点/隐患关联用) */
    public List<InspectPoint> list(String keyword, InspectPoint.Status status) {
        String kw = keyword == null ? "" : keyword.trim();
        return pointMapper.selectList(Wrappers.<InspectPoint>lambdaQuery()
                .and(!kw.isEmpty(), w -> w.like(InspectPoint::getName, kw)
                        .or().like(InspectPoint::getCode, kw)
                        .or().like(InspectPoint::getAddress, kw))
                .eq(status != null, InspectPoint::getStatus, status)
                .orderByAsc(InspectPoint::getCode));
    }

    public IPage<InspectPoint> page(PointQuery query) {
        String kw = query.getKeyword();
        String area = query.getArea();
        return pointMapper.selectPage(
                PageUtil.build(query, "id",
                        PageUtil.allowedCamel("name", "code", "category", "riskLevel", "area", "status", "createTime")),
                Wrappers.<InspectPoint>lambdaQuery()
                        .and(kw != null && !kw.isBlank(), w -> w.like(InspectPoint::getName, kw.trim())
                                .or().like(InspectPoint::getCode, kw.trim())
                                .or().like(InspectPoint::getAddress, kw.trim()))
                        .eq(query.getCategory() != null, InspectPoint::getCategory, query.getCategory())
                        .eq(query.getRiskLevel() != null, InspectPoint::getRiskLevel, query.getRiskLevel())
                        .eq(query.getStatus() != null, InspectPoint::getStatus, query.getStatus())
                        .eq(area != null && !area.isBlank(), InspectPoint::getArea, area));
    }

    public InspectPoint create(InspectPoint body) {
        validate(body);
        if (pointMapper.selectCount(Wrappers.<InspectPoint>lambdaQuery().eq(InspectPoint::getCode, body.getCode())) > 0) {
            throw BizException.of("点位编码已存在: " + body.getCode());
        }
        body.setId(null);
        applyDefaults(body);
        pointMapper.insert(body);
        return body;
    }

    public InspectPoint update(Long id, InspectPoint body) {
        InspectPoint point = require(id);
        if (body.getCode() != null && !body.getCode().equals(point.getCode())) {
            if (pointMapper.selectCount(Wrappers.<InspectPoint>lambdaQuery()
                    .eq(InspectPoint::getCode, body.getCode()).ne(InspectPoint::getId, id)) > 0) {
                throw BizException.of("点位编码已存在: " + body.getCode());
            }
            point.setCode(body.getCode());
        }
        if (body.getName() != null) point.setName(body.getName());
        if (body.getCategory() != null) point.setCategory(body.getCategory());
        if (body.getRiskLevel() != null) point.setRiskLevel(body.getRiskLevel());
        if (body.getArea() != null) point.setArea(body.getArea());
        if (body.getAddress() != null) point.setAddress(body.getAddress());
        if (body.getLongitude() != null) point.setLongitude(body.getLongitude());
        if (body.getLatitude() != null) point.setLatitude(body.getLatitude());
        if (body.getManager() != null) point.setManager(body.getManager());
        if (body.getManagerPhone() != null) point.setManagerPhone(body.getManagerPhone());
        if (body.getStatus() != null) point.setStatus(body.getStatus());
        if (body.getRemark() != null) point.setRemark(body.getRemark());
        pointMapper.updateById(point);
        return point;
    }

    public void delete(Long id) {
        require(id);
        pointMapper.deleteById(id);
    }

    public InspectPoint require(Long id) {
        InspectPoint point = pointMapper.selectById(id);
        if (point == null) {
            throw BizException.of("巡检点位不存在: " + id);
        }
        return point;
    }

    private void validate(InspectPoint body) {
        if (body.getName() == null || body.getName().isBlank()) {
            throw BizException.of("点位名称不能为空");
        }
        if (body.getCode() == null || body.getCode().isBlank()) {
            throw BizException.of("点位编码不能为空");
        }
        checkCoordinate(body.getLongitude(), body.getLatitude());
    }

    /** 经纬度范围校验:两者要么都不填,要么都填且落在中国境内可用的合法区间 */
    static void checkCoordinate(BigDecimal lng, BigDecimal lat) {
        if (lng == null && lat == null) {
            return;
        }
        if (lng == null || lat == null) {
            throw BizException.of("经度与纬度需同时填写");
        }
        if (lng.doubleValue() < -180 || lng.doubleValue() > 180) {
            throw BizException.of("经度取值范围 -180 ~ 180");
        }
        if (lat.doubleValue() < -90 || lat.doubleValue() > 90) {
            throw BizException.of("纬度取值范围 -90 ~ 90");
        }
    }

    private void applyDefaults(InspectPoint body) {
        if (body.getCategory() == null) body.setCategory(InspectPoint.Category.OTHER);
        if (body.getRiskLevel() == null) body.setRiskLevel(InspectPoint.RiskLevel.LOW);
        if (body.getStatus() == null) body.setStatus(InspectPoint.Status.ENABLED);
    }
}
