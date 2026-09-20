/**
 * 大疆上云 API 设备模拟器(对齐 Dock 3 能力集)
 *
 * 扮演一台「机场 + 一台挂载的无人机」,连到平台的 Netty MQTT Broker,完整走一遍设备侧协议:
 *   1. CONNECT(clientId = 机场 SN)→ 平台标记在线
 *   2. update_topo 上报拓扑,声明挂载的无人机;state 上报 live_capacity 直播能力
 *   3. 按 0.5Hz 推送 osd 遥测(机场属性 + 无人机属性各一路)
 *   4. 订阅 services,收到云端指令后按 tid 回 services_reply
 *   5. 航线任务三段式(prepare/execute/undo)+ Dock 3 扩展:
 *      暂停恢复 / 条件任务(flighttask_ready + flighttask_resource_get 请求)/ 空中下发航线 /
 *      返航轨迹(return_home_info)
 *   6. 直播(live_start_push 等 5 指令)、媒体(storage_config_get 请求 → file_upload_callback 流)、
 *      HMS 健康告警、OTA 升级、远程日志、AI 目标识别
 *
 * 用法:node sim.js [mqtt://host:port] [dockSn] [droneSn]
 */
import mqtt from 'mqtt'

const URL = process.argv[2] || 'mqtt://localhost:1883'
const DOCK_SN = process.argv[3] || 'DOCK-SIM-0001'
const DRONE_SN = process.argv[4] || 'DRONE-SIM-0001'

const log = (...a) => console.log(new Date().toISOString().slice(11, 19), ...a)
const uuid = () => crypto.randomUUID().replace(/-/g, '')

const T = {
  osd: (sn) => `thing/product/${sn}/osd`,
  services: (sn) => `thing/product/${sn}/services`,
  servicesReply: (sn) => `thing/product/${sn}/services_reply`,
  events: (sn) => `thing/product/${sn}/events`,
  state: (sn) => `thing/product/${sn}/state`,
  status: (sn) => `sys/product/${sn}/status`,
  statusReply: (sn) => `sys/product/${sn}/status_reply`,
  eventsReply: (sn) => `thing/product/${sn}/events_reply`,
  requests: (sn) => `thing/product/${sn}/requests`,
  requestsReply: (sn) => `thing/product/${sn}/requests_reply`,
  propertySet: (sn) => `thing/product/${sn}/property/set`
}

/** 设备状态机:让模拟数据随指令变化,便于在界面上看到真实反馈 */
const state = {
  dockMode: 0,        // 0 空闲 / 1 现场调试 / 2 远程调试 / 3 固件升级 / 4 作业中
  cover: 0,           // 0 关闭 / 1 打开 / 2 半开
  putter: 0,
  charging: 1,
  droneInDock: 1,
  dronePowerOn: 0,
  flying: false,
  battery: 100,
  altitude: 0,
  lng: 116.397428,
  lat: 39.90923,
  lens: 'normal',       // 当前直播镜头
  cameraPos: 0,         // 0 舱内 / 1 舱外
  ai: { enabled: false, follow: false, model: '通用目标检测', types: ['PERSON', 'CAR', 'BOAT'] }
}

/** 航线任务表:flightId → {steps,stepIdx,timer,canceled,paused,taskType},支持暂停后按步恢复 */
const jobs = new Map()
/** 直播会话表:videoId → {url, quality} */
const liveStreams = new Map()
/** 未决请求:tid → 处理函数(storage_config_get / flighttask_resource_get 的异步接续) */
const pendingRequests = new Map()

const client = mqtt.connect(URL, {
  clientId: DOCK_SN,
  clean: true,
  keepalive: 30,
  reconnectPeriod: 3000,
  connectTimeout: 8000
})

client.on('connect', () => {
  log(`已连接 ${URL} (clientId=${DOCK_SN})`)

  client.subscribe(
    [T.services(DOCK_SN), T.statusReply(DOCK_SN), T.eventsReply(DOCK_SN),
     T.requestsReply(DOCK_SN), T.propertySet(DOCK_SN)],
    { qos: 1 },
    (err) => err ? log('订阅失败', err.message) : log('已订阅指令与回复主题')
  )

  publishTopology()
  publishCapacity()
  startOsdLoop()
  startAiLoop()
  // 连接稳定后播一条 HMS 健康告警,让健康告警 TAB 有数据
  setTimeout(() => emitEvent('hms', {
    hms_list: [
      { code: 'dock_cover_exception', level: 1, module_index: 1, message: '舱盖开合受阻,请检查推杆机构', ts: Date.now() },
      { code: 'drone_batt_low', level: 0, module_index: 2, message: '飞行器电池电量偏低', ts: Date.now() }
    ]
  }), 8000)
})

client.on('reconnect', () => log('重连中 ...'))
client.on('error', (e) => log('MQTT 错误:', e.message))
client.on('close', () => log('连接已断开'))

/** 拓扑上报:声明机场自身 + 挂载的无人机 */
function publishTopology() {
  const body = {
    tid: uuid(),
    bid: uuid(),
    timestamp: Date.now(),
    method: 'update_topo',
    data: {
      type: 119,          // DJI Dock 2
      sub_type: 0,
      device_secret: 'sim-secret',
      nonce: uuid(),
      thing_version: '1.0.0',
      sub_devices: [{
        sn: DRONE_SN,
        type: 116,        // M3D 系列
        sub_type: 0,
        index: 'A',
        device_secret: 'sim-secret',
        nonce: uuid(),
        thing_version: '1.0.0'
      }]
    }
  }
  client.publish(T.status(DOCK_SN), JSON.stringify(body), { qos: 1 })
  log(`拓扑上报: 机场 ${DOCK_SN} 挂载飞行器 ${DRONE_SN}`)
}

/** 直播能力上报:机场双相机 + 飞行器云台相机,直播 TAB 据此拼 video_id */
function publishCapacity() {
  const body = {
    bid: uuid(),
    timestamp: Date.now(),
    data: {
      live_capacity: {
        capacity: {
          available_video_number: 3,
          device: [
            {
              sn: DOCK_SN,
              cameras: [
                { camera_index: '165-0', video_index: 'normal-0', video_types: ['normal', 'wide', 'zoom'] },
                { camera_index: '166-0', video_index: 'ir-0', video_types: ['ir'] }
              ]
            },
            {
              sn: DRONE_SN,
              cameras: [
                { camera_index: '39-0', video_index: 'normal-0', video_types: ['normal', 'zoom'] }
              ]
            }
          ]
        }
      }
    }
  }
  client.publish(T.state(DOCK_SN), JSON.stringify(body), { qos: 0 })
  log(`直播能力上报: 3 路视频源,推流中 ${liveStreams.size}`)
}

/** 0.5Hz 推送遥测:机场属性一路,无人机属性一路 */
function startOsdLoop() {
  setInterval(() => {
    // 机场
    client.publish(T.osd(DOCK_SN), JSON.stringify({
      bid: uuid(),
      timestamp: Date.now(),
      data: {
        mode_code: state.flying ? 4 : state.dockMode,
        cover_state: state.cover,
        putter_state: state.putter,
        drone_in_dock: state.flying ? 0 : 1,
        charging_state: { state: state.charging, capacity_percent: Math.round(state.battery) },
        environment_temperature: 24.5,
        wind_speed: 2.6,
        rainfall: 0,
        media_file_count: 12,
        network_state: { type: '4G', quality: 4, rate: 8.6 },
        sub_device: {
          device_sn: DRONE_SN,
          device_online_status: state.dronePowerOn,
          device_paired: 1
        }
      }
    }), { qos: 0 })

    // 无人机
    if (state.dronePowerOn) {
      if (state.flying) {
        state.altitude = Math.min(120, state.altitude + 6)
        state.battery = Math.max(5, state.battery - 0.2)
        state.lng += 0.00006       // 航线飞行中缓慢前移
        state.lat += 0.00004
      } else if (state.charging) {
        state.battery = Math.min(100, state.battery + 0.3)
      }
      client.publish(T.osd(DRONE_SN), JSON.stringify({
        bid: uuid(),
        timestamp: Date.now(),
        data: {
          latitude: state.lat,
          longitude: state.lng,
          height: Number(state.altitude.toFixed(1)),
          elevation: Number((state.altitude + 43).toFixed(1)),
          attitude_head: 92,
          horizontal_speed: state.flying ? 6.4 : 0,
          vertical_speed: state.flying ? 3.2 : 0,
          mode_code: state.flying ? 5 : 0,     // 5 航线飞行 / 0 待机
          gear: 1,
          position_state: { is_fixed: 1, quality: 5, gps_number: 18 },
          storage: { total: 64000, used: 12000 },
          gimbal: { gimbal_mode: 0, gimbal_pitch: state.flying ? -35.2 : 0, gimbal_yaw: 0 },
          battery: { capacity_percent: Math.round(state.battery), voltage: 24500, temperature: 31.5 }
        }
      }), { qos: 0 })
    }
  }, 2000)
}

/** AI 识别事件流:开关开启且飞行器上电后,4s 一个随机目标 */
function startAiLoop() {
  setInterval(() => {
    if (!state.ai.enabled || !state.dronePowerOn) return
    const type = state.ai.types.length
      ? state.ai.types[Math.floor(Math.random() * state.ai.types.length)]
      : 'PERSON'
    emitEvent('ai_target', {
      type,
      confidence: 62 + Math.floor(Math.random() * 37),
      longitude: Number((state.lng + (Math.random() - 0.5) * 0.004).toFixed(6)),
      latitude: Number((state.lat + (Math.random() - 0.5) * 0.004).toFixed(6)),
      time: Date.now()
    })
  }, 4000)
}

/** 处理云端下发的指令:按 tid 原样回复 */
client.on('message', (topic, buf) => {
  const msg = JSON.parse(buf.toString())

  if (topic === T.services(DOCK_SN)) {
    log(`收到指令: ${msg.method}  tid=${msg.tid.slice(0, 8)}…`)
    let ok
    let extra = null      // 需要随回复带回的载荷(如日志清单)
    switch (msg.method) {
      case 'flighttask_prepare':  ok = prepareFlightTask(msg.data); break
      case 'flighttask_execute':  ok = executeFlightTask(msg.data); break
      case 'flighttask_undo':     ok = undoFlightTask(msg.data); break
      case 'flighttask_pause':    ok = pauseFlightTask(msg.data); break
      case 'flighttask_recovery': ok = recoveryFlightTask(msg.data); break
      case 'in_flight_wayline_deliver': ok = deliverInFlightWayline(msg.data); break
      case 'in_flight_wayline_stop':    ok = inFlightWaylineAction(msg.data, 'stop'); break
      case 'in_flight_wayline_recover': ok = inFlightWaylineAction(msg.data, 'recover'); break
      case 'in_flight_wayline_cancel':  ok = inFlightWaylineAction(msg.data, 'cancel'); break
      case 'takeoff_to_point':    ok = takeoffToPoint(msg.data); break
      case 'ota_create':          ok = startOta(msg.data); break
      case 'logs_file_list':      ok = true; extra = { files: logFiles() }; break
      case 'logs_file_upload':    ok = uploadLogs(msg.data); break
      case 'live_start_push':     ok = liveStartPush(msg.data); break
      case 'live_stop_push':      ok = liveStopPush(msg.data); break
      case 'live_set_quality':    ok = liveSetQuality(msg.data); break
      case 'live_lens_change':    ok = liveLensChange(msg.data); break
      case 'live_camera_change':  ok = liveCameraChange(msg.data); break
      case 'upload_flighttask_media_prioritize': ok = mediaPrioritize(msg.data); break
      default:                    ok = applyCommand(msg.method)
    }

    const data = extra
      ? { result: ok ? 0 : 1, ...extra }
      : { result: ok ? 0 : 1, output: { status: ok ? 'ok' : 'rejected' } }
    client.publish(T.servicesReply(DOCK_SN), JSON.stringify({
      tid: msg.tid,
      bid: msg.bid,
      timestamp: Date.now(),
      method: msg.method,
      data
    }), { qos: 1 })

    // 舱盖/推杆等单元操作也报一次进度事件,让事件流可见
    if (['cover_open', 'cover_close', 'drone_open', 'drone_close'].includes(msg.method)) {
      emitEvent('flighttask_progress', { status: ok ? 'ok' : 'failed', progress: ok ? 100 : 0, current_step: 1 })
    }
    log(`已回复 ${msg.method} → result=${ok ? 0 : 1}`)
  }

  if (topic === T.statusReply(DOCK_SN)) {
    log(`拓扑回执: ${JSON.stringify(msg.data)}`)
  }
  if (topic === T.requestsReply(DOCK_SN)) {
    const cont = pendingRequests.get(msg.tid)
    if (cont) {
      pendingRequests.delete(msg.tid)
      cont(msg.data || {})
    } else {
      log(`请求回执(未跟接续): ${msg.method} ${JSON.stringify(msg.data).slice(0, 120)}`)
    }
  }
  if (topic === T.propertySet(DOCK_SN)) {
    applyPropertySet(msg.data || {})
    log(`属性设置: ${JSON.stringify(msg.data)}`)
    client.publish(`thing/product/${DOCK_SN}/property/set_reply`, JSON.stringify({
      tid: msg.tid, bid: msg.bid, timestamp: Date.now(), method: msg.method, data: { result: 0 }
    }), { qos: 1 })
  }
})

/** 设备主动请求(云端会回 requests_reply) */
function sendRequest(method, data, onReply) {
  const tid = uuid()
  if (onReply) pendingRequests.set(tid, onReply)
  client.publish(T.requests(DOCK_SN), JSON.stringify({
    tid, bid: uuid(), timestamp: Date.now(), method, data
  }), { qos: 1 })
  return tid
}

/** 事件上报(平台会自动回执) */
function emitEvent(method, data) {
  client.publish(T.events(DOCK_SN), JSON.stringify({
    tid: uuid(),
    bid: uuid(),
    timestamp: Date.now(),
    method,
    data
  }), { qos: 0 })
}

/* ==================== 航线任务 ==================== */

/** 常规任务的进度步骤:2s 一步;条件任务执行前先向云端拉取航线文件 */
function flightSteps(flightId) {
  const steps = [
    { delay: 500, fn: () => emitProgress(flightId, 'sent', 0) },
    { delay: 1500, fn: () => emitProgress(flightId, 'queued', 5) },
    { delay: 2500, fn: () => emitProgress(flightId, 'in_progress', 10, { breakpoint: breakpoint(1, 10) }) },
    { delay: 2000, fn: () => emitProgress(flightId, 'in_progress', 25, { breakpoint: breakpoint(2, 25) }) },
    { delay: 2000, fn: () => emitProgress(flightId, 'in_progress', 40, { breakpoint: breakpoint(3, 40) }) },
    { delay: 2000, fn: () => emitProgress(flightId, 'in_progress', 55, { breakpoint: breakpoint(4, 55) }) },
    { delay: 2000, fn: () => emitProgress(flightId, 'in_progress', 70, { breakpoint: breakpoint(5, 70) }) },
    { delay: 2000, fn: () => emitProgress(flightId, 'in_progress', 85, { breakpoint: breakpoint(6, 85) }) },
    {
      delay: 2000, fn: () => {
        emitProgress(flightId, 'ok', 100, { media_count: 6 })
        landAndStow()
        requestMediaUpload(flightId, 6)
        log(`任务完成: flight_id=${flightId}`)
      }
    }
  ]
  return steps
}

function breakpoint(index, progress) {
  return { index, progress, remain_margin: 60 - progress }
}

/** 按步调度:暂停即掐掉当前定时器,恢复从 stepIdx 继续 */
function scheduleStep(flightId, idx) {
  const job = jobs.get(flightId)
  if (!job || job.canceled || idx >= job.steps.length) return
  job.stepIdx = idx
  const step = job.steps[idx]
  job.timer = setTimeout(() => {
    step.fn()
    scheduleStep(flightId, idx + 1)
  }, step.delay)
}

function prepareFlightTask(data) {
  if (!data?.flight_id) return false
  const taskType = data.task_type ?? 0
  jobs.set(data.flight_id, {
    steps: flightSteps(data.flight_id),
    stepIdx: 0,
    timer: null,
    canceled: false,
    paused: false,
    taskType,
    channel: 'FLIGHTTASK'
  })
  const typeText = ['立即', '定时', '条件'][taskType] ?? '立即'
  log(`任务就绪: flight_id=${data.flight_id} [${typeText}任务]`)
  // 条件任务:监听 ready_conditions,满足后(模拟 5s)发 flighttask_ready 通知云端触发 execute
  if (taskType === 2) {
    setTimeout(() => {
      const job = jobs.get(data.flight_id)
      if (job && !job.canceled && job.stepIdx === 0) {
        emitEvent('flighttask_ready', { flight_id: data.flight_id, ready_time: Date.now() })
        log(`条件满足,任务就绪: flight_id=${data.flight_id}`)
      }
    }, 5000)
  }
  return true
}

function executeFlightTask(data) {
  const job = jobs.get(data?.flight_id)
  if (!job || job.canceled) return false
  const begin = () => {
    takeoffSequence()
    scheduleStep(data.flight_id, 0)
  }
  // 条件任务执行前设备主动拉取航线文件(flighttask_resource_get 请求 → 云端回文件句柄)
  if (job.taskType === 2 && job.stepIdx === 0) {
    sendRequest('flighttask_resource_get', { flight_id: data.flight_id }, (reply) => {
      if (reply?.file?.url) {
        log(`已获取航线文件: ${reply.file.url}`)
        begin()
      } else {
        log('航线文件获取失败,任务不执行')
        emitProgress(data.flight_id, 'failed', 0, { message: '航线文件获取失败' })
      }
    })
  } else {
    begin()
  }
  return true
}

function undoFlightTask(data) {
  const job = jobs.get(data?.flight_id)
  if (!job) return false
  job.timers?.forEach(clearTimeout)
  clearTimeout(job.timer)
  job.canceled = true
  emitProgress(data.flight_id, 'cancel', 0)
  if (state.flying) landAndStow()
  log(`任务取消: flight_id=${data.flight_id}`)
  return true
}

function pauseFlightTask(data) {
  const job = jobs.get(data?.flight_id)
  if (!job || job.canceled || job.paused) return false
  clearTimeout(job.timer)
  job.paused = true
  const held = job.steps[Math.max(0, job.stepIdx - 1)]
  held?.fn && emitProgress(data.flight_id, 'pause', lastProgressOf(job), {})
  log(`任务暂停: flight_id=${data.flight_id}`)
  return true
}

function recoveryFlightTask(data) {
  const job = jobs.get(data?.flight_id)
  if (!job || job.canceled || !job.paused) return false
  job.paused = false
  emitProgress(data.flight_id, 'recovering', lastProgressOf(job))
  scheduleStep(data.flight_id, job.stepIdx)
  log(`任务恢复: flight_id=${data.flight_id}`)
  return true
}

/** 最近一步的进度值(暂停/恢复事件带上,便于平台状态机对齐) */
function lastProgressOf(job) {
  const progressAt = [0, 5, 10, 25, 40, 55, 70, 85, 100]
  return progressAt[Math.min(job.stepIdx, progressAt.length - 1)] ?? 0
}

/* ==================== 空中下发航线(Dock 3) ==================== */

function deliverInFlightWayline(data) {
  if (!data?.flight_id) return false
  if (!state.dronePowerOn || !state.flying) {
    log('空中下发被拒:飞行器不在空中')
    return false
  }
  const steps = [
    { delay: 500, fn: () => inFlightProgress(data.flight_id, 'in_progress', 20) },
    { delay: 2000, fn: () => inFlightProgress(data.flight_id, 'in_progress', 50) },
    { delay: 2000, fn: () => inFlightProgress(data.flight_id, 'in_progress', 80) },
    {
      delay: 2000, fn: () => {
        inFlightProgress(data.flight_id, 'ok', 100)
        requestMediaUpload(data.flight_id, 2)
        log(`空中航线完成: flight_id=${data.flight_id}`)
      }
    }
  ]
  jobs.set(data.flight_id, {
    steps, stepIdx: 0, timer: null, canceled: false, paused: false, taskType: 0, channel: 'IN_FLIGHT'
  })
  scheduleStep(data.flight_id, 0)
  log(`空中下发航线: flight_id=${data.flight_id}`)
  return true
}

function inFlightWaylineAction(data, action) {
  const job = jobs.get(data?.flight_id)
  if (!job || job.channel !== 'IN_FLIGHT') return false
  if (action === 'stop') {
    clearTimeout(job.timer)
    job.paused = true
    inFlightProgress(data.flight_id, 'pause', lastProgressOf(job))
    log(`空中航线悬停: flight_id=${data.flight_id}`)
    return true
  }
  if (action === 'recover') {
    if (!job.paused) return false
    job.paused = false
    inFlightProgress(data.flight_id, 'recovering', lastProgressOf(job))
    scheduleStep(data.flight_id, job.stepIdx)
    log(`空中航线恢复: flight_id=${data.flight_id}`)
    return true
  }
  // cancel:取消航线并自动返航
  clearTimeout(job.timer)
  job.canceled = true
  inFlightProgress(data.flight_id, 'cancel', 0)
  emitReturnHomeInfo(data.flight_id)
  setTimeout(() => landAndStow(), 8000)
  log(`空中航线取消并返航: flight_id=${data.flight_id}`)
  return true
}

function inFlightProgress(flightId, status, progress) {
  emitEvent('in_flight_wayline_progress', { flight_id: flightId, status, progress })
}

/** 返航轨迹上报:取当前坐标外推 5 个规划点 */
function emitReturnHomeInfo(flightId) {
  const points = []
  for (let i = 0; i < 5; i++) {
    points.push({
      longitude: Number((state.lng - i * 0.0004).toFixed(6)),
      latitude: Number((state.lat - i * 0.0003).toFixed(6)),
      height: Number((Math.max(60, state.altitude) - i * 12).toFixed(1))
    })
  }
  emitEvent('return_home_info', { flight_id: flightId, planning_path: points })
}

function emitProgress(flightId, status, progress, extra = {}) {
  emitEvent('flighttask_progress', { flight_id: flightId, status, progress, ...extra })
}

/** 起飞前序:开舱盖 → 推杆 → 飞行器上电 */
function takeoffSequence() {
  state.dockMode = 4
  state.cover = 1
  state.putter = 1
  state.dronePowerOn = 1
  state.flying = true
  state.charging = 0
}

/** 任务结束:飞行器回落舱内,舱盖推杆收回并恢复充电 */
function landAndStow() {
  state.flying = false
  state.altitude = 0
  state.cover = 0
  state.putter = 0
  state.charging = 1
  state.dockMode = 0
}

/* ==================== 媒体上传(Dock 3) ==================== */

/** 任务完成后设备先向云端要存储配置,拿到 bucket/前缀后逐个回调上传结果 */
function requestMediaUpload(flightId, count) {
  sendRequest('storage_config_get', { module: 0 }, (reply) => {
    const prefix = reply?.object_key_prefix || 'media'
    log(`已获取存储配置: bucket=${reply?.bucket} prefix=${prefix}`)
    emitEvent('highest_priority_upload_flighttask_media', { flight_id: flightId })
    for (let i = 0; i < count; i++) {
      const isOriginal = i % 2 === 0
      setTimeout(() => {
        emitEvent('file_upload_callback', {
          file: {
            name: `${flightId.slice(0, 6)}_DJI_${String(i + 1).padStart(4, '0')}.${isOriginal ? 'JPG' : 'THR'}`,
            path: `${flightId}/${i + 1}`,
            flight_id: flightId,
            object_key: `${prefix}/${flightId}/${i + 1}_${Date.now()}.${isOriginal ? 'jpg' : 'thm'}`,
            sub_file_type: isOriginal ? 0 : 2,
            is_original: isOriginal,
            drone_model_key: '3TDDL-116',
            payload_model_key: '3TDDL-53',
            metadata: {
              longitude: Number((116.397 + i * 0.001).toFixed(6)),
              latitude: Number((39.909 + i * 0.0008).toFixed(6)),
              absolute_altitude: 132.4,
              relative_altitude: 88.5,
              gimbal_yaw_degree: -12.5,
              create_time: Date.now() - i * 1000
            }
          },
          result: 0
        })
      }, 800 + i * 700)
    }
    log(`媒体上传开始: flight_id=${flightId} ${count} 个文件`)
  })
}

/** 云端指定某任务媒体优先上传:设备回报确认 */
function mediaPrioritize(data) {
  if (!data?.flight_id) return false
  log(`媒体优先上传确认: flight_id=${data.flight_id}`)
  return true
}

/* ==================== 直播(Dock 3) ==================== */

function liveStartPush(data) {
  if (!data?.video_id || !data?.url_type) return false
  liveStreams.set(data.video_id, { url: data.url, quality: data.video_quality ?? 0 })
  state.lens = 'normal'
  publishCapacity()
  log(`直播开流: ${data.video_id} url_type=${data.url_type} quality=${data.video_quality}`)
  return true
}

function liveStopPush(data) {
  if (!data?.video_id || !liveStreams.has(data.video_id)) return false
  liveStreams.delete(data.video_id)
  publishCapacity()
  log(`直播停流: ${data.video_id}`)
  return true
}

function liveSetQuality(data) {
  const stream = liveStreams.get(data?.video_id)
  if (!stream) return false
  stream.quality = data.video_quality
  log(`直播清晰度: ${data.video_id} → ${data.video_quality}`)
  return true
}

function liveLensChange(data) {
  if (!['normal', 'wide', 'zoom', 'ir'].includes(data?.video_type)) return false
  state.lens = data.video_type
  log(`直播镜头切换: ${data.video_type}`)
  return true
}

function liveCameraChange(data) {
  if (data?.camera_position !== 0 && data?.camera_position !== 1) return false
  state.cameraPos = data.camera_position
  log(`直播相机位切换: ${data.camera_position === 0 ? '舱内' : '舱外'}`)
  return true
}

/* ==================== 远程调试 ==================== */

/** 指定点起飞:飞行器上电爬升到指定高度,悬停待命 */
function takeoffToPoint(data) {
  if (!data?.longitude || !data?.latitude) return false
  state.dockMode = 2
  state.cover = 1
  state.putter = 1
  state.dronePowerOn = 1
  state.flying = true
  state.charging = 0
  state.lng = data.longitude
  state.lat = data.latitude
  log(`指定点起飞: (${data.longitude}, ${data.latitude}) 高度 ${data.height ?? 50}m`)
  return true
}

/* ==================== 固件升级 ==================== */

function startOta(data) {
  if (!data?.file_url) return false
  state.dockMode = 3
  emitEvent('ota_progress', { status: 'downloading', progress: 0 })
  const t = (ms, fn) => setTimeout(fn, ms)
  t(2000, () => emitEvent('ota_progress', { status: 'downloading', progress: 15, message: '正在下载固件包' }))
  t(4000, () => emitEvent('ota_progress', { status: 'downloading', progress: 35, message: '正在下载固件包' }))
  t(6000, () => emitEvent('ota_progress', { status: 'downloading', progress: 50, message: '下载完成,校验 MD5' }))
  t(8000, () => emitEvent('ota_progress', { status: 'upgrading', progress: 55, message: '写入固件' }))
  t(10000, () => emitEvent('ota_progress', { status: 'upgrading', progress: 75, message: '写入固件' }))
  t(12000, () => emitEvent('ota_progress', { status: 'upgrading', progress: 90, message: '设备重启中' }))
  t(15000, () => {
    emitEvent('ota_progress', { status: 'success', progress: 100, message: '升级完成' })
    state.dockMode = 0
    // 升级重启后例行播报一条 HMS
    emitEvent('hms', {
      hms_list: [{ code: 'device_reboot_notice', level: 0, module_index: 0, message: '固件升级完成,设备已重启', ts: Date.now() }]
    })
    log('固件升级完成')
  })
  log(`OTA 开始: ${data.file_url}`)
  return true
}

/* ==================== 远程日志 ==================== */

/** 模拟设备侧日志文件清单:机场 5 个 + 飞行器 3 个 */
function logFiles() {
  const now = Date.now()
  const dock = [
    ['0001', 'dock_default_20260918.log'],
    ['0002', 'dock_link_20260918.log'],
    ['0003', 'dock_mqtt_20260918.log'],
    ['0004', 'dock_cover_20260918.log'],
    ['0005', 'dock_charge_20260918.log']
  ]
  const drone = [
    ['1001', 'drone_default_20260918.log'],
    ['1002', 'drone_flight_20260918.log'],
    ['1003', 'drone_gimbal_20260918.log']
  ]
  const toNode = ([id, name], module) => ({
    file_id: id,
    name,
    module,
    size: 1_500_000 + Number(id) * 137_000,
    time: now - Number(id) * 3_600_000
  })
  return [...dock.map((f) => toNode(f, 'DOCK')), ...drone.map((f) => toNode(f, 'DRONE'))]
}

/** 上传所选日志:每个文件错峰推进 25/50/75/100 */
function uploadLogs(data) {
  const files = data?.files
  if (!Array.isArray(files) || files.length === 0) return false
  files.forEach(({ file_id }, idx) => {
    const base = idx * 1500
    ;[25, 50, 75, 100].forEach((p, i) => {
      setTimeout(() => emitEvent('logs_file_upload_progress', {
        file_id,
        progress: p,
        status: 'uploading'
      }), base + i * 1200)
    })
  })
  log(`日志上传: ${files.length} 个文件`)
  return true
}

/* ==================== 属性设置(AI 配置) ==================== */

function applyPropertySet(d) {
  if ('ai_switch' in d) state.ai.enabled = !!d.ai_switch
  if ('ai_follow_switch' in d) state.ai.follow = !!d.ai_follow_switch
  if (d.ai_model) state.ai.model = d.ai_model
  if (Array.isArray(d.ai_target_filter_list)) state.ai.types = d.ai_target_filter_list
  log(`AI 配置生效: ${JSON.stringify(state.ai)}`)
}

/* ==================== 单元指令 ==================== */

/** 指令 → 设备状态变化 */
function applyCommand(method) {
  switch (method) {
    case 'cover_open':   state.cover = 1; return true
    case 'cover_close':  state.cover = 0; return true
    case 'putter_open':  state.putter = 1; return true
    case 'putter_close': state.putter = 0; return true
    case 'charge_open':  state.charging = 1; return true
    case 'charge_close': state.charging = 0; return true
    case 'drone_open':   state.dronePowerOn = 1; return true
    case 'drone_close':  state.dronePowerOn = 0; state.flying = false; state.altitude = 0; return true
    case 'return_home':
      // 返航前上报规划轨迹,20s 后落舱归位
      emitReturnHomeInfo('manual-rth')
      state.flying = true
      setTimeout(() => landAndStow(), 20000)
      return true
    case 'return_home_cancel':
      state.flying = false
      emitEvent('device_exit_homing_notify', { reason: 0 })
      return true
    case 'drone_self_check': return true
    case 'device_reboot': return true
    default: return false      // 未实现的指令如实回 result=1,便于验证失败路径
  }
}

process.on('SIGINT', () => {
  log('模拟器退出')
  client.end(true, () => process.exit(0))
})
