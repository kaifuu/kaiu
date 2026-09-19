package com.emergency.inspection.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.emergency.inspection.common.PageUtil;
import com.emergency.inspection.dto.query.LogQuery;
import com.emergency.inspection.entity.SysLog;
import com.emergency.inspection.mapper.SysLogMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;

/** 日志管理:操作/登录/设备 三类,分页查询 */
@Service
@RequiredArgsConstructor
public class SysLogService {

    private final SysLogMapper logMapper;

    public IPage<SysLog> page(LogQuery query) {
        String kw = query.getKeyword();
        return logMapper.selectPage(
                PageUtil.build(query, "create_time",
                        PageUtil.allowedCamel("type", "username", "action", "ip", "success", "createTime")),
                Wrappers.<SysLog>lambdaQuery()
                        .eq(query.getType() != null, SysLog::getType, query.getType())
                        .eq(query.getSuccess() != null, SysLog::getSuccess, query.getSuccess())
                        .and(kw != null && !kw.isBlank(), w -> w.like(SysLog::getUsername, kw.trim())
                                .or().like(SysLog::getAction, kw.trim())
                                .or().like(SysLog::getDetail, kw.trim())));
    }

    /** 各类日志条数(页签徽标) */
    public Map<String, Long> count() {
        Map<String, Long> data = new LinkedHashMap<>();
        for (SysLog.Type type : SysLog.Type.values()) {
            data.put(type.name(), logMapper.selectCount(Wrappers.<SysLog>lambdaQuery().eq(SysLog::getType, type)));
        }
        return data;
    }

    /** 一键清空全部日志;返回删除条数 */
    public long clear() {
        long removed = logMapper.selectCount(Wrappers.<SysLog>lambdaQuery());
        logMapper.delete(Wrappers.<SysLog>lambdaQuery());
        return removed;
    }

    /** 直写一条日志(登录成功/失败等非切面场景) */
    public void record(SysLog.Type type, String username, String action, String detail, String ip, boolean success) {
        logMapper.insert(new SysLog(type, username, action, detail, ip, success));
    }
}
