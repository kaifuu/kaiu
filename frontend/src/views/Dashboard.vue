<template>
  <div class="page dashboard" v-loading="loading">
    <div class="page-header">
      <span class="page-title">工作台</span>
      <span class="header-tip">应急巡检 · 全域态势总览</span>
    </div>

    <!-- 概览指标 -->
    <div class="tiles">
      <div v-for="t in tiles" :key="t.label" class="panel tile" :class="t.tone">
        <div class="tile-label">{{ t.label }}</div>
        <div class="tile-value glow-num">{{ t.value }}</div>
        <div class="tile-sub">{{ t.sub }}</div>
      </div>
    </div>

    <!-- 分布图表 -->
    <div class="charts">
      <div class="panel chart-panel">
        <div class="panel-title">点位风险等级分布</div>
        <div ref="riskRef" class="chart"></div>
      </div>
      <div class="panel chart-panel">
        <div class="panel-title">任务状态分布</div>
        <div ref="taskRef" class="chart"></div>
      </div>
    </div>

    <!-- 近 7 日趋势 -->
    <div class="panel chart-panel wide">
      <div class="panel-title">近 7 日巡检任务趋势</div>
      <div ref="trendRef" class="chart"></div>
    </div>

    <!-- 待办清单 -->
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
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted, onUnmounted, nextTick } from 'vue'
// 按需引入:整包 echarts 会让本页 chunk 达到 ~1MB,这里只注册用到的图表与组件
import * as echarts from 'echarts/core'
import { PieChart, BarChart, LineChart } from 'echarts/charts'
import { TooltipComponent, LegendComponent, GridComponent } from 'echarts/components'
import { CanvasRenderer } from 'echarts/renderers'

echarts.use([PieChart, BarChart, LineChart, TooltipComponent, LegendComponent, GridComponent, CanvasRenderer])
import http from '../api'
import {
  RISK_LEVEL, TASK_STATUS, EVENT_LEVEL, EVENT_STATUS, HAZARD_LEVEL, HAZARD_STATUS,
  dictLabel, dictTag
} from '../utils/dict'

const loading = ref(false)
const stats = reactive({})

const tiles = computed(() => [
  { label: '点位总数', value: stats.pointTotal ?? 0, sub: '覆盖全部风险等级', tone: 'tone-primary' },
  { label: '启用计划', value: stats.planEnabled ?? 0, sub: '可按计划生成任务', tone: 'tone-cyan' },
  { label: '今日任务', value: stats.taskToday ?? 0, sub: `待执行 ${stats.taskPending ?? 0} · 执行中 ${stats.taskRunning ?? 0}`, tone: 'tone-primary' },
  { label: '逾期任务', value: stats.taskOverdue ?? 0, sub: '超过计划结束时间', tone: 'tone-danger' },
  { label: '待处理隐患', value: stats.hazardPending ?? 0, sub: `处理中 ${stats.hazardProcessing ?? 0}`, tone: 'tone-warning' },
  { label: '进行中事件', value: stats.eventActive ?? 0, sub: `累计事件 ${stats.eventTotal ?? 0}`, tone: 'tone-danger' }
])

/* 与 el-tag 语义一致的图表配色,保证同屏同义同色 */
const RISK_COLOR = { LOW: '#98a2b3', MEDIUM: '#155eef', HIGH: '#f79009', EXTREME: '#f04438' }
const TASK_COLOR = { PENDING: '#98a2b3', RUNNING: '#155eef', DONE: '#12b76a', OVERDUE: '#f04438', CANCELED: '#d4dde9' }

/* ---------- ECharts:实例统一在 onUnmounted 释放,窗口尺寸变化时重绘 ---------- */
const riskRef = ref(null)
const taskRef = ref(null)
const trendRef = ref(null)
let riskChart = null
let taskChart = null
let trendChart = null

function renderRisk() {
  const data = Object.entries(stats.pointByRisk || {}).map(([k, v]) => ({
    name: dictLabel(RISK_LEVEL, k), value: v,
    itemStyle: { color: RISK_COLOR[k] || '#98a2b3' }
  }))
  riskChart?.setOption({
    tooltip: { trigger: 'item', formatter: '{b}: {c} 个 ({d}%)' },
    legend: { bottom: 0, icon: 'circle', itemWidth: 8, itemHeight: 8 },
    series: [{
      type: 'pie', radius: ['46%', '70%'], center: ['50%', '46%'],
      avoidLabelOverlap: true,
      label: { formatter: '{b}\n{c}' },
      data
    }]
  })
}

function renderTask() {
  const entries = Object.entries(stats.taskByStatus || {})
  taskChart?.setOption({
    tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' } },
    grid: { left: 12, right: 16, top: 24, bottom: 8, containLabel: true },
    xAxis: { type: 'category', data: entries.map(([k]) => dictLabel(TASK_STATUS, k)), axisTick: { show: false } },
    yAxis: { type: 'value', minInterval: 1, splitLine: { lineStyle: { color: '#eef2f7' } } },
    series: [{
      type: 'bar', barWidth: 26,
      itemStyle: { borderRadius: [6, 6, 0, 0] },
      data: entries.map(([k, v]) => ({ value: v, itemStyle: { color: TASK_COLOR[k] || '#98a2b3' } }))
    }]
  })
}

function renderTrend() {
  const trend = stats.taskTrend || []
  trendChart?.setOption({
    tooltip: { trigger: 'axis' },
    legend: { right: 8, top: 0, icon: 'circle', itemWidth: 8, itemHeight: 8 },
    grid: { left: 12, right: 20, top: 34, bottom: 8, containLabel: true },
    xAxis: {
      type: 'category', boundaryGap: false, axisTick: { show: false },
      // 后端给 yyyy-MM-dd,图表轴只保留 MM-dd
      data: trend.map((d) => String(d.date).slice(5))
    },
    yAxis: { type: 'value', minInterval: 1, splitLine: { lineStyle: { color: '#eef2f7' } } },
    series: [
      { name: '计划任务', type: 'line', smooth: true, symbolSize: 6, data: trend.map((d) => d.total),
        lineStyle: { width: 2, color: '#155eef' }, itemStyle: { color: '#155eef' },
        areaStyle: { color: 'rgba(21,94,239,.10)' } },
      { name: '已完成', type: 'line', smooth: true, symbolSize: 6, data: trend.map((d) => d.done),
        lineStyle: { width: 2, color: '#12b76a' }, itemStyle: { color: '#12b76a' },
        areaStyle: { color: 'rgba(18,183,106,.10)' } }
    ]
  })
}

function resize() {
  riskChart?.resize()
  taskChart?.resize()
  trendChart?.resize()
}

onMounted(async () => {
  loading.value = true
  try {
    Object.assign(stats, await http.get('/dashboard/stats'))
  } finally { loading.value = false }
  await nextTick()
  // 加载期间组件可能已被卸载(快速切走路由),refs 为空时直接放弃初始化
  if (!riskRef.value || !taskRef.value || !trendRef.value) return
  riskChart = echarts.init(riskRef.value)
  taskChart = echarts.init(taskRef.value)
  trendChart = echarts.init(trendRef.value)
  renderRisk()
  renderTask()
  renderTrend()
  window.addEventListener('resize', resize)
})

onUnmounted(() => {
  window.removeEventListener('resize', resize)
  riskChart?.dispose()
  taskChart?.dispose()
  trendChart?.dispose()
})
</script>

<style scoped>
.dashboard { padding-bottom: 26px; }
.header-tip { font-size: 13px; color: var(--text-dim); }

.tiles {
  display: grid; grid-template-columns: repeat(6, 1fr); gap: 12px;
}
.tile { padding: 14px 16px; border-top: 3px solid var(--primary); }
.tile.tone-cyan { border-top-color: var(--primary-2); }
.tile.tone-warning { border-top-color: var(--warning); }
.tile.tone-danger { border-top-color: var(--danger); }
.tile-label { font-size: 12.5px; color: var(--text-dim); }
.tile-value { font-size: 28px; line-height: 1.35; margin: 2px 0 3px; }
.tile-sub { font-size: 11.5px; color: var(--text-faint); white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }

.charts { display: grid; grid-template-columns: 1fr 1fr; gap: 12px; margin-top: 12px; }
.chart-panel { padding-bottom: 8px; }
.chart { height: 268px; width: 100%; }
.chart-panel.wide { margin-top: 12px; }
.chart-panel.wide .chart { height: 250px; }

.lists { display: grid; grid-template-columns: repeat(3, 1fr); gap: 12px; margin-top: 12px; }
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
.item:hover { border-color: #b8ccf7; background: #f7faff; transform: translateX(2px); }
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

@media (max-width: 1400px) {
  .tiles { grid-template-columns: repeat(3, 1fr); }
  .lists { grid-template-columns: 1fr; }
}
@media (max-width: 1000px) {
  .charts { grid-template-columns: 1fr; }
}
</style>
