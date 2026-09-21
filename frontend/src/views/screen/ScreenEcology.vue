<template>
  <div class="sub">
    <div class="scr-kpi-row" style="margin-bottom: 12px">
      <div class="scr-kpi" style="--kpi-line: linear-gradient(180deg, #34d399, #0e7490)">
        <div class="ico" v-html="ICONS.leaf"></div>
        <div class="body">
          <div class="label">生态巡检点位</div>
          <div class="value good">{{ kpi.pointTotal ?? 0 }}</div>
        </div>
      </div>
      <div class="scr-kpi" style="--kpi-line: linear-gradient(180deg, #fb923c, #b91c1c)">
        <div class="ico" v-html="ICONS.warn"></div>
        <div class="body">
          <div class="label">高风险点位</div>
          <div class="value warn">{{ kpi.highRisk ?? 0 }}</div>
        </div>
      </div>
      <div class="scr-kpi" style="--kpi-line: linear-gradient(180deg, #818cf8, #4338ca)">
        <div class="ico" v-html="ICONS.chip"></div>
        <div class="body">
          <div class="label">今日算法命中</div>
          <div class="value cyan">{{ kpi.alarmToday ?? 0 }}</div>
        </div>
      </div>
      <div class="scr-kpi" style="--kpi-line: linear-gradient(180deg, #fbbf24, #b45309)">
        <div class="ico" v-html="ICONS.gas"></div>
        <div class="body">
          <div class="label">臭气超标站</div>
          <div class="value" :class="{ bad: (kpi.odorOver ?? 0) > 0 }">{{ kpi.odorOver ?? 0 }}</div>
        </div>
      </div>
    </div>

    <div class="cols">
      <!-- ===== 左列 ===== -->
      <section class="col">
        <div class="scr-panel grow">
          <div class="panel-hd">点位类别分布</div>
          <div ref="catRef" class="chart-box"></div>
        </div>
        <div class="scr-panel grow">
          <div class="panel-hd">风险等级分布</div>
          <div ref="riskRef" class="chart-box"></div>
        </div>
      </section>

      <!-- ===== 中列:3D 生态场景 + 臭气排行 ===== -->
      <section class="col center">
        <div class="scr-panel grow scene-panel">
          <div class="panel-hd">
            生态要素三维视图
            <span class="hd-note">生态点位 / 臭气站网 / 扩散烟羽</span>
          </div>
          <div class="scene-host">
            <Scene3D :opts="{ orbitSpeed: 0.07, radius: 40, height: 26 }" @ready="onSceneReady" />
            <div class="scene-legend">
              <span class="li" style="color: #f87171"><i style="background: #f87171" />极大风险点位</span>
              <span class="li" style="color: #fbbf24"><i style="background: #fbbf24" />高风险点位</span>
              <span class="li" style="color: #34d399"><i style="background: #34d399" />生态点位</span>
              <span class="li" style="color: #22d3ee"><i style="background: #22d3ee" />臭气监测站</span>
              <span class="li" style="color: #fde68a"><i style="background: #fde68a" />扩散烟羽</span>
            </div>
            <div class="scene-hud">
              <span class="chip">监测站 {{ odorStations.length }}</span>
              <span class="chip" v-if="wind">风 {{ wind.speed }}m/s · {{ windDegText }}</span>
              <span class="chip" v-if="dispersion?.active">溯源中 {{ dispersion.maxH2sPpm }}ppm</span>
            </div>
            <div class="scene-tip">烟羽沿下风向动态飘移</div>
          </div>
        </div>
        <div class="scr-panel odor-panel">
          <div class="panel-hd">
            臭气站浓度排行
            <span class="hd-note">H₂S 预警阈值 0.05 ppm</span>
          </div>
          <div class="scr-bars">
            <div v-for="s in odorTop" :key="s.stationId" class="row">
              <span class="name" style="width: 86px">{{ s.stationName || '-' }}</span>
              <span class="track" :style="{ '--bar': odorBar(s) }">
                <i :style="{ width: odorWidth(s) }"></i>
              </span>
              <span class="val" :style="{ color: s.h2sPpm >= 0.05 ? '#f87171' : '#7dd3fc' }">
                {{ (s.h2sPpm ?? 0).toFixed(3) }}
              </span>
            </div>
            <div v-if="!odorTop.length" class="empty">暂无读数</div>
          </div>
        </div>
      </section>

      <!-- ===== 右列 ===== -->
      <section class="col">
        <div class="scr-panel">
          <div class="panel-hd">六类算法命中</div>
          <div class="scr-bars">
            <div v-for="(v, key) in alarmByCode" :key="key" class="row">
              <span class="name" style="width: 86px">{{ dictLabel(ALGO_CODE, key) }}</span>
              <span class="track" style="--bar: linear-gradient(90deg, #4338ca, #818cf8)">
                <i :style="{ width: barWidth(v, algoMax) }"></i>
              </span>
              <span class="val">{{ v }}</span>
            </div>
          </div>
        </div>

        <div class="scr-panel grow">
          <div class="panel-hd">
            最新算法告警
            <span class="hd-note">{{ recentAlarms.length }} 条</span>
          </div>
          <div class="scroll">
            <div v-for="a in recentAlarms" :key="a.id" class="scr-row click"
              :style="{ borderLeftColor: a.level === 'ERROR' ? 'rgba(248,113,113,.8)' : 'rgba(251,191,36,.8)' }"
              @click="focusAlarm(a)">
              <div class="main">
                <span class="t1">{{ a.title }}</span>
                <span class="t2">{{ (a.address || '') .split('·').pop() }} · {{ (a.occurredAt || '').slice(5, 16).replace('T', ' ') }}</span>
              </div>
              <div class="side">
                <el-tag size="small" :type="a.level === 'ERROR' ? 'danger' : 'warning'" effect="dark">
                  {{ dictLabel(ALGO_LEVEL, a.level) }}
                </el-tag>
                <span class="conf">{{ a.confidence }}%</span>
              </div>
            </div>
            <div v-if="!recentAlarms.length" class="empty">暂无告警</div>
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
import { POINT_CATEGORY, RISK_LEVEL, ALGO_CODE, ALGO_LEVEL, dictLabel } from '../../utils/dict'

const REFRESH_MS = 30000
const data = ref({})
const points = ref([])
const odorStations = ref([])
const dispersion = ref(null)
let timer = null
let sceneApi = null

const kpi = computed(() => data.value.kpi || {})
const pointByCategory = computed(() => data.value.pointByCategory || {})
const pointByRisk = computed(() => data.value.pointByRisk || {})
const alarmByCode = computed(() => data.value.alarmByCode || {})
const recentAlarms = computed(() => data.value.recentAlarms || [])
const odorTop = computed(() => data.value.odorTop || [])
const wind = computed(() => dispersion.value?.wind || null)

const algoMax = computed(() => Math.max(1, ...Object.values(alarmByCode.value)))
const odorMax = computed(() => Math.max(0.06, ...odorTop.value.map((s) => s.h2sPpm ?? 0)))

function barWidth(v, max) {
  return v <= 0 ? '0%' : Math.max(6, Math.round((v / max) * 100)) + '%'
}
function odorWidth(s) {
  // 下限压到 3%:小浓度站之间保持真实比例,不再被 8% 下限抹平
  return Math.max(3, Math.round(((s.h2sPpm ?? 0) / odorMax.value) * 100)) + '%'
}
function odorBar(s) {
  const r = (s.h2sPpm ?? 0) >= 0.05
  return r ? 'linear-gradient(90deg, #b45309, #f87171)' : 'linear-gradient(90deg, #0e7490, #22d3ee)'
}
const windDegText = computed(() => {
  const d = wind.value?.direction ?? 0
  return `${d}°`
})

const ico = (p) => `<svg viewBox="0 0 24 24" fill="none" stroke="#7dd3fc" stroke-width="1.7"
  stroke-linecap="round" stroke-linejoin="round">${p}</svg>`
const ICONS = {
  leaf: ico('<path d="M5 19c0-8 5-13 14-14-1 9-6 14-14 14z"/><path d="M5 19C8 13 12 9 17 6"/>'),
  warn: ico('<path d="M12 4.5 21 19.5H3z"/><path d="M12 10.5v4"/><circle cx="12" cy="17" r="0.4" fill="currentColor"/>'),
  chip: ico('<rect x="7" y="7" width="10" height="10" rx="2"/><path d="M12 4v3M12 17v3M4 12h3M17 12h3"/><circle cx="12" cy="12" r="1.6"/>'),
  gas: ico('<path d="M8 20a4 4 0 0 1-1-7.9A5.5 5.5 0 0 1 17.4 9 4.5 4.5 0 0 1 17 18"/>')
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

  const pts = points.value
    .map((p) => ({ lng: Number(p.longitude), lat: Number(p.latitude), name: p.name, risk: p.riskLevel }))
    .filter((p) => isFinite(p.lng) && isFinite(p.lat))
  const st = odorStations.value
    .map((s) => ({ lng: Number(s.longitude), lat: Number(s.latitude), name: s.stationName, over: (s.h2sPpm ?? 0) >= 0.05 }))
    .filter((p) => isFinite(p.lng) && isFinite(p.lat))
  const traj = (dispersion.value?.trajectory || [])
    .map((p) => ({ lng: Number(p.longitude), lat: Number(p.latitude) }))
    .filter((p) => isFinite(p.lng) && isFinite(p.lat))

  const basis = [...pts, ...st, ...traj]
  if (!basis.length) return
  api.setProjection(basis, 120)

  // 生态点位:风险配色光柱(绿青系为主,高风险以上带标签)
  for (const p of pts) {
    const color = p.risk === 'EXTREME' ? '#f87171'
      : p.risk === 'HIGH' ? '#fbbf24'
        : p.risk === 'MEDIUM' ? '#34d399' : '#22d3ee'
    const hi = p.risk === 'HIGH' || p.risk === 'EXTREME'
    api.addMarker(p, { color, label: hi ? (p.name || '').slice(0, 7) : '', size: hi ? 1.1 : 0.85, beam: hi ? 3.4 : 2.4 })
  }

  // 臭气站:小光点,超标站偏红并脉冲
  for (const s of st) {
    api.addMarker(s, {
      color: s.over ? '#f87171' : '#22d3ee',
      size: s.over ? 0.8 : 0.55,
      beam: s.over ? 2.2 : 1.4,
      pulse: s.over
    })
  }

  // 扩散烟羽:泄漏源红色脉冲 + 粒子飘移带
  const src = dispersion.value?.source
  if (src && traj.length > 1) {
    api.addMarker({ lng: Number(src.longitude), lat: Number(src.latitude) },
      { color: '#f04438', label: '溯源泄漏源', beam: 4.2, size: 1.25 })
    api.addPlume(traj, { colorA: '#fde68a', colorB: '#f87171' })
  }

  api.focus()
}

function focusAlarm(a) {
  if (!sceneApi || !a.longitude) return
  sceneApi.focus({ lng: Number(a.longitude), lat: Number(a.latitude) })
}

/* ==================== 图表 ==================== */

const catRef = ref(null)
const riskRef = ref(null)
let catChart = null
let riskChart = null

const CAT_COLORS = ['#34d399', '#22d3ee', '#38bdf8', '#818cf8', '#fbbf24', '#fb923c', '#f472b6']
const RISK_COLOR = { LOW: '#64748b', MEDIUM: '#22d3ee', HIGH: '#fbbf24', EXTREME: '#f87171' }

function renderCat() {
  const entries = Object.entries(pointByCategory.value)
  catChart?.setOption({
    ...ANIM,
    tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' }, ...TIP },
    grid: { left: 10, right: 14, top: 24, bottom: 4, containLabel: true },
    xAxis: {
      type: 'category', axisTick: { show: false },
      axisLine: { lineStyle: { color: 'rgba(56,189,248,.3)' } },
      axisLabel: { ...AXIS, interval: 0, fontSize: 10 },
      data: entries.map(([k]) => dictLabel(POINT_CATEGORY, k).slice(0, 2))
    },
    yAxis: { type: 'value', minInterval: 1, splitLine: SPLIT, axisLabel: AXIS },
    series: [{
      type: 'bar', barWidth: 20,
      animationDelay: (i) => i * 90,
      itemStyle: { borderRadius: [6, 6, 0, 0] },
      data: entries.map(([k, v], i) => ({ value: v, itemStyle: { color: barGradient(CAT_COLORS[i % CAT_COLORS.length]) } }))
    }]
  })
}

function renderRisk() {
  const entries = Object.entries(pointByRisk.value)
  riskChart?.setOption({
    ...ANIM,
    tooltip: { trigger: 'item', ...TIP, formatter: '{b}: {c} 个 ({d}%)' },
    legend: { bottom: 0, icon: 'circle', itemWidth: 8, itemHeight: 8, textStyle: AXIS },
    series: [{
      type: 'pie', radius: ['42%', '66%'], center: ['50%', '42%'],
      itemStyle: { borderColor: '#04102a', borderWidth: 2, borderRadius: 5 },
      label: { color: AXIS.color, fontSize: 11, formatter: '{c}' },
      labelLine: { lineStyle: { color: 'rgba(127,168,214,.5)' } },
      data: entries.map(([k, v]) => ({
        name: dictLabel(RISK_LEVEL, k), value: v,
        itemStyle: { color: RISK_COLOR[k] || '#64748b' }
      }))
    }]
  })
}

/* ==================== 数据 ==================== */

async function load() {
  const [eco, pointList, odorMap, disp] = await Promise.all([
    http.get('/screen/ecology'),
    http.get('/points'),
    http.get('/odor/map'),
    http.get('/odor/dispersion')
  ])
  data.value = eco || {}
  points.value = pointList || []
  odorStations.value = odorMap?.stations || []
  dispersion.value = disp || null
  buildScene()
  await nextTick()
  renderCat()
  renderRisk()
}

function resize() {
  catChart?.resize()
  riskChart?.resize()
}

onMounted(async () => {
  await nextTick()
  catChart = echarts.init(catRef.value)
  riskChart = echarts.init(riskRef.value)
  await load()
  window.addEventListener('resize', resize)
  timer = setInterval(load, REFRESH_MS)
})
onUnmounted(() => {
  clearInterval(timer)
  window.removeEventListener('resize', resize)
  catChart?.dispose()
  riskChart?.dispose()
  sceneApi = null
})
</script>

<style scoped>
.sub { display: flex; flex-direction: column; min-width: 0; }
.cols { flex: 1; min-height: 0; display: grid; grid-template-columns: 330px 1fr 360px; gap: 12px; }
.col { display: flex; flex-direction: column; gap: 12px; min-height: 0; }
.col.center { gap: 10px; }
.scene-panel { padding-bottom: 0; }
.odor-panel { flex: 0 0 218px; }
.conf { font-size: 11px; color: #7dd3fc; font-variant-numeric: tabular-nums; }

@media (max-width: 1500px) { .cols { grid-template-columns: 290px 1fr 320px; } }
@media (max-width: 1200px) {
  .cols { grid-template-columns: 1fr 1fr; grid-auto-rows: minmax(0, 1fr); }
  .col.center { grid-column: 1 / -1; }
}
</style>
