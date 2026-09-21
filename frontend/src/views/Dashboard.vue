<template>
  <div class="page dashboard" v-loading="loading">
    <div class="page-header">
      <span class="page-title">工作台</span>
      <span class="header-tip">应急巡检 · 全域态势总览</span>
      <div class="actions">
        <span class="live-chip"><i class="live-dot" />{{ countdown }}s 后自动刷新</span>
        <el-button size="small" :loading="loading" @click="loadStats">立即刷新</el-button>
      </div>
    </div>

    <!-- 欢迎横幅:白→亮蓝渐变 + 快捷入口 -->
    <div class="banner fade-in-up">
      <div class="banner-main">
        <div class="banner-hi">{{ greet }},{{ nickname }}</div>
        <div class="banner-date">{{ dateText }} · 应急巡检全域态势,数据实时汇聚</div>
      </div>
      <div class="banner-live">
        <i class="live-dot" />
        <span>已实时刷新 {{ lastRefresh || '—' }}</span>
      </div>
      <div class="banner-actions">
        <div v-for="a in actions" :key="a.label" class="action-chip" @click="$router.push(a.to)">
          <span class="action-ico" v-html="a.icon"></span>
          <span>{{ a.label }}</span>
        </div>
      </div>
    </div>

    <!-- 概览指标:数字滚动动画 -->
    <div class="tiles">
      <div v-for="t in tiles" :key="t.label" class="panel tile" :class="t.tone">
        <div class="tile-ico" v-html="t.icon"></div>
        <div class="tile-body">
          <div class="tile-label">{{ t.label }}</div>
          <div class="tile-value" :class="{ hot: t.alert && t.value > 0 }">{{ t.value }}</div>
          <div class="tile-sub">{{ t.sub }}</div>
        </div>
      </div>
    </div>

    <!-- 分布图表:三列 ECharts,数据更新带过渡动画 -->
    <div class="charts">
      <div class="panel chart-panel">
        <div class="panel-title">点位风险等级分布</div>
        <div ref="riskRef" class="chart"></div>
      </div>
      <div class="panel chart-panel">
        <div class="panel-title">任务状态分布</div>
        <div ref="taskRef" class="chart"></div>
      </div>
      <div class="panel chart-panel">
        <div class="panel-title">事件等级分布</div>
        <div ref="eventRef" class="chart"></div>
      </div>
    </div>

    <!-- 近 7 日趋势 -->
    <div class="panel chart-panel wide">
      <div class="panel-title">近 7 日巡检任务趋势</div>
      <div ref="trendRef" class="chart"></div>
    </div>

    <!-- 待办清单:四列 -->
    <div class="lists">
      <div class="panel list-panel">
        <div class="panel-title list-head">
          <span>最近任务</span>
          <el-button link type="primary" size="small" @click="$router.push('/tasks')">全部 →</el-button>
        </div>
        <div class="list-body">
          <div v-if="!stats.recentTasks?.length" class="empty">暂无任务</div>
          <div v-for="t in stats.recentTasks" :key="t.id" class="item" @click="$router.push('/tasks')">
            <div class="item-main">
              <div class="item-title">{{ t.name }}</div>
              <div class="item-sub">{{ t.pointName || '-' }} · {{ t.executor || '未指派' }}</div>
            </div>
            <el-tag size="small" :type="dictTag(TASK_STATUS, t.status)" effect="light">
              {{ dictLabel(TASK_STATUS, t.status) }}
            </el-tag>
          </div>
        </div>
      </div>

      <div class="panel list-panel">
        <div class="panel-title list-head">
          <span>进行中事件</span>
          <el-button link type="primary" size="small" @click="$router.push('/events')">全部 →</el-button>
        </div>
        <div class="list-body">
          <div v-if="!stats.activeEvents?.length" class="empty">暂无进行中事件</div>
          <div v-for="e in stats.activeEvents" :key="e.id" class="item" @click="$router.push('/events')">
            <div class="item-main">
              <div class="item-title">{{ e.title }}</div>
              <div class="item-sub">{{ e.address || '-' }} · {{ e.occurTime || '-' }}</div>
            </div>
            <div class="item-tags">
              <el-tag size="small" :type="dictTag(EVENT_LEVEL, e.level)" effect="light">
                {{ dictLabel(EVENT_LEVEL, e.level) }}
              </el-tag>
              <el-tag size="small" :type="dictTag(EVENT_STATUS, e.status)" effect="plain">
                {{ dictLabel(EVENT_STATUS, e.status) }}
              </el-tag>
            </div>
          </div>
        </div>
      </div>

      <div class="panel list-panel">
        <div class="panel-title list-head">
          <span>待处理隐患</span>
          <el-button link type="primary" size="small" @click="$router.push('/hazards')">全部 →</el-button>
        </div>
        <div class="list-body">
          <div v-if="!stats.recentHazards?.length" class="empty">暂无待处理隐患</div>
          <div v-for="h in stats.recentHazards" :key="h.id" class="item" @click="$router.push('/hazards')">
            <div class="item-main">
              <div class="item-title">{{ h.title }}</div>
              <div class="item-sub">{{ h.pointName || '-' }} · {{ h.reportTime || '-' }}</div>
            </div>
            <div class="item-tags">
              <el-tag size="small" :type="dictTag(HAZARD_LEVEL, h.level)" effect="light">
                {{ dictLabel(HAZARD_LEVEL, h.level) }}
              </el-tag>
              <el-tag size="small" :type="dictTag(HAZARD_STATUS, h.status)" effect="plain">
                {{ dictLabel(HAZARD_STATUS, h.status) }}
              </el-tag>
            </div>
          </div>
        </div>
      </div>

      <div class="panel list-panel">
        <div class="panel-title list-head">
          <span>最近安全预警</span>
          <el-button link type="primary" size="small" @click="$router.push('/safe-alerts')">全部 →</el-button>
        </div>
        <div class="list-body">
          <div v-if="!stats.safeAlerts?.length" class="empty">暂无安全预警</div>
          <div v-for="s in stats.safeAlerts" :key="s.id" class="item" @click="$router.push('/safe-alerts')">
            <div class="item-main">
              <div class="item-title">{{ s.title }}</div>
              <div class="item-sub">{{ s.deviceSn }} · {{ (s.occurredAt || '').replace('T', ' ') }}</div>
            </div>
            <div class="item-tags">
              <el-tag size="small" :type="s.level === 'ERROR' ? 'danger' : 'warning'" effect="light">
                {{ s.level === 'ERROR' ? '严重' : '警告' }}
              </el-tag>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted, onUnmounted, nextTick } from 'vue'
// 按需引入:整包 echarts 会让本页 chunk 达到 ~1MB,这里只注册用到的图表与组件
import * as echarts from 'echarts/core'
import { PieChart, BarChart, LineChart } from 'echarts/charts'
import { TooltipComponent, LegendComponent, GridComponent, TitleComponent } from 'echarts/components'
import { UniversalTransition } from 'echarts/features'
import { CanvasRenderer } from 'echarts/renderers'

echarts.use([PieChart, BarChart, LineChart, TooltipComponent, LegendComponent, GridComponent,
  TitleComponent, UniversalTransition, CanvasRenderer])
import http from '../api'
import {
  RISK_LEVEL, TASK_STATUS, EVENT_LEVEL, EVENT_STATUS, HAZARD_LEVEL, HAZARD_STATUS,
  dictLabel, dictTag
} from '../utils/dict'

const loading = ref(false)
const stats = reactive({})
const lastRefresh = ref('')
const countdown = ref(30)

/* ---------- 问候与日期 ---------- */
const nickname = localStorage.getItem('nickname') || '指挥员'
const greet = computed(() => {
  const h = new Date().getHours()
  if (h < 6) return '凌晨好'
  if (h < 12) return '上午好'
  if (h < 14) return '中午好'
  if (h < 18) return '下午好'
  return '晚上好'
})
const dateText = computed(() => {
  const d = new Date()
  return `${d.getMonth() + 1}月${d.getDate()}日 星期${'日一二三四五六'[d.getDay()]}`
})

/* ---------- 快捷入口图标:白描线性,与登录卡同语言 ---------- */
const ico = (paths) =>
  `<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7"
    stroke-linecap="round" stroke-linejoin="round">${paths}</svg>`

const ACTIONS_ICON = {
  task: ico('<path d="M5 4h14v16H5z"/><path d="M9 4V2.6M15 4V2.6"/><path d="M8.5 10.5l1.8 1.8 3.4-3.4"/><path d="M8.5 15.5h7"/>'),
  hazard: ico('<path d="M12 4 21 19H3z"/><path d="M12 10v4"/><circle cx="12" cy="16.6" r="0.4" fill="currentColor"/>'),
  dock: ico('<path d="M12 20v-7"/><path d="M8.5 13 12 9l3.5 4"/><path d="M6 8.2a8.5 8.5 0 0 1 12 0"/><path d="M3.8 5.4a12 12 0 0 1 16.4 0"/>'),
  algo: ico('<rect x="7" y="7" width="10" height="10" rx="2"/><path d="M12 4v3M12 17v3M4 12h3M17 12h3M7.5 4.8 9 6.3M16.5 4.8 15 6.3M7.5 19.2 9 17.7M16.5 19.2 15 17.7"/>')
}
const actions = [
  { label: '巡检任务', to: '/tasks', icon: ACTIONS_ICON.task },
  { label: '隐患上报', to: '/hazards', icon: ACTIONS_ICON.hazard },
  { label: '机场控制', to: '/docks', icon: ACTIONS_ICON.dock },
  { label: '算法管理', to: '/algorithms', icon: ACTIONS_ICON.algo }
]

/* ---------- 指标卡图标(同风格) ---------- */
const T = {
  point: ico('<path d="M12 21s-6.5-5.6-6.5-10.4A6.5 6.5 0 0 1 12 4a6.5 6.5 0 0 1 6.5 6.6C18.5 15.4 12 21 12 21z"/><circle cx="12" cy="10.4" r="2.3"/>'),
  plan: ico('<rect x="4" y="5.5" width="16" height="14" rx="2.5"/><path d="M8 3.4v4M16 3.4v4"/><path d="M4 10.5h16"/><path d="M8.5 14.5h4"/>'),
  taskOk: ico('<path d="M5 4.5h14v15H5z"/><path d="M8.5 11l2.2 2.2 4.3-4.4"/><path d="M8.5 16.5h7"/>'),
  clock: ico('<circle cx="12" cy="12.5" r="7.5"/><path d="M12 8.8v3.7l2.6 1.8"/><path d="M9.6 3h4.8"/>'),
  warn: ico('<path d="M12 4.5 21 19.5H3z"/><path d="M12 10.5v4"/><circle cx="12" cy="17" r="0.4" fill="currentColor"/>'),
  bolt: ico('<path d="M13 3 5.5 13.5H11L10 21l7.5-10.5H12z"/>'),
  dock: ico('<path d="M12 20.5v-7"/><path d="M8.5 13.5 12 9.8l3.5 3.7"/><path d="M6 8.6a8.5 8.5 0 0 1 12 0"/><path d="M3.8 5.8a12 12 0 0 1 16.4 0"/>'),
  chip: ico('<rect x="7" y="7" width="10" height="10" rx="2"/><path d="M12 4v3M12 17v3M4 12h3M17 12h3"/><circle cx="12" cy="12" r="1.6"/>')
}

/* 数字滚动:rAF 缓动,数据刷新时从旧值滚到新值 */
const shown = reactive({})
const rafs = {}
function countUp(key, to) {
  if (typeof to !== 'number') { shown[key] = to ?? 0; return }
  const from = typeof shown[key] === 'number' ? shown[key] : 0
  if (from === to) { shown[key] = to; return }
  cancelAnimationFrame(rafs[key])
  const t0 = performance.now(), dur = 700
  const tick = (t) => {
    const p = Math.min(1, (t - t0) / dur)
    shown[key] = Math.round(from + (to - from) * (1 - Math.pow(1 - p, 3)))
    if (p < 1) rafs[key] = requestAnimationFrame(tick)
  }
  rafs[key] = requestAnimationFrame(tick)
}

const tiles = computed(() => [
  { label: '点位总数', value: shown.pointTotal ?? 0, sub: '覆盖全部风险等级', tone: 'tone-primary', icon: T.point },
  { label: '启用计划', value: shown.planEnabled ?? 0, sub: '可按计划生成任务', tone: 'tone-cyan', icon: T.plan },
  { label: '今日任务', value: shown.taskToday ?? 0, sub: `待执行 ${stats.taskPending ?? 0} · 执行中 ${stats.taskRunning ?? 0}`, tone: 'tone-primary', icon: T.taskOk },
  { label: '逾期任务', value: shown.taskOverdue ?? 0, sub: '超过计划结束时间', tone: 'tone-danger', icon: T.clock, alert: true },
  { label: '待处理隐患', value: shown.hazardPending ?? 0, sub: `处理中 ${stats.hazardProcessing ?? 0}`, tone: 'tone-warning', icon: T.warn, alert: true },
  { label: '进行中事件', value: shown.eventActive ?? 0, sub: `累计事件 ${stats.eventTotal ?? 0}`, tone: 'tone-danger', icon: T.bolt, alert: true },
  { label: '机场在线', value: shown.dockOnline ?? 0, sub: `无人机在线 ${stats.droneOnline ?? 0}`, tone: 'tone-cyan', icon: T.dock },
  { label: 'AI 告警今日', value: shown.alarmToday ?? 0, sub: '六类算法识别命中', tone: 'tone-purple', icon: T.chip }
])

/* 与 el-tag 语义一致的图表配色,保证同屏同义同色 */
const RISK_COLOR = { LOW: '#98a2b3', MEDIUM: '#155eef', HIGH: '#f79009', EXTREME: '#f04438' }
const TASK_COLOR = { PENDING: '#98a2b3', RUNNING: '#155eef', DONE: '#12b76a', OVERDUE: '#f04438', CANCELED: '#d4dde9' }
const EVENT_COLOR = { I: '#f04438', II: '#f79009', III: '#155eef', IV: '#98a2b3' }

/* ---------- ECharts:统一动画参数,数据更新时形态过渡 ---------- */
const ANIM = {
  animationDuration: 800,
  animationEasing: 'cubicOut',
  animationDurationUpdate: 700,
  animationEasingUpdate: 'cubicInOut'
}
const AXIS_LABEL = { color: '#667085', fontSize: 11 }

const riskRef = ref(null)
const taskRef = ref(null)
const eventRef = ref(null)
const trendRef = ref(null)
let riskChart = null
let taskChart = null
let eventChart = null
let trendChart = null

/** 亮蓝纵向渐变柱:顶饱和 → 底半透明 */
const barGradient = (hex) => {
  const r = parseInt(hex.slice(1, 3), 16), g = parseInt(hex.slice(3, 5), 16), b = parseInt(hex.slice(5, 7), 16)
  return new echarts.graphic.LinearGradient(0, 0, 0, 1, [
    { offset: 0, color: hex },
    { offset: 1, color: `rgba(${r},${g},${b},.45)` }
  ])
}

function renderRisk() {
  const data = Object.entries(stats.pointByRisk || {}).map(([k, v]) => ({
    name: dictLabel(RISK_LEVEL, k), value: v,
    itemStyle: { color: RISK_COLOR[k] || '#98a2b3' }
  }))
  const total = data.reduce((s, d) => s + d.value, 0)
  riskChart?.setOption({
    ...ANIM,
    title: {
      text: String(total), subtext: '点位总数', left: 'center', top: '34%',
      textStyle: { fontSize: 25, fontWeight: 700, color: '#101828' },
      subtextStyle: { fontSize: 11.5, color: '#98a2b3' }
    },
    tooltip: { trigger: 'item', formatter: '{b}: {c} 个 ({d}%)' },
    legend: { bottom: 0, icon: 'circle', itemWidth: 8, itemHeight: 8, textStyle: AXIS_LABEL },
    series: [{
      type: 'pie', radius: ['55%', '74%'], center: ['50%', '43%'],
      avoidLabelOverlap: true, universalTransition: true,
      itemStyle: { borderColor: '#fff', borderWidth: 2, borderRadius: 5 },
      label: { formatter: '{b} {c}', fontSize: 11.5, color: '#475467' },
      labelLine: { length: 12, length2: 8, lineStyle: { color: '#cdd5e1' } },
      data
    }]
  })
}

function renderTask() {
  const entries = Object.entries(stats.taskByStatus || {})
  taskChart?.setOption({
    ...ANIM,
    tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' } },
    grid: { left: 10, right: 14, top: 26, bottom: 6, containLabel: true },
    xAxis: {
      type: 'category', data: entries.map(([k]) => dictLabel(TASK_STATUS, k)),
      axisTick: { show: false }, axisLine: { lineStyle: { color: '#e4e9f2' } }, axisLabel: AXIS_LABEL
    },
    yAxis: {
      type: 'value', minInterval: 1,
      splitLine: { lineStyle: { color: '#f0f3f8' } }, axisLabel: AXIS_LABEL
    },
    series: [{
      type: 'bar', barWidth: 26,
      itemStyle: { borderRadius: [7, 7, 0, 0] },
      animationDelay: (i) => i * 90,
      data: entries.map(([k, v]) => ({ value: v, itemStyle: { color: barGradient(TASK_COLOR[k] || '#98a2b3') } }))
    }]
  })
}

function renderEvent() {
  const entries = Object.entries(stats.eventByLevel || {})
  eventChart?.setOption({
    ...ANIM,
    tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' } },
    grid: { left: 10, right: 14, top: 26, bottom: 6, containLabel: true },
    xAxis: {
      type: 'category',
      data: entries.map(([k]) => dictLabel(EVENT_LEVEL, k).replace('级 ', '\n')),
      axisTick: { show: false }, axisLine: { lineStyle: { color: '#e4e9f2' } },
      axisLabel: { ...AXIS_LABEL, lineHeight: 14 }
    },
    yAxis: {
      type: 'value', minInterval: 1,
      splitLine: { lineStyle: { color: '#f0f3f8' } }, axisLabel: AXIS_LABEL
    },
    series: [{
      type: 'bar', barWidth: 26,
      itemStyle: { borderRadius: [7, 7, 0, 0] },
      animationDelay: (i) => i * 90,
      data: entries.map(([k, v]) => ({ value: v, itemStyle: { color: barGradient(EVENT_COLOR[k] || '#98a2b3') } }))
    }]
  })
}

function renderTrend() {
  const trend = stats.taskTrend || []
  trendChart?.setOption({
    ...ANIM,
    animationDuration: 1100,
    tooltip: { trigger: 'axis' },
    legend: { right: 8, top: 0, icon: 'circle', itemWidth: 8, itemHeight: 8, textStyle: AXIS_LABEL },
    grid: { left: 10, right: 20, top: 34, bottom: 8, containLabel: true },
    xAxis: {
      type: 'category', boundaryGap: false, axisTick: { show: false },
      axisLine: { lineStyle: { color: '#e4e9f2' } }, axisLabel: AXIS_LABEL,
      // 后端给 yyyy-MM-dd,图表轴只保留 MM-dd
      data: trend.map((d) => String(d.date).slice(5))
    },
    yAxis: {
      type: 'value', minInterval: 1,
      splitLine: { lineStyle: { color: '#f0f3f8' } }, axisLabel: AXIS_LABEL
    },
    series: [
      { name: '计划任务', type: 'line', smooth: true, symbolSize: 6,
        symbol: 'circle', itemStyle: { color: '#155eef', borderColor: '#fff', borderWidth: 1.5 },
        lineStyle: { width: 2.5, color: '#155eef' },
        areaStyle: { color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
          { offset: 0, color: 'rgba(21,94,239,.16)' }, { offset: 1, color: 'rgba(21,94,239,0)' }]) },
        animationDelay: 150,
        data: trend.map((d) => d.total) },
      { name: '已完成', type: 'line', smooth: true, symbolSize: 6,
        symbol: 'circle', itemStyle: { color: '#12b76a', borderColor: '#fff', borderWidth: 1.5 },
        lineStyle: { width: 2.5, color: '#12b76a' },
        areaStyle: { color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
          { offset: 0, color: 'rgba(18,183,106,.12)' }, { offset: 1, color: 'rgba(18,183,106,0)' }]) },
        animationDelay: 300,
        data: trend.map((d) => d.done) }
    ]
  })
}

function renderAll() {
  renderRisk()
  renderTask()
  renderEvent()
  renderTrend()
}

function resize() {
  riskChart?.resize()
  taskChart?.resize()
  eventChart?.resize()
  trendChart?.resize()
}

/* ---------- 数据加载 + 30s 自动刷新(页面隐藏时暂停) ---------- */
async function loadStats() {
  loading.value = true
  try {
    Object.assign(stats, await http.get('/dashboard/stats'))
    lastRefresh.value = new Date().toLocaleTimeString('zh-CN', { hour12: false })
    // 指标数字滚动到新值
    ;['pointTotal', 'planEnabled', 'taskToday', 'taskOverdue', 'hazardPending',
      'eventActive', 'dockOnline', 'alarmToday'].forEach((k) => countUp(k, stats[k] ?? 0))
    await nextTick()
    renderAll()
  } finally { loading.value = false }
}

let cdTimer = null

onMounted(async () => {
  await nextTick()
  // 加载期间组件可能已被卸载(快速切走路由),refs 为空时直接放弃初始化
  if (!riskRef.value || !taskRef.value || !eventRef.value || !trendRef.value) return
  riskChart = echarts.init(riskRef.value)
  taskChart = echarts.init(taskRef.value)
  eventChart = echarts.init(eventRef.value)
  trendChart = echarts.init(trendRef.value)
  await loadStats()
  window.addEventListener('resize', resize)
  // 倒计时展示 + 到点刷新(标签页不可见时跳过本轮,回前台立即补一次)
  cdTimer = setInterval(() => {
    countdown.value -= 1
    if (countdown.value <= 0) {
      countdown.value = 30
      if (!document.hidden) loadStats()
    }
  }, 1000)
  document.addEventListener('visibilitychange', onVisible)
})

function onVisible() {
  if (!document.hidden && lastRefresh.value) {
    countdown.value = 30
    loadStats()
  }
}

onUnmounted(() => {
  window.removeEventListener('resize', resize)
  document.removeEventListener('visibilitychange', onVisible)
  clearInterval(cdTimer)
  Object.values(rafs).forEach((id) => cancelAnimationFrame(id))
  riskChart?.dispose()
  taskChart?.dispose()
  eventChart?.dispose()
  trendChart?.dispose()
})
</script>

<style scoped>
.dashboard { padding-bottom: 26px; }
.header-tip { font-size: 13px; color: var(--text-dim); }

/* 实时刷新提示 */
.live-chip {
  display: inline-flex; align-items: center; gap: 6px;
  font-size: 12px; color: var(--text-dim);
  padding: 0 4px;
}
.live-dot {
  width: 7px; height: 7px; border-radius: 50%; background: #12b76a; flex-shrink: 0;
  animation: live-pulse 2s ease-out infinite;
}
@keyframes live-pulse {
  0% { box-shadow: 0 0 0 0 rgba(18, 183, 106, .45); }
  70% { box-shadow: 0 0 0 7px rgba(18, 183, 106, 0); }
  100% { box-shadow: 0 0 0 0 rgba(18, 183, 106, 0); }
}

/* ---------------- 欢迎横幅:白 → 亮蓝渐变,克制留白 ---------------- */
.banner {
  position: relative; overflow: hidden;
  display: flex; align-items: center; gap: 18px;
  padding: 20px 26px; border-radius: 12px;
  background: linear-gradient(115deg, #ffffff 0%, #f3f8ff 55%, #eaf3ff 100%);
  border: 1px solid #d9e7fb;
}
.banner::after {
  content: ''; position: absolute; left: 0; right: 0; bottom: 0; height: 2px;
  background: linear-gradient(90deg, #155eef, #0ea5e9 55%, rgba(14, 165, 233, 0));
}
.banner-main { min-width: 0; }
.banner-hi {
  font-size: 21px; font-weight: 800; letter-spacing: 1px; color: #0b2447;
}
.banner-date { margin-top: 6px; font-size: 12.5px; color: #5b7ba6; letter-spacing: 1px; }
.banner-live {
  margin-left: auto; display: flex; align-items: center; gap: 7px;
  font-size: 12px; color: #5b7ba6; white-space: nowrap;
}
.banner-actions { display: flex; gap: 10px; flex-shrink: 0; z-index: 1; }
.action-chip {
  display: flex; align-items: center; gap: 8px;
  padding: 10px 16px; border-radius: 999px; cursor: pointer;
  background: rgba(255, 255, 255, 0.85); backdrop-filter: blur(6px);
  border: 1px solid rgba(21, 94, 239, 0.2);
  font-size: 13px; font-weight: 600; color: #17325c;
  transition: all 0.2s;
}
.action-chip:hover {
  transform: translateY(-2px); border-color: rgba(21, 94, 239, 0.5);
  box-shadow: 0 8px 20px -8px rgba(21, 94, 239, 0.45); color: var(--primary);
}
.action-ico { width: 17px; height: 17px; display: inline-flex; }
.action-ico :deep(svg) { width: 100%; height: 100%; color: var(--primary); }

/* ---------------- 指标卡:去色条,浅底色图标 ---------------- */
.tiles {
  display: grid; grid-template-columns: repeat(8, 1fr); gap: 12px; margin-top: 12px;
}
.tile {
  display: flex; align-items: flex-start; gap: 12px;
  padding: 15px 14px 13px; overflow: hidden;
  transition: transform 0.2s, box-shadow 0.2s;
}
.tile:hover { transform: translateY(-3px); box-shadow: 0 14px 30px -12px rgba(21, 94, 239, 0.28); }
.tile-ico {
  width: 40px; height: 40px; border-radius: 10px; flex-shrink: 0;
  display: flex; align-items: center; justify-content: center;
  background: #eff6ff; color: #155eef;
}
.tile.tone-cyan .tile-ico { background: #e8f8fd; color: #0e9fbd; }
.tile.tone-warning .tile-ico { background: #fef4e6; color: #e08700; }
.tile.tone-danger .tile-ico { background: #feefec; color: #e0493a; }
.tile.tone-purple .tile-ico { background: #f3efff; color: #7c3aed; }
.tile-ico :deep(svg) { width: 21px; height: 21px; }
.tile-body { min-width: 0; }
.tile-label { font-size: 12.5px; color: var(--text-dim); white-space: nowrap; }
.tile-value {
  font-size: 27px; font-weight: 700; color: #101828; line-height: 1.3;
  margin: 1px 0 2px; font-variant-numeric: tabular-nums;
}
/* 需要关注的指标(逾期/隐患/事件)非零时数字转红 */
.tile-value.hot { color: #f04438; }
.tile-sub {
  font-size: 11px; color: var(--text-faint);
  white-space: nowrap; overflow: hidden; text-overflow: ellipsis;
}

/* ---------------- 图表 ---------------- */
.charts { display: grid; grid-template-columns: repeat(3, 1fr); gap: 12px; margin-top: 12px; }
.chart-panel { padding-bottom: 8px; }
.chart { height: 268px; width: 100%; }
.chart-panel.wide { margin-top: 12px; }
.chart-panel.wide .chart { height: 250px; }

/* ---------------- 列表 ---------------- */
.lists { display: grid; grid-template-columns: repeat(4, 1fr); gap: 12px; margin-top: 12px; }
.list-panel { padding-bottom: 8px; }
.list-head { justify-content: space-between; }
.list-head::before { display: none; }
.list-head > span { display: flex; align-items: center; gap: 8px; }
.list-head > span::before {
  content: ''; width: 4px; height: 16px; border-radius: 2px;
  background: linear-gradient(180deg, var(--primary), var(--primary-2));
}
.list-body { padding: 2px 10px 6px; max-height: 268px; overflow-y: auto; }
.empty { padding: 30px 0; text-align: center; color: var(--text-faint); font-size: 12.5px; }
.item {
  display: flex; align-items: center; justify-content: space-between; gap: 10px;
  padding: 9px 10px; margin-bottom: 6px; border-radius: 9px;
  border: 1px solid var(--border); cursor: pointer; transition: all .15s;
}
.item:hover { border-color: #b8ccf7; background: #f7faff; }
.item-main { min-width: 0; }
.item-tags { display: flex; flex-direction: column; align-items: flex-end; gap: 4px; flex-shrink: 0; }
.item-title {
  font-size: 13px; font-weight: 600; color: var(--text);
  white-space: nowrap; overflow: hidden; text-overflow: ellipsis;
}
.item-sub {
  font-size: 11.5px; color: var(--text-faint); margin-top: 3px;
  white-space: nowrap; overflow: hidden; text-overflow: ellipsis;
}

@media (max-width: 1500px) {
  .tiles { grid-template-columns: repeat(4, 1fr); }
  .lists { grid-template-columns: repeat(2, 1fr); }
}
@media (max-width: 1000px) {
  .charts { grid-template-columns: 1fr; }
  .banner { flex-wrap: wrap; }
  .banner-live { margin-left: 0; }
}
</style>
