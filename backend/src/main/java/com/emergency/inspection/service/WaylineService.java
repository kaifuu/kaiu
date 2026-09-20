package com.emergency.inspection.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.emergency.inspection.common.BizException;
import com.emergency.inspection.entity.Wayline;
import com.emergency.inspection.mapper.WaylineMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/** 航线库:WPML 航线的平台侧管理(增删改查 + 航点序列化) */
@Service
@RequiredArgsConstructor
public class WaylineService {

    private final WaylineMapper waylineMapper;
    private final ObjectMapper objectMapper;

    public List<Wayline> listAll() {
        List<Wayline> list = waylineMapper.selectList(
                Wrappers.<Wayline>lambdaQuery().orderByDesc(Wayline::getId));
        list.forEach(this::fillCount);
        return list;
    }

    public Wayline require(Long id) {
        Wayline wayline = waylineMapper.selectById(id);
        if (wayline == null) {
            throw BizException.of("航线不存在: " + id);
        }
        fillCount(wayline);
        return wayline;
    }

    @Transactional
    public Wayline create(Wayline body, List<Map<String, Object>> waypoints) {
        validate(body, waypoints);
        body.setId(null);
        body.setCode(nextCode());
        body.setWaypointsJson(toJson(waypoints));
        waylineMapper.insert(body);
        return require(body.getId());
    }

    @Transactional
    public Wayline update(Long id, Wayline body, List<Map<String, Object>> waypoints) {
        Wayline wayline = require(id);
        if (body.getName() != null) {
            wayline.setName(body.getName());
        }
        if (body.getTemplateTypes() != null) {
            wayline.setTemplateTypes(body.getTemplateTypes());
        }
        if (body.getAlt() != null) {
            wayline.setAlt(body.getAlt());
        }
        if (body.getSpeed() != null) {
            wayline.setSpeed(body.getSpeed());
        }
        if (body.getRemark() != null) {
            wayline.setRemark(body.getRemark());
        }
        if (waypoints != null) {
            validate(wayline, waypoints);
            wayline.setWaypointsJson(toJson(waypoints));
        }
        waylineMapper.updateById(wayline);
        return require(id);
    }

    public void delete(Long id) {
        waylineMapper.deleteById(require(id).getId());
    }

    /* ==================== 校验与工具 ==================== */

    private void validate(Wayline body, List<Map<String, Object>> waypoints) {
        if (body.getName() == null || body.getName().isBlank()) {
            throw BizException.of("航线名称不能为空");
        }
        if (waypoints == null || waypoints.isEmpty()) {
            throw BizException.of("航线至少需要 1 个航点");
        }
        for (int i = 0; i < waypoints.size(); i++) {
            Map<String, Object> wp = waypoints.get(i);
            if (num(wp.get("longitude")) == null || num(wp.get("latitude")) == null) {
                throw BizException.of("第 " + (i + 1) + " 个航点的经纬度不完整");
            }
        }
    }

    private static Double num(Object value) {
        return value instanceof Number n ? n.doubleValue() : null;
    }

    /** 编码从当前最大序号顺延,跳过已被占用(删除会造成空洞)的号 */
    private String nextCode() {
        long base = waylineMapper.selectCount(Wrappers.<Wayline>lambdaQuery());
        for (long i = base + 1; i < base + 100; i++) {
            String code = String.format("WL%04d", i);
            Long exists = waylineMapper.selectCount(
                    Wrappers.<Wayline>lambdaQuery().eq(Wayline::getCode, code));
            if (exists == 0) {
                return code;
            }
        }
        return "WL" + System.currentTimeMillis();
    }

    private String toJson(List<Map<String, Object>> waypoints) {
        try {
            return objectMapper.writeValueAsString(waypoints);
        } catch (Exception e) {
            throw BizException.of("航点数据序列化失败");
        }
    }

    private void fillCount(Wayline wayline) {
        try {
            List<?> list = objectMapper.readValue(wayline.getWaypointsJson(), List.class);
            wayline.setWaypointCount(list == null ? 0 : list.size());
        } catch (Exception e) {
            wayline.setWaypointCount(0);
        }
    }

    /** 默认飞行参数(供种子数据构造) */
    public static Wayline of(String name, Wayline.TemplateTypes tpl, double alt, double speed,
                             String waypointsJson, String remark) {
        Wayline w = new Wayline();
        w.setName(name);
        w.setTemplateTypes(tpl);
        w.setAlt(BigDecimal.valueOf(alt));
        w.setSpeed(BigDecimal.valueOf(speed));
        w.setWaypointsJson(waypointsJson);
        w.setRemark(remark);
        return w;
    }
}
