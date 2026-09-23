<template>
  <div class="sub">
    <div class="scr-kpi-row" style="margin-bottom: 12px">
      <div class="scr-kpi" style="--kpi-line: linear-gradient(180deg, #38bdf8, #1d4ed8)">
        <div class="ico" v-html="ICONS.chip"></div>
        <div class="body">
          <div class="label">设备总数</div>
          <div class="value">{{ deviceTotal }}</div>
        </div>
      </div>
      <div class="scr-kpi" style="--kpi-line: linear-gradient(180deg, #34d399, #0e7490)">
        <div class="ico" v-html="ICONS.wifi"></div>
        <div class="body">
          <div class="label">在线设备</div>
          <div class="value good">{{ onlineTotal }}</div>
        </div>
      </div>
      <div class="scr-kpi" style="--kpi-line: linear-gradient(180deg, #818cf8, #4338ca)">
        <div class="ico" v-html="ICONS.dock"></div>
        <div class="body">
          <div class="label">机场(在线/总)</div>
          <div class="value cyan">{{ stats.DOCK?.online ?? 0 }} / {{ stats.DOCK?.total ?? 0 }}</div>
        </div>
      </div>
      <div class="scr-kpi" style="--kpi-line: linear-gradient(180deg, #7dd3fc, #0284c7)">
        <div class="ico" v-html="ICONS.drone"></div>
        <div class="body">
          <div class="label">无人机(在线/总)</div>
          <div class="value cyan">{{ stats.DRONE?.online ?? 0 }} / {{ stats.DRONE?.total ?? 0 }}</div>
        </div>
      </div>
    </div>

    <div class="cols">
      <!-- ===== 左列:设备台账 + 遥测 ===== -->
      <section class="col">
        <div class="scr-panel grow">
          <div class="panel-hd">
            设备遥测
            <span class="hd-note">点击选中联动 3D</span>
          </div>
          <div class="scroll">
            <div v-for="d in devices" :key="d.id" class="scr-row click" :class="{ on: d.id === selectedId }"
              :style="{ borderLeftColor: d.status === 'ONLINE' ? 'rgba(52,211,153,.8)' : 'rgba(100,116,139,.6)' }"
              @click="select(d.id)">
              <div class="main">
                <span class="t1">
                  <img class="dev-ico" :src="resolveDeviceIcon(d, { online: d.status === 'ONLINE', color: '#7dd3fc', bg: 'transparent' })" />
                  <i class="st-dot" :class="d.status === 'ONLINE' ? 'on' : 'off'" />{{ d.name }}
                </span>
                <span class="t2">
                  {{ dictLabel(DEVICE_TYPE, d.deviceType) }} · {{ d.deviceModel }}
                  {{ d.gatewayName ? ` · 挂载 ${d.gatewayName}` : '' }}
                </span>
              </div>
              <div class="side">
                <span class="batt" :class="battClass(d.batteryPercent)">{{ d.batteryPercent ?? '--' }}%</span>
                <span class="mode">{{ d.modeCode != null ? dictLabel(AIRCRAFT_MODE, d.modeCode) : (d.status === 'ONLINE' ? '待命' : '离线') }}</span>
              </div>
            </div>
            <div v-if="!devices.length" class="empty">暂无设备(等待模拟器 / 真机接入)</div>
          </div>
        </div>
      </section>

      <!-- ===== 中列:3D 设备孪生 ===== -->
      <section class="col center">
        <div class="scr-panel grow scene-panel">
          <div class="panel-hd">
            设备三维孪生
            <span class="hd-note">无人机姿态 / 机场舱盖 实时驱动</span>
          </div>
          <div class="scene-host">
            <Scene3D :opts="{ orbitSpeed: 0.1, radius: 11.5, height: 6.8 }" @ready="onSceneReady" />
            <div class="scene-hud">
              <span class="chip">{{ selected?.name || '未选中设备' }}</span>
              <span class="chip" v-if="selected?.batteryPercent != null">电量 {{ selected.batteryPercent }}%</span>
              <span class="chip" v-if="selected?.height != null">相对高度 {{ selected.height }} m</span>
              <span class="chip">{{ dockCover ? '舱盖开启' : '舱盖闭合' }}</span>
            </div>
            <div class="scene-tip">旋翼转速随飞行状态变化</div>
          </div>
        </div>
      </section>

      <!-- ===== 右列:指令 + 事件 ===== -->
      <section class="col">
        <div class="scr-panel grow">
          <div class="panel-hd">指令下发记录</div>
          <div class="scroll">
            <div v-for="c in recentCommands" :key="c.id" class="scr-row" style="border-left-color: rgba(129,140,248,.7)">
              <div class="main">
                <span class="t1 mono">{{ c.method }}</span>
                <span class="t2">{{ c.deviceSn }} · {{ (c.sentAt || '').slice(5, 16).replace('T', ' ') }}</span>
              </div>
              <el-tag size="small" :type="dictTag(COMMAND_STATUS, c.status)" effect="dark">
                {{ dictLabel(COMMAND_STATUS, c.status) }}
              </el-tag>
            </div>
            <div v-if="!recentCommands.length" class="empty">暂无指令记录</div>
          </div>
        </div>

        <div class="scr-panel grow">
          <div class="panel-hd">设备事件</div>
          <div class="scroll">
            <div v-for="e in recentEvents" :key="e.id" class="scr-row"
              :style="{ borderLeftColor: e.level === 'ERROR' ? 'rgba(248,113,113,.8)' : e.level === 'WARN' ? 'rgba(251,191,36,.8)' : 'rgba(56,189,248,.6)' }">
              <div class="main">
                <span class="t1">{{ dictLabel(DEVICE_EVENT_TYPE, e.eventType) }}</span>
                <span class="t2">{{ e.deviceSn }} · {{ (e.createTime || '').slice(5, 16).replace('T', ' ') }}</span>
              </div>
              <el-tag size="small" :type="e.level === 'ERROR' ? 'danger' : e.level === 'WARN' ? 'warning' : 'info'" effect="plain">
                {{ e.level }}
              </el-tag>
            </div>
            <div v-if="!recentEvents.length" class="empty">暂无事件</div>
          </div>
        </div>
      </section>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted } from 'vue'
import http from '../../api'
import Scene3D from './Scene3D.vue'
import { DEVICE_TYPE, AIRCRAFT_MODE, COMMAND_STATUS, DEVICE_EVENT_TYPE, dictLabel, dictTag } from '../../utils/dict'
import { resolveDeviceIcon } from '../../utils/deviceIcon'

const REFRESH_MS = 5000 // 设备遥测屏刷新更勤
const data = ref({})
const selectedId = ref(null)
let timer = null
let sceneApi = null
let droneApi = null
let dockApi = null
let animTimer = null

const devices = computed(() => data.value.devices || [])
const stats = computed(() => data.value.deviceStats || {})
const recentCommands = computed(() => data.value.recentCommands || [])
const recentEvents = computed(() => data.value.recentEvents || [])

const deviceTotal = computed(() =>
  Object.values(stats.value).reduce((s, v) => s + (v?.total ?? 0), 0))
const onlineTotal = computed(() =>
  Object.values(stats.value).reduce((s, v) => s + (v?.online ?? 0), 0))

/** 选中的无人机(默认取第一台 DRONE);无无人机时展示机场本体 */
const selected = computed(() => devices.value.find((d) => d.id === selectedId.value) || null)
const drone = computed(() =>
  devices.value.find((d) => d.id === selectedId.value && d.deviceType === 'DRONE')
  || devices.value.find((d) => d.deviceType === 'DRONE') || null)
const dockCover = computed(() => (drone.value?.modeCode ?? 0) >= 2)

const ico = (p) => `<svg viewBox="0 0 24 24" fill="none" stroke="#7dd3fc" stroke-width="1.7"
  stroke-linecap="round" stroke-linejoin="round">${p}</svg>`
const ICONS = {
  chip: ico('<rect x="7" y="7" width="10" height="10" rx="2"/><path d="M12 4v3M12 17v3M4 12h3M17 12h3"/><circle cx="12" cy="12" r="1.6"/>'),
  wifi: ico('<path d="M2.5 9a15 15 0 0 1 19 0"/><path d="M5.5 12.5a10 10 0 0 1 13 0"/><path d="M8.5 16a5 5 0 0 1 7 0"/><circle cx="12" cy="19" r="0.6" fill="currentColor"/>'),
  dock: ico('<rect x="4" y="14" width="16" height="5" rx="1.5"/><path d="M8 14v-3M16 14v-3"/><path d="M12 11V4"/><path d="M9.5 6.2 12 3.6l2.5 2.6"/>'),
  drone: ico('<rect x="9" y="9" width="6" height="6" rx="1.5"/><path d="M9 9 5.5 5.5M15 9l3.5-3.5M9 15l-3.5 3.5M15 15l3.5 3.5"/><circle cx="5" cy="5" r="1.8"/><circle cx="19" cy="5" r="1.8"/><circle cx="5" cy="19" r="1.8"/><circle cx="19" cy="19" r="1.8"/>')
}

function battClass(v) {
  if (v == null) return ''
  if (v < 30) return 'low'
  if (v < 60) return 'mid'
  return 'ok'
}

/* ==================== 3D:机场 + 无人机孪生 ==================== */

function onSceneReady(api) {
  sceneApi = api
  api.setOrbit({ radius: 11.5, height: 6.8, speed: 0.1 })
  dockApi = api.addDockModel({ scale: 1 })
  droneApi = api.addDroneModel({ scale: 1.15 })
  droneApi.group.position.set(0, 2.4, 1.35)
  updateModels()
}

function updateModels() {
  if (!dockApi || !droneApi) return
  dockApi.setCover(dockCover.value)
  const m = drone.value?.modeCode ?? 1
  const flying = m >= 2
  droneApi.setPose({ rotor: flying ? 1 : 0.25 })
}

/** 轻量动画:无人机悬停微沉浮 + 缓慢自转(无遥测时也有"活"感) */
function startAnim() {
  let t = 0
  animTimer = setInterval(() => {
    t += 0.12
    if (!droneApi) return
    const flying = (drone.value?.modeCode ?? 1) >= 2
    const y = flying ? 3.5 + Math.sin(t) * 0.22 : 2.4 + Math.sin(t * 0.5) * 0.06
    const r = flying ? 1.6 : 0.35
    droneApi.group.position.set(Math.cos(t * 0.3) * r, y, 1.35 + Math.sin(t * 0.3) * r)
    droneApi.setPose({ heading: (t * 18) % 360, rotor: flying ? 1 : 0.25 })
  }, 120)
}

function select(id) {
  selectedId.value = id
  updateModels()
}

/* ==================== 数据 ==================== */

async function load() {
  data.value = (await http.get('/screen/devices')) || {}
  if (!selectedId.value && devices.value.length) {
    selectedId.value = (devices.value.find((d) => d.deviceType === 'DRONE') || devices.value[0]).id
  }
  updateModels()
}

onMounted(() => {
  load()
  timer = setInterval(load, REFRESH_MS)
  startAnim()
})
onUnmounted(() => {
  clearInterval(timer)
  clearInterval(animTimer)
  // 引擎由 Scene3D 组件自行 dispose,这里只断引用
  sceneApi = null
  droneApi = null
  dockApi = null
})
</script>

<style scoped>
.sub { display: flex; flex-direction: column; min-width: 0; }
.cols { flex: 1; min-height: 0; display: grid; grid-template-columns: 340px 1fr 380px; gap: 12px; }
.col { display: flex; flex-direction: column; gap: 12px; min-height: 0; }
.col.center { gap: 10px; }
.scene-panel { padding-bottom: 0; }

.st-dot { display: inline-block; width: 7px; height: 7px; border-radius: 50%; margin-right: 6px; vertical-align: 1px; }
.dev-ico { width: 16px; height: 16px; vertical-align: -3px; margin-right: 6px; opacity: .9; }
.st-dot.on { background: #34d399; box-shadow: 0 0 6px #34d399; }
.st-dot.off { background: #64748b; }
.batt { font-size: 13px; font-weight: 700; font-variant-numeric: tabular-nums; }
.batt.ok { color: #34d399; }
.batt.mid { color: #fbbf24; }
.batt.low { color: #f87171; }
.mode { font-size: 10.5px; color: var(--scr-dim); }
.mono { font-family: monospace; }

@media (max-width: 1500px) { .cols { grid-template-columns: 300px 1fr 330px; } }
@media (max-width: 1200px) {
  .cols { grid-template-columns: 1fr 1fr; grid-auto-rows: minmax(0, 1fr); }
  .col.center { grid-column: 1 / -1; }
}
</style>
