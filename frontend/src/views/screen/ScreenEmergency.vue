<template>
  <div class="sub">
    <!-- KPI 数字条 -->
    <div class="scr-kpi-row" style="margin-bottom: 12px">
      <div class="scr-kpi" style="--kpi-line: linear-gradient(180deg, #f87171, #b91c1c)">
        <div class="ico" v-html="ICONS.bolt"></div>
        <div class="body">
          <div class="label">进行中事件</div>
          <div class="value" :class="{ bad: kpi.activeCount > 0 }">{{ kpi.activeCount ?? 0 }}</div>
        </div>
      </div>
      <div class="scr-kpi" style="--kpi-line: linear-gradient(180deg, #fb923c, #c2410c)">
        <div class="ico" v-html="ICONS.plus"></div>
        <div class="body">
          <div class="label">今日新增事件</div>
          <div class="value warn">{{ kpi.todayNew ?? 0 }}</div>
        </div>
      </div>
      <div class="scr-kpi" style="--kpi-line: linear-gradient(180deg, #fbbf24, #b45309)">
        <div class="ico" v-html="ICONS.bell"></div>
        <div class="body">
          <div class="label">待处置安全预警</div>
          <div class="value" :class="{ warn: safeAlertPending > 0 }">{{ safeAlertPending }}</div>
        </div>
      </div>
      <div class="scr-kpi" style="--kpi-line: linear-gradient(180deg, #34d399, #0e7490)">
        <div class="ico" v-html="ICONS.fence"></div>
        <div class="body">
          <div class="label">保障围栏</div>
          <div class="value good">{{ kpi.fenceCount ?? 0 }}</div>
        </div>
      </div>
    </div>

    <div class="cols">
      <!-- ===== 左列:等级 + 类别 ===== -->
      <section class="col">
        <div class="scr-panel grow">
          <div class="panel-hd">事件等级分布</div>
          <div ref="levelRef" class="chart-box"></div>
        </div>
        <div class="scr-panel">
          <div class="panel-hd">事件类别分布</div>
          <div class="scr-bars">
            <div v-for="(v, key) in eventByCategory" :key="key" class="row">
              <span class="name" style="width: 92px">{{ dictLabel(EVENT_CATEGORY, key) }}</span>
              <span class="track" style="--bar: linear-gradient(90deg, #dc2626, #fb923c)">
                <i :style="{ width: barWidth(v, catMax) }"></i>
              </span>
              <span class="val" style="color: #fbbf24">{{ v }}</span>
            </div>
          </div>
        </div>
      </section>

      <!-- ===== 中列:3D 应急态势 + 趋势 ===== -->
      <section class="col center">
        <div class="scr-panel grow scene-panel">
          <div class="panel-hd">
            应急态势三维视图
            <span class="hd-note">事件光柱 / 空域围栏 / 预警点位</span>
          </div>
          <div class="scene-host">
            <Scene3D :opts="{ orbitSpeed: 0.07, radius: 38, height: 25 }" @ready="onSceneReady" />
            <div class="scene-legend">
              <span class="li" style="color: #f87171"><i style="background: #f87171" />Ⅰ 级事件</span>
              <span class="li" style="color: #fb923c"><i style="background: #fb923c" />Ⅱ 级事件</span>
              <span class="li" style="color: #fbbf24"><i style="background: #fbbf24" />Ⅲ/Ⅳ 级事件</span>
              <span class="li" style="color: #64748b"><i style="background: #64748b" />历史事件</span>
            </div>
            <div class="scene-hud">
              <span class="chip">进行中 {{ activeEvents.length }}</span>
              <span class="chip">围栏 {{ fences.length }}</span>
            </div>
            <div class="scene-tip">光柱越高事件等级越高</div>
          </div>
        </div>
        <div class="scr-panel trend-panel">
          <div class="panel-hd">近 7 日事件趋势<span class="hd-note">当日发生 / 当日处置</span></div>
          <div ref="trendRef" class="chart-box"></div>
        </div>
      </section>

      <!-- ===== 右列:进行中 + 预警 ===== -->
      <section class="col">
        <div class="scr-panel grow">
          <div class="panel-hd">进行中事件</div>
          <div class="scroll">
            <div v-for="e in activeEvents" :key="e.id" class="scr-row click" style="border-left-color: rgba(248,113,113,.7)"
              @click="focusEvent(e)">
              <div class="main">
                <span class="t1">{{ e.title }}</span>
                <span class="t2">{{ e.address }} · {{ (e.occurTime || '').slice(5, 16).replace('T', ' ') }}</span>
              </div>
              <div class="side">
                <el-tag size="small" type="danger" effect="dark">{{ dictLabel(EVENT_LEVEL, e.level) }}</el-tag>
                <el-tag size="small" :type="dictTag(EVENT_STATUS, e.status)" effect="plain">
                  {{ dictLabel(EVENT_STATUS, e.status) }}
                </el-tag>
              </div>
            </div>
            <div v-if="!activeEvents.length" class="empty">当前无进行中事件</div>
          </div>
        </div>

        <div class="scr-panel grow">
          <div class="panel-hd">
            最近安全预警
            <span class="hd-note">{{ safeAlertPending }} 条待处置</span>
          </div>
          <div class="scroll">
            <div v-for="a in recentSafeAlerts" :key="a.id" class="scr-row" style="border-left-color: rgba(251,191,36,.7)">
              <div class="main">
                <span class="t1">{{ a.title }}</span>
                <span class="t2">{{ a.deviceSn }} · {{ (a.occurredAt || '').slice(5, 16).replace('T', ' ') }}</span>
              </div>
              <div class="side">
                <el-tag size="small" :type="a.level === 'ERROR' ? 'danger' : 'warning'" effect="dark">
                  {{ dictLabel(SAFE_ALERT_LEVEL, a.level) }}
                </el-tag>
              </div>
            </div>
            <div v-if="!recentSafeAlerts.length" class="empty">暂无安全预警</div>
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
import { echarts, AXIS, SPLIT, TIP, ANIM } from './echartsDark'
import { EVENT_CATEGORY, EVENT_LEVEL, EVENT_STATUS, SAFE_ALERT_LEVEL, dictLabel, dictTag } from '../../utils/dict'

const REFRESH_MS = 30000
const data = ref({})
let timer = null
let sceneApi = null

const kpi = computed(() => data.value.kpi || {})
const eventByLevel = computed(() => data.value.eventByLevel || {})
const eventByCategory = computed(() => data.value.eventByCategory || {})
const activeEvents = computed(() => data.value.activeEvents || [])
const recentEvents = computed(() => data.value.recentEvents || [])
const fences = computed(() => data.value.fences || [])
const safeAlertPending = computed(() => data.value.safeAlertPending ?? 0)
const recentSafeAlerts = computed(() => data.value.recentSafeAlerts || [])
const trend = computed(() => data.value.eventTrend || [])

const catMax = computed(() => Math.max(1, ...Object.values(eventByCategory.value)))
function barWidth(v, max) {
  return v <= 0 ? '0%' : Math.max(6, Math.round((v / max) * 100)) + '%'
}

/* KPI 线性图标(白描) */
const ico = (p) => `<svg viewBox="0 0 24 24" fill="none" stroke="#7dd3fc" stroke-width="1.7"
  stroke-linecap="round" stroke-linejoin="round">${p}</svg>`
const ICONS = {
  bolt: ico('<path d="M13 3 5.5 13.5H11L10 21l7.5-10.5H12z"/>'),
  plus: ico('<path d="M12 5v14M5 12h14"/>'),
  bell: ico('<path d="M6 10a6 6 0 0 1 12 0c0 5 2 6 2 6H4s2-1 2-6"/><path d="M10 20a2 2 0 0 0 4 0"/>'),
  fence: ico('<path d="M5 20V9M12 20V6M19 20V9"/><path d="M3.4 10.4 5 8.8l1.6 1.6M10.4 7.4 12 5.8l1.6 1.6M17.4 10.4 19 8.8l1.6 1.6"/>')
}

/* ==================== 3D ==================== */

/** 事件等级 → 光柱高度 / 颜色 */
const LEVEL_STYLE = {
  I: { color: '#f87171', beam: 5.4 },
  II: { color: '#fb923c', beam: 4.4 },
  III: { color: '#fbbf24', beam: 3.4 },
  IV: { color: '#38bdf8', beam: 2.6 }
}

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

  const evt = (e) => ({ lng: Number(e.longitude), lat: Number(e.latitude) })
  const actives = activeEvents.value.map(evt).filter((p) => isFinite(p.lng) && isFinite(p.lat))
  const recents = recentEvents.value.map(evt).filter((p) => isFinite(p.lng) && isFinite(p.lat))
  const fencePts = fences.value.flatMap(parseFencePoints)
  const alertPts = recentSafeAlerts.value
    .map((a) => ({ lng: Number(a.longitude), lat: Number(a.latitude) }))
    .filter((p) => isFinite(p.lng) && isFinite(p.lat))

  const basis = [...actives, ...recents, ...fencePts, ...alertPts]
  if (!basis.length) return
  api.setProjection(basis, 120)

  // 进行中事件:等级光柱(高度随等级),点击列表可聚焦
  activeEvents.value.forEach((e) => {
    const p = evt(e)
    if (!isFinite(p.lng)) return
    const s = LEVEL_STYLE[e.level] || LEVEL_STYLE.IV
    api.addMarker(p, { color: s.color, label: (e.title || '').slice(0, 7), beam: s.beam, size: 1.2 })
  })
  // 历史事件:灰柱不脉冲,衬托态势
  const activeIds = new Set(activeEvents.value.map((e) => e.id))
  recentEvents.value.forEach((e) => {
    if (activeIds.has(e.id)) return
    const p = evt(e)
    if (!isFinite(p.lng)) return
    api.addMarker(p, { color: '#64748b', size: 0.7, beam: 1.8, pulse: false, label: '' })
  })
  // 空域围栏
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
    api.addFence(center, radius, { color: FENCE_COLOR[f.fenceType] || '#38bdf8', height: 2.2 })
  }
  // 预警点位:黄色小脉冲
  alertPts.forEach((p) => api.addMarker(p, { color: '#fbbf24', size: 0.55, beam: 1.4 }))

  api.focus()
}

function focusEvent(e) {
  if (!sceneApi || !e.longitude) return
  sceneApi.focus({ lng: Number(e.longitude), lat: Number(e.latitude) })
}

/* ==================== 图表 ==================== */

const levelRef = ref(null)
const trendRef = ref(null)
let levelChart = null
let trendChart = null

const LEVEL_COLOR = { I: '#f87171', II: '#fb923c', III: '#fbbf24', IV: '#38bdf8' }

function renderLevel() {
  levelChart?.setOption({
    ...ANIM,
    tooltip: { trigger: 'item', ...TIP, formatter: '{b}: {c} 起 ({d}%)' },
    legend: { bottom: 0, icon: 'circle', itemWidth: 8, itemHeight: 8, textStyle: AXIS },
    series: [{
      type: 'pie', radius: ['34%', '62%'], center: ['50%', '44%'],
      roseType: 'radius',
      itemStyle: { borderColor: '#04102a', borderWidth: 2, borderRadius: 4 },
      // 等级名(dictLabel 是"Ⅳ级 一般"两段式)只取首段,避免 340px 面板里截断
      label: { color: AXIS.color, fontSize: 11, formatter: (p) => `${p.name.split(' ')[0]} ${p.value}` },
      labelLine: { lineStyle: { color: 'rgba(127,168,214,.5)' } },
      data: Object.entries(eventByLevel.value).map(([k, v]) => ({
        name: dictLabel(EVENT_LEVEL, k), value: v,
        itemStyle: { color: LEVEL_COLOR[k] || '#64748b' }
      }))
    }]
  })
}

function renderTrend() {
  const t = trend.value
  trendChart?.setOption({
    ...ANIM,
    tooltip: { trigger: 'axis', ...TIP },
    legend: { right: 8, top: 0, icon: 'circle', itemWidth: 8, itemHeight: 8, textStyle: AXIS },
    grid: { left: 10, right: 18, top: 30, bottom: 4, containLabel: true },
    xAxis: {
      type: 'category', boundaryGap: false, axisTick: { show: false },
      axisLine: { lineStyle: { color: 'rgba(56,189,248,.3)' } }, axisLabel: AXIS,
      data: t.map((d) => String(d.date).slice(5))
    },
    yAxis: { type: 'value', minInterval: 1, splitLine: SPLIT, axisLabel: AXIS },
    series: [
      { name: '当日发生', type: 'line', smooth: true, symbolSize: 6,
        itemStyle: { color: '#f87171', borderColor: '#04102a', borderWidth: 1.5 },
        lineStyle: { width: 2.5, color: '#f87171' },
        areaStyle: { color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
          { offset: 0, color: 'rgba(248,113,113,.25)' }, { offset: 1, color: 'rgba(248,113,113,0)' }]) },
        data: t.map((d) => d.total) },
      { name: '当日处置', type: 'line', smooth: true, symbolSize: 6,
        itemStyle: { color: '#34d399', borderColor: '#04102a', borderWidth: 1.5 },
        lineStyle: { width: 2.5, color: '#34d399' }, data: t.map((d) => d.done) }
    ]
  })
}

/* ==================== 数据 ==================== */

async function load() {
  data.value = (await http.get('/screen/emergency')) || {}
  buildScene()
  await nextTick()
  renderLevel()
  renderTrend()
}

function resize() {
  levelChart?.resize()
  trendChart?.resize()
}

onMounted(async () => {
  await nextTick()
  levelChart = echarts.init(levelRef.value)
  trendChart = echarts.init(trendRef.value)
  await load()
  window.addEventListener('resize', resize)
  timer = setInterval(load, REFRESH_MS)
})
onUnmounted(() => {
  clearInterval(timer)
  window.removeEventListener('resize', resize)
  levelChart?.dispose()
  trendChart?.dispose()
  sceneApi = null
})
</script>

<style scoped>
.sub { display: flex; flex-direction: column; min-width: 0; }
.cols { flex: 1; min-height: 0; display: grid; grid-template-columns: 330px 1fr 360px; gap: 12px; }
.col { display: flex; flex-direction: column; gap: 12px; min-height: 0; }
.col.center { gap: 10px; }
.scene-panel { padding-bottom: 0; }
.trend-panel { flex: 0 0 172px; padding-bottom: 6px; }

@media (max-width: 1500px) { .cols { grid-template-columns: 290px 1fr 320px; } }
@media (max-width: 1200px) {
  .cols { grid-template-columns: 1fr 1fr; grid-auto-rows: minmax(0, 1fr); }
  .col.center { grid-column: 1 / -1; }
}
</style>
