package com.emergency.inspection.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.emergency.inspection.common.BizException;
import com.emergency.inspection.common.PageUtil;
import com.emergency.inspection.dto.query.PilotQuery;
import com.emergency.inspection.entity.Pilot;
import com.emergency.inspection.mapper.PilotMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 飞手管理 */
@Service
@RequiredArgsConstructor
public class PilotService {

    private final PilotMapper pilotMapper;

    public IPage<Pilot> page(PilotQuery query) {
        String kw = query.getKeyword();
        String area = query.getArea();
        return pilotMapper.selectPage(
                PageUtil.build(query, "id",
                        PageUtil.allowedCamel("name", "area", "certType", "status",
                                "experienceYears", "createTime")),
                Wrappers.<Pilot>lambdaQuery()
                        .and(kw != null && !kw.isBlank(), w -> w.like(Pilot::getName, kw.trim())
                                .or().like(Pilot::getPhone, kw.trim())
                                .or().like(Pilot::getArea, kw.trim()))
                        .eq(query.getStatus() != null, Pilot::getStatus, query.getStatus())
                        .eq(query.getCertType() != null, Pilot::getCertType, query.getCertType())
                        .eq(area != null && !area.isBlank(), Pilot::getArea, area));
    }

    public List<Pilot> listAll() {
        return pilotMapper.selectList(Wrappers.<Pilot>lambdaQuery().orderByAsc(Pilot::getId));
    }

    public Pilot require(Long id) {
        Pilot pilot = pilotMapper.selectById(id);
        if (pilot == null) {
            throw BizException.of("飞手不存在: " + id);
        }
        return pilot;
    }

    public Pilot create(Pilot body) {
        if (body.getName() == null || body.getName().isBlank()) {
            throw BizException.of("飞手姓名不能为空");
        }
        body.setId(null);
        if (body.getCertType() == null) body.setCertType(Pilot.CertType.CAAC);
        if (body.getStatus() == null) body.setStatus(Pilot.Status.AVAILABLE);
        pilotMapper.insert(body);
        return body;
    }

    public Pilot update(Long id, Pilot body) {
        Pilot pilot = require(id);
        if (body.getName() != null) pilot.setName(body.getName());
        if (body.getPhone() != null) pilot.setPhone(body.getPhone());
        if (body.getAge() != null) pilot.setAge(body.getAge());
        if (body.getExperienceYears() != null) pilot.setExperienceYears(body.getExperienceYears());
        if (body.getArea() != null) pilot.setArea(body.getArea());
        if (body.getCertType() != null) pilot.setCertType(body.getCertType());
        if (body.getCertOrg() != null) pilot.setCertOrg(body.getCertOrg());
        if (body.getCertNo() != null) pilot.setCertNo(body.getCertNo());
        if (body.getStatus() != null) pilot.setStatus(body.getStatus());
        if (body.getRemark() != null) pilot.setRemark(body.getRemark());
        pilotMapper.updateById(pilot);
        return pilot;
    }

    public void delete(Long id) {
        require(id);
        pilotMapper.deleteById(id);
    }

    public Map<String, Long> countByStatus() {
        Map<String, Long> out = new LinkedHashMap<>();
        for (Pilot.Status s : Pilot.Status.values()) {
            out.put(s.name(), pilotMapper.selectCount(Wrappers.<Pilot>lambdaQuery().eq(Pilot::getStatus, s)));
        }
        return out;
    }
}
