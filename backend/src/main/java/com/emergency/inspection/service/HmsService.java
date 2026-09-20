package com.emergency.inspection.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.emergency.inspection.common.PageUtil;
import com.emergency.inspection.dto.query.HmsQuery;
import com.emergency.inspection.entity.DeviceHms;
import com.emergency.inspection.mapper.DeviceHmsMapper;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * HMS 健康告警:hms 事件中的 hms_list 逐条结构化落库,
 * 与 device_event 里的原始报文互补,支持按等级筛选统计。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class HmsService {

    private final DeviceHmsMapper hmsMapper;

    public IPage<DeviceHms> page(HmsQuery query) {
        return hmsMapper.selectPage(
                PageUtil.build(query, "create_time",
                        PageUtil.allowedCamel("level", "eventTime", "createTime")),
                Wrappers.<DeviceHms>lambdaQuery()
                        .eq(query.getDeviceSn() != null && !query.getDeviceSn().isBlank(),
                                DeviceHms::getDeviceSn, query.getDeviceSn())
                        .eq(query.getLevel() != null, DeviceHms::getLevel, query.getLevel()));
    }

    /** hms 事件:hms_list 数组逐条入库(兼容 list 字段名的旧形态) */
    @Transactional
    public void onHms(String sn, JsonNode data) {
        if (data == null) {
            return;
        }
        JsonNode list = data.has("hms_list") ? data.get("hms_list") : data.get("list");
        if (list == null || !list.isArray() || list.isEmpty()) {
            return;
        }
        for (JsonNode item : list) {
            if (!item.hasNonNull("code")) {
                continue;
            }
            DeviceHms row = new DeviceHms();
            row.setDeviceSn(sn);
            row.setCode(item.get("code").asText());
            row.setLevel(DeviceHms.Level.of(item.hasNonNull("level") ? item.get("level").asInt() : null));
            row.setModuleIndex(item.hasNonNull("module_index") ? item.get("module_index").asInt() : null);
            row.setMessage(item.hasNonNull("message") ? truncate(item.get("message").asText(), 250) : null);
            row.setEventTime(item.hasNonNull("ts")
                    ? toTime(item.get("ts").asLong()) : LocalDateTime.now());
            hmsMapper.insert(row);
        }
        log.info("HMS 健康告警入库: sn={} {} 条", sn, list.size());
    }

    private static LocalDateTime toTime(long epochMilli) {
        try {
            return LocalDateTime.ofInstant(Instant.ofEpochMilli(epochMilli), ZoneId.systemDefault());
        } catch (Exception e) {
            return LocalDateTime.now();
        }
    }

    private static String truncate(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max);
    }
}
