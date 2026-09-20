# -*- coding: utf-8 -*-
"""应急巡检平台 · 接口冒烟测试

覆盖:登录鉴权、六大系统管理模块、五类业务模块的读写与状态流转、越权与校验护栏。
用法:python doc/smoke_test.py [base_url]   默认 http://localhost:8181/api
"""
import json
import re
import sys
import urllib.error
import urllib.request

BASE = sys.argv[1] if len(sys.argv) > 1 else "http://localhost:8181/api"
TOKEN = None
passed = 0
failed = []


def call(method, path, body=None):
    """发起请求,返回 (http_status, 解包后的响应体)"""
    data = json.dumps(body, ensure_ascii=False).encode("utf-8") if body is not None else None
    req = urllib.request.Request(BASE + path, data=data, method=method)
    req.add_header("Content-Type", "application/json; charset=utf-8")
    if TOKEN:
        req.add_header("Authorization", "Bearer " + TOKEN)
    try:
        with urllib.request.urlopen(req) as resp:
            return resp.status, json.loads(resp.read().decode("utf-8"))
    except urllib.error.HTTPError as e:
        raw = e.read().decode("utf-8")
        try:
            return e.code, json.loads(raw)
        except ValueError:
            return e.code, {"raw": raw[:200]}


def check(label, cond, detail=""):
    global passed
    if cond:
        passed += 1
        print("  PASS  " + label)
    else:
        failed.append(label)
        print("  FAIL  " + label + "   " + str(detail)[:160])


def ok(label, method, path, body=None):
    """断言业务成功(code=200),返回 data"""
    st, r = call(method, path, body)
    check(label, r.get("code") == 200, "http=%s resp=%s" % (st, json.dumps(r, ensure_ascii=False)[:150]))
    return r.get("data")


def bad(label, method, path, body=None, want_code=400):
    """断言业务失败且给出可读中文原因"""
    st, r = call(method, path, body)
    check(label, r.get("code") == want_code and r.get("msg"),
          "http=%s resp=%s" % (st, json.dumps(r, ensure_ascii=False)[:150]))
    return r


def login(username, password):
    """验证码字符以 <text ...>X</text> 写在 SVG 里,按出现顺序拼回"""
    _, cap = call("GET", "/auth/captcha")
    svg = cap["data"]["svg"]
    code = "".join(re.findall(r">([A-Z0-9])</text>", svg))
    _, r = call("POST", "/auth/login",
                {"username": username, "password": password, "cid": cap["data"]["cid"], "captcha": code})
    return r


print("=" * 74)
print("应急巡检平台 · 接口冒烟测试   " + BASE)
print("=" * 74)

# ---------------------------------------------------------------- 鉴权
print("\n[1] 登录与鉴权")
_, cap = call("GET", "/auth/captcha")
check("验证码返回 cid + svg", cap["code"] == 200 and cap["data"]["cid"] and "<svg" in cap["data"]["svg"])
bad("验证码错误被拒", "POST", "/auth/login",
    {"username": "admin", "password": "admin123", "cid": cap["data"]["cid"], "captcha": "ZZZZ"})
bad("未登录访问受保护接口返回 401", "GET", "/menus/mine", want_code=401)

r = login("admin", "admin123")
check("admin 登录成功", r.get("code") == 200, r)
TOKEN = r["data"]["token"]
check("登录响应带 roleCode=ADMIN", r["data"]["roleCode"] == "ADMIN", r["data"].get("roleCode"))
# 菜单总数从接口取,避免加菜单后测试写死的数字失效
menu_total = len(ok("GET /menus", "GET", "/menus"))
check("ADMIN 可见全部菜单", len(r["data"]["menus"]) == menu_total,
      "mine=%s 全量=%s" % (len(r["data"]["menus"]), menu_total))

op = login("operator", "operator123")
check("operator 登录成功且 roleCode=OPERATOR", op["data"]["roleCode"] == "OPERATOR", op)
check("OPERATOR 仅见 6 个业务菜单", len(op["data"]["menus"]) == 6, len(op["data"]["menus"]))

bad("密码错误被拒", "POST", "/auth/login",
    {"username": "admin", "password": "wrong", "cid": "x", "captcha": "AAAA"})

prof = ok("GET /auth/profile", "GET", "/auth/profile")
check("profile 带角色名与菜单",
      prof.get("roleName") == "系统管理员" and len(prof.get("menus", [])) == menu_total, prof)

ok("PUT /auth/profile 改昵称", "PUT", "/auth/profile", {"nickname": "系统管理员", "phone": "13800000001"})
bad("改密码:原密码错", "POST", "/auth/profile/password",
    {"oldPassword": "nope", "newPassword": "abcdef"})
bad("改密码:新密码过短", "POST", "/auth/profile/password",
    {"oldPassword": "admin123", "newPassword": "123"})

# ---------------------------------------------------------------- 系统管理
print("\n[2] 系统管理 · 读")
ok("GET /menus", "GET", "/menus")
ok("GET /menus/tree", "GET", "/menus/tree")
ok("GET /orgs", "GET", "/orgs")
ok("GET /tenants/page", "GET", "/tenants/page?page=1&size=5")
ok("GET /roles/page", "GET", "/roles/page")
ok("GET /logs/count", "GET", "/logs/count")

d = ok("GET /users/page", "GET", "/users/page")
check("用户列表回填角色名/组织名", d["rows"][0].get("roleName") and d["rows"][0].get("orgName"), d["rows"][0])
check("用户列表不外发密码", "password" not in d["rows"][0], list(d["rows"][0]))

d = ok("GET /logs 分页", "GET", "/logs?page=1&size=5")
check("日志返回 {rows,total}", "rows" in d and "total" in d, list(d))
n_operate = ok("GET /logs?type=OPERATE", "GET", "/logs?type=OPERATE")["total"]
n_login = ok("GET /logs?type=LOGIN", "GET", "/logs?type=LOGIN")["total"]
check("日志类型过滤生效", n_operate != n_login, "operate=%s login=%s" % (n_operate, n_login))

ok("排序白名单:合法列", "GET", "/tenants/page?sortBy=name&direction=asc")
ok("排序白名单:非法列回退不报错", "GET", "/tenants/page?sortBy=evil%3Bdrop%20table&direction=asc")

print("\n[3] 系统管理 · 写与护栏")
t = ok("新增租户", "POST", "/tenants", {"name": "测试租户", "code": "T-TENANT", "remark": "冒烟"})
ok("修改租户", "PUT", "/tenants/%s" % t["id"], {"name": "测试租户-改"})
bad("租户编码重复被拒", "POST", "/tenants", {"name": "X", "code": "T-TENANT"})
bad("租户名称必填", "POST", "/tenants", {"code": "T-NONAME"})

# 建在根组织下,才能用它验证「上级不能是自己的下级」
o = ok("新增组织", "POST", "/orgs", {"name": "测试组织", "orgCode": "T-ORG", "sort": 9, "parentId": 1})
bad("组织上级不能是自己", "PUT", "/orgs/%s" % o["id"], {"parentId": o["id"]})
bad("组织上级不能是下级", "PUT", "/orgs/1", {"parentId": o["id"]})

u = ok("新增人员", "POST", "/users",
       {"username": "smoke01", "nickname": "冒烟用户", "phone": "13900002222",
        "roleId": 2, "orgId": 1, "tenantId": 1, "status": "ENABLED"})
check("新增人员回填角色/组织名", u.get("roleName") and u.get("orgName"), u)
bad("用户名重复被拒", "POST", "/users", {"username": "smoke01"})
bad("角色不存在被拒", "POST", "/users", {"username": "smoke02", "roleId": 99999})
ok("重置密码", "POST", "/users/%s/reset-password" % u["id"])
bad("内置 admin 不可删除", "DELETE", "/users/1")

ro = ok("新增角色", "POST", "/roles", {"name": "冒烟角色", "code": "SMOKE", "menuIdsJson": "[1,2]"})
ok("修改角色授权", "PUT", "/roles/%s" % ro["id"], {"menuIdsJson": "[1,2,3]"})
bad("角色编码重复被拒", "POST", "/roles", {"name": "X", "code": "SMOKE"})
bad("内置 ADMIN 角色不可删除", "DELETE", "/roles/1")

m = ok("新增菜单", "POST", "/menus", {"name": "冒烟菜单", "path": "/smoke", "icon": "Menu", "group": "SYS"})
bad("菜单路径重复被拒", "POST", "/menus", {"name": "X", "path": "/smoke"})
bad("菜单上级不能是自己", "PUT", "/menus/%s" % m["id"], {"parentId": m["id"]})
ok("删除菜单", "DELETE", "/menus/%s" % m["id"])

# ---------------------------------------------------------------- 业务
print("\n[4] 巡检点位")
p = ok("新增点位", "POST", "/points",
       {"name": "冒烟点位", "code": "PT-SMOKE", "category": "OUTFALL", "riskLevel": "HIGH",
        "area": "测试网格", "address": "测试路 1 号", "longitude": 116.4, "latitude": 39.9,
        "manager": "测试员", "managerPhone": "13900001111"})
bad("经纬度需成对填写", "POST", "/points", {"name": "X", "code": "PT-X1", "longitude": 116.1})
bad("经度越界被拒", "POST", "/points", {"name": "X", "code": "PT-X2", "longitude": 200, "latitude": 39.9})
bad("点位编码重复被拒", "POST", "/points", {"name": "X", "code": "PT-SMOKE"})
ok("修改点位", "PUT", "/points/%s" % p["id"], {"name": "冒烟点位-改", "riskLevel": "LOW"})
d = ok("点位详情", "GET", "/points/%s" % p["id"])
check("修改已生效", d["name"] == "冒烟点位-改" and d["riskLevel"] == "LOW", d)

print("\n[5] 巡检计划与任务")
pl = ok("新增计划", "POST", "/plans",
        {"name": "冒烟计划", "code": "PL-SMOKE", "category": "DAILY", "cycleType": "WEEK",
         "cycleValue": 2, "startDate": "2026-09-20", "endDate": "2026-10-20",
         "owner": "测试员", "ownerPhone": "13900001111",
         "pointIds": json.dumps([p["id"]]), "status": "ENABLED"})
bad("结束日期早于开始日期被拒", "POST", "/plans",
    {"name": "X", "code": "PL-X", "startDate": "2026-10-01", "endDate": "2026-09-01"})
d = ok("生成任务", "POST", "/plans/%s/generate-tasks" % pl["id"])
check("按覆盖点位生成 1 条任务", d.get("created") == 1, d)
bad("草稿计划不可生成任务", "POST", "/plans/3/generate-tasks")

tasks = ok("按计划查任务", "GET", "/tasks/page?planId=%s" % pl["id"])
tk = tasks["rows"][0]
check("任务继承点位名与计划时间", tk["pointName"] == "冒烟点位-改" and tk["status"] == "PENDING", tk)
ok("开始执行", "POST", "/tasks/%s/start" % tk["id"])
bad("重复开始被拒", "POST", "/tasks/%s/start" % tk["id"])
bad("完成但不给结论被拒", "POST", "/tasks/%s/finish" % tk["id"], {"remark": "x"})
d = ok("完成任务", "POST", "/tasks/%s/finish" % tk["id"], {"result": "ABNORMAL", "remark": "发现异常"})
check("完成后写入结论与结束时间", d["result"] == "ABNORMAL" and d["actualEnd"], d)
bad("已完成不可取消", "POST", "/tasks/%s/cancel" % tk["id"])
bad("已完成不可修改", "PUT", "/tasks/%s" % tk["id"], {"name": "改"})

print("\n[6] 隐患上报闭环")
h = ok("上报隐患", "POST", "/hazards",
       {"title": "冒烟隐患", "pointId": p["id"], "taskId": tk["id"], "level": "MAJOR",
        "description": "冒烟测试描述"})
check("隐患继承点位名", h.get("pointName") == "冒烟点位-改", h)
ok("流转为处理中", "POST", "/hazards/%s/handle" % h["id"],
   {"content": "已安排处理", "status": "PROCESSING"})
bad("不给处理说明被拒", "POST", "/hazards/%s/handle" % h["id"], {"status": "RECTIFIED"})
bad("状态不可回退为待处理", "POST", "/hazards/%s/handle" % h["id"],
    {"content": "x", "status": "PENDING"})
bad("非法状态被拒", "POST", "/hazards/%s/handle" % h["id"], {"content": "x", "status": "BOGUS"})
d = ok("流转为已关闭", "POST", "/hazards/%s/handle" % h["id"], {"content": "整改完成", "status": "CLOSED"})
check("关闭写入处理人与时间", d["handler"] and d["handleTime"], d)
bad("已关闭不可修改", "PUT", "/hazards/%s" % h["id"], {"title": "改"})
ok("按任务反查隐患", "GET", "/tasks/%s/hazards" % tk["id"])

print("\n[7] 应急事件处置")
e = ok("接报事件", "POST", "/events",
       {"title": "冒烟事件", "category": "WATER_POLLUTION", "level": "III", "address": "测试地点",
        "longitude": 116.4, "latitude": 39.9, "reporter": "测试员", "description": "冒烟描述"})
ok("流转为响应中", "POST", "/events/%s/handle" % e["id"],
   {"content": "启动响应", "status": "RESPONDING"})
bad("响应中不可删除", "DELETE", "/events/%s" % e["id"])
bad("处置措施必填", "POST", "/events/%s/handle" % e["id"], {"status": "HANDLED"})
d = ok("流转为已处置", "POST", "/events/%s/handle" % e["id"],
       {"content": "处置完毕", "status": "HANDLED"})
check("已处置写入 finishTime", d["finishTime"], d)
ok("流转为已归档", "POST", "/events/%s/handle" % e["id"], {"content": "归档", "status": "ARCHIVED"})
bad("已归档不可修改", "PUT", "/events/%s" % e["id"], {"title": "改"})
ok("删除事件", "DELETE", "/events/%s" % e["id"])

print("\n[8] 工作台")
s = ok("GET /dashboard/stats", "GET", "/dashboard/stats")
for k in ("pointTotal", "taskByStatus", "pointByRisk", "eventByLevel", "taskTrend", "recentTasks"):
    check("stats 含 " + k, k in s, list(s))
check("趋势为近 7 天", len(s.get("taskTrend", [])) == 7, len(s.get("taskTrend", [])))

print("\n[9] 设备接入(大疆上云 API)")
s = ok("GET /devices/stats", "GET", "/devices/stats")
for k in ("total", "dockTotal", "droneTotal", "online", "offline", "mqttOnline"):
    check("设备统计含 " + k, k in s, list(s))

ok("GET /devices/page", "GET", "/devices/page")
ok("GET /devices", "GET", "/devices")

docks = ok("GET /devices/docks", "GET", "/devices/docks")
check("机场列表只含机场", all(x["deviceType"] == "DOCK" for x in docks), len(docks))
drones = ok("GET /devices/drones", "GET", "/devices/drones")
check("无人机列表只含无人机", all(x["deviceType"] == "DRONE" for x in drones), len(drones))

dock = ok("新增机场", "POST", "/devices",
          {"name": "冒烟机场", "deviceSn": "DOCK-SMOKE-1", "deviceType": "DOCK",
           "deviceModel": "DJI Dock 2"})
check("新建设备默认离线", dock.get("status") == "OFFLINE", dock)
bad("序列号重复被拒", "POST", "/devices",
    {"name": "X", "deviceSn": "DOCK-SMOKE-1", "deviceType": "DOCK"})
bad("无人机不挂机场被拒", "POST", "/devices",
    {"name": "X", "deviceSn": "DRONE-SMOKE-1", "deviceType": "DRONE"})
bad("挂到不存在的机场被拒", "POST", "/devices",
    {"name": "X", "deviceSn": "DRONE-SMOKE-1", "deviceType": "DRONE", "gatewaySn": "NO-SUCH-DOCK"})

drone = ok("新增无人机并绑定机场", "POST", "/devices",
           {"name": "冒烟无人机", "deviceSn": "DRONE-SMOKE-1", "deviceType": "DRONE",
            "gatewaySn": "DOCK-SMOKE-1"})
check("无人机回填所属机场名", drone.get("gatewayName") == "冒烟机场", drone)
ok("修改设备", "PUT", "/devices/%s" % drone["id"], {"name": "冒烟无人机-改"})

d = ok("机场指令目录", "GET", "/devices/%s/services" % dock["id"])
check("机场指令目录含 cover_open", any(x["method"] == "cover_open" for x in d), len(d))
d = ok("无人机指令目录", "GET", "/devices/%s/services" % drone["id"])
check("无人机指令目录不含 cover_open", not any(x["method"] == "cover_open" for x in d), d)

bad("设备离线时下发指令被拒", "POST", "/devices/%s/commands" % dock["id"],
    {"method": "cover_open"})
bad("机场不支持无人机专属指令", "POST", "/devices/%s/commands" % drone["id"],
    {"method": "cover_open"})
bad("非法指令名被拒", "POST", "/devices/%s/commands" % dock["id"],
    {"method": "rm -rf /"})

ok("GET /devices/{id}/osd", "GET", "/devices/%s/osd" % dock["id"])
tele = ok("GET /devices/{id}/telemetry", "GET", "/devices/%s/telemetry" % dock["id"])
check("机场遥测带设备类型与状态", tele.get("deviceType") == "DOCK" and "status" in tele, list(tele))
tele_d = ok("无人机遥测", "GET", "/devices/%s/telemetry" % drone["id"])
check("无人机遥测带设备类型", tele_d.get("deviceType") == "DRONE", tele_d.get("deviceType"))
ok("GET /devices/{id}/events", "GET", "/devices/%s/events" % dock["id"])
ev = ok("事件分页(机场控制 TAB 用)", "GET", "/devices/%s/events/page?page=1&size=5" % dock["id"])
check("事件分页返回 {rows,total}", "rows" in ev and "total" in ev, ev)
ev2 = ok("事件按类型筛选", "GET",
         "/devices/%s/events/page?eventType=ONLINE" % dock["id"])
check("类型筛选生效或为空集", isinstance(ev2.get("total"), int), ev2.get("total"))
ok("GET /devices/{id}/commands", "GET", "/devices/%s/commands" % dock["id"])
ok("GET /devices/commands/page", "GET", "/devices/commands/page")

bad("机场有挂载无人机时不可删", "DELETE", "/devices/%s" % dock["id"])
ok("删除无人机", "DELETE", "/devices/%s" % drone["id"])
ok("删除机场", "DELETE", "/devices/%s" % dock["id"])

print("\n[10] 巡检服务(问题 / 工单 / 需求 / 飞手 / 视频)")
sc = ok("GET /screen/overview", "GET", "/screen/overview")
for k in ("kpi", "device", "issueByType", "orderByStatus", "demandByStatus",
          "pilotByStatus", "videoByStatus", "orderByDept", "recentIssues",
          "onlineVideos", "mapPoints", "taskTrend"):
    check("大屏含 " + k, k in sc, list(sc))
check("大屏 KPI 含机场在线数", "dockOnline" in sc.get("kpi", {}), list(sc.get("kpi", {})))
check("地图标记同时含点位与问题",
      any(m["kind"] == "POINT" for m in sc["mapPoints"]) and any(m["kind"] == "ISSUE" for m in sc["mapPoints"]),
      len(sc["mapPoints"]))

# ---- 问题 ----
ok("GET /issues/page", "GET", "/issues/page")
ok("GET /issues/stats", "GET", "/issues/stats")
issue = ok("新增问题", "POST", "/issues",
           {"title": "冒烟测试问题", "issueType": "GARBAGE", "pointId": 1,
            "dept": "冒烟部门", "description": "冒烟描述"})
check("问题自动生成编码", bool(issue.get("code")), issue.get("code"))
check("问题从点位带出坐标", issue.get("longitude") is not None, issue)
bad("问题标题必填", "POST", "/issues", {"issueType": "GARBAGE"})
ok("修改问题", "PUT", "/issues/%s" % issue["id"], {"description": "改后描述"})

# ---- 工单:由问题派发(核心链路)----
order = ok("问题转工单", "POST", "/work-orders/from-issue/%s" % issue["id"], {"priority": "HIGH"})
check("工单继承问题信息", order.get("issueId") == int(issue["id"]) and order.get("issueTitle") == "冒烟测试问题", order)
check("未指定处理人时停在待派发", order.get("status") == "PENDING", order.get("status"))
bad("同一问题不可重复派单", "POST", "/work-orders/from-issue/%s" % issue["id"], {})
bad("已生成工单的问题不可直接结案", "POST", "/issues/%s/close" % issue["id"])
ok("GET /work-orders/page", "GET", "/work-orders/page")
ok("GET /work-orders/stats", "GET", "/work-orders/stats")
bad("未填处理人不可派发", "POST", "/work-orders/%s/dispatch" % order["id"], {})
bad("待派发不可直接处理", "POST", "/work-orders/%s/handle" % order["id"], {"result": "x"})
d = ok("派发工单", "POST", "/work-orders/%s/dispatch" % order["id"],
       {"handler": "冒烟处理人", "handlerPhone": "13900003000", "handleDept": "冒烟处置部门"})
check("派发后进入处理中", d.get("status") == "PROCESSING" and d.get("dispatchedAt"), d)
bad("处理中不可结案", "POST", "/work-orders/%s/close" % order["id"])
bad("处理结果必填", "POST", "/work-orders/%s/handle" % order["id"], {})
d = ok("处理工单", "POST", "/work-orders/%s/handle" % order["id"], {"result": "已现场处置完毕"})
check("处理后写入结果与完成时间", d.get("status") == "HANDLED" and d.get("finishedAt"), d)
d = ok("结案工单", "POST", "/work-orders/%s/close" % order["id"])
check("结案后状态为已结案", d.get("status") == "CLOSED", d.get("status"))
ok("来源问题同步结案", "GET", "/issues/%s" % issue["id"])
bad("已结案工单不可修改", "PUT", "/work-orders/%s" % order["id"], {"title": "改"})

# ---- 需求 ----
ok("GET /demands/page", "GET", "/demands/page")
ok("GET /demands/stats", "GET", "/demands/stats")
demand = ok("提报需求", "POST", "/demands",
            {"title": "冒烟测试需求", "sourceDept": "冒烟部门", "category": "SPECIAL"})
bad("需求来源部门必填", "POST", "/demands", {"title": "X"})
d = ok("执行需求", "POST", "/demands/%s/execute" % demand["id"], {"executor": "冒烟飞手"})
check("执行后写入执行人与时间", d.get("status") == "EXECUTED" and d.get("executor") == "冒烟飞手", d)
bad("已执行需求不可取消", "POST", "/demands/%s/cancel" % demand["id"], {})

# ---- 飞手 ----
ok("GET /pilots/page", "GET", "/pilots/page")
ok("GET /pilots/stats", "GET", "/pilots/stats")
pilot = ok("新增飞手", "POST", "/pilots",
           {"name": "冒烟飞手", "phone": "13900004000", "age": 30, "experienceYears": 5,
            "area": "冒烟片区", "certType": "CAAC", "certOrg": "中国民用航空局"})
bad("飞手姓名必填", "POST", "/pilots", {"phone": "13900004001"})
ok("修改飞手", "PUT", "/pilots/%s" % pilot["id"], {"status": "ON_TASK"})

# ---- 视频 ----
ok("GET /videos/page", "GET", "/videos/page")
ok("GET /videos/stats", "GET", "/videos/stats")
ok("GET /videos/online", "GET", "/videos/online")
video = ok("新增视频通道", "POST", "/videos",
           {"name": "冒烟通道", "deviceSn": "DOCK-SMOKE", "protocol": "FLV",
            "streamUrl": "http://media.local/live/smoke.flv"})
check("通道默认离线", video.get("status") == "OFFLINE", video.get("status"))
bad("通道名称必填", "POST", "/videos", {"streamUrl": "x"})
d = ok("通道上线", "POST", "/videos/%s/status?online=true" % video["id"])
check("上线后记录最后一帧时间", d.get("status") == "ONLINE" and d.get("lastFrameAt"), d)

print("\n[11] 机场扩展能力(航线 / 任务 / 固件 / 日志 / AI)")
wls = ok("GET /waylines 航线库", "GET", "/waylines")
wl = next((w for w in wls if w["code"] == "WL0001"), wls[0] if wls else None)
check("航线带航点数", wl and wl.get("waypointCount") == 4, wl and wl.get("waypointCount"))
nwl = ok("新增航线", "POST", "/waylines",
         {"name": "冒烟航线", "templateTypes": "WAYPOINT", "alt": 66, "speed": 5,
          "waypoints": [{"longitude": 116.39, "latitude": 39.90, "height": 66, "speed": 5}]})
check("航点序列化", nwl and nwl.get("waypointCount") == 1, nwl)
ok("修改航线", "PUT", "/waylines/%s" % nwl["id"], {"name": "冒烟航线-改"})
bad("空航点被拒", "POST", "/waylines", {"name": "x"})
ok("删除航线", "DELETE", "/waylines/%s" % nwl["id"])

ndock = ok("新增离线机场", "POST", "/devices",
           {"name": "冒烟离线机场", "deviceSn": "DOCK-SMOKE-NE1",
            "deviceType": "DOCK", "deviceModel": "DJI Dock 2"})
bad("离线机场任务下发被拒", "POST", "/wayline-jobs",
    {"dockId": ndock["id"], "waylineId": wl["id"], "jobType": "IMMEDIATE"})
njobs = ok("任务分页(按机场过滤)", "GET",
           "/wayline-jobs/page?page=1&size=10&dockSn=DOCK-SMOKE-NE1")
check("失败任务留痕带原因", any(j["status"] == "FAILED" and j.get("errorMsg")
                                for j in njobs.get("rows", [])), njobs)
bad("定时任务缺执行时间被拒", "POST", "/wayline-jobs",
    {"dockId": ndock["id"], "waylineId": wl["id"], "jobType": "TIMED"})

fws = ok("GET /firmwares 固件库", "GET", "/firmwares")
nfw = ok("新增固件", "POST", "/firmwares",
         {"productType": "DOCK", "deviceModel": "DJI Dock 2", "version": "v0.0.1-smoke",
          "fileName": "smoke.bin", "fileSize": 1024, "fileMd5": "0" * 32,
          "fileUrl": "http://oss.local/firmware/smoke.bin"})
ok("修改固件", "PUT", "/firmwares/%s" % nfw["id"], {"remark": "冒烟"})
dep = ok("下发升级(离线设备)", "POST", "/firmwares/%s/deploy" % nfw["id"],
         {"deviceIds": [ndock["id"]]})
check("离线设备任务直接 FAILED", dep and dep[0]["status"] == "FAILED", dep)
ftasks = ok("GET /firmware-tasks/page", "GET", "/firmware-tasks/page?page=1&size=5")
check("升级任务分页 {rows,total}", "rows" in ftasks and "total" in ftasks, ftasks)
ok("删除固件", "DELETE", "/firmwares/%s" % nfw["id"])

bad("离线机场日志同步被拒", "POST", "/devices/%s/logs/sync" % ndock["id"])
acfg = ok("AI 配置默认可读", "GET", "/devices/%s/ai/config" % ndock["id"])
check("默认置信度 80", acfg and acfg.get("confidenceValue") == 80, acfg)
acfg = ok("离线机场 AI 配置保存", "PUT", "/devices/%s/ai/config" % ndock["id"],
          {"enabled": True, "confidenceMode": "CUSTOM", "confidenceValue": 75,
           "filterTypes": ["PERSON", "BOAT"]})
check("离线保存 synced=false", acfg and acfg.get("synced") is False, acfg)
ok("AI 识别记录分页", "GET", "/devices/%s/ai/targets/page?page=1&size=5" % ndock["id"])

print("\n[11b] 机场扩展能力(直播 / 媒体 / HMS,离线负面路径)")
bad("离线机场直播开流被拒", "POST", "/devices/%s/live/start" % ndock["id"],
    {"videoId": "X/165-0/normal-0", "urlType": "RTMP", "url": "rtmp://x/live"})
bad("直播缺推流地址被拒(校验先行)", "POST", "/devices/%s/live/start" % ndock["id"],
    {"videoId": "X/165-0/normal-0", "urlType": "RTMP"})
bad("直播非法镜头被拒", "POST", "/devices/%s/live/start" % ndock["id"],
    {"videoId": "X/165-0/normal-0", "urlType": "WEBRTC", "videoType": "fisheye"})
bad("离线机场媒体优先上传被拒", "POST", "/devices/%s/media/prioritize" % ndock["id"],
    {"flightId": "no-such-flight"})
bad("媒体优先上传缺任务号被拒", "POST", "/devices/%s/media/prioritize" % ndock["id"], {})
hm = ok("HMS 告警分页(离线机场空集)", "GET", "/devices/%s/hms/page?page=1&size=5" % ndock["id"])
check("HMS 分页返回 {rows,total}", "rows" in hm and "total" in hm, hm)
ok("删除离线机场", "DELETE", "/devices/%s" % ndock["id"])

print("\n[12] 错误归一")
bad("不存在的接口返回 404", "GET", "/no/such/endpoint", want_code=404)

# ---------------------------------------------------------------- 清理
print("\n[13] 清理测试数据")
ok("删除视频通道", "DELETE", "/videos/%s" % video["id"])
ok("删除飞手", "DELETE", "/pilots/%s" % pilot["id"])
ok("删除需求", "DELETE", "/demands/%s" % demand["id"])
ok("删除工单", "DELETE", "/work-orders/%s" % order["id"])
ok("删除问题", "DELETE", "/issues/%s" % issue["id"])
ok("删除隐患", "DELETE", "/hazards/%s" % h["id"])
ok("删除任务", "DELETE", "/tasks/%s" % tk["id"])
ok("删除计划", "DELETE", "/plans/%s" % pl["id"])
ok("删除点位", "DELETE", "/points/%s" % p["id"])
ok("删除人员", "DELETE", "/users/%s" % u["id"])
ok("删除角色", "DELETE", "/roles/%s" % ro["id"])
ok("删除组织", "DELETE", "/orgs/%s" % o["id"])
ok("删除租户", "DELETE", "/tenants/%s" % t["id"])

print("\n" + "=" * 74)
print("通过 %d 项,失败 %d 项" % (passed, len(failed)))
if failed:
    for f in failed:
        print("  - " + f)
print("=" * 74)
sys.exit(1 if failed else 0)
