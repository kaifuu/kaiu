-- ============================================================================
-- 应急巡检平台 · 数据库结构(PostgreSQL 16)
-- 幂等脚本:全部 IF NOT EXISTS,可重复执行(Spring sql.init 每次启动执行一次,
-- 同时被 docker-entrypoint-initdb.d 挂载用于容器首启建库)
-- ============================================================================

-- ============================ 系统管理 ============================

-- ---------------- 租户 ----------------
CREATE TABLE IF NOT EXISTS sys_tenant (
    id          BIGSERIAL    PRIMARY KEY,
    name        VARCHAR(64)  NOT NULL,
    code        VARCHAR(32)  NOT NULL,
    remark      VARCHAR(255),
    enabled     BOOLEAN      NOT NULL DEFAULT TRUE,
    create_time TIMESTAMP    NOT NULL DEFAULT NOW(),
    update_time TIMESTAMP,
    CONSTRAINT uk_sys_tenant_code UNIQUE (code)
);
COMMENT ON TABLE  sys_tenant           IS '租户';
COMMENT ON COLUMN sys_tenant.code      IS '租户编码,全局唯一';

-- ---------------- 组织(树形,parent_id 平列不建外键) ----------------
CREATE TABLE IF NOT EXISTS sys_org (
    id          BIGSERIAL   PRIMARY KEY,
    name        VARCHAR(64) NOT NULL,
    parent_id   BIGINT,
    org_code    VARCHAR(32),
    sort        INTEGER     NOT NULL DEFAULT 0,
    enabled     BOOLEAN     NOT NULL DEFAULT TRUE,
    create_time TIMESTAMP   NOT NULL DEFAULT NOW(),
    update_time TIMESTAMP
);
COMMENT ON TABLE  sys_org           IS '组织(树形)';
COMMENT ON COLUMN sys_org.parent_id IS '上级组织 id;NULL = 顶级';
CREATE INDEX IF NOT EXISTS idx_sys_org_parent ON sys_org (parent_id);

-- ---------------- 角色(menu_ids_json 为授权菜单 id 数组;code=ADMIN 特判拥有全部菜单) ----------------
CREATE TABLE IF NOT EXISTS sys_role (
    id            BIGSERIAL   PRIMARY KEY,
    name          VARCHAR(32) NOT NULL,
    code          VARCHAR(32) NOT NULL,
    remark        VARCHAR(255),
    menu_ids_json TEXT        NOT NULL DEFAULT '[]',
    enabled       BOOLEAN     NOT NULL DEFAULT TRUE,
    create_time   TIMESTAMP   NOT NULL DEFAULT NOW(),
    update_time   TIMESTAMP,
    CONSTRAINT uk_sys_role_code UNIQUE (code)
);
COMMENT ON TABLE  sys_role               IS '角色';
COMMENT ON COLUMN sys_role.menu_ids_json IS '授权菜单 id 数组 JSON,如 [1,2,3]';

-- ---------------- 用户(人员) ----------------
CREATE TABLE IF NOT EXISTS sys_user (
    id            BIGSERIAL    PRIMARY KEY,
    username      VARCHAR(32)  NOT NULL,
    password      VARCHAR(100) NOT NULL,
    nickname      VARCHAR(32),
    phone         VARCHAR(20),
    role_id       BIGINT,
    org_id        BIGINT,
    tenant_id     BIGINT,
    status        VARCHAR(16)  NOT NULL DEFAULT 'ENABLED',
    last_login_at TIMESTAMP,
    create_time   TIMESTAMP    NOT NULL DEFAULT NOW(),
    update_time   TIMESTAMP,
    CONSTRAINT uk_sys_user_username UNIQUE (username),
    CONSTRAINT ck_sys_user_status CHECK (status IN ('ENABLED', 'DISABLED'))
);
COMMENT ON TABLE  sys_user          IS '系统用户(人员管理)';
COMMENT ON COLUMN sys_user.password IS 'BCrypt 密文';
CREATE INDEX IF NOT EXISTS idx_sys_user_role   ON sys_user (role_id);
CREATE INDEX IF NOT EXISTS idx_sys_user_org    ON sys_user (org_id);
CREATE INDEX IF NOT EXISTS idx_sys_user_tenant ON sys_user (tenant_id);

-- ---------------- 菜单(group 为保留字,列名用 menu_group) ----------------
CREATE TABLE IF NOT EXISTS sys_menu (
    id          BIGSERIAL   PRIMARY KEY,
    name        VARCHAR(32) NOT NULL,
    path        VARCHAR(64),
    icon        VARCHAR(32),
    menu_group  VARCHAR(8)  NOT NULL DEFAULT 'BIZ',
    parent_id   BIGINT,
    sort        INTEGER     NOT NULL DEFAULT 0,
    enabled     BOOLEAN     NOT NULL DEFAULT TRUE,
    create_time TIMESTAMP   NOT NULL DEFAULT NOW(),
    update_time TIMESTAMP,
    CONSTRAINT uk_sys_menu_path UNIQUE (path),
    CONSTRAINT ck_sys_menu_group CHECK (menu_group IN ('BIZ', 'SYS'))
);
COMMENT ON TABLE  sys_menu            IS '菜单(树形)';
COMMENT ON COLUMN sys_menu.menu_group IS 'BIZ 业务菜单 / SYS 系统管理';
CREATE INDEX IF NOT EXISTS idx_sys_menu_parent ON sys_menu (parent_id);

-- ---------------- 日志(操作/登录/设备) ----------------
CREATE TABLE IF NOT EXISTS sys_log (
    id          BIGSERIAL     PRIMARY KEY,
    type        VARCHAR(16)   NOT NULL,
    username    VARCHAR(32),
    action      VARCHAR(64),
    detail      VARCHAR(1000),
    ip          VARCHAR(64),
    success     BOOLEAN       NOT NULL DEFAULT TRUE,
    create_time TIMESTAMP     NOT NULL DEFAULT NOW(),
    update_time TIMESTAMP,
    CONSTRAINT ck_sys_log_type CHECK (type IN ('OPERATE', 'LOGIN', 'DEVICE'))
);
COMMENT ON TABLE sys_log IS '系统日志:OPERATE 操作 / LOGIN 登录 / DEVICE 设备';
CREATE INDEX IF NOT EXISTS idx_sys_log_type_time ON sys_log (type, create_time DESC);
CREATE INDEX IF NOT EXISTS idx_sys_log_user_time ON sys_log (username, create_time DESC);

-- ============================ 应急巡检业务 ============================

-- ---------------- 巡检点位 ----------------
CREATE TABLE IF NOT EXISTS inspect_point (
    id            BIGSERIAL    PRIMARY KEY,
    name          VARCHAR(64)  NOT NULL,
    code          VARCHAR(32)  NOT NULL,
    category      VARCHAR(24)  NOT NULL DEFAULT 'OTHER',
    risk_level    VARCHAR(16)  NOT NULL DEFAULT 'LOW',
    area          VARCHAR(64),
    address       VARCHAR(255),
    longitude     NUMERIC(10, 6),
    latitude      NUMERIC(10, 6),
    manager       VARCHAR(32),
    manager_phone VARCHAR(20),
    status        VARCHAR(16)  NOT NULL DEFAULT 'ENABLED',
    remark        VARCHAR(500),
    create_time   TIMESTAMP    NOT NULL DEFAULT NOW(),
    update_time   TIMESTAMP,
    CONSTRAINT uk_inspect_point_code UNIQUE (code),
    CONSTRAINT ck_inspect_point_risk CHECK (risk_level IN ('LOW', 'MEDIUM', 'HIGH', 'EXTREME')),
    CONSTRAINT ck_inspect_point_status CHECK (status IN ('ENABLED', 'DISABLED'))
);
COMMENT ON TABLE  inspect_point            IS '巡检点位';
COMMENT ON COLUMN inspect_point.category   IS 'OUTFALL 入河排污口 / RIVER 河道断面 / AIR 空气自动站 / WATER_SOURCE 饮用水源地 / SOLID_WASTE 固废堆场 / FOREST 林地 / OTHER 其他';
COMMENT ON COLUMN inspect_point.risk_level IS 'LOW 低 / MEDIUM 中 / HIGH 高 / EXTREME 极高';
CREATE INDEX IF NOT EXISTS idx_inspect_point_category ON inspect_point (category);
CREATE INDEX IF NOT EXISTS idx_inspect_point_area     ON inspect_point (area);

-- ---------------- 巡检计划 ----------------
CREATE TABLE IF NOT EXISTS inspect_plan (
    id          BIGSERIAL   PRIMARY KEY,
    name        VARCHAR(64) NOT NULL,
    code        VARCHAR(32) NOT NULL,
    category    VARCHAR(24) NOT NULL DEFAULT 'DAILY',
    cycle_type  VARCHAR(16) NOT NULL DEFAULT 'DAY',
    cycle_value INTEGER     NOT NULL DEFAULT 1,
    start_date  DATE,
    end_date    DATE,
    owner       VARCHAR(32),
    owner_phone VARCHAR(20),
    point_ids   TEXT        NOT NULL DEFAULT '[]',
    status      VARCHAR(16) NOT NULL DEFAULT 'DRAFT',
    remark      VARCHAR(500),
    create_time TIMESTAMP   NOT NULL DEFAULT NOW(),
    update_time TIMESTAMP,
    CONSTRAINT uk_inspect_plan_code UNIQUE (code),
    CONSTRAINT ck_inspect_plan_category CHECK (category IN ('DAILY', 'SPECIAL', 'EMERGENCY')),
    CONSTRAINT ck_inspect_plan_cycle    CHECK (cycle_type IN ('DAY', 'WEEK', 'MONTH', 'ONCE')),
    CONSTRAINT ck_inspect_plan_status   CHECK (status IN ('DRAFT', 'ENABLED', 'DISABLED'))
);
COMMENT ON TABLE  inspect_plan            IS '巡检计划';
COMMENT ON COLUMN inspect_plan.category   IS 'DAILY 日常巡检 / SPECIAL 专项巡检 / EMERGENCY 应急巡检';
COMMENT ON COLUMN inspect_plan.point_ids  IS '覆盖点位 id 数组 JSON';
CREATE INDEX IF NOT EXISTS idx_inspect_plan_status ON inspect_plan (status, create_time DESC);

-- ---------------- 巡检任务 ----------------
CREATE TABLE IF NOT EXISTS inspect_task (
    id             BIGSERIAL   PRIMARY KEY,
    name           VARCHAR(64) NOT NULL,
    plan_id        BIGINT,
    point_id       BIGINT,
    point_name     VARCHAR(64),
    executor       VARCHAR(32),
    executor_phone VARCHAR(20),
    plan_start     TIMESTAMP,
    plan_end       TIMESTAMP,
    actual_start   TIMESTAMP,
    actual_end     TIMESTAMP,
    status         VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    result         VARCHAR(16),
    remark         VARCHAR(500),
    create_time    TIMESTAMP   NOT NULL DEFAULT NOW(),
    update_time    TIMESTAMP,
    CONSTRAINT ck_inspect_task_status CHECK (status IN ('PENDING', 'RUNNING', 'DONE', 'OVERDUE', 'CANCELED')),
    CONSTRAINT ck_inspect_task_result CHECK (result IS NULL OR result IN ('NORMAL', 'ABNORMAL'))
);
COMMENT ON TABLE  inspect_task          IS '巡检任务';
COMMENT ON COLUMN inspect_task.status   IS 'PENDING 待执行 / RUNNING 执行中 / DONE 已完成 / OVERDUE 已逾期 / CANCELED 已取消';
COMMENT ON COLUMN inspect_task.result   IS 'NORMAL 正常 / ABNORMAL 异常;未完成为 NULL';
CREATE INDEX IF NOT EXISTS idx_inspect_task_plan   ON inspect_task (plan_id);
CREATE INDEX IF NOT EXISTS idx_inspect_task_point  ON inspect_task (point_id);
CREATE INDEX IF NOT EXISTS idx_inspect_task_status ON inspect_task (status, plan_start DESC);

-- ---------------- 隐患上报 ----------------
CREATE TABLE IF NOT EXISTS hazard (
    id            BIGSERIAL     PRIMARY KEY,
    title         VARCHAR(128)  NOT NULL,
    point_id      BIGINT,
    point_name    VARCHAR(64),
    task_id       BIGINT,
    level         VARCHAR(16)   NOT NULL DEFAULT 'GENERAL',
    description   VARCHAR(1000),
    images_json   TEXT          NOT NULL DEFAULT '[]',
    reporter      VARCHAR(32),
    report_time   TIMESTAMP     NOT NULL DEFAULT NOW(),
    status        VARCHAR(16)   NOT NULL DEFAULT 'PENDING',
    handler       VARCHAR(32),
    handle_result VARCHAR(1000),
    handle_time   TIMESTAMP,
    deadline      TIMESTAMP,
    create_time   TIMESTAMP     NOT NULL DEFAULT NOW(),
    update_time   TIMESTAMP,
    CONSTRAINT ck_hazard_level  CHECK (level IN ('GENERAL', 'MAJOR', 'SEVERE', 'CRITICAL')),
    CONSTRAINT ck_hazard_status CHECK (status IN ('PENDING', 'PROCESSING', 'RECTIFIED', 'CLOSED'))
);
COMMENT ON TABLE  hazard            IS '隐患上报';
COMMENT ON COLUMN hazard.level      IS 'GENERAL 一般 / MAJOR 较大 / SEVERE 重大 / CRITICAL 特别重大';
COMMENT ON COLUMN hazard.status     IS 'PENDING 待处理 / PROCESSING 处理中 / RECTIFIED 已整改 / CLOSED 已关闭';
COMMENT ON COLUMN hazard.images_json IS '现场图片 URL 数组 JSON';
CREATE INDEX IF NOT EXISTS idx_hazard_status ON hazard (status, report_time DESC);
CREATE INDEX IF NOT EXISTS idx_hazard_level  ON hazard (level);
CREATE INDEX IF NOT EXISTS idx_hazard_point  ON hazard (point_id);

-- ---------------- 应急事件 ----------------
CREATE TABLE IF NOT EXISTS emergency_event (
    id             BIGSERIAL    PRIMARY KEY,
    title          VARCHAR(128) NOT NULL,
    category       VARCHAR(24)  NOT NULL DEFAULT 'OTHER',
    level          VARCHAR(8)   NOT NULL DEFAULT 'IV',
    address        VARCHAR(255),
    longitude      NUMERIC(10, 6),
    latitude       NUMERIC(10, 6),
    occur_time     TIMESTAMP,
    reporter       VARCHAR(32),
    reporter_phone VARCHAR(20),
    description    VARCHAR(1000),
    status         VARCHAR(16)  NOT NULL DEFAULT 'PENDING',
    commander      VARCHAR(32),
    measure        VARCHAR(1000),
    finish_time    TIMESTAMP,
    create_time    TIMESTAMP    NOT NULL DEFAULT NOW(),
    update_time    TIMESTAMP,
    CONSTRAINT ck_event_level  CHECK (level IN ('I', 'II', 'III', 'IV')),
    CONSTRAINT ck_event_status CHECK (status IN ('PENDING', 'RESPONDING', 'HANDLED', 'ARCHIVED'))
);
COMMENT ON TABLE  emergency_event           IS '应急事件';
COMMENT ON COLUMN emergency_event.category  IS 'WATER_POLLUTION 水污染 / AIR_POLLUTION 大气污染 / SOIL_POLLUTION 土壤污染 / CHEMICAL 危化品泄漏 / ECOLOGY 生态破坏 / OTHER 其他';
COMMENT ON COLUMN emergency_event.level     IS 'I 特别重大 / II 重大 / III 较大 / IV 一般';
COMMENT ON COLUMN emergency_event.status    IS 'PENDING 待响应 / RESPONDING 响应中 / HANDLED 已处置 / ARCHIVED 已归档';
CREATE INDEX IF NOT EXISTS idx_event_status ON emergency_event (status, occur_time DESC);
CREATE INDEX IF NOT EXISTS idx_event_level  ON emergency_event (level);

-- ============================ 设备接入(大疆上云 API) ============================

-- ---------------- 设备:无人机 / 机场 ----------------
CREATE TABLE IF NOT EXISTS device (
    id               BIGSERIAL    PRIMARY KEY,
    device_sn        VARCHAR(64)  NOT NULL,
    name             VARCHAR(64)  NOT NULL,
    device_type      VARCHAR(16)  NOT NULL,
    device_model     VARCHAR(64),
    gateway_sn       VARCHAR(64),
    firmware_version VARCHAR(32),
    status           VARCHAR(16)  NOT NULL DEFAULT 'OFFLINE',
    bound_at         TIMESTAMP,
    last_online_at   TIMESTAMP,
    remark           VARCHAR(500),
    create_time      TIMESTAMP    NOT NULL DEFAULT NOW(),
    update_time      TIMESTAMP,
    CONSTRAINT uk_device_sn UNIQUE (device_sn),
    CONSTRAINT ck_device_type   CHECK (device_type IN ('DRONE', 'DOCK')),
    CONSTRAINT ck_device_status CHECK (status IN ('ONLINE', 'OFFLINE'))
);
COMMENT ON TABLE  device              IS '接入设备:无人机 / 机场';
COMMENT ON COLUMN device.device_sn    IS '设备序列号,与设备上报的 SN 一致,全局唯一';
COMMENT ON COLUMN device.device_type  IS 'DRONE 无人机 / DOCK 机场';
COMMENT ON COLUMN device.gateway_sn   IS '所属网关(机场)SN;无人机挂载到机场,机场自身为空';
COMMENT ON COLUMN device.status       IS 'ONLINE 在线 / OFFLINE 离线,由 MQTT 连接与拓扑上报共同维护';
CREATE INDEX IF NOT EXISTS idx_device_type    ON device (device_type);
CREATE INDEX IF NOT EXISTS idx_device_gateway ON device (gateway_sn);

-- ---------------- 设备最新遥测(每设备一行,覆盖更新) ----------------
CREATE TABLE IF NOT EXISTS device_osd (
    id              BIGSERIAL     PRIMARY KEY,
    device_sn       VARCHAR(64)   NOT NULL,
    osd_json        TEXT          NOT NULL DEFAULT '{}',
    mode_code       INTEGER,
    longitude       NUMERIC(10, 6),
    latitude        NUMERIC(10, 6),
    height          NUMERIC(8, 2),
    battery_percent INTEGER,
    create_time     TIMESTAMP     NOT NULL DEFAULT NOW(),
    update_time     TIMESTAMP,
    CONSTRAINT uk_device_osd_sn UNIQUE (device_sn)
);
COMMENT ON TABLE  device_osd       IS '设备最新遥测快照(原始报文存 osd_json,常用字段抽列便于查询)';
COMMENT ON COLUMN device_osd.height IS '相对起飞点高度 m(无人机)';

-- ---------------- 设备事件(HMS 告警 / 任务进度 / 上下线) ----------------
CREATE TABLE IF NOT EXISTS device_event (
    id          BIGSERIAL    PRIMARY KEY,
    device_sn   VARCHAR(64)  NOT NULL,
    event_type  VARCHAR(32)  NOT NULL,
    method      VARCHAR(64),
    level       VARCHAR(16),
    message     VARCHAR(500),
    data_json   TEXT         NOT NULL DEFAULT '{}',
    create_time TIMESTAMP    NOT NULL DEFAULT NOW(),
    update_time TIMESTAMP
);
COMMENT ON TABLE  device_event            IS '设备事件流';
COMMENT ON COLUMN device_event.event_type IS 'ONLINE/OFFLINE 上下线 / HMS 健康告警 / FLIGHTTASK 任务进度 / FILE_UPLOAD 媒体上传';
COMMENT ON COLUMN device_event.level      IS 'INFO / WARN / ERROR';
CREATE INDEX IF NOT EXISTS idx_device_event_sn ON device_event (device_sn, create_time DESC);

-- ---------------- 指令下发记录 ----------------
CREATE TABLE IF NOT EXISTS device_command (
    id           BIGSERIAL    PRIMARY KEY,
    device_sn    VARCHAR(64)  NOT NULL,
    tid          VARCHAR(64)  NOT NULL,
    method       VARCHAR(64)  NOT NULL,
    status       VARCHAR(16)  NOT NULL,
    request_json TEXT         NOT NULL DEFAULT '{}',
    reply_json   TEXT,
    sent_at      TIMESTAMP    NOT NULL DEFAULT NOW(),
    reply_at     TIMESTAMP,
    create_time  TIMESTAMP    NOT NULL DEFAULT NOW(),
    update_time  TIMESTAMP,
    CONSTRAINT uk_device_command_tid UNIQUE (tid),
    CONSTRAINT ck_device_command_status CHECK (status IN ('SENT', 'OK', 'FAILED', 'TIMEOUT'))
);
COMMENT ON TABLE  device_command        IS '云端下发指令记录,与设备 services_reply 按 tid 关联';
COMMENT ON COLUMN device_command.tid    IS '报文事务 id,云端生成,设备回复时原样带回';
COMMENT ON COLUMN device_command.status IS 'SENT 已下发 / OK 设备执行成功 / FAILED 设备拒绝或失败 / TIMEOUT 超时未回复';
CREATE INDEX IF NOT EXISTS idx_device_command_sn ON device_command (device_sn, sent_at DESC);

-- ============================ 巡检服务(大屏与运营管理) ============================

-- ---------------- 巡检问题:无人机/AI 在巡检中发现的问题,是工单的来源 ----------------
CREATE TABLE IF NOT EXISTS inspect_issue (
    id          BIGSERIAL     PRIMARY KEY,
    code        VARCHAR(32)   NOT NULL,
    title       VARCHAR(128)  NOT NULL,
    issue_type  VARCHAR(24)   NOT NULL DEFAULT 'OTHER',
    point_id    BIGINT,
    point_name  VARCHAR(64),
    address     VARCHAR(255),
    longitude   NUMERIC(10, 6),
    latitude    NUMERIC(10, 6),
    device_sn   VARCHAR(64),
    device_name VARCHAR(64),
    source      VARCHAR(16)   NOT NULL DEFAULT 'AI',
    dept        VARCHAR(64),
    description VARCHAR(500),
    images_json TEXT          NOT NULL DEFAULT '[]',
    found_at    TIMESTAMP     NOT NULL DEFAULT NOW(),
    status      VARCHAR(16)   NOT NULL DEFAULT 'PENDING',
    create_time TIMESTAMP     NOT NULL DEFAULT NOW(),
    update_time TIMESTAMP,
    CONSTRAINT uk_inspect_issue_code UNIQUE (code),
    CONSTRAINT ck_issue_status CHECK (status IN ('PENDING', 'DISPATCHED', 'WORK_ORDER', 'CLOSED'))
);
COMMENT ON TABLE  inspect_issue            IS '巡检问题清单:AI 算法识别或人工上报';
COMMENT ON COLUMN inspect_issue.issue_type IS 'ILLEGAL_BUILD 疑似违建 / GARBAGE 垃圾堆放 / FLOATING 水面漂浮物 / ILLEGAL_NET 非法围网 / OUTFALL 疑似排污口 / SLUDGE 渣土车 / CRACK 路面裂纹 / WATER_PLANT 水面植物 / CONSTRUCTION 施工堆料 / OTHER 其他';
COMMENT ON COLUMN inspect_issue.source     IS 'AI 算法识别 / MANUAL 人工上报';
COMMENT ON COLUMN inspect_issue.status     IS 'PENDING 待处理 / DISPATCHED 已推送 / WORK_ORDER 已生成工单 / CLOSED 已结案';
CREATE INDEX IF NOT EXISTS idx_issue_status_time ON inspect_issue (status, found_at DESC);
CREATE INDEX IF NOT EXISTS idx_issue_type        ON inspect_issue (issue_type);

-- ---------------- 工单:问题派发到责任部门后的处置单 ----------------
CREATE TABLE IF NOT EXISTS work_order (
    id             BIGSERIAL     PRIMARY KEY,
    code           VARCHAR(32)   NOT NULL,
    title          VARCHAR(128)  NOT NULL,
    issue_id       BIGINT,
    issue_title    VARCHAR(128),
    issue_type     VARCHAR(24),
    point_name     VARCHAR(64),
    address        VARCHAR(255),
    longitude      NUMERIC(10, 6),
    latitude       NUMERIC(10, 6),
    dept           VARCHAR(64),
    handler        VARCHAR(32),
    handler_phone  VARCHAR(20),
    handle_dept    VARCHAR(64),
    priority       VARCHAR(16)   NOT NULL DEFAULT 'NORMAL',
    status         VARCHAR(16)   NOT NULL DEFAULT 'PENDING',
    description    VARCHAR(500),
    result         VARCHAR(500),
    dispatched_at  TIMESTAMP,
    deadline       TIMESTAMP,
    finished_at    TIMESTAMP,
    create_time    TIMESTAMP     NOT NULL DEFAULT NOW(),
    update_time    TIMESTAMP,
    CONSTRAINT uk_work_order_code UNIQUE (code),
    CONSTRAINT ck_wo_priority CHECK (priority IN ('LOW', 'NORMAL', 'HIGH', 'URGENT')),
    CONSTRAINT ck_wo_status   CHECK (status IN ('PENDING', 'PROCESSING', 'HANDLED', 'CLOSED'))
);
COMMENT ON TABLE  work_order           IS '工单:由巡检问题派发而来';
COMMENT ON COLUMN work_order.priority  IS 'LOW 低 / NORMAL 一般 / HIGH 高 / URGENT 紧急';
COMMENT ON COLUMN work_order.status    IS 'PENDING 待派发 / PROCESSING 处理中 / HANDLED 已处理 / CLOSED 已结案';
CREATE INDEX IF NOT EXISTS idx_wo_status_time ON work_order (status, create_time DESC);
CREATE INDEX IF NOT EXISTS idx_wo_dept        ON work_order (dept);

-- ---------------- 需求:各业务部门提报的巡检需求 ----------------
CREATE TABLE IF NOT EXISTS inspect_demand (
    id           BIGSERIAL     PRIMARY KEY,
    code         VARCHAR(32)   NOT NULL,
    title        VARCHAR(128)  NOT NULL,
    source_dept  VARCHAR(64)   NOT NULL,
    category     VARCHAR(24)   NOT NULL DEFAULT 'DAILY',
    description  VARCHAR(500),
    expect_date  DATE,
    submitted_at TIMESTAMP     NOT NULL DEFAULT NOW(),
    status       VARCHAR(16)   NOT NULL DEFAULT 'PENDING',
    executor     VARCHAR(32),
    executed_at  TIMESTAMP,
    remark       VARCHAR(500),
    create_time  TIMESTAMP     NOT NULL DEFAULT NOW(),
    update_time  TIMESTAMP,
    CONSTRAINT uk_demand_code UNIQUE (code),
    CONSTRAINT ck_demand_status CHECK (status IN ('PENDING', 'EXECUTED', 'OVERDUE', 'CANCELED'))
);
COMMENT ON TABLE  inspect_demand             IS '巡检需求:业务部门提报';
COMMENT ON COLUMN inspect_demand.source_dept IS '需求来源部门';
COMMENT ON COLUMN inspect_demand.status      IS 'PENDING 待执行 / EXECUTED 已执行 / OVERDUE 已超时 / CANCELED 已取消';
CREATE INDEX IF NOT EXISTS idx_demand_status ON inspect_demand (status, submitted_at DESC);

-- ---------------- 飞手 ----------------
CREATE TABLE IF NOT EXISTS pilot (
    id               BIGSERIAL    PRIMARY KEY,
    name             VARCHAR(32)  NOT NULL,
    phone            VARCHAR(20),
    age              INTEGER,
    experience_years INTEGER,
    area             VARCHAR(64),
    cert_type        VARCHAR(16)  NOT NULL DEFAULT 'CAAC',
    cert_org         VARCHAR(64),
    cert_no          VARCHAR(64),
    status           VARCHAR(16)  NOT NULL DEFAULT 'AVAILABLE',
    remark           VARCHAR(500),
    create_time      TIMESTAMP    NOT NULL DEFAULT NOW(),
    update_time      TIMESTAMP,
    CONSTRAINT ck_pilot_cert   CHECK (cert_type IN ('CAAC', 'UTC', 'AOPA', 'NONE')),
    CONSTRAINT ck_pilot_status CHECK (status IN ('AVAILABLE', 'ON_TASK', 'LEAVE', 'DISABLED'))
);
COMMENT ON TABLE  pilot                  IS '飞手(无人机驾驶员)';
COMMENT ON COLUMN pilot.area             IS '所属板块 / 责任片区';
COMMENT ON COLUMN pilot.cert_type        IS 'CAAC 民航局执照 / UTC 大疆慧飞 / AOPA / NONE 无证';
COMMENT ON COLUMN pilot.status           IS 'AVAILABLE 可调度 / ON_TASK 执行中 / LEAVE 休假 / DISABLED 停用';
CREATE INDEX IF NOT EXISTS idx_pilot_status ON pilot (status);

-- ============================ 机场扩展(航线 / 固件 / 远程日志 / AI 识别) ============================

-- ---------------- 航线库(WPML 航线的简化载体:航点序列 + 飞行参数) ----------------
CREATE TABLE IF NOT EXISTS wayline (
    id             BIGSERIAL    PRIMARY KEY,
    code           VARCHAR(32)  NOT NULL,
    name           VARCHAR(64)  NOT NULL,
    template_types VARCHAR(24)  NOT NULL DEFAULT 'WAYPOINT',
    alt            NUMERIC(8, 2)  NOT NULL DEFAULT 80,
    speed          NUMERIC(8, 2)  NOT NULL DEFAULT 8,
    waypoints_json TEXT         NOT NULL DEFAULT '[]',
    remark         VARCHAR(500),
    create_time    TIMESTAMP    NOT NULL DEFAULT NOW(),
    update_time    TIMESTAMP,
    CONSTRAINT uk_wayline_code UNIQUE (code),
    CONSTRAINT ck_wayline_tpl CHECK (template_types IN ('WAYPOINT', 'POI', 'INSPECT', 'STRIP', 'SOLID'))
);
COMMENT ON TABLE  wayline                 IS '航线库:大疆 WPML 航线的平台侧管理单元';
COMMENT ON COLUMN wayline.template_types  IS 'WAYPOINT 航点 / POI 兴趣点 / INSPECT 巡检 / STRIP 航带 / SOLID 立体';
COMMENT ON COLUMN wayline.waypoints_json  IS '航点数组 JSON:[{longitude,latitude,height,speed}]';
COMMENT ON COLUMN wayline.alt             IS '默认航线高度 m';

-- ---------------- 航线飞行任务(flighttask) ----------------
CREATE TABLE IF NOT EXISTS wayline_job (
    id              BIGSERIAL    PRIMARY KEY,
    flight_id       VARCHAR(64)  NOT NULL,
    prepare_tid     VARCHAR(64),
    dock_sn         VARCHAR(64)  NOT NULL,
    drone_sn        VARCHAR(64),
    wayline_id      BIGINT,
    wayline_name    VARCHAR(64),
    job_type        VARCHAR(16)  NOT NULL DEFAULT 'IMMEDIATE',
    execute_time    TIMESTAMP,
    status          VARCHAR(16)  NOT NULL DEFAULT 'SENT',
    progress        INTEGER      NOT NULL DEFAULT 0,
    current_step    INTEGER,
    breakpoint_json TEXT,
    media_count     INTEGER      NOT NULL DEFAULT 0,
    error_msg       VARCHAR(255),
    dispatched_at   TIMESTAMP,
    begin_at        TIMESTAMP,
    end_at          TIMESTAMP,
    create_time     TIMESTAMP    NOT NULL DEFAULT NOW(),
    update_time     TIMESTAMP,
    CONSTRAINT uk_wayline_job_flight UNIQUE (flight_id),
    CONSTRAINT ck_wayline_job_type   CHECK (job_type IN ('IMMEDIATE', 'TIMED')),
    CONSTRAINT ck_wayline_job_status CHECK (status IN ('SENT', 'READY', 'QUEUED', 'RUNNING', 'SUCCESS', 'FAILED', 'CANCELED'))
);
COMMENT ON TABLE  wayline_job            IS '航线飞行任务:云端经 flighttask_prepare / execute 下发,进度由 flighttask_progress 事件驱动';
COMMENT ON COLUMN wayline_job.flight_id  IS '任务事务 id,云端生成,设备进度上报时带回';
COMMENT ON COLUMN wayline_job.status     IS 'SENT 已下发 / READY 机场就绪(定时待执行) / QUEUED 已入队 / RUNNING 执行中 / SUCCESS 成功 / FAILED 失败 / CANCELED 已取消';
COMMENT ON COLUMN wayline_job.breakpoint_json IS '断点信息 JSON:{index,progress,remain_margin};失败/取消后可断点续飞';
CREATE INDEX IF NOT EXISTS idx_wayline_job_dock   ON wayline_job (dock_sn, create_time DESC);
CREATE INDEX IF NOT EXISTS idx_wayline_job_status ON wayline_job (status);

-- ---------------- 固件库 ----------------
CREATE TABLE IF NOT EXISTS firmware (
    id           BIGSERIAL    PRIMARY KEY,
    product_type VARCHAR(16)  NOT NULL,
    device_model VARCHAR(64),
    version      VARCHAR(32)  NOT NULL,
    file_name    VARCHAR(128),
    file_size    BIGINT,
    file_md5     VARCHAR(64),
    file_url     VARCHAR(500),
    remark       VARCHAR(500),
    create_time  TIMESTAMP    NOT NULL DEFAULT NOW(),
    update_time  TIMESTAMP,
    CONSTRAINT ck_firmware_type CHECK (product_type IN ('DOCK', 'DRONE'))
);
COMMENT ON TABLE  firmware             IS '固件库:机场 / 飞行器 OTA 升级包';
COMMENT ON COLUMN firmware.product_type IS 'DOCK 机场固件 / DRONE 飞行器固件';

-- ---------------- 固件升级任务 ----------------
CREATE TABLE IF NOT EXISTS firmware_task (
    id               BIGSERIAL    PRIMARY KEY,
    firmware_id      BIGINT       NOT NULL,
    firmware_version VARCHAR(32),
    device_sn        VARCHAR(64)  NOT NULL,
    device_name      VARCHAR(64),
    status           VARCHAR(16)  NOT NULL DEFAULT 'SENT',
    progress         INTEGER      NOT NULL DEFAULT 0,
    message          VARCHAR(255),
    finished_at      TIMESTAMP,
    create_time      TIMESTAMP    NOT NULL DEFAULT NOW(),
    update_time      TIMESTAMP,
    CONSTRAINT ck_fw_task_status CHECK (status IN ('SENT', 'DOWNLOADING', 'UPGRADING', 'SUCCESS', 'FAILED'))
);
COMMENT ON TABLE  firmware_task        IS '固件升级任务:云端 ota_create 下发,ota_progress 事件驱动进度';
COMMENT ON COLUMN firmware_task.status IS 'SENT 已下发 / DOWNLOADING 下载中 / UPGRADING 升级中 / SUCCESS 成功 / FAILED 失败';
CREATE INDEX IF NOT EXISTS idx_fw_task_device ON firmware_task (device_sn, create_time DESC);
CREATE INDEX IF NOT EXISTS idx_fw_task_status ON firmware_task (status);

-- ---------------- 远程日志文件 ----------------
CREATE TABLE IF NOT EXISTS device_log_file (
    id          BIGSERIAL    PRIMARY KEY,
    device_sn   VARCHAR(64)  NOT NULL,
    file_id     VARCHAR(64)  NOT NULL,
    name        VARCHAR(255),
    module      VARCHAR(16),
    size        BIGINT,
    file_time   TIMESTAMP,
    status      VARCHAR(16)  NOT NULL DEFAULT 'FOUND',
    percent     INTEGER      NOT NULL DEFAULT 0,
    object_key  VARCHAR(500),
    create_time TIMESTAMP    NOT NULL DEFAULT NOW(),
    update_time TIMESTAMP,
    CONSTRAINT uk_device_log_file UNIQUE (device_sn, file_id),
    CONSTRAINT ck_log_file_status CHECK (status IN ('FOUND', 'UPLOADING', 'UPLOADED', 'FAILED')),
    CONSTRAINT ck_log_file_module CHECK (module IS NULL OR module IN ('DOCK', 'DRONE'))
);
COMMENT ON TABLE  device_log_file        IS '远程日志文件:logs_file_list 拉取,logs_file_upload 上传';
COMMENT ON COLUMN device_log_file.status IS 'FOUND 已发现待上传 / UPLOADING 上传中 / UPLOADED 已上传 / FAILED 上传失败';
COMMENT ON COLUMN device_log_file.module IS '日志归属:DOCK 机场本体 / DRONE 挂载飞行器';

-- ---------------- AI 目标识别配置(property/set 下发到机场) ----------------
CREATE TABLE IF NOT EXISTS device_ai_config (
    id                BIGSERIAL    PRIMARY KEY,
    device_sn         VARCHAR(64)  NOT NULL,
    enabled           BOOLEAN      NOT NULL DEFAULT FALSE,
    follow_enabled    BOOLEAN      NOT NULL DEFAULT FALSE,
    model             VARCHAR(64),
    confidence_mode   VARCHAR(16)  NOT NULL DEFAULT 'CUSTOM',
    confidence_value  INTEGER      NOT NULL DEFAULT 80,
    filter_types_json TEXT         NOT NULL DEFAULT '["PERSON","CAR","BOAT"]',
    create_time       TIMESTAMP    NOT NULL DEFAULT NOW(),
    update_time       TIMESTAMP,
    CONSTRAINT uk_device_ai_config UNIQUE (device_sn),
    CONSTRAINT ck_ai_conf_mode CHECK (confidence_mode IN ('COUNT', 'RESCUE', 'CUSTOM'))
);
COMMENT ON TABLE  device_ai_config               IS '机场 AI 目标识别配置:识别开关 / 跟随 / 置信度模式 / 目标过滤';
COMMENT ON COLUMN device_ai_config.confidence_mode IS 'COUNT 计数模式 / RESCUE 搜救模式 / CUSTOM 自定义';

-- ---------------- AI 识别目标记录 ----------------
CREATE TABLE IF NOT EXISTS device_ai_target (
    id          BIGSERIAL     PRIMARY KEY,
    device_sn   VARCHAR(64)   NOT NULL,
    target_type VARCHAR(16)   NOT NULL,
    confidence  INTEGER,
    longitude   NUMERIC(10, 6),
    latitude    NUMERIC(10, 6),
    event_time  TIMESTAMP,
    create_time TIMESTAMP     NOT NULL DEFAULT NOW(),
    update_time TIMESTAMP,
    CONSTRAINT ck_ai_target_type CHECK (target_type IN ('PERSON', 'CAR', 'BOAT'))
);
COMMENT ON TABLE device_ai_target            IS 'AI 目标识别记录:设备识别事件流';
COMMENT ON COLUMN device_ai_target.target_type IS 'PERSON 人员 / CAR 车辆 / BOAT 船只';
CREATE INDEX IF NOT EXISTS idx_ai_target_sn ON device_ai_target (device_sn, event_time DESC);

-- ---------------- 视频通道:机场/无人机的实时画面 ----------------
CREATE TABLE IF NOT EXISTS video_channel (
    id            BIGSERIAL    PRIMARY KEY,
    code          VARCHAR(32)  NOT NULL,
    name          VARCHAR(64)  NOT NULL,
    device_sn     VARCHAR(64),
    device_name   VARCHAR(64),
    channel_type  VARCHAR(16)  NOT NULL DEFAULT 'LIVE',
    protocol      VARCHAR(16)  NOT NULL DEFAULT 'FLV',
    stream_url    VARCHAR(500),
    resolution    VARCHAR(16),
    status        VARCHAR(16)  NOT NULL DEFAULT 'OFFLINE',
    last_frame_at TIMESTAMP,
    remark        VARCHAR(500),
    create_time   TIMESTAMP    NOT NULL DEFAULT NOW(),
    update_time   TIMESTAMP,
    CONSTRAINT uk_video_channel_code UNIQUE (code),
    CONSTRAINT ck_video_type   CHECK (channel_type IN ('LIVE', 'PLAYBACK')),
    CONSTRAINT ck_video_proto  CHECK (protocol IN ('RTMP', 'FLV', 'HLS', 'GB28181', 'WEBRTC')),
    CONSTRAINT ck_video_status CHECK (status IN ('ONLINE', 'OFFLINE'))
);
COMMENT ON TABLE  video_channel              IS '视频通道:机场/无人机实时画面';
COMMENT ON COLUMN video_channel.channel_type IS 'LIVE 直播 / PLAYBACK 回放';
COMMENT ON COLUMN video_channel.protocol     IS 'RTMP / FLV / HLS / GB28181 / WEBRTC';
COMMENT ON COLUMN video_channel.status       IS 'ONLINE 在线 / OFFLINE 离线';
CREATE INDEX IF NOT EXISTS idx_video_status ON video_channel (status);
