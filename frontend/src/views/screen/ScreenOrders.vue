<template>
  <div class="sub">
    <div class="scr-kpi-row" style="margin-bottom: 12px">
      <div class="scr-kpi" style="--kpi-line: linear-gradient(180deg, #38bdf8, #1d4ed8)">
        <div class="ico" v-html="ICONS.order"></div>
        <div class="body">
          <div class="label">工单总数</div>
          <div class="value">{{ kpi.total ?? 0 }}</div>
        </div>
      </div>
      <div class="scr-kpi" style="--kpi-line: linear-gradient(180deg, #fbbf24, #b45309)">
        <div class="ico" v-html="ICONS.wait"></div>
        <div class="body">
          <div class="label">待派发</div>
          <div class="value" :class="{ warn: (kpi.pending ?? 0) > 0 }">{{ kpi.pending ?? 0 }}</div>
        </div>
      </div>
      <div class="scr-kpi" style="--kpi-line: linear-gradient(180deg, #fb923c, #c2410c)">
        <div class="ico" v-html="ICONS.doing"></div>
        <div class="body">
          <div class="label">处理中</div>
          <div class="value cyan">{{ kpi.processing ?? 0 }}</div>
        </div>
      </div>
      <div class="scr-kpi" style="--kpi-line: linear-gradient(180deg, #34d399, #0e7490)">
        <div class="ico" v-html="ICONS.done"></div>
        <div class="body">
          <div class="label">已办结</div>
          <div class="value good">{{ kpi.done ?? 0 }}</div>
        </div>
      </div>
    </div>

    <div class="cols">
      <!-- ===== 左列 ===== -->
      <section class="col">
        <div class="scr-panel grow">
          <div class="panel-hd">工单状态分布</div>
          <div ref="statusRef" class="chart-box"></div>
        </div>
        <div class="scr-panel grow">
          <div class="panel-hd">优先级分布</div>
          <div ref="prioRef" class="chart-box"></div>
        </div>
      </section>

      <!-- ===== 中列 ===== -->
      <section class="col center">
        <div class="scr-panel grow">
          <div class="panel-hd">部门工单量排行</div>
          <div ref="deptRef" class="chart-box"></div>
        </div>
        <div class="scr-panel" style="flex: 0 0 190px">
          <div class="panel-hd">近 7 日工单趋势<span class="hd-note">新建 / 办结</span></div>
          <div ref="trendRef" class="chart-box"></div>
        </div>
      </section>

      <!-- ===== 右列 ===== -->
      <section class="col">
        <div class="scr-panel grow">
          <div class="panel-hd">
            最近工单
            <span class="hd-note">{{ recentOrders.length }} 条</span>
          </div>
          <div class="scroll">
            <div v-for="o in recentOrders" :key="o.id" class="scr-row"
              :style="{ borderLeftColor: prioColor(o.priority) }">
              <div class="main">
                <span class="t1">{{ o.title }}</span>
                <span class="t2">
                  {{ o.dept || '—' }} · {{ o.handler || '未派单' }} · {{ (o.createTime || '').slice(5, 10) }}
                </span>
              </div>
              <div class="side">
                <el-tag size="small" :type="dictTag(WORK_ORDER_STATUS, o.status)" effect="dark">
                  {{ dictLabel(WORK_ORDER_STATUS, o.status) }}
                </el-tag>
                <el-tag size="small" :type="dictTag(WORK_ORDER_PRIORITY, o.priority)" effect="plain">
                  {{ dictLabel(WORK_ORDER_PRIORITY, o.priority) }}
                </el-tag>
              </div>
            </div>
            <div v-if="!recentOrders.length" class="empty">暂无工单</div>
          </div>
        </div>
        <div class="scr-panel" style="flex: 0 0 auto">
          <div class="panel-hd">巡检需求状态</div>
          <div class="demand">
            <div v-for="(v, key) in demandByStatus" :key="key" class="dm-item">
              <span class="dm-val">{{ v }}</span>
              <span class="dm-label">{{ dictLabel(DEMAND_STATUS, key) }}</span>
            </div>
          </div>
        </div>
      </section>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted, nextTick } from 'vue'
import http from '../../api'
import { echarts, AXIS, SPLIT, TIP, ANIM, barGradient } from './echartsDark'
import { WORK_ORDER_STATUS, WORK_ORDER_PRIORITY, DEMAND_STATUS, dictLabel, dictTag } from '../../utils/dict'

const REFRESH_MS = 30000
const data = ref({})
let timer = null

const kpi = computed(() => data.value.kpi || {})
const orderByStatus = computed(() => data.value.orderByStatus || {})
const orderByPriority = computed(() => data.value.orderByPriority || {})
const orderByDept = computed(() => data.value.orderByDept || [])
const recentOrders = computed(() => data.value.recentOrders || [])
const demandByStatus = computed(() => data.value.demandByStatus || {})
const trend = computed(() => data.value.orderTrend || [])

const STATUS_COLOR = { PENDING: '#fbbf24', PROCESSING: '#38bdf8', HANDLED: '#34d399', CLOSED: '#64748b' }
const PRIO_COLOR = { LOW: '#64748b', NORMAL: '#38bdf8', HIGH: '#fb923c', URGENT: '#f87171' }
const prioColor = (p) => PRIO_COLOR[p] || '#38bdf8'

const ico = (p) => `<svg viewBox="0 0 24 24" fill="none" stroke="#7dd3fc" stroke-width="1.7"
  stroke-linecap="round" stroke-linejoin="round">${p}</svg>`
const ICONS = {
  order: ico('<rect x="4" y="4.5" width="16" height="15" rx="2.5"/><path d="M8.5 4.5V2.8M15.5 4.5V2.8"/><path d="M4 9.5h16"/><path d="M8.5 13h5M8.5 16.2h3"/>'),
  wait: ico('<circle cx="12" cy="12" r="8"/><path d="M12 8v4.5l3 1.8"/>'),
  doing: ico('<path d="M5 19c2.5 0 2.5-4 5-4s2.5 4 5 4 2.5-2 4-2"/><path d="M12 13.5V4"/><circle cx="12" cy="4" r="1.6"/>'),
  done: ico('<circle cx="12" cy="12" r="8"/><path d="m8.6 12.2 2.3 2.3 4.5-4.6"/>')
}

/* ==================== 图表 ==================== */

const statusRef = ref(null)
const prioRef = ref(null)
const deptRef = ref(null)
const trendRef = ref(null)
let statusChart = null
let prioChart = null
let deptChart = null
let trendChart = null

function renderStatus() {
  statusChart?.setOption({
    ...ANIM,
    tooltip: { trigger: 'item', ...TIP, formatter: '{b}: {c} 单 ({d}%)' },
    legend: { bottom: 0, icon: 'circle', itemWidth: 8, itemHeight: 8, textStyle: AXIS },
    series: [{
      type: 'pie', radius: ['40%', '64%'], center: ['50%', '42%'],
      itemStyle: { borderColor: '#04102a', borderWidth: 2, borderRadius: 4 },
      label: { color: AXIS.color, fontSize: 11, formatter: '{c}' },
      data: Object.entries(orderByStatus.value).map(([k, v]) => ({
        name: dictLabel(WORK_ORDER_STATUS, k), value: v,
        itemStyle: { color: STATUS_COLOR[k] || '#64748b' }
      }))
    }]
  })
}

function renderPrio() {
  const entries = Object.entries(orderByPriority.value)
  prioChart?.setOption({
    ...ANIM,
    tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' }, ...TIP },
    grid: { left: 10, right: 14, top: 22, bottom: 4, containLabel: true },
    xAxis: {
      type: 'category', axisTick: { show: false },
      axisLine: { lineStyle: { color: 'rgba(56,189,248,.3)' } }, axisLabel: AXIS,
      data: entries.map(([k]) => dictLabel(WORK_ORDER_PRIORITY, k))
    },
    yAxis: { type: 'value', minInterval: 1, splitLine: SPLIT, axisLabel: AXIS },
    series: [{
      type: 'bar', barWidth: 22,
      animationDelay: (i) => i * 90,
      itemStyle: { borderRadius: [6, 6, 0, 0] },
      data: entries.map(([k, v]) => ({ value: v, itemStyle: { color: barGradient(PRIO_COLOR[k] || '#38bdf8') } }))
    }]
  })
}

function renderDept() {
  const list = orderByDept.value
  deptChart?.setOption({
    ...ANIM,
    tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' }, ...TIP },
    grid: { left: 10, right: 42, top: 8, bottom: 0, containLabel: true },
    xAxis: { type: 'value', minInterval: 1, splitLine: SPLIT, axisLabel: AXIS },
    yAxis: {
      type: 'category', inverse: true,
      axisTick: { show: false }, axisLine: { show: false },
      axisLabel: { ...AXIS, width: 118, overflow: 'truncate' },
      data: list.map((d) => d.dept)
    },
    series: [{
      type: 'bar', barWidth: 12,
      animationDelay: (i) => i * 70,
      itemStyle: { borderRadius: [0, 6, 6, 0] },
      label: { show: true, position: 'right', color: '#7dd3fc', fontSize: 11 },
      data: list.map((d, i) => ({
        value: d.count,
        itemStyle: { color: barGradient(['#f87171', '#fb923c', '#fbbf24', '#38bdf8', '#818cf8', '#22d3ee', '#34d399', '#7dd3fc'][i % 8]) }
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
    grid: { left: 10, right: 18, top: 28, bottom: 4, containLabel: true },
    xAxis: {
      type: 'category', boundaryGap: false, axisTick: { show: false },
      axisLine: { lineStyle: { color: 'rgba(56,189,248,.3)' } }, axisLabel: AXIS,
      data: t.map((d) => String(d.date).slice(5))
    },
    yAxis: { type: 'value', minInterval: 1, splitLine: SPLIT, axisLabel: AXIS },
    series: [
      { name: '新建', type: 'line', smooth: true, symbolSize: 6,
        itemStyle: { color: '#38bdf8', borderColor: '#04102a', borderWidth: 1.5 },
        lineStyle: { width: 2.5, color: '#38bdf8' },
        areaStyle: { color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
          { offset: 0, color: 'rgba(56,189,248,.22)' }, { offset: 1, color: 'rgba(56,189,248,0)' }]) },
        data: t.map((d) => d.total) },
      { name: '办结', type: 'line', smooth: true, symbolSize: 6,
        itemStyle: { color: '#34d399', borderColor: '#04102a', borderWidth: 1.5 },
        lineStyle: { width: 2.5, color: '#34d399' }, data: t.map((d) => d.done) }
    ]
  })
}

/* ==================== 数据 ==================== */

async function load() {
  data.value = (await http.get('/screen/orders')) || {}
  await nextTick()
  renderStatus()
  renderPrio()
  renderDept()
  renderTrend()
}

function resize() {
  statusChart?.resize()
  prioChart?.resize()
  deptChart?.resize()
  trendChart?.resize()
}

onMounted(async () => {
  await nextTick()
  statusChart = echarts.init(statusRef.value)
  prioChart = echarts.init(prioRef.value)
  deptChart = echarts.init(deptRef.value)
  trendChart = echarts.init(trendRef.value)
  await load()
  window.addEventListener('resize', resize)
  timer = setInterval(load, REFRESH_MS)
})
onUnmounted(() => {
  clearInterval(timer)
  window.removeEventListener('resize', resize)
  statusChart?.dispose()
  prioChart?.dispose()
  deptChart?.dispose()
  trendChart?.dispose()
})
</script>

<style scoped>
.sub { display: flex; flex-direction: column; min-width: 0; }
.cols { flex: 1; min-height: 0; display: grid; grid-template-columns: 340px 1fr 370px; gap: 12px; }
.col { display: flex; flex-direction: column; gap: 12px; min-height: 0; }
.col.center { gap: 10px; }

.demand { display: grid; grid-template-columns: repeat(4, 1fr); gap: 8px; padding: 2px 12px 10px; }
.dm-item {
  display: flex; flex-direction: column; align-items: center; gap: 3px;
  padding: 9px 4px; border-radius: 6px;
  background: rgba(56, 189, 248, .07);
  border: 1px solid rgba(56, 189, 248, .14);
}
.dm-val { font-size: 20px; font-weight: 800; color: #e0f2fe; font-variant-numeric: tabular-nums; }
.dm-label { font-size: 11px; color: var(--scr-dim); }

@media (max-width: 1500px) { .cols { grid-template-columns: 300px 1fr 330px; } }
@media (max-width: 1200px) {
  .cols { grid-template-columns: 1fr 1fr; grid-auto-rows: minmax(0, 1fr); }
  .col.center { grid-column: 1 / -1; }
}
</style>
