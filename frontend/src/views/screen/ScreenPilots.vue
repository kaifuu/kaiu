<template>
  <div class="sub">
    <div class="scr-kpi-row" style="margin-bottom: 12px">
      <div class="scr-kpi" style="--kpi-line: linear-gradient(180deg, #38bdf8, #1d4ed8)">
        <div class="ico" v-html="ICONS.user"></div>
        <div class="body">
          <div class="label">飞手总数</div>
          <div class="value">{{ kpi.total ?? 0 }}</div>
        </div>
      </div>
      <div class="scr-kpi" style="--kpi-line: linear-gradient(180deg, #34d399, #0e7490)">
        <div class="ico" v-html="ICONS.ok"></div>
        <div class="body">
          <div class="label">可调度</div>
          <div class="value good">{{ kpi.available ?? 0 }}</div>
        </div>
      </div>
      <div class="scr-kpi" style="--kpi-line: linear-gradient(180deg, #fb923c, #c2410c)">
        <div class="ico" v-html="ICONS.fly"></div>
        <div class="body">
          <div class="label">执行任务中</div>
          <div class="value cyan">{{ kpi.onTask ?? 0 }}</div>
        </div>
      </div>
      <div class="scr-kpi" style="--kpi-line: linear-gradient(180deg, #818cf8, #4338ca)">
        <div class="ico" v-html="ICONS.medal"></div>
        <div class="body">
          <div class="label">平均飞行年限</div>
          <div class="value small">{{ kpi.avgExperience ?? 0 }} 年</div>
        </div>
      </div>
    </div>

    <div class="cols">
      <!-- ===== 左列 ===== -->
      <section class="col">
        <div class="scr-panel grow">
          <div class="panel-hd">飞手状态分布</div>
          <div ref="statusRef" class="chart-box"></div>
        </div>
        <div class="scr-panel grow">
          <div class="panel-hd">证照类型分布</div>
          <div ref="certRef" class="chart-box"></div>
        </div>
      </section>

      <!-- ===== 中列:飞手卡片墙 ===== -->
      <section class="col center">
        <div class="scr-panel grow">
          <div class="panel-hd">
            飞手队伍
            <span class="hd-note">{{ pilots.length }} 人 · 状态实时</span>
          </div>
          <div class="cards">
            <div v-for="p in pilots" :key="p.id" class="card" :data-status="p.status">
              <div class="avatar">{{ (p.name || '?').slice(0, 1) }}</div>
              <div class="info">
                <div class="name-row">
                  <span class="name">{{ p.name }}</span>
                  <span class="age">{{ p.age }} 岁</span>
                </div>
                <div class="tags">
                  <span class="tag">{{ p.area }}</span>
                  <span class="tag cert">{{ dictLabel(PILOT_CERT, p.certType) }}</span>
                </div>
                <div class="foot">
                  <span class="years">{{ p.experienceYears }} 年驾龄</span>
                  <span class="status"><i />{{ dictLabel(PILOT_STATUS, p.status) }}</span>
                </div>
              </div>
            </div>
            <div v-if="!pilots.length" class="empty">暂无飞手档案</div>
          </div>
        </div>
      </section>

      <!-- ===== 右列 ===== -->
      <section class="col">
        <div class="scr-panel grow">
          <div class="panel-hd">负责片区分布</div>
          <div class="scr-bars big">
            <div v-for="(v, area) in pilotByArea" :key="area" class="row">
              <span class="name" style="width: 76px">{{ area }}</span>
              <span class="track" style="--bar: linear-gradient(90deg, #0e7490, #22d3ee)">
                <i :style="{ width: barWidth(v, areaMax) }"></i>
              </span>
              <span class="val">{{ v }} 人</span>
            </div>
            <div v-if="!Object.keys(pilotByArea).length" class="empty">暂无数据</div>
          </div>
        </div>
        <div class="scr-panel grow">
          <div class="panel-hd">证照签发机构</div>
          <div class="orgs">
            <div v-for="(v, org) in orgCount" :key="org" class="org">
              <span class="org-name">{{ org }}</span>
              <span class="org-val">{{ v }} 人</span>
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
import { PILOT_STATUS, PILOT_CERT, dictLabel } from '../../utils/dict'

const REFRESH_MS = 30000
const data = ref({})
let timer = null

const kpi = computed(() => data.value.kpi || {})
const pilotByStatus = computed(() => data.value.pilotByStatus || {})
const pilotByCert = computed(() => data.value.pilotByCert || {})
const pilotByArea = computed(() => data.value.pilotByArea || {})
const pilots = computed(() => data.value.pilots || [])

const areaMax = computed(() => Math.max(1, ...Object.values(pilotByArea.value)))
/** 签发机构统计(卡片墙同源数据聚合) */
const orgCount = computed(() => {
  const m = {}
  for (const p of pilots.value) {
    if (p.certOrg) m[p.certOrg] = (m[p.certOrg] || 0) + 1
  }
  return m
})

function barWidth(v, max) {
  return Math.max(8, Math.round((v / max) * 100)) + '%'
}

const ico = (p) => `<svg viewBox="0 0 24 24" fill="none" stroke="#7dd3fc" stroke-width="1.7"
  stroke-linecap="round" stroke-linejoin="round">${p}</svg>`
const ICONS = {
  user: ico('<circle cx="12" cy="8" r="3.6"/><path d="M5 20c.8-4 3.6-6 7-6s6.2 2 7 6"/>'),
  ok: ico('<circle cx="12" cy="12" r="8"/><path d="m8.6 12.2 2.3 2.3 4.5-4.6"/>'),
  fly: ico('<path d="M12 3.5 13.5 11l7 4v2l-7-2-1 5 2.5 2v1.5L12 20l-3 1.5V20l2.5-2-1-5-7 2v-2l7-4z"/>'),
  medal: ico('<circle cx="12" cy="14.5" r="5"/><path d="m9.8 10 -2-6M14.2 10l2-6"/><path d="m10.3 14.4 1.2 1.2 2.3-2.4"/>')
}

/* ==================== 图表 ==================== */

const statusRef = ref(null)
const certRef = ref(null)
let statusChart = null
let certChart = null

const STATUS_COLOR = { AVAILABLE: '#34d399', ON_TASK: '#38bdf8', LEAVE: '#fbbf24', DISABLED: '#64748b' }
const CERT_COLOR = { CAAC: '#38bdf8', UTC: '#34d399', AOPA: '#818cf8' }

function renderStatus() {
  statusChart?.setOption({
    ...ANIM,
    tooltip: { trigger: 'item', ...TIP, formatter: '{b}: {c} 人 ({d}%)' },
    // 环上不放引出标签(340px 面板必截断),数值并进底部图例
    legend: {
      bottom: 0, icon: 'circle', itemWidth: 8, itemHeight: 8, textStyle: AXIS,
      formatter: (name) => {
        const hit = Object.entries(pilotByStatus.value)
          .find(([k]) => dictLabel(PILOT_STATUS, k) === name)
        return `${name} ${hit ? hit[1] : 0}`
      }
    },
    series: [{
      type: 'pie', radius: ['40%', '64%'], center: ['50%', '42%'],
      itemStyle: { borderColor: '#04102a', borderWidth: 2, borderRadius: 4 },
      label: { show: false },
      data: Object.entries(pilotByStatus.value).map(([k, v]) => ({
        name: dictLabel(PILOT_STATUS, k), value: v,
        itemStyle: { color: STATUS_COLOR[k] || '#64748b' }
      }))
    }]
  })
}

function renderCert() {
  // 数据侧就给短名(CAAC/UTC/AOPA),并剔除 0 值的"无证"类——零高柱只会挤占轴标签
  const entries = Object.entries(pilotByCert.value).filter(([, v]) => v > 0)
  certChart?.setOption({
    ...ANIM,
    tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' }, ...TIP },
    grid: { left: 10, right: 14, top: 22, bottom: 4, containLabel: true },
    xAxis: {
      type: 'category', axisTick: { show: false },
      axisLine: { lineStyle: { color: 'rgba(56,189,248,.3)' } },
      axisLabel: { ...AXIS, interval: 0 },
      data: entries.map(([k]) => dictLabel(PILOT_CERT, k).split(' ')[0])
    },
    yAxis: { type: 'value', minInterval: 1, splitLine: SPLIT, axisLabel: AXIS },
    series: [{
      type: 'bar', barWidth: 24,
      animationDelay: (i) => i * 90,
      itemStyle: { borderRadius: [6, 6, 0, 0] },
      data: entries.map(([k, v]) => ({ value: v, itemStyle: { color: barGradient(CERT_COLOR[k] || '#38bdf8') } }))
    }]
  })
}

/* ==================== 数据 ==================== */

async function load() {
  data.value = (await http.get('/screen/pilots')) || {}
  await nextTick()
  renderStatus()
  renderCert()
}

function resize() {
  statusChart?.resize()
  certChart?.resize()
}

onMounted(async () => {
  await nextTick()
  statusChart = echarts.init(statusRef.value)
  certChart = echarts.init(certRef.value)
  await load()
  window.addEventListener('resize', resize)
  timer = setInterval(load, REFRESH_MS)
})
onUnmounted(() => {
  clearInterval(timer)
  window.removeEventListener('resize', resize)
  statusChart?.dispose()
  certChart?.dispose()
})
</script>

<style scoped>
.sub { display: flex; flex-direction: column; min-width: 0; }
.cols { flex: 1; min-height: 0; display: grid; grid-template-columns: 340px 1fr 360px; gap: 12px; }
.col { display: flex; flex-direction: column; gap: 12px; min-height: 0; }
.col.center { gap: 10px; }

/* 飞手卡片墙 */
.cards {
  flex: 1; min-height: 0; overflow-y: auto;
  display: grid; grid-template-columns: repeat(auto-fill, minmax(236px, 1fr));
  gap: 10px; padding: 2px 12px 8px;
  /* 行高随面板拉伸,8 卡正好铺满卡片墙,不留底部空白 */
  grid-auto-rows: minmax(108px, 1fr);
}
.cards::-webkit-scrollbar { width: 4px; }
.cards::-webkit-scrollbar-thumb { background: rgba(56, 189, 248, .35); border-radius: 2px; }
.card {
  display: flex; gap: 11px; padding: 12px;
  border-radius: 8px;
  background: rgba(56, 189, 248, .06);
  border: 1px solid rgba(56, 189, 248, .18);
  transition: transform .2s, border-color .2s;
}
.card:hover { transform: translateY(-2px); border-color: rgba(56, 189, 248, .45); }
.avatar {
  width: 44px; height: 44px; border-radius: 10px; flex-shrink: 0;
  display: flex; align-items: center; justify-content: center;
  font-size: 19px; font-weight: 800; color: #04102a;
  background: linear-gradient(160deg, #7dd3fc, #38bdf8);
  box-shadow: 0 0 14px rgba(56, 189, 248, .35);
}
.card[data-status='ON_TASK'] .avatar { background: linear-gradient(160deg, #fdba74, #fb923c); box-shadow: 0 0 14px rgba(251, 146, 60, .4); }
.card[data-status='LEAVE'] .avatar { background: linear-gradient(160deg, #fcd34d, #fbbf24); box-shadow: none; opacity: .8; }
.info { min-width: 0; flex: 1; }
.name-row { display: flex; align-items: baseline; justify-content: space-between; gap: 6px; }
.name { font-size: 14.5px; font-weight: 700; color: #e0f2fe; }
.age { font-size: 11px; color: var(--scr-dim); }
.tags { display: flex; gap: 5px; margin-top: 5px; flex-wrap: wrap; }
.tag {
  padding: 1px 7px; border-radius: 3px; font-size: 10.5px;
  color: #9ec9ee; background: rgba(56, 189, 248, .12);
  border: 1px solid rgba(56, 189, 248, .22);
}
.tag.cert { color: #c4b5fd; background: rgba(129, 140, 248, .12); border-color: rgba(129, 140, 248, .3); }
.foot { display: flex; align-items: center; justify-content: space-between; margin-top: 7px; }
.years { font-size: 11px; color: #7dd3fc; }
.status { display: inline-flex; align-items: center; gap: 5px; font-size: 11px; color: var(--scr-dim); }
.status i { width: 7px; height: 7px; border-radius: 50%; background: #64748b; }
.card[data-status='AVAILABLE'] .status { color: #34d399; }
.card[data-status='AVAILABLE'] .status i { background: #34d399; box-shadow: 0 0 6px #34d399; }
.card[data-status='ON_TASK'] .status { color: #fb923c; }
.card[data-status='ON_TASK'] .status i { background: #fb923c; box-shadow: 0 0 6px #fb923c; }
.card[data-status='LEAVE'] .status i { background: #fbbf24; }

/* 片区/机构 */
.scr-bars.big .row { padding: 7px 0; }
.orgs { padding: 4px 12px 10px; display: flex; flex-direction: column; gap: 8px; }
.org {
  display: flex; align-items: center; justify-content: space-between; gap: 8px;
  padding: 9px 12px; border-radius: 6px;
  background: rgba(129, 140, 248, .08);
  border: 1px solid rgba(129, 140, 248, .2);
}
.org-name { font-size: 12px; color: #cfe6ff; }
.org-val { font-size: 13px; font-weight: 700; color: #a5b4fc; font-variant-numeric: tabular-nums; }

@media (max-width: 1500px) { .cols { grid-template-columns: 300px 1fr 320px; } }
@media (max-width: 1200px) {
  .cols { grid-template-columns: 1fr 1fr; grid-auto-rows: minmax(0, 1fr); }
  .col.center { grid-column: 1 / -1; }
}
</style>
