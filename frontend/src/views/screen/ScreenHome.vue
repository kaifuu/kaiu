<template>
  <div class="sub">
    <div class="cols">
      <!-- ===== 左列 ===== -->
      <section class="col">
        <div class="scr-panel">
          <div class="panel-hd">巡检概览</div>
          <div class="overview">
            <div class="ov-item" v-for="o in overviewItems" :key="o.label">
              <span class="ov-label">{{ o.label }}</span>
              <b class="ov-value" :class="o.tone">{{ o.value }}</b>
            </div>
          </div>
        </div>

        <div class="scr-panel grow">
          <div class="panel-hd">计划任务清单</div>
          <div class="scroll">
            <div v-for="t in tasks" :key="t.id" class="scr-row">
              <div class="main">
                <span class="t1">{{ t.name }}</span>
                <span class="t2">{{ t.pointName || '-' }} · {{ t.executor || '未指派' }}</span>
              </div>
              <div class="side">
                <span class="time">{{ (t.planStart || '').slice(5, 16) }}</span>
                <el-tag size="small" :type="dictTag(TASK_STATUS, t.status)" effect="dark">
                  {{ dictLabel(TASK_STATUS, t.status) }}
                </el-tag>
              </div>
            </div>
            <div v-if="!tasks.length" class="empty">暂无任务</div>
          </div>
        </div>

        <div class="scr-panel">
          <div class="panel-hd">工单部门排行</div>
          <div class="scr-bars">
            <div v-for="(d, i) in orderByDept" :key="d.dept" class="row">
              <span class="no" :class="'n' + (i + 1)">{{ i + 1 }}</span>
              <span class="name" style="width: 92px">{{ d.dept }}</span>
              <span class="track"><i :style="{ width: barWidth(d.count, deptMax) }"></i></span>
              <span class="val">{{ d.count }}</span>
            </div>
            <div v-if="!orderByDept.length" class="empty">暂无数据</div>
          </div>
        </div>
      </section>

      <!-- ===== 中列:3D 数字孪生 ===== -->
      <section class="col center">
        <div class="scr-panel grow scene-panel">
          <div class="panel-hd">
            区域三维态势
            <span class="hd-note">数字孪生 · 点位 / 问题 / 围栏 / 航线巡航</span>
          </div>
          <div class="scene-host">
            <Scene3D :opts="{ orbitSpeed: 0.075, radius: 44, height: 27 }" @ready="onSceneReady" />
            <div class="scene-legend">
              <span class="li" style="color: #f87171"><i style="background: #f87171" />重大/极大风险</span>
              <span class="li" style="color: #fbbf24"><i style="background: #fbbf24" />高风险点位</span>
              <span class="li" style="color: #38bdf8"><i style="background: #38bdf8" />一般点位</span>
              <span class="li" style="color: #fb923c"><i style="background: #fb923c" />在办问题</span>
              <span class="li" style="color: #7dd3fc"><i style="background: #7dd3fc" />巡航航线</span>
            </div>
            <div class="scene-hud">
              <span class="chip">机场在线 {{ kpi.dockOnline ?? 0 }}/{{ kpi.dockTotal ?? 0 }}</span>
              <span class="chip">巡检点位 {{ mapPoints.length }}</span>
              <span class="chip">电子围栏 {{ fences.length }}</span>
              <span class="chip" v-if="cruiseName">巡航 · {{ cruiseName }}</span>
            </div>
            <div class="scene-tip">鼠标移动视差 · 视角自动环绕</div>
          </div>
        </div>

        <!-- KPI 环 -->
        <div class="kpi-row">
          <div v-for="k in kpiRings" :key="k.label" class="kpi">
            <svg viewBox="0 0 120 120" class="kpi-ring">
              <circle cx="60" cy="60" r="48" fill="none" stroke="rgba(56,189,248,.14)" stroke-width="9" />
              <circle cx="60" cy="60" r="48" fill="none" :stroke="k.color" stroke-width="9"
                      stroke-linecap="round" :stroke-dasharray="k.dash" transform="rotate(-90 60 60)" />
              <text x="60" y="62" class="kpi-num" text-anchor="middle">{{ k.value }}</text>
              <text x="60" y="80" class="kpi-sub" text-anchor="middle">{{ k.sub }}</text>
            </svg>
            <span class="kpi-label">{{ k.label }}</span>
          </div>
        </div>
      </section>

      <!-- ===== 右列 ===== -->
      <section class="col">
        <div class="scr-panel">
          <div class="panel-hd">AI 识别类型</div>
          <div class="scr-bars">
            <div v-for="t in issueByType" :key="t.type" class="row">
              <span class="name" style="width: 78px">{{ dictLabel(ISSUE_TYPE, t.type) }}</span>
              <span class="track" style="--bar: linear-gradient(90deg, #f97316, #fbbf24)">
                <i :style="{ width: barWidth(t.count, typeMax) }"></i>
              </span>
              <span class="val" style="color: #fbbf24">{{ t.count }}</span>
            </div>
            <div v-if="!issueByType.length" class="empty">暂无识别记录</div>
          </div>
        </div>

        <div class="scr-panel grow">
          <div class="panel-hd">
            近期问题
            <span class="hd-note">{{ recentIssues.length }} 条</span>
          </div>
          <div class="scroll">
            <div v-for="i in recentIssues" :key="i.id" class="scr-row" style="border-left-color: rgba(249,115,22,.7)">
              <div class="main">
                <span class="t1">{{ i.title }}</span>
                <span class="t2">{{ i.pointName || i.address }} · {{ (i.foundAt || '').slice(5, 16) }}</span>
              </div>
              <el-tag size="small" :type="dictTag(ISSUE_STATUS, i.status)" effect="dark">
                {{ dictLabel(ISSUE_STATUS, i.status) }}
              </el-tag>
            </div>
            <div v-if="!recentIssues.length" class="empty">暂无问题</div>
          </div>
        </div>

        <div class="scr-panel alert-panel">
          <div class="panel-hd">
            飞行安全预警
            <span class="hd-note">{{ safeAlertPending }} 条待处理</span>
          </div>
          <div class="scroll">
            <div v-for="a in recentSafeAlerts" :key="a.id" class="scr-row" style="border-left-color: rgba(248,113,113,.7)">
              <div class="main">
                <span class="t1">
                  <i class="al-dot" :class="a.level === 'ERROR' ? 'err' : 'warn'" />{{ a.title }}
                </span>
                <span class="t2">{{ a.deviceSn }} · {{ (a.occurredAt || '').slice(5, 16).replace('T', ' ') }}</span>
              </div>
              <el-tag size="small" :type="dictTag(SAFE_ALERT_STATUS, a.status)" effect="dark">
                {{ dictLabel(SAFE_ALERT_STATUS, a.status) }}
              </el-tag>
            </div>
            <div v-if="!recentSafeAlerts.length" class="empty">暂无飞行安全预警</div>
          </div>
        </div>
      </section>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted } from 'vue'
import http from '../../api'
import { RISK_COLORS } from './scene3d'
import Scene3D from './Scene3D.vue'
import { TASK_STATUS, ISSUE_TYPE, ISSUE_STATUS, SAFE_ALERT_STATUS, dictLabel, dictTag } from '../../utils/dict'

const REFRESH_MS = 30000
const data = ref({})
const tasks = ref([])
const fences = ref([])
const waylines = ref([])
let timer = null
let sceneApi = null

const kpi = computed(() => data.value.kpi || {})
const device = computed(() => data.value.device || {})
const issueByType = computed(() => data.value.issueByType || [])
const orderByDept = computed(() => data.value.orderByDept || [])
const recentIssues = computed(() => data.value.recentIssues || [])
const recentSafeAlerts = computed(() => data.value.recentSafeAlerts || [])
const safeAlertPending = computed(() => data.value.safeAlertPending ?? 0)
const mapPoints = computed(() => data.value.mapPoints || [])
const cruiseName = computed(() => waylines.value[0]?.name || '')

const deptMax = computed(() => Math.max(1, ...orderByDept.value.map((d) => d.count)))
const typeMax = computed(() => Math.max(1, ...issueByType.value.map((t) => t.count)))

const overviewItems = computed(() => [
  { label: '巡检点位', value: kpi.value.pointTotal ?? 0, tone: '' },
  { label: '机场(在线/总)', value: `${kpi.value.dockOnline ?? 0} / ${kpi.value.dockTotal ?? 0}`, tone: 'good' },
  { label: '无人机(在线/总)', value: `${device.value.DRONE?.online ?? 0} / ${device.value.DRONE?.total ?? 0}`, tone: 'good' },
  { label: '巡检任务', value: kpi.value.taskTotal ?? 0, tone: '' },
  { label: '待处理问题', value: (kpi.value.issueTotal ?? 0) - (kpi.value.orderDone ?? 0), tone: 'warn' },
  { label: '未办结工单', value: (kpi.value.orderTotal ?? 0) - (kpi.value.orderDone ?? 0), tone: 'warn' },
  { label: '未闭环隐患', value: kpi.value.hazardOpen ?? 0, tone: 'warn' },
  { label: '待执行需求', value: kpi.value.demandPending ?? 0, tone: 'warn' }
])

/** 五个环形指标:周长 2πr ≈ 301.6 */
const RING = 2 * Math.PI * 48
const kpiRings = computed(() => {
  const k = kpi.value
  const pct = (a, b) => (b > 0 ? Math.min(1, a / b) : 0)
  return [
    { label: '巡检点位', value: k.pointTotal ?? 0, sub: '个', color: '#38bdf8', dash: `${RING * 0.82} ${RING}` },
    { label: '在线机场', value: k.dockOnline ?? 0, sub: `共 ${k.dockTotal ?? 0} 台`, color: '#22d3ee',
      dash: `${RING * pct(k.dockOnline, k.dockTotal)} ${RING}` },
    { label: '巡检任务', value: k.taskTotal ?? 0, sub: `完成 ${k.taskDone ?? 0}`, color: '#818cf8',
      dash: `${RING * pct(k.taskDone, k.taskTotal)} ${RING}` },
    { label: '已办结工单', value: k.orderDone ?? 0, sub: `共 ${k.orderTotal ?? 0} 单`, color: '#34d399',
      dash: `${RING * pct(k.orderDone, k.orderTotal)} ${RING}` },
    { label: '可调度飞手', value: k.pilotAvailable ?? 0, sub: '人', color: '#fbbf24', dash: `${RING * 0.7} ${RING}` }
  ]
})

function barWidth(v, max) {
  return v <= 0 ? '0%' : Math.max(6, Math.round((v / max) * 100)) + '%'
}

/* ==================== 3D 场景装配 ==================== */

/** 航点 JSON → {lng,lat,h}[] */
function parseWaypoints(w) {
  try {
    return (JSON.parse(w || '[]') || []).map((p) => ({
      lng: Number(p.longitude), lat: Number(p.latitude), h: Number(p.height) || 0
    }))
  } catch { return [] }
}

/** 围栏 pointsJson → {lng,lat}[] */
function parseFencePoints(f) {
  try { return (JSON.parse(f.pointsJson || '[]') || []).map((p) => ({ lng: Number(p.lng), lat: Number(p.lat) })) }
  catch { return [] }
}

function onSceneReady(api) {
  sceneApi = api
  buildScene()
}

function buildScene() {
  const api = sceneApi
  if (!api) return
  api.clearDynamic()

  const pts = mapPoints.value.map((p) => ({ ...p, lng: Number(p.longitude), lat: Number(p.latitude) }))
    .filter((p) => isFinite(p.lng) && isFinite(p.lat))
  const wlPts = waylines.value.flatMap((w) => parseWaypoints(w.waypointsJson))
  const fencePts = fences.value.flatMap(parseFencePoints)

  // 投影基准 = 本屏全部坐标,保证都在底盘内
  const basis = [...pts.map((p) => ({ lng: p.lng, lat: p.lat })), ...wlPts, ...fencePts]
  if (!basis.length) return
  const maxAlt = Math.max(10, ...wlPts.map((p) => p.h))
  api.setProjection(basis, maxAlt)

  // 1. 河道:首条航线沿河布设,地面投影作为发光河道带
  if (waylines.value.length) {
    const rp = parseWaypoints(waylines.value[0].waypointsJson)
    if (rp.length > 1) api.addRiver(rp)
  }

  // 2. 电子围栏:圆形画薄壁,多边形按外接圆近似;颜色按类型
  const FENCE_COLOR = { NO_FLY: '#f87171', WORK: '#34d399', LIMIT: '#fbbf24' }
  for (const f of fences.value) {
    const fp = parseFencePoints(f)
    if (!fp.length) continue
    let center = fp[0]
    let radius = Number(f.radius) || 200
    if (f.shape === 'POLYGON') {
      const cx = fp.reduce((s, p) => s + p.lng, 0) / fp.length
      const cy = fp.reduce((s, p) => s + p.lat, 0) / fp.length
      center = { lng: cx, lat: cy }
      radius = Math.max(...fp.map((p) => Math.hypot((p.lng - cx) * 85000, (p.lat - cy) * 111000)))
    }
    api.addFence(center, radius, { color: FENCE_COLOR[f.fenceType] || '#38bdf8' })
  }

  // 3. 点位光柱:风险配色,高风险以上带标签
  for (const p of pts) {
    if (p.kind !== 'POINT') continue
    const color = RISK_COLORS[p.riskLevel] || '#38bdf8'
    const hi = p.riskLevel === 'HIGH' || p.riskLevel === 'EXTREME'
    api.addMarker(p, {
      color,
      label: hi ? p.name.slice(0, 7) : '',
      size: hi ? 1.15 : 0.9,
      beam: hi ? 3.6 : 2.6
    })
  }

  // 4. 在办问题:橙色脉冲光柱
  for (const p of pts) {
    if (p.kind !== 'ISSUE') continue
    api.addMarker(p, { color: '#fb923c', size: 0.8, beam: 2.0 })
  }

  // 5. 首条航线 3D 弧 + 巡航光点
  if (waylines.value.length) {
    const rp = parseWaypoints(waylines.value[0].waypointsJson)
    if (rp.length > 1) api.addRoute(rp, { color: '#7dd3fc', cruise: true })
  }

  api.focus()
}

/* ==================== 数据 ==================== */

async function load() {
  const [overview, recent, fenceList, wl] = await Promise.all([
    http.get('/screen/overview'),
    // 取最近任务而非按 PENDING 过滤:逾期的任务同样是"待办",按状态筛会漏
    http.get('/tasks/recent', { params: { limit: 8 } }),
    http.get('/fences'),
    http.get('/screen/waylines')
  ])
  data.value = overview || {}
  tasks.value = recent || []
  fences.value = (fenceList || []).filter((f) => f.enabled)
  waylines.value = wl?.waylines || []
  buildScene()
}

onMounted(() => {
  load()
  timer = setInterval(load, REFRESH_MS)
})
onUnmounted(() => {
  clearInterval(timer)
  sceneApi = null
})
</script>

<style scoped>
.sub { display: flex; flex-direction: column; min-width: 0; }
.cols { flex: 1; min-height: 0; display: grid; grid-template-columns: 340px 1fr 360px; gap: 12px; }
.col { display: flex; flex-direction: column; gap: 12px; min-height: 0; }
.col.center { gap: 10px; }

.scene-panel { padding-bottom: 0; }

/* 巡检概览网格 */
.overview { display: grid; grid-template-columns: 1fr 1fr; gap: 8px; padding: 0 12px 4px; }
.ov-item {
  display: flex; flex-direction: column; gap: 3px;
  padding: 8px 10px; border-radius: 6px;
  background: rgba(56, 189, 248, .07);
  border: 1px solid rgba(56, 189, 248, .14);
}
.ov-label { font-size: 11.5px; color: var(--scr-dim); }
.ov-value { font-size: 19px; font-weight: 700; color: #e0f2fe; font-variant-numeric: tabular-nums; }
.ov-value.good { color: #34d399; }
.ov-value.warn { color: #fbbf24; }

.time { font-size: 11px; color: #7dd3fc; font-variant-numeric: tabular-nums; }

/* 排行名次角标 */
.no {
  width: 17px; height: 17px; border-radius: 3px; flex-shrink: 0;
  display: flex; align-items: center; justify-content: center;
  font-size: 11px; font-weight: 700; color: #04102a; background: #64748b;
}
.no.n1 { background: #fbbf24; }
.no.n2 { background: #cbd5e1; }
.no.n3 { background: #fb923c; }

/* KPI 环 */
.kpi-row { display: grid; grid-template-columns: repeat(5, 1fr); gap: 8px; flex-shrink: 0; }
.kpi {
  display: flex; flex-direction: column; align-items: center; gap: 2px;
  padding: 6px 4px 8px; border-radius: 8px;
  background: var(--scr-panel); border: 1px solid var(--scr-border);
}
.kpi-ring { width: 100%; max-width: 104px; }
.kpi-num { font-size: 22px; font-weight: 800; fill: #e0f2fe; font-variant-numeric: tabular-nums; }
.kpi-sub { font-size: 10px; fill: #7fa8d6; }
.kpi-label { font-size: 12px; color: var(--scr-text); letter-spacing: .5px; }

/* 预警面板:固定高度,超量滚动 */
.alert-panel { flex: 0 0 auto; max-height: 200px; display: flex; flex-direction: column; }
.al-dot { display: inline-block; width: 7px; height: 7px; border-radius: 50%; margin-right: 6px; vertical-align: 1px; }
.al-dot.err { background: #f87171; box-shadow: 0 0 6px #f87171; }
.al-dot.warn { background: #fbbf24; box-shadow: 0 0 6px #fbbf24; }

@media (max-width: 1500px) {
  .cols { grid-template-columns: 300px 1fr 320px; }
}
@media (max-width: 1200px) {
  .cols { grid-template-columns: 1fr 1fr; grid-auto-rows: minmax(0, 1fr); }
  .col.center { grid-column: 1 / -1; }
}
</style>
