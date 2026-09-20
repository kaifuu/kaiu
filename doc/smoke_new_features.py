# -*- coding: utf-8 -*-
"""新功能冒烟:航线管理 / 航线任务 / 固件升级 / 远程日志 / AI 目标识别"""
import re
import sys
import time
import json
import urllib.request

BASE = "http://localhost:8181/api"
TOKEN = ""
PASSED, FAILED = 0, 0


def call(method, path, body=None):
    req = urllib.request.Request(BASE + path, method=method)
    req.add_header("Content-Type", "application/json")
    if TOKEN:
        req.add_header("Authorization", "Bearer " + TOKEN)
    data = json.dumps(body).encode() if body is not None else None
    try:
        with urllib.request.urlopen(req, data=data, timeout=15) as resp:
            return resp.status, json.loads(resp.read().decode())
    except urllib.error.HTTPError as e:
        return e.code, json.loads(e.read().decode())


def ok(name, cond, extra=""):
    global PASSED, FAILED
    if cond:
        PASSED += 1
        print("  [PASS] %s" % name)
    else:
        FAILED += 1
        print("  [FAIL] %s  %s" % (name, extra))


def data_of(r):
    return r.get("data") if isinstance(r, dict) else None


def api(method, path, body=None):
    """call() 返回 (http_status, body) 元组,新段落用这个直接拿 body"""
    _, r = call(method, path, body)
    return r


print("=" * 70)
print("新功能接口冒烟(需后端 + 模拟器运行中)")

# ---------- 登录 ----------
_, cap = call("GET", "/auth/captcha")
code = "".join(re.findall(r">([A-Z0-9])</text>", cap["data"]["svg"]))
_, r = call("POST", "/auth/login",
            {"username": "admin", "password": "admin123", "cid": cap["data"]["cid"], "captcha": code})
TOKEN = r["data"]["token"]
ok("登录成功", r.get("code") == 200)

# ---------- 设备定位 ----------
_, r = call("GET", "/devices")
devices = r["data"]
sim_dock = next((d for d in devices if d["deviceSn"] == "DOCK-SIM-0001"), None)
ok("定位模拟机场", sim_dock is not None)
# 库里未必有第二台机场,自建一台离线机场走负面路径(上次运行若中途崩溃会残留同名设备,先清掉)
_, r = call("POST", "/devices", {"name": "冒烟离线机场", "deviceSn": "DOCK-SMOKE-NE1",
                                 "deviceType": "DOCK", "deviceModel": "DJI Dock 2"})
if r.get("code") != 200:
    _, r = call("GET", "/devices")
    stale = next((d for d in r["data"] if d["deviceSn"] == "DOCK-SMOKE-NE1"), None)
    if stale:
        call("DELETE", "/devices/%d" % stale["id"])
    _, r = call("POST", "/devices", {"name": "冒烟离线机场", "deviceSn": "DOCK-SMOKE-NE1",
                                     "deviceType": "DOCK", "deviceModel": "DJI Dock 2"})
offline_dock = r["data"] if r.get("code") == 200 else None
ok("自建离线机场", offline_dock is not None and offline_dock.get("status") == "OFFLINE", r)
# 同一机场同时只允许一个活跃任务:上次崩溃可能残留,先撤销清场
_, r = call("GET", "/wayline-jobs/page?page=1&size=50&dockSn=" + sim_dock["deviceSn"])
for j in (r["data"]["rows"] or []):
    if j["status"] in ("SENT", "READY", "QUEUED", "RUNNING", "PAUSED"):
        call("POST", "/wayline-jobs/%d/undo" % j["id"])

print("\n[1] 航线管理")
_, r = call("GET", "/waylines")
ok("种子航线 3 条", len(r["data"]) == 3, len(r["data"]))
wl = next(w for w in r["data"] if w["code"] == "WL0001")
ok("航线带航点数", wl.get("waypointCount") == 4, wl.get("waypointCount"))
_, r = call("POST", "/waylines", {
    "name": "冒烟测试航线", "templateTypes": "WAYPOINT", "alt": 66, "speed": 5,
    "waypoints": [{"longitude": 116.39, "latitude": 39.90, "height": 66, "speed": 5}]})
ok("新增航线(数组航点)", r.get("code") == 200 and r["data"]["waypointCount"] == 1, r)
new_id = r["data"]["id"]
_, r = call("PUT", "/waylines/%d" % new_id, {
    "name": "冒烟测试航线-改", "waypointsJson": json.dumps(
        [{"longitude": 116.39, "latitude": 39.90}, {"longitude": 116.40, "latitude": 39.91}])})
ok("修改航线(JSON 字符串航点)", r["data"]["waypointCount"] == 2, r)
_, r = call("DELETE", "/waylines/%d" % new_id)
ok("删除航线", r.get("code") == 200)
_, r = call("POST", "/waylines", {"name": "", "waypoints": []})
ok("空航线被拒", r.get("code") != 200)

print("\n[2] 航线任务(在线机场·立即任务)")
_, r = call("POST", "/wayline-jobs", {"dockId": sim_dock["id"], "waylineId": wl["id"], "jobType": "IMMEDIATE"})
ok("立即任务下发", r.get("code") == 200 and r["data"]["status"] in ("SENT", "QUEUED", "RUNNING"), r)
job_id = r["data"]["id"]
status = ""
for _ in range(15):
    time.sleep(2)
    _, r = call("GET", "/wayline-jobs/%d" % job_id)
    status = r["data"]["status"]
    if status in ("SUCCESS", "FAILED", "CANCELED"):
        break
ok("任务事件驱动到 SUCCESS", status == "SUCCESS", status)
_, r = call("GET", "/wayline-jobs/%d" % job_id)
ok("完成态带媒体数与进度100", r["data"]["mediaCount"] >= 1 and r["data"]["progress"] == 100, r["data"])
_, r = call("GET", "/wayline-jobs/page?page=1&size=5")
ok("任务分页含机场名", any(j.get("dockName") for j in r["data"]["rows"]), r["data"])

print("\n[3] 离线拒绝与负面路径")
_, r = call("POST", "/wayline-jobs", {"dockId": offline_dock["id"], "waylineId": wl["id"], "jobType": "IMMEDIATE"})
ok("离线机场任务下发被拒", r.get("code") != 200, r)
_, r = call("GET", "/wayline-jobs/page?page=1&size=10&dockSn=" + offline_dock["deviceSn"])
failed_job = next((j for j in r["data"]["rows"] if j["status"] == "FAILED" and j.get("errorMsg")), None)
ok("失败任务留痕带原因", failed_job is not None, r["data"])
_, r = call("POST", "/wayline-jobs/%d/resume" % failed_job["id"])
ok("离线续飞被拒(如实报错)", r.get("code") != 200, r)
_, r = call("POST", "/wayline-jobs", {"dockId": offline_dock["id"], "waylineId": wl["id"], "jobType": "TIMED"})
ok("定时任务缺执行时间被拒", r.get("code") != 200)
_, r = call("POST", "/wayline-jobs", {"dockId": sim_dock["id"], "waylineId": wl["id"],
                                      "jobType": "TIMED", "executeTime": "bad-format"})
ok("非法时间格式被拒", r.get("code") != 200)

print("\n[4] 定时任务到点触发")
_, r = call("POST", "/wayline-jobs", {"dockId": sim_dock["id"], "waylineId": wl["id"], "jobType": "TIMED",
                                      "executeTime": time.strftime("%Y-%m-%d %H:%M:%S", time.localtime(time.time() + 8))})
ok("定时任务就绪", r.get("code") == 200, r)
timed_id = r["data"]["id"]
status = ""
for _ in range(20):
    time.sleep(2)
    _, r = call("GET", "/wayline-jobs/%d" % timed_id)
    status = r["data"]["status"]
    if status in ("SUCCESS", "FAILED", "CANCELED"):
        break
ok("定时任务到点后执行完成", status == "SUCCESS", status)

print("\n[5] 固件升级")
_, r = call("GET", "/firmwares")
ok("种子固件 4 条", len(r["data"]) == 4, len(r["data"]))
fw = next(f for f in r["data"] if f["version"] == "v4.2.1")
_, r = call("POST", "/firmwares/%d/deploy" % fw["id"], {"deviceIds": [sim_dock["id"], offline_dock["id"]]})
ok("升级下发建 2 条任务", r.get("code") == 200 and len(r["data"]) == 2, r)
status = ""
for _ in range(12):
    time.sleep(2)
    _, r = call("GET", "/firmware-tasks/page?page=1&size=5")
    rows = r["data"]["rows"]
    online_task = next((t for t in rows if t["deviceSn"] == sim_dock["deviceSn"]), None)
    offline_task = next((t for t in rows if t["deviceSn"] == offline_dock["deviceSn"]), None)
    if online_task and online_task["status"] == "SUCCESS":
        ok("离线设备任务直接 FAILED", offline_task and offline_task["status"] == "FAILED", offline_task)
        ok("在线设备升级 SUCCESS", True)
        break
else:
    ok("在线设备升级 SUCCESS", False, online_task)
_, r = call("GET", "/devices/%d" % sim_dock["id"])
ok("升级成功后台账版本刷新", r["data"].get("firmwareVersion") == "v4.2.1", r["data"].get("firmwareVersion"))

print("\n[6] 远程日志")
_, r = call("POST", "/devices/%d/logs/sync" % sim_dock["id"])
ok("同步指令送达", r.get("code") == 200)
time.sleep(3)
_, r = call("GET", "/devices/%d/logs" % sim_dock["id"])
files = r["data"]
ok("日志清单 8 个(机场5+飞行器3)", len(files) == 8, len(files))
targets = [f for f in files if f["module"] == "DOCK" and f["status"] == "FOUND"][:2]
if not targets:
    # 历史运行已把文件全部传成 UPLOADED,重传会被拒;此处只验证清单可读
    ok("无可重传文件(均已上传,历史运行覆盖)", all(f.get("objectKey") for f in files if f["status"] == "UPLOADED"))
else:
    ids = [f["fileId"] for f in targets]
    _, r = call("POST", "/devices/%d/logs/upload" % sim_dock["id"], {"fileIds": ids})
    ok("上传指令送达", r.get("code") == 200, r)
    ups = []
    for _ in range(10):
        time.sleep(2)
        _, r = call("GET", "/devices/%d/logs" % sim_dock["id"])
        ups = [f for f in r["data"] if f["fileId"] in ids]
        if len(ups) == len(ids) and all(f["status"] == "UPLOADED" for f in ups):
            break
    ok("所选文件上传完成带对象键", ups and all(f["status"] == "UPLOADED" and f.get("objectKey") for f in ups), ups)

print("\n[7] AI 目标识别")
_, r = call("GET", "/devices/%d/ai/config" % sim_dock["id"])
# 上次运行若中途崩溃,末尾恢复默认的清理不会执行,库里会残留保存值——只验证可读且值合法
ok("AI 配置可读(默认或上次保存值)",
   r.get("code") == 200 and r["data"]["confidenceValue"] in range(50, 100)
   and isinstance(r["data"]["filterTypes"], list), r)
_, r = call("PUT", "/devices/%d/ai/config" % sim_dock["id"], {
    "enabled": True, "followEnabled": True, "model": "河道目标检测",
    "confidenceMode": "CUSTOM", "confidenceValue": 70, "filterTypes": ["PERSON", "BOAT"]})
ok("配置保存并同步在线设备", r.get("code") == 200 and r["data"].get("synced") is True, r)
_, r = call("POST", "/devices/%d/commands" % sim_dock["id"], {"method": "drone_open", "data": {}})
ok("飞行器上电", r.get("code") == 200, r)
time.sleep(10)
_, r = call("GET", "/devices/%d/ai/targets/page?page=1&size=10" % sim_dock["id"])
rows = r["data"]["rows"]
ok("识别记录流入(类型受限 PERSON/BOAT)", len(rows) >= 1 and all(
    x["targetType"] in ("PERSON", "BOAT") for x in rows), r["data"])

print("\n[8] 直播(Dock 3 live)")
cap = data_of(api("GET", "/devices/%d/live/capacity" % sim_dock["id"]))
ok("直播能力已上报", bool(cap and json.loads(cap["capacityJson"] or "{}").get("capacity")), cap)
video = "%s/165-0/normal-0" % sim_dock["deviceSn"]
r = api("POST", "/devices/%d/live/start" % sim_dock["id"], {
    "videoId": video, "urlType": "RTMP", "url": "rtmp://media.local/live/smoke",
    "videoQuality": 3, "videoType": "zoom"})
ok("开启直播", r.get("code") == 200 and r["data"]["status"] == "PUSHING", r)
sid = r["data"]["id"]
r = api("POST", "/devices/%d/live/streams/%d/quality" % (sim_dock["id"], sid), {"videoQuality": 4})
ok("切换清晰度", r.get("code") == 200 and r["data"]["videoQuality"] == 4, r)
r = api("POST", "/devices/%d/live/lens" % sim_dock["id"], {"videoType": "ir"})
ok("切换镜头", r.get("code") == 200, r)
r = api("POST", "/devices/%d/live/streams/%d/camera" % (sim_dock["id"], sid), {"cameraPosition": 1})
ok("切换相机位(舱外)", r.get("code") == 200, r)
r = api("POST", "/devices/%d/live/streams/%d/stop" % (sim_dock["id"], sid))
ok("停止直播", r.get("code") == 200 and r["data"]["status"] == "STOPPED", r)
r = api("POST", "/devices/%d/live/streams/%d/stop" % (sim_dock["id"], sid))
ok("重复停流被拒", r.get("code") != 200, r)

print("\n[9] 条件任务与媒体闭环(Dock 3 wayline/media)")
r = api("POST", "/wayline-jobs", {
    "dockId": sim_dock["id"], "waylineId": wl["id"], "jobType": "CONDITION",
    "rthAltitude": 100,
    "readyConditionsJson": json.dumps({"battery_capacity": 80, "begin_time": int(time.time() * 1000)})})
ok("条件任务下发", r.get("code") == 200 and r["data"]["jobType"] == "CONDITION", r)
cond_id, cond_fid = r["data"]["id"], r["data"]["flightId"]
status = ""
for _ in range(25):
    time.sleep(2)
    status = data_of(api("GET", "/wayline-jobs/%d" % cond_id))["status"]
    if status in ("SUCCESS", "FAILED", "CANCELED"):
        break
ok("条件就绪→资源请求→执行完成", status == "SUCCESS", status)
time.sleep(6)   # 等媒体回调陆续入库
r = api("GET", "/devices/%d/media/page?page=1&size=10" % sim_dock["id"])
rows = r["data"]["rows"]
ok("媒体文件入库(3 原始 + 3 预览)", len(rows) >= 6 and
   sum(1 for x in rows if x["isOriginal"]) >= 3, len(rows))
ok("媒体带回归属任务名", any(x.get("flightName") for x in rows), rows[0] if rows else None)
job = data_of(api("GET", "/wayline-jobs/%d" % cond_id))
ok("任务媒体数=实际原始文件数", job["mediaCount"] == sum(
    1 for x in rows if x["isOriginal"] and x["flightId"] == cond_fid), job["mediaCount"])
pr = data_of(api("GET", "/devices/%d/media/priority" % sim_dock["id"]))
ok("设备上报了优先上传任务", pr and pr["flightId"] == cond_fid, pr)
r = api("POST", "/devices/%d/media/prioritize" % sim_dock["id"], {"flightId": cond_fid})
ok("云端指定优先上传", r.get("code") == 200, r)
r = api("POST", "/wayline-jobs", {"dockId": sim_dock["id"], "waylineId": wl["id"], "jobType": "CONDITION"})
ok("无条件条件任务被拒", r.get("code") != 200, r)

print("\n[10] 任务暂停 / 恢复 / 一键返航")
r = api("POST", "/wayline-jobs", {"dockId": sim_dock["id"], "waylineId": wl["id"], "jobType": "IMMEDIATE"})
pause_id = r["data"]["id"]
status = ""
for _ in range(20):
    time.sleep(2)
    status = data_of(api("GET", "/wayline-jobs/%d" % pause_id))["status"]
    if status == "RUNNING":
        break
ok("任务进入执行中", status == "RUNNING", status)
r = api("POST", "/wayline-jobs/%d/pause" % pause_id)
ok("下发暂停", r.get("code") == 200 and r["data"]["status"] == "PAUSED", r)
time.sleep(3)
ok("事件驱动维持暂停态", data_of(api("GET", "/wayline-jobs/%d" % pause_id))["status"] == "PAUSED")
r = api("POST", "/wayline-jobs/%d/recovery" % pause_id)
ok("下发恢复", r.get("code") == 200 and r["data"]["status"] == "RUNNING", r)
status = ""
for _ in range(15):
    time.sleep(2)
    status = data_of(api("GET", "/wayline-jobs/%d" % pause_id))["status"]
    if status in ("SUCCESS", "FAILED", "CANCELED"):
        break
ok("恢复后执行完成", status == "SUCCESS", status)
r = api("POST", "/wayline-jobs/%d/return-home" % pause_id)
ok("已结束任务返航被拒", r.get("code") != 200, r)

print("\n[11] 空中下发航线(Dock 3 in-flight wayline)")
api("POST", "/devices/%d/commands" % sim_dock["id"], {"method": "drone_open"})
api("POST", "/devices/%d/commands" % sim_dock["id"],
    {"method": "takeoff_to_point", "data": {"longitude": 116.40, "latitude": 39.91, "height": 80}})
time.sleep(2)
r = api("POST", "/wayline-jobs/in-flight", {"dockId": sim_dock["id"], "waylineId": wl["id"]})
ok("空中下发", r.get("code") == 200 and r["data"]["jobChannel"] == "IN_FLIGHT", r)
inflight_id = r["data"]["id"]
status = ""
for _ in range(15):
    time.sleep(2)
    status = data_of(api("GET", "/wayline-jobs/%d" % inflight_id))["status"]
    if status == "RUNNING":
        break
ok("空中航线执行中", status == "RUNNING", status)
r = api("POST", "/wayline-jobs/%d/in-flight/stop" % inflight_id)
ok("空中悬停", r.get("code") == 200, r)
time.sleep(3)
ok("悬停后任务暂停态", data_of(api("GET", "/wayline-jobs/%d" % inflight_id))["status"] == "PAUSED")
r = api("POST", "/wayline-jobs/%d/in-flight/recover" % inflight_id)
ok("空中恢复", r.get("code") == 200, r)
status = ""
for _ in range(15):
    time.sleep(2)
    status = data_of(api("GET", "/wayline-jobs/%d" % inflight_id))["status"]
    if status in ("SUCCESS", "FAILED", "CANCELED"):
        break
ok("空中航线完成", status == "SUCCESS", status)
r = api("POST", "/wayline-jobs/%d/resume" % inflight_id)
ok("空中任务不支持断点续飞", r.get("code") != 200, r)

print("\n[12] HMS 健康告警")
r = api("GET", "/devices/%d/hms/page?page=1&size=10" % sim_dock["id"])
ok("HMS 告警入库", r.get("code") == 200 and r["data"]["total"] >= 1, r["data"]["total"])
ok("HMS 含等级与描述", all(x["level"] in ("NOTICE", "WARN", "ERROR") and x["code"] for x in r["data"]["rows"]))

# ---------- 清理 ----------
call("PUT", "/devices/%d/ai/config" % sim_dock["id"], {
    "enabled": False, "followEnabled": False, "confidenceMode": "CUSTOM",
    "confidenceValue": 80, "filterTypes": ["PERSON", "CAR", "BOAT"]})
call("DELETE", "/devices/%d" % offline_dock["id"])

print("\n" + "=" * 70)
print("结果: %d 通过 / %d 失败" % (PASSED, FAILED))
sys.exit(1 if FAILED else 0)
