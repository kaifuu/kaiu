<template>
  <div class="sub">
    <div class="scr-kpi-row" style="margin-bottom: 12px">
      <div class="scr-kpi" style="--kpi-line: linear-gradient(180deg, #38bdf8, #1d4ed8)">
        <div class="ico" v-html="ICONS.flight"></div>
        <div class="body">
          <div class="label">飞行总架次</div>
          <div class="value">{{ kpi.total ?? 0 }}</div>
        </div>
      </div>
      <div class="scr-kpi" style="--kpi-line: linear-gradient(180deg, #7dd3fc, #0284c7)">
        <div class="ico" v-html="ICONS.today"></div>
        <div class="body">
          <div class="label">今日架次</div>
          <div class="value cyan">{{ kpi.today ?? 0 }}</div>
        </div>
      </div>
      <div class="scr-kpi" style="--kpi-line: linear-gradient(180deg, #818cf8, #4338ca)">
        <div class="ico" v-html="ICONS.clock"></div>
        <div class="body">
          <div class="label">累计飞行时长</div>
          <div class="value small">{{ hoursText }}</div>
        </div>
      </div>
      <div class="scr-kpi" style="--kpi-line: linear-gradient(180deg, #34d399, #0e7490)">
        <div class="ico" v-html="ICONS.check"></div>
        <div class="body">
          <div class="label">架次成功率</div>
          <div class="value good">{{ kpi.successRate ?? 0 }}%</div>
        </div>
      </div>
      <div class="scr-kpi" style="--kpi-line: linear-gradient(180deg, #fbbf24, #b45309)">
        <div class="ico" v-html="ICONS.camera"></div>
        <div class="body">
          <div class="label">累计媒体素材</div>
          <div class="value warn">{{ kpi.mediaTotal ?? 0 }}</div>
        </div>
      </div>
    </div>

    <div class="cols">
      <!-- ===== 左列:架次列表 ===== -->
      <section class="col">
        <div class="scr-panel grow">
          <div class="panel-hd">
            飞行架次
            <span class="hd-note">点击选择回放</span>
          </div>
          <div class="scroll">
            <div v-for="f in flights" :key="f.flightId" class="scr-row click" :class="{ on: f.flightId === selectedFlightId }"
              :style="{ borderLeftColor: statusColor(f.status) }" @click="select(f.flightId)">
              <div class="main">
                <span class="t1">{{ f.waylineName || '未知航线' }}</span>
                <span class="t2">
                  {{ (f.beginAt || '').slice(5, 16).replace('T', ' ') }} ·
                  {{ f.droneName || f.droneSn || '-' }}
                </span>
              </div>
              <div class="side">
                <el-tag size="small" :type="dictTag(WAYLINE_JOB_STATUS, f.status)" effect="dark">
                  {{ dictLabel(WAYLINE_JOB_STATUS, f.status) }}
                </el-tag>
                <span class="meta">
                  {{ f.durationMin != null ? `${f.durationMin} 分钟` : '—' }} ·
                  {{ f.mediaCount ?? 0 }} 照片 · {{ f.alarmCount }} 告警
                </span>
              </div>
            </div>
            <div v-if="!flights.length" class="empty">暂无飞行记录</div>
          </div>
        </div>
      </section>

      <!-- ===== 中列:3D 轨迹回放 ===== -->
      <section class="col center">
        <div class="scr-panel grow scene-panel">
          <div class="panel-hd">
            轨迹三维回放
            <span class="hd-note">{{ selected ? `${selected.flightId} · ${selected.waylineName}` : '未选择架次' }}</span>
          </div>
          <div class="scene-host">
            <Scene3D :opts="{ orbitSpeed: 0.06, radius: 40, height: 25 }" @ready="onSceneReady" />
            <div class="scene-legend">
              <span class="li" style="color: #38bdf8"><i style="background: #38bdf8" />已飞轨迹</span>
              <span class="li" style="color: #64748b"><i style="background: #64748b" />计划航线</span>
            </div>
            <div class="scene-hud">
              <span class="chip" v-if="selected">{{ statusText }}</span>
              <span class="chip" v-if="selected">{{ progressClock }}</span>
              <span class="chip">回放 ×{{ speed }} 倍速</span>
            </div>

            <!-- 播放控制条 -->
            <div class="play-bar" v-if="selected">
              <el-button size="small" circle :type="playing ? 'warning' : 'primary'" @click="togglePlay">
                <el-icon><component :is="playing ? 'VideoPause' : 'VideoPlay'" /></el-icon>
              </el-button>
              <el-slider v-model="progress" :min="0" :max="1" :step="0.002"
                :show-tooltip="false" class="slider" @input="onSeek" />
              <span class="time">{{ progressClock }} / {{ endClock }}</span>
              <el-select v-model="speed" size="small" class="speed" @change="onSpeedChange">
                <el-option :value="1" label="×1" />
                <el-option :value="2" label="×2" />
                <el-option :value="4" label="×4" />
                <el-option :value="8" label="×8" />
              </el-select>
            </div>
          </div>
        </div>
      </section>

      <!-- ===== 右列:趋势 + 状态 ===== -->
      <section class="col">
        <div class="scr-panel" style="flex: 0 0 190px">
          <div class="panel-hd">近 7 日架次</div>
          <div ref="trendRef" class="chart-box"></div>
        </div>
        <div class="scr-panel" style="flex: 0 0 218px">
          <div class="panel-hd">架次状态分布</div>
          <div ref="statusRef" class="chart-box"></div>
        </div>
        <div class="scr-panel grow">
          <div class="panel-hd">今日架次明细</div>
          <div class="scroll">
            <div v-for="f in todayFlights" :key="f.flightId" class="scr-row">
              <div class="main">
                <span class="t1">{{ f.waylineName }}</span>
                <span class="t2">{{ (f.beginAt || '').slice(11, 16) }} 起飞 · {{ f.droneName || f.droneSn }}</span>
              </div>
              <span class="dur" v-if="f.durationMin != null">{{ f.durationMin }}′</span>
            </div>
            <div v-if="!todayFlights.length" class="empty">今日暂无架次</div>
          </div>
        </div>
      </section>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted, nextTick } from 'vue'
import http from '../../api'
import Scene3D from './Scene3D.vue'
import { echarts, AXIS, SPLIT, TIP, ANIM, barGradient } from './echartsDark'
import { WAYLINE_JOB_STATUS, dictLabel, dictTag } from '../../utils/dict'

const REFRESH_MS = 30000
const data = ref({})
const waylineIndex = ref({}) // name → waypoints
const selectedFlightId = ref(null)
const playing = ref(false)
const progress = ref(0)
const speed = ref(2)
let timer = null
let playTimer = null
let sceneApi = null
let trackApi = null

const kpi = computed(() => data.value.kpi || {})
const flights = computed(() => data.value.flights || [])
const trend = computed(() => data.value.trend || [])
const statusMap = computed(() => {
  // 从架次列表统计(回放页够用,不再单独开接口)
  const m = {}
  for (const f of flights.value) m[f.status] = (m[f.status] || 0) + 1
  return m
})
const selected = computed(() => flights.value.find((f) => f.flightId === selectedFlightId.value) || null)
const todayFlights = computed(() => {
  const today = new Date().toISOString().slice(0, 10)
  return flights.value.filter((f) => (f.beginAt || '').startsWith(today)).slice(0, 8)
})
const hoursText = computed(() => {
  const min = kpi.value.totalMinutes ?? 0
  return min >= 60 ? `${Math.floor(min / 60)} 时 ${min % 60} 分` : `${min} 分钟`
})

const STATUS_HEX = {
  SUCCESS: '#34d399', FAILED: '#f87171', CANCELED: '#fbbf24',
  RUNNING: '#38bdf8', QUEUED: '#818cf8', SENT: '#64748b', READY: '#7dd3fc'
}
const statusColor = (s) => STATUS_HEX[s] || '#38bdf8'

const statusText = computed(() =>
  selected.value ? dictLabel(WAYLINE_JOB_STATUS, selected.value.status) : '')

/** 回放时间:开始时刻 + 进度 × 时长 */
const progressClock = computed(() => {
  if (!selected.value) return '--:--'
  const durMin = selected.value.durationMin ?? 15
  const base = new Date(selected.value.beginAt || Date.now()).getTime()
  const t = new Date(base + progress.value * durMin * 60000)
  const p = (n) => String(n).padStart(2, '0')
  return `${p(t.getHours())}:${p(t.getMinutes())}:${p(t.getSeconds())}`
})
const endClock = computed(() => {
  if (!selected.value) return '--:--'
  const t = new Date(selected.value.endAt || selected.value.beginAt || Date.now())
  const p = (n) => String(n).padStart(2, '0')
  return `${p(t.getHours())}:${p(t.getMinutes())}:${p(t.getSeconds())}`
})

const ico = (p) => `<svg viewBox="0 0 24 24" fill="none" stroke="#7dd3fc" stroke-width="1.7"
  stroke-linecap="round" stroke-linejoin="round">${p}</svg>`
const ICONS = {
  flight: ico('<path d="M12 3.5 13.5 11l7 4v2l-7-2-1 5 2.5 2v1.5L12 20l-3 1.5V20l2.5-2-1-5-7 2v-2l7-4z"/>'),
  today: ico('<rect x="4" y="5" width="16" height="15" rx="2"/><path d="M8 3v4M16 3v4M4 10h16"/><path d="M12 13v3.5M10.2 14.6 12 16.4l1.8-1.8"/>'),
  clock: ico('<circle cx="12" cy="12.5" r="7.5"/><path d="M12 8.8v3.7l2.6 1.8"/><path d="M9.6 3h4.8"/>'),
  check: ico('<circle cx="12" cy="12" r="8"/><path d="m8.6 12.2 2.3 2.3 4.5-4.6"/>'),
  camera: ico('<rect x="3.5" y="7" width="17" height="13" rx="2.5"/><path d="M9 7l1.4-2.6h3.2L15 7"/><circle cx="12" cy="13.5" r="3.4"/>')
}

/* ==================== 轨迹合成与回放 ==================== */

/**
 * 历史架次没有逐秒轨迹落库(轨迹只在飞行中产生),按航线航点采样合成演示轨迹:
 * 分段线性插值 + 横向正弦摆动 + 高度微变,视觉接近真实航迹。
 */
function synthTrack(wps, status) {
  const N = 56
  const pts = []
  const segs = wps.length - 1
  for (let i = 0; i <= N; i++) {
    const k = i / N
    const pos = k * segs
    const i0 = Math.min(segs - 1, Math.floor(pos))
    const u = pos - i0
    const a = wps[i0], b = wps[i0 + 1]
    // 起飞爬升 / 末端下降包络:前 8% 从地面爬到巡航高,后 10% 降回,弧线呈现完整的飞行剖面
    const ramp = Math.max(0, Math.min(1, k / 0.08, (1 - k) / 0.1))
    pts.push({
      lng: a.lng + (b.lng - a.lng) * u + Math.sin(k * Math.PI * 5) * 0.00004,
      lat: a.lat + (b.lat - a.lat) * u + Math.cos(k * Math.PI * 4) * 0.00004,
      h: (a.h + (b.h - a.h) * u + Math.sin(k * Math.PI * 6) * 2) * (0.05 + 0.95 * ramp)
    })
  }
  // 失败/取消的架次只飞到中断点
  if (status === 'FAILED') return pts.slice(0, Math.floor(N * 0.38) + 1)
  if (status === 'CANCELED') return pts.slice(0, 2)
  return pts
}

function parseWps(json) {
  try {
    return (JSON.parse(json || '[]') || []).map((p) => ({
      lng: Number(p.longitude), lat: Number(p.latitude), h: Number(p.height) || 0
    }))
  } catch { return [] }
}

function onSceneReady(api) {
  sceneApi = api
  buildScene()
}

function buildScene() {
  const api = sceneApi
  if (!api) return
  api.clearDynamic()
  trackApi = null

  const f = selected.value
  if (!f) return
  const wps = waylineIndex.value[f.waylineName]
  if (!wps || wps.length < 2) return

  const track = synthTrack(wps, f.status)
  api.setProjection(track, Math.max(10, ...track.map((p) => p.h)))
  trackApi = api.addTrack(track, { color: statusColor(f.status) })
  // 计划航线淡色对照
  api.addRoute(track, { color: '#64748b', cruise: false, radius: 0.03 })
  trackApi?.setProgress(progress.value)
  api.focus()
}

function select(flightId) {
  selectedFlightId.value = flightId
  progress.value = 0
  playing.value = true
  buildScene()
}

function togglePlay() {
  playing.value = !playing.value
}

function onSeek(v) {
  trackApi?.setProgress(v)
}

function onSpeedChange() { /* 仅改倍速,下一拍生效 */ }

function startPlayLoop() {
  playTimer = setInterval(() => {
    if (!playing.value || !trackApi) return
    progress.value = Math.min(1, progress.value + 0.0016 * speed.value)
    trackApi.setProgress(progress.value)
    if (progress.value >= 1) playing.value = false
  }, 40)
}

/* ==================== 图表 ==================== */

const trendRef = ref(null)
const statusRef = ref(null)
let trendChart = null
let statusChart = null

function renderTrend() {
  trendChart?.setOption({
    ...ANIM,
    tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' }, ...TIP },
    grid: { left: 10, right: 12, top: 20, bottom: 4, containLabel: true },
    xAxis: {
      type: 'category', axisTick: { show: false },
      axisLine: { lineStyle: { color: 'rgba(56,189,248,.3)' } }, axisLabel: AXIS,
      data: trend.value.map((d) => String(d.date).slice(5))
    },
    yAxis: { type: 'value', minInterval: 1, splitLine: SPLIT, axisLabel: AXIS },
    series: [{
      type: 'bar', barWidth: 18,
      animationDelay: (i) => i * 90,
      itemStyle: { borderRadius: [6, 6, 0, 0] },
      data: trend.value.map((d) => ({ value: d.total, itemStyle: { color: barGradient('#38bdf8') } }))
    }]
  })
}

function renderStatus() {
  statusChart?.setOption({
    ...ANIM,
    tooltip: { trigger: 'item', ...TIP, formatter: '{b}: {c} 架次 ({d}%)' },
    legend: { bottom: 0, icon: 'circle', itemWidth: 7, itemHeight: 7, textStyle: { ...AXIS, fontSize: 10 } },
    series: [{
      type: 'pie', radius: ['40%', '64%'], center: ['50%', '42%'],
      itemStyle: { borderColor: '#04102a', borderWidth: 2, borderRadius: 4 },
      label: { show: false },
      data: Object.entries(statusMap.value)
        .filter(([, v]) => v > 0)
        .map(([k, v]) => ({
          name: dictLabel(WAYLINE_JOB_STATUS, k), value: v,
          itemStyle: { color: STATUS_HEX[k] || '#64748b' }
        }))
    }]
  })
}

/* ==================== 数据 ==================== */

async function load() {
  const [fl, wl] = await Promise.all([
    http.get('/screen/flights'),
    http.get('/screen/waylines')
  ])
  data.value = fl || {}
  const idx = {}
  for (const w of wl?.waylines || []) idx[w.name] = parseWps(w.waypointsJson)
  waylineIndex.value = idx
  if (!selectedFlightId.value && flights.value.length) {
    select(flights.value[0].flightId)
  } else {
    buildScene()
  }
  await nextTick()
  renderTrend()
  renderStatus()
}

function resize() {
  trendChart?.resize()
  statusChart?.resize()
}

onMounted(async () => {
  await nextTick()
  trendChart = echarts.init(trendRef.value)
  statusChart = echarts.init(statusRef.value)
  await load()
  window.addEventListener('resize', resize)
  startPlayLoop()
  timer = setInterval(load, REFRESH_MS)
})
onUnmounted(() => {
  clearInterval(timer)
  clearInterval(playTimer)
  window.removeEventListener('resize', resize)
  trendChart?.dispose()
  statusChart?.dispose()
  sceneApi = null
  trackApi = null
})
</script>

<style scoped>
.sub { display: flex; flex-direction: column; min-width: 0; }
.cols { flex: 1; min-height: 0; display: grid; grid-template-columns: 355px 1fr 360px; gap: 12px; }
.col { display: flex; flex-direction: column; gap: 12px; min-height: 0; }
.col.center { gap: 10px; }
.scene-panel { padding-bottom: 0; }
.meta { font-size: 10.5px; color: var(--scr-dim); font-variant-numeric: tabular-nums; }
.dur { font-size: 12px; font-weight: 700; color: #7dd3fc; }
.slider { flex: 1; margin: 0 6px; }
.slider :deep(.el-slider__runway) { background: rgba(56, 189, 248, .18); }
.slider :deep(.el-slider__bar) { background: linear-gradient(90deg, #2563eb, #38bdf8); }
.speed { width: 74px; }
.speed :deep(.el-input__wrapper) { background: rgba(6, 24, 52, .8); box-shadow: 0 0 0 1px rgba(56, 189, 248, .3) inset; }

@media (max-width: 1500px) { .cols { grid-template-columns: 310px 1fr 320px; } }
@media (max-width: 1200px) {
  .cols { grid-template-columns: 1fr 1fr; grid-auto-rows: minmax(0, 1fr); }
  .col.center { grid-column: 1 / -1; }
}
</style>
