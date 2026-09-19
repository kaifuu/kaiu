# 应急巡检平台

面向应急管理场景的巡检业务平台:点位台账、周期巡检计划、任务派发与执行、隐患上报整改闭环、应急事件接报处置,
巡检服务运营(问题 → 工单 → 结案、需求、视频、飞手)与全屏指挥大屏,
以及大疆上云 API 设备接入(机场 / 无人机)与完整的 RBAC 系统管理。

技术栈与 `F:\10_WRJ` 保持同一套工程约定,持久层改为 **MyBatis-Plus + JWT**。

| 层 | 技术 |
|---|---|
| 后端 | Java 17 · Spring Boot 3.5.7 · MyBatis-Plus 3.5.9 · JJWT 0.12 · BCrypt |
| 数据库 | PostgreSQL 16 |
| 前端 | Vue 3 · Vite 5 · Element Plus 2.9 · vue-router 4 · ECharts 5 |

---

## 一、目录结构

```
02_kais/
├── docker-compose.yml            # PostgreSQL 16(首启自动建表)
├── doc/API.md                    # 接口契约(前后端唯一约定来源)
├── backend/
│   ├── pom.xml
│   ├── sql/schema.sql            # 幂等建表脚本(与容器 initdb 共用同一份)
│   └── src/main/java/com/emergency/inspection/
│       ├── common/               # ApiResponse / PageQuery / PageUtil / BizException / OpLog
│       ├── config/               # MyBatis-Plus 分页与自动填充 / Jackson / WebMvc / DataInitializer
│       ├── security/             # JWT 签发校验 / 登录拦截器 / 登录上下文
│       ├── entity/ mapper/ dto/  # 实体、Mapper、DTO 与查询对象
│       ├── gateway/mqtt/         # Netty 自研 MQTT Broker(设备接入)
│       ├── dji/                  # 大疆上云 API 协议层(主题/拓扑/遥测/指令/事件)
│       ├── service/              # 业务逻辑(含状态流转校验)
│       └── controller/           # REST 接口
└── frontend/
    └── src/
        ├── api/ router/ styles/ utils/
        └── views/                # Login / Layout / Dashboard / Screen
                                  # biz 巡检业务 · device 机场与无人机 · svc 巡检服务 · sys 系统管理
```

## 二、快速启动

### 1. 启动数据库

```bash
docker compose up -d
```

容器首启会把 `backend/sql/schema.sql` 挂进 `docker-entrypoint-initdb.d` 自动建表。
端口映射到宿主机 **5433**(避开本机可能已有的 5432)。

> 已有本地 PostgreSQL 时也可跳过容器:自行建库 `emergency_inspect`,改
> `backend/src/main/resources/application.yml` 里的 `spring.datasource` 即可。
> 建表无需手工执行——后端启动时会跑同一份 `schema.sql`(幂等)。

### 2. 启动后端

```bash
cd backend
mvn spring-boot:run
```

服务地址 `http://localhost:8181`。首次启动会自动写入演示数据(租户/组织/菜单/角色/用户 + 巡检业务样例)。

> **JDK 版本**:项目按 Java 17 编译运行。若本机 `JAVA_HOME` 指向更高版本 JDK,
> Maven 需显式指定,例如 `JAVA_HOME='E:\03_soft\Java\jdk-17' mvn spring-boot:run`。

### 3. 启动前端

```bash
cd frontend
npm install
npm run dev
```

访问 `http://localhost:5175`,`/api` 已代理到 `8181`。

### 4. 演示账号

| 账号 | 密码 | 角色 | 可见范围 |
|---|---|---|---|
| `admin` | `admin123` | 系统管理员 | 全部菜单 |
| `operator` | `operator123` | 业务操作员 | 仅业务菜单 |
| `inspector` | `inspector123` | 巡检员 | 工作台 / 巡检任务 / 隐患上报 |

新增人员与重置密码的初始密码统一为 `123456`(配置项 `user.default-password`)。

## 三、功能模块

### 系统管理

| 模块 | 能力 |
|---|---|
| 租户管理 | 分页检索、启停、删除前校验用户归属 |
| 组织管理 | 树形维护、上级链校验(防成环)、删除前校验下级与用户归属 |
| 人员管理 | 分页检索(账号/姓名/手机号/角色/组织/状态)、角色与组织分配、重置密码、内置 admin 保护 |
| 角色管理 | 分页检索、菜单树授权(ADMIN 固定全量)、删除前校验用户绑定 |
| 菜单管理 | 树形维护、图标选择、分组(业务菜单/系统管理)、路径唯一、防成环 |
| 日志管理 | 操作/登录/设备三类分页检索、类型计数、一键清空 |

### 生态环境应急巡检业务

巡检对象为生态环境要素:入河排污口、河道断面、空气自动站、饮用水源地、固废堆场、林地。

| 模块 | 能力 |
|---|---|
| 巡检点位 | 点位台账(类型/风险等级/所属网格/经纬度/责任人),编码唯一,经纬度成对校验 |
| 巡检计划 | 周期计划(日常/专项/应急 × 天/周/月/一次性)、覆盖点位多选、**一键按点位生成任务** |
| 巡检任务 | 待执行 → 执行中 → 已完成 / 已逾期 / 已取消 的状态流转,完成时必须给出巡检结论;定时任务自动标记逾期 |
| 隐患上报 | 四级隐患(一般/较大/重大/特别重大)、待处理 → 处理中 → 已整改 → 已关闭 闭环,每次流转留痕 |
| 应急事件 | 环境事件接报(水污染/大气污染/土壤污染/危化品泄漏/生态破坏 × Ⅰ-Ⅳ 级)、待响应 → 响应中 → 已处置 → 已归档,归档前必须有处置措施 |
| 工作台 | 概览指标 + 点位风险分布 / 任务状态分布 / 事件等级分布 / 近 7 日任务趋势 + 待办列表 |
| 机场管理 | 机场台账、舱体状态剖面图、运行指标、指令下发、上下线与健康事件 |
| 无人机管理 | 飞行器台账、飞行姿态罗盘、电池与定位、指令下发、事件 |

### 巡检服务(大屏与运营管理)

| 模块 | 能力 |
|---|---|
| 服务大屏 | 全屏指挥大屏:巡检概览、区域态势地图、5 个 KPI 环、AI 识别类型分布、工单部门排行、近期问题、实时画面;每 30 秒整屏刷新 |
| 问题清单 | AI 识别/人工上报的问题台账,**一键生成工单**(状态回写为「已生成工单」),支持结案 |
| 工单管理 | 待派发 → 处理中 → 已处理 → 已结案 的四段状态机,派发需指定处理人,结案同步关闭来源问题 |
| 需求管理 | 业务部门提报 → 待执行 → 已执行;逾期由定时任务自动标记 |
| 视频管理 | 通道台账(协议/分辨率/流地址)与在线状态;实际拉流需外部流媒体服务 |
| 飞手管理 | 飞手台账(执照类型/发证机构/所属片区/可调度状态) |

**核心链路:问题 → 工单 → 结案**。问题清单里点「生成工单」,问题的地点/部门/类型/坐标带进工单并回写问题状态;
工单结案时反向关闭来源问题,形成可追溯的闭环。

## 四、设备接入(大疆上云 API)

机场与无人机通过 **MQTT 直连本平台**,不需要额外部署 EMQX —— 平台内嵌了一个基于
**Netty** 的 MQTT Broker(`gateway/mqtt`,用 netty-codec-mqtt 编解码),默认监听 **1883**。

### 接入链路

```
机场(DJI Dock) ──MQTT──▶ Netty Broker ──▶ MqttMessageDispatcher ──▶ DJI 主题处理器 ──▶ 落库
                                          │
云端指令 ◀── MqttPublisher ◀──────────────┘
```

| 层 | 职责 |
|---|---|
| `gateway/mqtt` | 纯 MQTT 协议:CONNECT/PUBLISH(QoS0-1)/SUBSCRIBE/PINGREQ,通配符订阅匹配、会话管理、下行发布。**不认识任何业务语义** |
| `dji` | 大疆上云 API:主题定义与解析、报文信封、拓扑绑定、遥测、指令目录与下发、事件 |

### 支持的主题

| 主题 | 方向 | 用途 |
|---|---|---|
| `thing/product/{sn}/osd` | 上行 | 定频遥测(0.5Hz)→ 覆盖写入 `device_osd` |
| `thing/product/{sn}/state` | 上行 | 状态变化 → 记录事件 |
| `sys/product/{sn}/status` | 上行 | `update_topo` 拓扑上报,自动纳管机场与挂载的无人机 |
| `thing/product/{sn}/services_reply` | 上行 | 指令执行结果,按 `tid` 关联回 `device_command` |
| `thing/product/{sn}/events` | 上行 | HMS 健康告警、航线任务进度 → 落事件并回执 |
| `thing/product/{sn}/requests` | 上行 | 设备请求(本期回「未支持」,避免设备反复重试) |
| `thing/product/{sn}/services` | 下行 | 指令下发 |
| `thing/product/{sn}/property/set` | 下行 | 属性设置 |

### 指令闭环

平台下发指令时生成 `tid` 并**先落库再下行**,设备在 `services_reply` 中原样带回 `tid`,
据此把结果写回同一条记录(`OK` / `FAILED`);超时未回复的由定时任务结算为 `TIMEOUT`。
指令白名单按设备类型校验(机场 20 条 / 无人机 4 条),未登记的 method 不会透传到设备。

> 顺序很关键:下行必须发生在事务提交之后。设备可能在毫秒级回复,若在事务内下行,
> 回复到达时记录尚未可见,会被误判成「未知 tid」而丢结果。

### 设备模拟器(无真机验证)

`tools/dji-sim` 是一个 Node 模拟器,扮演「一台机场 + 一台挂载的无人机」,完整走设备侧协议:
连接 → 拓扑上报 → 推送遥测 → 接收指令并按 `tid` 回复。

```bash
cd tools/dji-sim
npm install
node sim.js                       # 默认 mqtt://localhost:1883
node sim.js mqtt://host:1883 DOCK-SN-001 DRONE-SN-001
```

启动后平台会自动纳管这两台设备(拓扑上报即登记),可在「机场管理」「无人机管理」页看到遥测与指令记录。

> 演示数据为北京 6 个生态环境巡检对象(潮白河入河排污口、永定河晓月湖断面、奥体中心空气自动站、
> 密云水库饮用水源地、首钢园区固废暂存场、西山国家森林公园),组织架构为市生态环境局及其下属单位。

## 五、工程约定

**统一响应**:所有接口返回 `{ code, msg, data }`,`code !== 200` 即失败,`msg` 可直接展示。
HTTP 401 表示登录失效,前端拦截器会自动清理 token 并跳登录页。

**鉴权**:`Authorization: Bearer <token>`。JWT 无状态,服务端不存会话;
`AuthInterceptor` 校验后把用户写入 `LoginContext`(ThreadLocal),请求结束即清理。
菜单级权限由 `/api/menus/mine` 按角色下发可见菜单实现,`ADMIN` 固定全量。

**分页**:列表统一 `GET .../page`,`page` 为 **1 起页码**,返回 `{ rows, total }`。
排序字段走服务端白名单(`PageUtil.allowedCamel`),非法列自动回退默认排序——
新增可排序列时需同步改 Service 里的白名单。

**操作日志**:写接口打 `@OpLog(module, action)`,`OpLogAspect` 环绕记录成功与失败,
操作人取自 `LoginContext`。

**建表**:`backend/sql/schema.sql` 全量 `IF NOT EXISTS`,由 `spring.sql.init` 每次启动执行,
同时挂给容器 initdb,一份脚本两处复用。改表结构时直接改这一份。

## 六、接口冒烟测试

`doc/smoke_test.py` 覆盖登录鉴权、六大系统管理模块、五类巡检业务、设备接入、巡检服务
(问题/工单/需求/飞手/视频)与大屏聚合的读写与状态流转,共 **214 项断言**,并在最后自动清理自己造的数据:

```bash
python doc/smoke_test.py                 # 默认 http://localhost:8181/api
python doc/smoke_test.py http://host:8181/api
```

全部通过时输出 `通过 214 项,失败 0 项` 并以退出码 0 结束,可直接接进 CI。

> 测试会真实登录:验证码字符本身就以 `<text>` 写在 `/auth/captcha` 返回的 SVG 里,
> 脚本按出现顺序读回,因此走的是与浏览器完全一致的登录链路。

## 七、前端 UI 冒烟测试

`frontend/scripts/ui_check.mjs` 用真实浏览器(系统已装的 Chrome,不额外下载)走一遍完整前端:
登录 → 侧边栏菜单下发 → 逐页渲染 → 打开表单抽屉,并把每页截图存到
`frontend/doc/screenshots/`。同时收集控制台报错、页面异常与 5xx 响应,并检查
**表格横向溢出**(表宽超出容器时右侧固定操作列会盖住「状态/结论」等尾部列,截图上看不出来)。

```bash
cd frontend
npm run dev              # 另开一个终端
npm run check:ui         # 默认 http://localhost:5175
```

## 八、配置项

| 配置 | 默认值 | 说明 |
|---|---|---|
| `server.port` | 8181 | 后端端口 |
| `jwt.secret` | 内置默认 | **生产必须用环境变量 `JWT_SECRET` 覆盖**(≥32 字节) |
| `jwt.ttl-hours` | 12 | 令牌有效期 |
| `user.default-password` | 123456 | 新增人员 / 重置密码的初始密码 |
| `spring.datasource.*` | `localhost:5433` | 数据库连接(容器映射端口为 5433) |
| `dji.mqtt.port` | 1883 | 设备接入的 MQTT 端口 |
| `dji.mqtt.idle-timeout-seconds` | 180 | 读空闲超时,超过即判设备掉线 |
| `dji.command.timeout-seconds` | 15 | 指令下发后等待回复的超时 |
| `dji.command.history-keep` | 2000 | 指令记录保留条数 |
