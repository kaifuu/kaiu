package com.emergency.inspection.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * 存量库结构迁移。
 *
 * 必须独立于 DataInitializer 且**不带事务**:PostgreSQL 的 DDL 是事务性的,
 * 若放在 DataInitializer 的 @Transactional 里,一旦后续种子数据插入失败,
 * 整个事务回滚会连 ALTER 一起撤销,表现为「日志说改成功了但库里没变」。
 *
 * HIGHEST_PRECEDENCE:约束必须先于菜单种子插入,否则 SVC 分组会被旧约束拒绝。
 */
@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
@RequiredArgsConstructor
public class SchemaMigration implements ApplicationRunner {

    private final JdbcTemplate jdbc;

    @Override
    public void run(ApplicationArguments args) {
        // 菜单分组新增 SVC:建表语句是 IF NOT EXISTS,存量库的 CHECK 仍只允许 BIZ/SYS。
        // 放在 Java 而不是 schema.sql:Spring 的脚本执行器按 `;` 切分语句,
        // 而 PostgreSQL 的 DO $$ ... $$ 块内部含分号,会被切碎。
        tryExec("ALTER TABLE sys_menu DROP CONSTRAINT IF EXISTS ck_sys_menu_group",
                "已移除旧的菜单分组约束");
        tryExec("ALTER TABLE sys_menu ADD CONSTRAINT ck_sys_menu_group "
                        + "CHECK (menu_group IN ('BIZ', 'SVC', 'SYS'))",
                "菜单分组约束已放行 SVC");

        // 设备台账字段:device 表是 CREATE TABLE IF NOT EXISTS,存量库拿不到新列,逐列补齐。
        // ADD COLUMN IF NOT EXISTS 本身幂等,重复启动不会报错。
        migrateDeviceLedger();
    }

    /** 设备台账:规格与归属字段(参考 10_WRJ 的设备台账模型) */
    private void migrateDeviceLedger() {
        tryExec("ALTER TABLE device ADD COLUMN IF NOT EXISTS manufacturer VARCHAR(64)",
                "device.manufacturer");
        tryExec("ALTER TABLE device ADD COLUMN IF NOT EXISTS usage VARCHAR(32)",
                "device.usage");
        tryExec("ALTER TABLE device ADD COLUMN IF NOT EXISTS home_lng NUMERIC(10,6)",
                "device.home_lng");
        tryExec("ALTER TABLE device ADD COLUMN IF NOT EXISTS home_lat NUMERIC(10,6)",
                "device.home_lat");
        tryExec("ALTER TABLE device ADD COLUMN IF NOT EXISTS max_altitude NUMERIC(6,1)",
                "device.max_altitude");
        tryExec("ALTER TABLE device ADD COLUMN IF NOT EXISTS max_endurance NUMERIC(5,1)",
                "device.max_endurance");
        tryExec("ALTER TABLE device ADD COLUMN IF NOT EXISTS pilot_id BIGINT",
                "device.pilot_id");
        tryExec("ALTER TABLE device ADD COLUMN IF NOT EXISTS enabled BOOLEAN NOT NULL DEFAULT TRUE",
                "device.enabled");
        tryExec("ALTER TABLE device ADD COLUMN IF NOT EXISTS virtual BOOLEAN NOT NULL DEFAULT FALSE",
                "device.virtual");
        // 自定义图标存 dataURL(参考实现限 200KB,base64 后约 273KB),必须 TEXT
        tryExec("ALTER TABLE device ADD COLUMN IF NOT EXISTS icon TEXT",
                "device.icon");

        // 存量行回填:新列对老数据是 NULL,而代码按 '' 语义处理 icon
        tryExec("UPDATE device SET icon = '' WHERE icon IS NULL",
                "device.icon 回填空串");
        // 模拟器接入的设备(SN 含 SIM)标记为虚拟设备,便于台账上与真机区分
        tryExec("UPDATE device SET virtual = TRUE WHERE virtual = FALSE AND UPPER(device_sn) LIKE '%SIM%'",
                "device.virtual 回填模拟器设备");
        // 本平台走大疆上云 API,存量设备厂商统一回填
        tryExec("UPDATE device SET manufacturer = '大疆' WHERE manufacturer IS NULL",
                "device.manufacturer 回填");
        // 部署/归航坐标缺省用厂区锚点(与演示数据的厂区中心一致)
        tryExec("UPDATE device SET home_lng = 116.1836, home_lat = 39.9132 "
                        + "WHERE home_lng IS NULL OR home_lat IS NULL",
                "device 坐标回填厂区锚点");
        // 飞行器规格缺省值(DJI M3D 量级:500m 航高 / 55min 续航)
        tryExec("UPDATE device SET usage = '巡检' WHERE device_type = 'DRONE' AND usage IS NULL",
                "device.usage 回填");
        tryExec("UPDATE device SET max_altitude = 500, max_endurance = 55 "
                        + "WHERE device_type = 'DRONE' AND max_altitude IS NULL",
                "device 航高/续航回填");
        // 虚拟(模拟器)飞行器绑定一台飞手,便于演示台账的归属展示;真机不自动绑定
        tryExec("UPDATE device SET pilot_id = (SELECT id FROM pilot ORDER BY id LIMIT 1) "
                        + "WHERE device_type = 'DRONE' AND virtual = TRUE AND pilot_id IS NULL "
                        + "AND EXISTS (SELECT 1 FROM pilot)",
                "device.pilot_id 回填(仅虚拟设备)");
    }

    private void tryExec(String sql, String okMsg) {
        try {
            jdbc.execute(sql);
            log.info("SchemaMigration: {}", okMsg);
        } catch (Exception e) {
            log.warn("SchemaMigration 失败(非致命): {} -> {}", sql, e.getMessage());
        }
    }
}
