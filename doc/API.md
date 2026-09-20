# 应急巡检平台 · 接口契约

后端 `http://localhost:8181`,前端 Vite 开发端口 `5175`,`/api` 走代理。

## 统一约定

**响应包装**(所有接口):

```json
{ "code": 200, "msg": "ok", "data": ... }
```

`code !== 200` 即失败,`msg` 为可直接展示的中文原因。HTTP 401 表示登录失效。

**鉴权**:除 `/api/auth/captcha`、`/api/auth/login` 外,所有 `/api/**` 需带
`Authorization: Bearer <token>`。

**分页**:列表接口统一 `GET .../page`,入参 `page`(1 起)、`size`、`sortBy`(实体属性名)、
`direction`(`asc`/`desc`)、`keyword`(模糊检索)及各自筛选字段。返回:

```json
{ "code": 200, "data": { "rows": [...], "total": 123 } }
```

`sortBy` 走服务端白名单,非法值回退默认排序,不会报错。

**时间格式**:`LocalDateTime` → `"yyyy-MM-dd HH:mm:ss"`,`LocalDate` → `"yyyy-MM-dd"`,
前端直接展示,无需裁剪。

**枚举**:一律传字符串名称(如 `"ENABLED"`),前后端一致。

---

## 1. 认证 `/api/auth`

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/captcha` | 免登录。返回 `{ cid, svg }`,`svg` 为 SVG 源码字符串,前端用 `data:image/svg+xml;utf8,` + `encodeURIComponent(svg)` 渲染 |
| POST | `/login` | 免登录。入参 `{ username, password, cid, captcha }` |
| POST | `/logout` | — |
| GET | `/profile` | 返回 `{ id, username, nickname, phone, roleCode, roleName, orgId, lastLoginAt, menus }` |
| PUT | `/profile` | 入参 `{ nickname, phone }` |
| POST | `/profile/password` | 入参 `{ oldPassword, newPassword }` |

`/login` 成功返回:

```json
{
  "token": "...", "username": "admin", "nickname": "系统管理员", "roleCode": "ADMIN",
  "menus": [{ "id": 1, "name": "工作台", "path": "/dashboard", "icon": "Monitor", "group": "BIZ", "sort": 1 }]
}
```

前端需把 `token`/`nickname`/`roleCode`/`menus` 存 localStorage。

## 2. 菜单 `/api/menus`

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/mine` | 当前用户可见菜单(扁平 `MenuDto` 数组) |
| GET | `` | 全量菜单(扁平 `SysMenu` 数组) |
| GET | `/tree` | 菜单树(`MenuNode`:`{ id, name, path, icon, group, parentId, sort, enabled, children }`) |
| POST | `` | 新增菜单 |
| PUT | `/{id}` | 修改菜单 |
| DELETE | `/{id}` | 删除(有下级时 400) |

`SysMenu` 字段:`{ id, name, path, icon, menuGroup, parentId, sort, enabled, createTime, updateTime }`。
注意 JSON 里字段名是 **`menuGroup`**(值 `BIZ`/`SYS`),而 `/mine` 与 `/tree` 里简化为 **`group`**。

`parentId` 传 `0` 表示顶级(修改时显式清空上级的唯一通道);服务端存 `null`。

## 3. 租户 `/api/tenants`

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `` | 全量租户 |
| GET | `/page` | 筛选:`keyword`(名称/编码)、`enabled` |
| POST / PUT `/{id}` / DELETE `/{id}` | | |

字段:`{ id, name, code, remark, enabled, createTime, updateTime }`。`code` 唯一且不可改。

## 4. 组织 `/api/orgs`

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `` | 组织树,节点 `{ id, name, parentId, orgCode, sort, enabled, children }` |
| GET | `/list` | 全量扁平 |
| POST / PUT `/{id}` / DELETE `/{id}` | | 删除时有下级或有用户归属则 400 |

## 5. 人员 `/api/users`

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `` | 全量人员,支持 `keyword` |
| GET | `/page` | 筛选:`keyword`(账号/姓名/手机号)、`roleId`、`orgId`、`tenantId`、`status` |
| POST | `` | 新增,入参 `SysUserForm` |
| PUT | `/{id}` | 修改,入参 `SysUserForm` |
| DELETE | `/{id}` | `admin` 不可删 |
| POST | `/{id}/reset-password` | 返回 `{ password: "123456" }` |

**新增/修改入参**(`SysUserForm`,角色用平铺 `roleId`,不要传嵌套对象):

```json
{ "username": "zhangsan", "nickname": "张三", "phone": "13800000000",
  "roleId": 2, "orgId": 1, "tenantId": 1, "status": "ENABLED" }
```

**列表返回**行对象(`SysUser` + 联表回填,无 `password`):

```json
{ "id": 1, "username": "admin", "nickname": "系统管理员", "phone": "13800000001",
  "roleId": 1, "roleName": "系统管理员", "roleCode": "ADMIN",
  "orgId": 1, "orgName": "市应急管理局", "tenantId": 1, "tenantName": "默认租户",
  "status": "ENABLED", "lastLoginAt": "2026-09-18 10:20:30", "createTime": "..." }
```

初始密码由服务端统一设定为 `123456`。

## 6. 角色 `/api/roles`

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `` | 全量角色 |
| GET | `/page` | 筛选:`keyword`(名称/编码)、`enabled` |
| POST / PUT `/{id}` / DELETE `/{id}` | | `ADMIN` 角色不可删;有用户绑定不可删 |

字段:`{ id, name, code, remark, menuIdsJson, enabled, createTime, updateTime }`。
`menuIdsJson` 是**字符串形式**的 JSON 数组,如 `"[1,2,3]"`;前端提交前用
`JSON.stringify(勾选的菜单id数组)` 生成。

## 7. 日志 `/api/logs`

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `` | 筛选:`type`(`OPERATE`/`LOGIN`/`DEVICE`)、`keyword`(账号/动作/详情)、`success` |
| GET | `/count` | 返回 `{ OPERATE: n, LOGIN: n, DEVICE: n }` |
| DELETE | `` | 清空全部日志,返回删除条数 |

字段:`{ id, type, username, action, detail, ip, success, createTime }`。

## 8. 工作台 `/api/dashboard`

`GET /api/dashboard/stats` 返回:

```json
{
  "pointTotal": 6, "planEnabled": 2, "taskToday": 6,
  "taskPending": 2, "taskRunning": 1, "taskOverdue": 1,
  "hazardPending": 1, "hazardProcessing": 1,
  "eventActive": 1, "eventTotal": 3,
  "pointByRisk":  { "LOW": 1, "MEDIUM": 2, "HIGH": 2, "EXTREME": 1 },
  "taskByStatus": { "PENDING": 2, "RUNNING": 1, "DONE": 2, "OVERDUE": 1, "CANCELED": 0 },
  "eventByLevel": { "I": 0, "II": 0, "III": 1, "IV": 2 },
  "taskTrend": [ { "date": "2026-09-12", "done": 3, "total": 5 } ],
  "recentTasks": [...], "activeEvents": [...], "recentHazards": [...]
}
```

## 9. 巡检点位(生态环境巡检对象)`/api/points`

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `` | 全量点位,支持 `keyword`、`status` |
| GET | `/page` | 筛选:`keyword`(名称/编码/地址)、`category`、`riskLevel`、`status`、`area` |
| GET | `/{id}` | 详情 |
| POST / PUT `/{id}` / DELETE `/{id}` | | |

字段:`{ id, name, code, category, riskLevel, area, address, longitude, latitude,
manager, managerPhone, status, remark, createTime, updateTime }`

- `category`:`OUTFALL` 入河排污口 / `RIVER` 河道断面 / `AIR` 空气自动站 / `WATER_SOURCE` 饮用水源地 / `SOLID_WASTE` 固废堆场 / `FOREST` 林地 / `OTHER` 其他
- `riskLevel`:`LOW` 低 / `MEDIUM` 中 / `HIGH` 高 / `EXTREME` 极高
- `status`:`ENABLED` / `DISABLED`
- `code` 唯一;经纬度需同时填写

## 10. 巡检计划 `/api/plans`

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `` | 已启用计划 |
| GET | `/page` | 筛选:`keyword`(名称/编码/负责人)、`category`、`status`、`owner` |
| GET | `/{id}` | 详情 |
| POST / PUT `/{id}` / DELETE `/{id}` | | 有任务时不可删 |
| POST | `/{id}/generate-tasks` | 按覆盖点位生成待执行任务,返回 `{ created: n }` |

字段:`{ id, name, code, category, cycleType, cycleValue, startDate, endDate,
owner, ownerPhone, pointIds, status, remark, ... }`

- `category`:`DAILY` 日常 / `SPECIAL` 专项 / `EMERGENCY` 应急
- `cycleType`:`DAY` / `WEEK` / `MONTH` / `ONCE`
- `status`:`DRAFT` 草稿 / `ENABLED` 已启用 / `DISABLED` 已停用
- `pointIds`:字符串形式的 JSON 数组,如 `"[1,2,3]"`(与 `menuIdsJson` 同款约定)
- 仅 `ENABLED` 计划可生成任务;`generate-tasks` 失败时返回 400 与中文原因

## 11. 巡检任务 `/api/tasks`

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/page` | 筛选:`keyword`(任务名/点位名/执行人)、`planId`、`pointId`、`status`、`result`、`executor`、`startDate`、`endDate`(按计划开始日期过滤) |
| GET | `/recent?limit=10` | 最近任务 |
| GET | `/{id}` | 详情 |
| GET | `/{id}/hazards` | 该任务下已上报的隐患 |
| POST / PUT `/{id}` / DELETE `/{id}` | | 已完成任务不可修改 |
| POST | `/{id}/start` | 开始执行 |
| POST | `/{id}/finish` | 入参 `{ result: "NORMAL"\|"ABNORMAL", remark }` |
| POST | `/{id}/cancel` | 取消 |

字段:`{ id, name, planId, pointId, pointName, executor, executorPhone,
planStart, planEnd, actualStart, actualEnd, status, result, remark, ... }`

- `status`:`PENDING` 待执行 / `RUNNING` 执行中 / `DONE` 已完成 / `OVERDUE` 已逾期 / `CANCELED` 已取消
- `result`:`NORMAL` 正常 / `ABNORMAL` 异常,未完成为 `null`

## 12. 隐患上报 `/api/hazards`

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/page` | 筛选:`keyword`(标题/点位名/上报人)、`level`、`status`、`pointId`、`taskId` |
| GET | `/{id}` | 详情 |
| POST / PUT `/{id}` / DELETE `/{id}` | | 已关闭不可修改 |
| POST | `/{id}/handle` | 入参 `{ operator, content, status }` |

字段:`{ id, title, pointId, pointName, taskId, level, description, imagesJson,
reporter, reportTime, status, handler, handleResult, handleTime, deadline, ... }`

- `level`:`GENERAL` 一般 / `MAJOR` 较大 / `SEVERE` 重大 / `CRITICAL` 特别重大
- `status`:`PENDING` 待处理 / `PROCESSING` 处理中 / `RECTIFIED` 已整改 / `CLOSED` 已关闭
- `handle` 要求 `content` 必填;目标状态不能是 `PENDING`

## 13. 应急事件 `/api/events`

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/page` | 筛选:`keyword`(标题/地点/上报人)、`category`、`level`、`status` |
| GET | `/active?limit=10` | 进行中事件 |
| GET | `/{id}` | 详情 |
| POST / PUT `/{id}` / DELETE `/{id}` | | 已归档不可修改;响应中不可删除 |
| POST | `/{id}/handle` | 入参 `{ operator, content, status }` |

字段:`{ id, title, category, level, address, longitude, latitude, occurTime,
reporter, reporterPhone, description, status, commander, measure, finishTime, ... }`

- `category`:`WATER_POLLUTION` 水污染 / `AIR_POLLUTION` 大气污染 / `SOIL_POLLUTION` 土壤污染 / `CHEMICAL` 危化品泄漏 / `ECOLOGY` 生态破坏 / `OTHER` 其他
- `level`:`I` 特别重大 / `II` 重大 / `III` 较大 / `IV` 一般
- `status`:`PENDING` 待响应 / `RESPONDING` 响应中 / `HANDLED` 已处置 / `ARCHIVED` 已归档
- `handle` 要求 `content` 必填;`HANDLED`/`ARCHIVED` 会写 `finishTime`

---

## 演示账号

| 账号 | 密码 | 角色 | 可见范围 |
|---|---|---|---|
| `admin` | `admin123` | 系统管理员 | 全部菜单 |
| `operator` | `operator123` | 业务操作员 | 仅业务菜单 |
| `inspector` | `inspector123` | 巡检员 | 工作台 / 巡检任务 / 隐患上报 |

---

## 14. 设备管理(机场 / 无人机) `/api/devices`

对接大疆上云 API。机场与无人机通过 MQTT 直连平台内嵌的 Netty Broker(默认 1883),
设备的上下线、遥测、指令回复都由设备侧主动上报驱动,因此本模块的多数字段是**只读的运行态**。

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/page` | 筛选:`keyword`(名称/序列号/机型)、`deviceType`、`status`、`gatewaySn` |
| GET | `` | 全量设备 |
| GET | `/docks` | 全量机场(机场管理模块) |
| GET | `/drones` | 全量无人机(无人机管理模块) |
| GET | `/{id}` | 详情 |
| POST / PUT `/{id}` / DELETE `/{id}` | | 台账维护;无人机必须绑定机场,机场有挂载时不可删 |
| GET | `/{id}/osd` | 最新遥测快照(原始行) |
| GET | `/{id}/telemetry` | **结构化遥测**:按设备类型归一化,控制页直接渲染 |
| GET | `/{id}/events?limit=30` | 设备事件(最近 N 条) |
| GET | `/{id}/events/page` | **事件分页**:筛选 `eventType`、`level`、`method`、`keyword`(说明/方法名) |
| GET | `/{id}/commands?limit=20` | 指令下发记录 |
| GET | `/{id}/services` | 该设备类型可下发的指令目录 |
| POST | `/{id}/commands` | 下发指令,入参 `{ method, data? }` |
| GET | `/commands/page` | 指令记录分页:`deviceSn`、`method`、`status`、`startDate`/`endDate`(按下发时间) |
| GET | `/stats` | 设备统计 + MQTT 在线连接数 |

**设备字段**:

```json
{ "id": 2, "deviceSn": "DOCK-SIM-0001", "name": "机场 …SIM-0001",
  "deviceType": "DOCK", "deviceModel": "DJI Dock 2", "gatewaySn": null,
  "gatewayName": null, "subDeviceCount": 1, "firmwareVersion": "1.0.0",
  "status": "ONLINE", "boundAt": "...", "lastOnlineAt": "...", "remark": "..." }
```

- `deviceType`:`DOCK` 机场(网关)/ `DRONE` 无人机(挂载在机场下的子设备)
- `status`:`ONLINE` / `OFFLINE`。无人机的在线状态随所属机场级联 —— 它没有独立 MQTT 连接
- 无人机的 `gatewaySn` 必填;机场自身即网关,该字段为空

**结构化遥测** `/{id}/telemetry` —— 把 osd 原始报文拆成前端可直接渲染的字段,
避免每个页面各自解析机型差异。机场返回 `coverState/putterState/droneInDock/environmentTemperature/
windSpeed/rainfall/chargingState/networkState/subDevice`;飞行器返回
`longitude/latitude/height/elevation/attitudeHead/horizontalSpeed/verticalSpeed/gear/
positionState/storage/gimbal/battery`。未归一化的字段仍通过 `raw` 透出。

**遥测** `/osd` 返回最新快照:`{ deviceSn, osdJson, modeCode, longitude, latitude, height,
batteryPercent, updateTime }`。`osdJson` 是原始报文,机型间字段差异大;常用字段抽成了列。
`modeCode` 的含义按设备类型区分(机场见 `DOCK_MODE`,飞行器见 `AIRCRAFT_MODE`)。

**指令下发** `POST /{id}/commands`:

```json
{ "method": "cover_open", "data": {} }
```

- `method` 必须在该设备类型的指令目录内,否则 400
- 设备离线时 400;下行目标是**网关(机场)**,面向飞行器的指令带上 `sn` 由机场转发
- 返回指令记录,含 `tid`;设备回复后 `status` 变为 `OK`/`FAILED`,15 秒无回复置 `TIMEOUT`

**指令目录** `GET /{id}/services` 返回 `[{ method, label, group, danger }]`,
`danger=true` 的是有副作用的操作(重启、格式化、返航等),前端应二次确认。

机场指令:`cover_open/close/force_close`、`putter_open/close`、`charge_open/close`、
`drone_open/close`、`drone_self_check`、`return_home`、`return_home_cancel`、`drone_format`、
`supplement_light_open/close`、`debug_mode_open/close`、`rtk_calibration`、
`device_reboot`、`device_format`。

无人机指令:`drone_open/close`、`return_home`、`return_home_cancel`。

**MQTT 主题**(设备侧直连,不走 HTTP):

| 主题 | 方向 | 用途 |
|---|---|---|
| `thing/product/{sn}/osd` | 上行 | 定频遥测(0.5Hz) |
| `thing/product/{sn}/state` | 上行 | 状态变化 |
| `sys/product/{sn}/status` | 上行 | `update_topo` 拓扑上报,自动纳管设备 |
| `thing/product/{sn}/services_reply` | 上行 | 指令结果,按 `tid` 关联 |
| `thing/product/{sn}/events` | 上行 | HMS 告警 / 任务进度 |
| `thing/product/{sn}/requests` | 上行 | 设备请求(本期回未支持) |
| `thing/product/{sn}/services` | 下行 | 指令下发 |
| `thing/product/{sn}/property/set` | 下行 | 属性设置 |


---

## 15. 巡检服务

### 服务大屏 `/api/screen`

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/overview` | **一次返回整屏数据**(大屏每 30 秒整体刷新,拆成多接口会导致各卡片取自不同时刻) |

返回:`{ kpi, device, issueByType, orderByStatus, demandByStatus, pilotByStatus,
videoByStatus, orderByDept, recentIssues, recentOrders, onlineVideos, mapPoints, taskTrend }`

- `kpi`:`pointTotal / dockOnline / dockTotal / taskTotal / taskDone / issueTotal /
  orderTotal / orderDone / hazardOpen / demandPending / pilotAvailable`
- `mapPoints`:地图标记数组,每项 `{ name, kind, longitude, latitude, riskLevel? | issueType? }`,
  `kind` 为 `POINT`(点位)或 `ISSUE`(在办问题)

### 问题清单 `/api/issues`

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/page` | 筛选:`keyword`(标题/点位/设备/地点)、`issueType`、`status`、`source`、`dept` |
| GET | `/recent?limit=12` | 最近问题 |
| GET | `/{id}` | 详情 |
| POST / PUT `/{id}` / DELETE `/{id}` | | 已结案不可修改 |
| POST | `/{id}/close` | 结案;已生成工单的问题须在工单中结案 |
| GET | `/stats` | 按问题类型计数 |

- `issueType`:`ILLEGAL_BUILD` 疑似违建 / `GARBAGE` 垃圾堆放 / `FLOATING` 水面漂浮物 /
  `ILLEGAL_NET` 非法围网 / `OUTFALL` 疑似排污口 / `SLUDGE` 渣土车 / `CRACK` 路面裂纹 /
  `WATER_PLANT` 水面植物 / `CONSTRUCTION` 施工堆料 / `OTHER`
- `status`:`PENDING` 待处理 / `DISPATCHED` 已推送 / `WORK_ORDER` 已生成工单 / `CLOSED` 已结案
- `source`:`AI` 算法识别 / `MANUAL` 人工上报
- 新增时传 `pointId` 会自动带出点位名称、地址与经纬度

### 工单 `/api/work-orders`

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/page` | 筛选:`keyword`、`status`、`priority`、`dept`、`handler` |
| GET | `/{id}` / POST / PUT `/{id}` / DELETE `/{id}` | |
| POST | `/from-issue/{issueId}` | **由问题生成工单**,body 可传 `{ title, priority, handler, handlerPhone, handleDept, deadline }` |
| POST | `/{id}/dispatch` | 派发:`{ handler, handlerPhone, handleDept, deadline }`,handler 必填 |
| POST | `/{id}/handle` | 处理:`{ result }`,必填 |
| POST | `/{id}/close` | 结案;同步关闭来源问题 |
| GET | `/stats` | 按状态计数 |

状态机:`PENDING` 待派发 → `PROCESSING` 处理中 → `HANDLED` 已处理 → `CLOSED` 已结案。
跳步会被拒(如待派发不能直接处理、处理中不能直接结案)。`priority`:`LOW/NORMAL/HIGH/URGENT`。

### 需求 `/api/demands`

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/page` | 筛选:`keyword`(名称/编码/来源部门)、`status`、`sourceDept`、`category` |
| GET | `/recent?limit=20` / `/{id}` | |
| POST / PUT `/{id}` / DELETE `/{id}` | | 已执行不可修改 |
| POST | `/{id}/execute` | 执行:`{ executor }`,留空记当前登录人 |
| POST | `/{id}/cancel` | 取消:`{ remark }` |
| GET | `/stats` | 按状态计数 |

`status`:`PENDING` 待执行 / `EXECUTED` 已执行 / `OVERDUE` 已超时 / `CANCELED` 已取消。
`category` 复用计划口径:`DAILY/SPECIAL/EMERGENCY`。逾期每小时自动标记一次。

### 视频通道 `/api/videos`

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/page` | 筛选:`keyword`、`status`、`protocol`、`channelType` |
| GET | `` / `/online?limit=6` / `/{id}` | |
| POST / PUT `/{id}` / DELETE `/{id}` | | |
| POST | `/{id}/status?online=true\|false` | 手动切换在线状态 |
| GET | `/stats` | 按状态计数 |

`protocol`:`RTMP/FLV/HLS/GB28181/WEBRTC`;`channelType`:`LIVE` 直播 / `PLAYBACK` 回放。
平台只维护台账与状态,**实际拉流需外部流媒体服务**(如 SRS / ZLMediaKit)。

### 飞手 `/api/pilots`

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/page` | 筛选:`keyword`(姓名/手机号/片区)、`status`、`certType`、`area` |
| GET | `` / `/{id}` | |
| POST / PUT `/{id}` / DELETE `/{id}` | | |
| GET | `/stats` | 按状态计数 |

`certType`:`CAAC` 民航局 / `UTC` 大疆慧飞 / `AOPA` / `NONE`;
`status`:`AVAILABLE` 可调度 / `ON_TASK` 执行中 / `LEAVE` 休假 / `DISABLED` 停用。

## 16. 机场扩展能力(上云 API 深度功能)

### 航线库 `/api/waylines`

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `` / `/{id}` | 全量列表 / 详情,带 `waypointCount` |
| POST / PUT `/{id}` / DELETE `/{id}` | | 航点支持 `waypoints` 数组或 `waypointsJson` 字符串 |

`templateTypes`:`WAYPOINT` 航点 / `POI` 兴趣点 / `INSPECT` 巡查拍照 / `STRIP` 航带 / `SOLID` 立体。
航点结构:`{longitude, latitude, height, speed}`;至少 1 个且经纬度必填。

### 航线任务 `/api/wayline-jobs`(flighttask 三段式 + Dock 3 扩展)

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/page` | 筛选:`dockSn`、`status`;排序 `sortBy/direction` |
| GET | `/{id}` | 含 `returnHomeJson` 返航轨迹、`readyConditionsJson` 就绪条件 |
| POST | `` | `{waylineId, dockId, jobType, executeTime, rthAltitude, readyConditionsJson}` |
| POST | `/{id}/undo` | 取消(活跃态) |
| POST | `/{id}/resume` | 断点续飞(FAILED / CANCELED 且有断点;空中任务不支持) |
| POST | `/{id}/pause` / `/{id}/recovery` | 暂停 / 恢复(`flighttask_pause` / `flighttask_recovery`) |
| POST | `/{id}/return-home` | 一键返航(对所属机场下发 `return_home`) |
| POST | `/in-flight` | 空中下发航线(`in_flight_wayline_deliver`),要求飞行器在空中 |
| POST | `/{id}/in-flight/{action}` | `stop` 悬停 / `recover` 恢复 / `cancel` 取消并返航 |

编排:`flighttask_prepare` →(立即任务自动 / 定时任务到点)`flighttask_execute` → 设备
`flighttask_progress` 事件驱动 `SENT→READY→QUEUED→RUNNING→PAUSED→SUCCESS/FAILED/CANCELED`。
`jobType`:`IMMEDIATE` 立即 / `TIMED` 定时 / `CONDITION` 条件(至少一个就绪条件:
`readyConditionsJson = {"battery_capacity":80,"begin_time":ms,"end_time":ms}`)。
条件任务由设备监听条件,满足后发 `flighttask_ready` 事件,云端随即 execute;
设备执行前经 `flighttask_resource_get` 请求向云端取航线文件句柄。
`jobChannel`:`FLIGHTTASK` 常规三段式 / `IN_FLIGHT` 空中下发(进度走 `in_flight_wayline_progress`)。
返航时设备上报 `return_home_info`(planning_path),存 `returnHomeJson` 供前端展开。
同一机场同时只允许一个活跃任务;下发失败任务行留痕(FAILED + errorMsg)。

### 固件升级 `/api/firmwares` + `/api/firmware-tasks`

| 方法 | 路径 | 说明 |
|---|---|---|
| GET / POST / PUT `/{id}` / DELETE `/{id}` | `/api/firmwares` | 固件库 CRUD |
| POST | `/api/firmwares/{id}/deploy` | `{deviceIds:[...]}` 批量 OTA(`ota_create`) |
| GET | `/api/firmware-tasks/page` | 筛选:`deviceSn`、`status` |

任务状态:`SENT/DOWNLOADING/UPGRADING/SUCCESS/FAILED`,进度由 `ota_progress` 事件回填;
升级成功自动刷新设备台账 `firmwareVersion`;离线设备任务直接 FAILED。

### 远程日志 `/api/devices/{id}/logs`

| 方法 | 路径 | 说明 |
|---|---|---|
| POST | `/logs/sync` | 下发 `logs_file_list`,清单经 services_reply 异步落库 |
| GET | `/logs` | 该设备日志文件列表 |
| POST | `/logs/upload` | `{fileIds:[...]}` 批量上传,进度经 `logs_file_upload_progress` 回填 |

文件状态:`FOUND/UPLOADING/UPLOADED/FAILED`;`module`:`DOCK/DRONE`。

### AI 目标识别 `/api/devices/{id}/ai`

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/ai/config` | 读配置,未保存过返回默认(不落库) |
| PUT | `/ai/config` | 保存并经 `property/set` 同步在线设备,返回 `synced` |
| GET | `/ai/targets/page` | 识别记录,筛选 `type` |

`confidenceMode`:`COUNT` 计数(65%) / `RESCUE` 搜救(50%) / `CUSTOM` 自定义(50-99);
`filterTypes` 出入参为数组(`PERSON/CAR/BOAT`),库内存 JSON。
设备侧 `ai_target` 事件落识别记录;删除设备级联清理 AI 配置、识别记录与日志文件。

### 直播管理 `/api/devices/{id}/live`(Dock 3 live)

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/capacity` | 设备经 state 上报的 `live_capacity`(可用视频源 / 相机 / 镜头) |
| GET | `/streams/page` | 直播会话分页,筛选 `status` |
| POST | `/start` | `{videoId, urlType, url, videoQuality, videoType}` 下发 `live_start_push` |
| POST | `/streams/{sid}/stop` | `live_stop_push` |
| POST | `/streams/{sid}/quality` | `{videoQuality}` 下发 `live_set_quality` |
| POST | `/lens` | `{videoType}` 下发 `live_lens_change`(作用于该机场当前推流) |
| POST | `/streams/{sid}/camera` | `{cameraPosition}` 0 舱内 / 1 舱外(`live_camera_change`) |

`videoId` 形如 `{sn}/{camera_index}/{video_index}`,由 `capacity` 清单拼出;
`urlType`:`RTMP`(协议值 1)/ `GB28181`(3)/ `WEBRTC`(4,免 url)/ `AGORA`(0);
`videoQuality`:`0` 自适应 / `1` 流畅 / `2` 标清 / `3` 高清 / `4` 超清;
`videoType`:`normal` 广角 / `wide` 超广角 / `zoom` 变焦 / `ir` 红外。
会话状态:`PUSHING/STOPPED/FAILED`(离线机场开流任务直接 FAILED 留痕)。

### 媒体管理 `/api/devices/{id}/media`(Dock 3 media)

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/page` | 媒体文件分页,筛选 `flightId`、`isOriginal`;回填 `flightName` |
| GET | `/priority` | 当前优先上传媒体的任务(设备上报或云端指定) |
| POST | `/prioritize` | `{flightId}` 下发 `upload_flighttask_media_prioritize` |

链路:任务完成 → 设备发 `storage_config_get` 请求,云端回对象存储句柄
(bucket / credentials / object_key_prefix)→ 设备上传后逐个 `file_upload_callback`
事件落库(坐标 / 高度 / 云台角 / 拍摄时刻)→ 任务 `mediaCount` 以实际原始文件数回填;
设备同时上报 `highest_priority_upload_flighttask_media` 维护优先级台账。

### HMS 健康告警 `/api/devices/{id}/hms`

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/page` | 分页,筛选 `level` |

`level`:`NOTICE` 提示 / `WARN` 警告 / `ERROR` 严重(设备数字等级 0/1/2 归一);
来源为设备 `hms` 事件中的 `hms_list` 逐条落库,事件流里同时留有原始报文。

## 17. 算法管理(识别算法 / 告警闭环 / 臭气溯源)

### 算法注册与配置 `/api/algorithms`

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `` | 全量列表(内置 6 个算法,无增删) |
| PUT | `/{id}` | `{enabled, confidenceValue, alarmLevel}` |
| POST | `/{id}/run` | 手动执行一次识别,返回命中的告警(停用算法 400) |

内置算法 `code`:`SMOKE_FIRE` 烟火识别(需红外 + 自动录像)/ `ODOR_TRACE` 臭气溯源 /
`ILLEGAL_DUMP` 非法倾倒 / `COVER_MEMBRANE` 覆盖膜异常 / `CHIMNEY_EMISSION` 烟囱排放(自动录像)/
`LEAK_DETECT` 泄漏检测。`confidenceValue` 50-99;`alarmLevel`:`NOTICE/WARN/ERROR`。
识别入口有二:① 航线任务 RUNNING 时后台每 20s 自动识别(每算法 120s 节流,位置取飞行器 OSD 遥测);
② 控制台手动 `run`。命中即落告警,录像算法生成 `videoObjectKey`(15-90s)+ 取证时刻;
明火 / 危废 / 可燃泄漏 / 黑烟浓黄烟 / 大面积异常自动升级 ERROR。

### 算法告警 `/api/algo-alarms`

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/page` | 筛选:`algorithmCode`、`level`、`status`、`startTime/endTime`、`keyword`(标题/位置) |
| GET | `/{id}` | 详情,`payload` 已解析为对象(算法私有结论) |
| POST | `/{id}/handle` | `{remark}` 处置意见必填;PENDING → HANDLED,重复处置 400 |

`level` 与 HMS 同口径;`status`:`PENDING` 待处置 / `HANDLED` 已处置(记录 handler / handleTime)。
`payload` 常见键:`fireType` 烟火类型、`irMaxTempC` 红外最高温、`smokeColor` 烟羽颜色、
`opacityPercent` 不透光度、`dumpType` 倾倒类型、`anomalyType` 膜面异常、`leakType` 泄漏类型、
`areaM2` 面积、`backtrackM` 溯源回溯距离等。

### 臭气检测与扩散溯源 `/api/odor`

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/map` | 分布图:8 个监测站最新读数(含 `level` 分级)+ 区域风 + 阈值 |
| GET | `/dispersion` | 扩散模拟与溯源(超标时顺带落一条 ODOR_TRACE 告警,10 分钟去重) |
| PUT | `/wind` | `{speed 0-17, direction 0-359}` 覆盖区域风并立即重算一批读数 |

判定阈值:H2S ≥ 0.05 ppm 警告 / ≥ 0.2 ppm 严重(`level`:NORMAL/WARN/ERROR)。
读数批次 180s 过期自动刷新,60% 概率出现泄漏事件(候选:应急调节池 / 罐区 / 填埋二区,
沿下风向按 `exp(-dist/400)` × 风向对齐度衰减)。溯源:浓度前二加权质心沿上风向回溯
`clamp(80~600m)` 定源,再沿下风向输出 12 个采样点的高斯烟羽轨迹
(`widthM` 烟羽宽度、`h2sPpm` 中心线浓度指数衰减),并给出受影响站点按浓度排序与距源距离;
`source.uncertaintyM` 为回溯距离 30% 的不确定半径。
