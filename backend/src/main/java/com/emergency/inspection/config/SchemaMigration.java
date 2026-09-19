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
