/**
 * 大疆上云 API 设备模拟器
 *
 * 扮演一台「机场 + 一台挂载的无人机」,连到平台的 Netty MQTT Broker,完整走一遍设备侧协议:
 *   1. CONNECT(clientId = 机场 SN)→ 平台标记在线
 *   2. update_topo 上报拓扑,声明挂载的无人机
 *   3. 按 0.5Hz 推送 osd 遥测(机场属性 + 无人机属性各一路)
 *   4. 订阅 services,收到云端指令后按 tid 回 services_reply
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
  lat: 39.90923
}

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
  startOsdLoop()
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
        charging_state: { state: state.charging, capacity_percent: state.battery },
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

/** 处理云端下发的指令:按 tid 原样回复 */
client.on('message', (topic, buf) => {
  const msg = JSON.parse(buf.toString())

  if (topic === T.services(DOCK_SN)) {
    log(`收到指令: ${msg.method}  tid=${msg.tid.slice(0, 8)}…`)
    const ok = applyCommand(msg.method)
    const result = ok ? 0 : 1

    client.publish(T.servicesReply(DOCK_SN), JSON.stringify({
      tid: msg.tid,
      bid: msg.bid,
      timestamp: Date.now(),
      method: msg.method,
      data: { result, output: { status: ok ? 'ok' : 'rejected' } }
    }), { qos: 1 })

    // 任务类指令额外上报一次进度事件
    if (['cover_open', 'cover_close', 'drone_open', 'drone_close'].includes(msg.method)) {
      client.publish(T.events(DOCK_SN), JSON.stringify({
        tid: msg.tid,
        bid: uuid(),
        timestamp: Date.now(),
        method: 'flighttask_progress',
        data: { status: ok ? 'ok' : 'failed', progress: ok ? 100 : 0, current_step: 1 }
      }), { qos: 0 })
    }
    log(`已回复 ${msg.method} → result=${result}`)
  }

  if (topic === T.statusReply(DOCK_SN)) {
    log(`拓扑回执: ${JSON.stringify(msg.data)}`)
  }
  if (topic === T.propertySet(DOCK_SN)) {
    log(`属性设置: ${JSON.stringify(msg.data)}`)
    client.publish(`thing/product/${DOCK_SN}/property/set_reply`, JSON.stringify({
      tid: msg.tid, bid: msg.bid, timestamp: Date.now(), method: msg.method, data: { result: 0 }
    }), { qos: 1 })
  }
})

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
    case 'return_home':  state.flying = true; setTimeout(() => { state.flying = false; state.altitude = 0 }, 20000); return true
    case 'return_home_cancel': state.flying = false; return true
    case 'drone_self_check': return true
    case 'device_reboot': return true
    default: return false      // 未实现的指令如实回 result=1,便于验证失败路径
  }
}

process.on('SIGINT', () => {
  log('模拟器退出')
  client.end(true, () => process.exit(0))
})
