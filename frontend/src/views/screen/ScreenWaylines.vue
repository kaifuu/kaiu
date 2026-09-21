<template>
  <div class="sub">
    <div class="scr-kpi-row" style="margin-bottom: 12px">
      <div class="scr-kpi" style="--kpi-line: linear-gradient(180deg, #7dd3fc, #0284c7)">
        <div class="ico" v-html="ICONS.route"></div>
        <div class="body">
          <div class="label">航线总数</div>
          <div class="value cyan">{{ kpi.waylineTotal ?? 0 }}</div>
        </div>
      </div>
      <div class="scr-kpi" style="--kpi-line: linear-gradient(180deg, #38bdf8, #1d4ed8)">
        <div class="ico" v-html="ICONS.jobs"></div>
        <div class="body">
          <div class="label">任务总数</div>
          <div class="value">{{ kpi.jobTotal ?? 0 }}</div>
        </div>
      </div>
      <div class="scr-kpi" style="--kpi-line: linear-gradient(180deg, #34d399, #0e7490)">
        <div class="ico" v-html="ICONS.check"></div>
        <div class="body">
          <div class="label">任务成功率</div>
          <div class="value good">{{ kpi.successRate ?? 0 }}%</div>
        </div>
      </div>
      <div class="scr-kpi" style="--kpi-line: linear-gradient(180deg, #fbbf24, #b45309)">
        <div class="ico" v-html="ICONS.play"></div>
        <div class="body">
          <div class="label">执行中任务</div>
          <div class="value" :class="{ warn: (kpi.active ?? 0) > 0 }">{{ kpi.active ?? 0 }}</div>
        </div>
      </div>
    </div>

    <div class="cols">
      <!-- ===== 左列:航线库 ===== -->
      <section class="col">
        <div class="scr-panel grow">
          <div class="panel-hd">
            航线库
            <span class="hd-note">点击切换 3D 航线</span>
          </div>
          <div class="scroll">
            <div v-for="w in waylines" :key="w.id" class="scr-row click" :class="{ on: w.id === selectedId }"
              @click="select(w.id)">
              <div class="main">
                <span class="t1">{{ w.name }}</span>
                <span class="t2">
                  {{ w.code }} · {{ dictLabel(WAYLINE_TEMPLATE, w.templateTypes) }}
                  · {{ w.waypointCount ?? wpCount(w) }} 航点 · {{ w.alt }}m
                </span>
              </div>
              <div class="side">
                <span class="runs">{{ runsOf(w.id) }} 次执行</span>
                <span class="ok-rate">成功率 {{ successRateOf(w.id) }}%</span>
              </div>
            </div>
            <div v-if="!waylines.length" class="empty">暂无航线</div>
          </div>
        </div>
      </section>

      <!-- ===== 中列:3D 航线 ===== -->
      <section class="col center">
        <div class="scr-panel grow scene-panel">
          <div class="panel-hd">
            航线三维视图
            <span class="hd-note">{{ selectedWayline?.name || '未选择' }} · 真实高度投影</span>
          </div>
          <div class="scene-host">
            <Scene3D :opts="{ orbitSpeed: 0.08, radius: 40, height: 26 }" @ready="onSceneReady" />
            <div class="scene-legend">
              <span class="li" style="color: #7dd3fc"><i style="background: #7dd3fc" />选中航线(巡航中)</span>
              <span class="li" style="color: #6ea8e8"><i style="background: #6ea8e8" />其他航线</span>
            </div>
            <div class="scene-hud">
              <span class="chip">航线 {{ waylines.length }} 条</span>
              <span class="chip" v-if="selectedWayline">航高 {{ selectedWayline.alt }}m · {{ selectedWayline.speed }}m/s</span>
            </div>
            <div class="scene-tip">航点八面体 + 沿线巡航光点</div>
          </div>
        </div>
      </section>

      <!-- ===== 右列:任务状态 + 最近任务 ===== -->
      <section class="col">
        <div class="scr-panel" style="flex: 0 0 218px">
          <div class="panel-hd">任务状态分布</div>
          <div ref="statusRef" class="chart-box"></div>
        </div>
        <div class="scr-panel grow">
          <div class="panel-hd">最近任务</div>
          <div class="scroll">
            <div v-for="j in recentJobs" :key="j.id" class="scr-row" style="border-left-color: rgba(125,211,252,.6)">
              <div class="main">
                <span class="t1">{{ j.waylineName || j.flightId }}</span>
                <span class="t2">{{ (j.createTime || '').slice(5, 16).replace('T', ' ') }} · {{ j.flightId || '-' }}</span>
              </div>
              <el-tag size="small" :type="dictTag(WAYLINE_JOB_STATUS, j.status)" effect="dark">
                {{ dictLabel(WAYLINE_JOB_STATUS, j.status) }}
              </el-tag>
            </div>
            <div v-if="!recentJobs.length" class="empty">暂无任务</div>
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
import { echarts, AXIS, TIP, ANIM } from './echartsDark'
import { WAYLINE_TEMPLATE, WAYLINE_JOB_STATUS, dictLabel, dictTag } from '../../utils/dict'

const REFRESH_MS = 30000
const data = ref({})
const selectedId = ref(null)
let timer = null
let sceneApi = null

const kpi = computed(() => data.value.kpi || {})
const waylines = computed(() => data.value.waylines || [])
const jobByStatus = computed(() => data.value.jobByStatus || {})
const runsByWayline = computed(() => data.value.runsByWayline || [])
const recentJobs = computed(() => data.value.recentJobs || [])
const selectedWayline = computed(() => waylines.value.find((w) => w.id === selectedId.value) || null)

function parseWps(w) {
  try {
    return (JSON.parse(w.waypointsJson || '[]') || []).map((p) => ({
      lng: Number(p.longitude), lat: Number(p.latitude), h: Number(p.height) || 0
    }))
  } catch { return [] }
}
const wpCount = (w) => parseWps(w).length
const runsOf = (id) => runsByWayline.value.find((r) => r.waylineId === id)?.runs ?? 0
const successRateOf = (id) => {
  const r = runsByWayline.value.find((x) => x.waylineId === id)
  return !r || !r.runs ? 0 : Math.round((r.success / r.runs) * 100)
}

const ico = (p) => `<svg viewBox="0 0 24 24" fill="none" stroke="#7dd3fc" stroke-width="1.7"
  stroke-linecap="round" stroke-linejoin="round">${p}</svg>`
const ICONS = {
  route: ico('<path d="M4 18c6 0 4-12 10-12h6"/><path d="M16.5 3.5 20 6l-3.5 2.5"/><circle cx="4" cy="18" r="1.6" fill="currentColor"/>'),
  jobs: ico('<rect x="5" y="4" width="14" height="16" rx="2"/><path d="M9 4V2.6M15 4V2.6"/><path d="M9 10h6M9 14h4"/>'),
  check: ico('<circle cx="12" cy="12" r="8"/><path d="m8.6 12.2 2.3 2.3 4.5-4.6"/>'),
  play: ico('<circle cx="12" cy="12" r="8"/><path d="M10.2 9v6l4.8-3z"/>')
}

/* ==================== 3D ==================== */

function onSceneReady(api) {
  sceneApi = api
  buildScene()
}

function buildScene() {
  const api = sceneApi
  if (!api) return
  api.clearDynamic()

  const parsed = waylines.value.map((w) => ({ w, pts: parseWps(w) })).filter((x) => x.pts.length > 1)
  if (!parsed.length) return

  const basis = parsed.flatMap((x) => x.pts)
  const maxAlt = Math.max(10, ...basis.map((p) => p.h))
  api.setProjection(basis, maxAlt)

  for (const { w, pts } of parsed) {
    const sel = w.id === selectedId.value
    api.addRoute(pts, {
      color: sel ? '#7dd3fc' : '#6ea8e8',
      cruise: sel,
      radius: sel ? 0.07 : 0.045
    })
  }
  // 聚焦选中航线中心
  const sel = parsed.find((x) => x.w.id === selectedId.value)
  if (sel) {
    const mid = sel.pts[Math.floor(sel.pts.length / 2)]
    api.focus({ lng: mid.lng, lat: mid.lat })
  } else {
    api.focus()
  }
}

function select(id) {
  selectedId.value = id
  buildScene()
}

/* ==================== 图表 ==================== */

const statusRef = ref(null)
let statusChart = null

const STATUS_COLOR = {
  SENT: '#64748b', READY: '#38bdf8', QUEUED: '#818cf8', RUNNING: '#34d399',
  SUCCESS: '#22d3ee', FAILED: '#f87171', CANCELED: '#fbbf24', TIMEOUT: '#fb923c'
}

function renderStatus() {
  statusChart?.setOption({
    ...ANIM,
    tooltip: { trigger: 'item', ...TIP, formatter: '{b}: {c} 单 ({d}%)' },
    legend: { bottom: 0, icon: 'circle', itemWidth: 7, itemHeight: 7, textStyle: { ...AXIS, fontSize: 10 } },
    series: [{
      type: 'pie', radius: ['38%', '62%'], center: ['50%', '42%'],
      itemStyle: { borderColor: '#04102a', borderWidth: 2, borderRadius: 4 },
      label: { show: false },
      data: Object.entries(jobByStatus.value)
        .filter(([, v]) => v > 0)
        .map(([k, v]) => ({
          name: dictLabel(WAYLINE_JOB_STATUS, k), value: v,
          itemStyle: { color: STATUS_COLOR[k] || '#64748b' }
        }))
    }]
  })
}

/* ==================== 数据 ==================== */

async function load() {
  data.value = (await http.get('/screen/waylines')) || {}
  if (!selectedId.value && waylines.value.length) {
    selectedId.value = waylines.value[0].id
  }
  buildScene()
  await nextTick()
  renderStatus()
}

function resize() { statusChart?.resize() }

onMounted(async () => {
  await nextTick()
  statusChart = echarts.init(statusRef.value)
  await load()
  window.addEventListener('resize', resize)
  timer = setInterval(load, REFRESH_MS)
})
onUnmounted(() => {
  clearInterval(timer)
  window.removeEventListener('resize', resize)
  statusChart?.dispose()
  sceneApi = null
})
</script>

<style scoped>
.sub { display: flex; flex-direction: column; min-width: 0; }
.cols { flex: 1; min-height: 0; display: grid; grid-template-columns: 350px 1fr 370px; gap: 12px; }
.col { display: flex; flex-direction: column; gap: 12px; min-height: 0; }
.col.center { gap: 10px; }
.scene-panel { padding-bottom: 0; }
.runs { font-size: 12px; font-weight: 700; color: #7dd3fc; font-variant-numeric: tabular-nums; }
.ok-rate { font-size: 10.5px; color: var(--scr-dim); }

@media (max-width: 1500px) { .cols { grid-template-columns: 310px 1fr 330px; } }
@media (max-width: 1200px) {
  .cols { grid-template-columns: 1fr 1fr; grid-auto-rows: minmax(0, 1fr); }
  .col.center { grid-column: 1 / -1; }
}
</style>
