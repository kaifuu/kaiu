<template>
  <div class="screen">
    <!-- ===== 顶栏 ===== -->
    <header class="scr-head">
      <div class="head-side">
        <span class="weather"><i>☀</i>晴 24℃ · 东南风 2 级</span>
      </div>
      <h1 class="head-title">应急巡检服务大屏</h1>
      <div class="head-side right">
        <span class="live"><i></i>数据实时刷新</span>
        <span class="clock">{{ clock }}</span>
      </div>
    </header>

    <div class="scr-body" v-loading="loading" element-loading-background="rgba(4,16,38,.7)">
      <!-- ==================== 左列 ==================== -->
      <section class="col">
        <div class="panel">
          <div class="panel-hd">巡检概览</div>
          <div class="overview">
            <div class="ov-item" v-for="o in overviewItems" :key="o.label">
              <span class="ov-label">{{ o.label }}</span>
              <b class="ov-value" :class="o.tone">{{ o.value }}</b>
            </div>
          </div>
        </div>

        <div class="panel grow">
          <div class="panel-hd">计划任务清单</div>
          <div class="scroll">
            <div v-for="t in tasks" :key="t.id" class="task-row">
              <div class="tr-main">
                <span class="tr-name">{{ t.name }}</span>
                <span class="tr-sub">{{ t.pointName || '-' }} · {{ t.executor || '未指派' }}</span>
              </div>
              <div class="tr-right">
                <span class="tr-time">{{ (t.planStart || '').slice(5, 16) }}</span>
                <el-tag size="small" :type="dictTag(TASK_STATUS, t.status)" effect="dark">
                  {{ dictLabel(TASK_STATUS, t.status) }}
                </el-tag>
              </div>
            </div>
            <div v-if="!tasks.length" class="empty">暂无任务</div>
          </div>
        </div>

        <div class="panel alert-panel">
          <div class="panel-hd">
            飞行安全预警
            <span class="hd-note">{{ safeAlertPending }} 条待处理</span>
          </div>
          <div class="scroll">
            <div v-for="a in recentSafeAlerts" :key="a.id" class="alert-row">
              <div class="al-main">
                <span class="al-title">
                  <i class="al-dot" :class="a.level === 'ERROR' ? 'err' : 'warn'" />
                  {{ a.title }}
                </span>
                <span class="al-sub">{{ a.deviceSn }} · {{ (a.occurredAt || '').slice(5, 16).replace('T', ' ') }}</span>
              </div>
              <el-tag size="small" :type="dictTag(SAFE_ALERT_STATUS, a.status)" effect="dark">
                {{ dictLabel(SAFE_ALERT_STATUS, a.status) }}
              </el-tag>
            </div>
            <div v-if="!recentSafeAlerts.length" class="empty">暂无飞行安全预警</div>
          </div>
        </div>

        <div class="panel">
          <div class="panel-hd">工单部门排行</div>
          <div class="rank">
            <div v-for="(d, i) in orderByDept" :key="d.dept" class="rank-row">
              <span class="rk-no" :class="'n' + (i + 1)">{{ i + 1 }}</span>
              <span class="rk-name">{{ d.dept }}</span>
              <span class="rk-bar"><i :style="{ width: barWidth(d.count, deptMax) }"></i></span>
              <span class="rk-val">{{ d.count }}</span>
            </div>
            <div v-if="!orderByDept.length" class="empty">暂无数据</div>
          </div>
        </div>
      </section>

      <!-- ==================== 中列 ==================== -->
      <section class="col center">
        <div class="panel grow map-panel">
          <div class="panel-hd">
            区域态势
            <span class="hd-note">点位与在办问题分布</span>
          </div>
          <svg class="map" viewBox="0 0 1000 620" preserveAspectRatio="xMidYMid meet">
            <defs>
              <radialGradient id="mapGlow" cx=".5" cy=".5" r=".5">
                <stop offset="0" stop-color="rgba(56,189,248,.20)"/>
                <stop offset="1" stop-color="rgba(56,189,248,0)"/>
              </radialGradient>
              <pattern id="mapGrid" width="50" height="50" patternUnits="userSpaceOnUse">
                <path d="M50 0 H0 V50" fill="none" stroke="rgba(56,189,248,.10)" stroke-width="1"/>
              </pattern>
            </defs>

            <rect width="1000" height="620" fill="url(#mapGrid)"/>
            <ellipse cx="500" cy="310" rx="440" ry="270" fill="url(#mapGlow)"/>

            <!-- 辖区轮廓:按标记包围盒生成,保证一定包住所有点位
                 (固定图形会与按真实经纬度投影的标记错位) -->
            <template v-if="hullPath">
              <path :d="hullPath" fill="rgba(37,99,235,.10)" stroke="rgba(96,165,250,.55)" stroke-width="2"/>
              <path :d="hullPath" fill="none" stroke="rgba(96,165,250,.20)" stroke-width="10"
                    stroke-linejoin="round" transform="translate(0,0) scale(1)" opacity=".25"/>
            </template>

            <!-- 标记 -->
            <g v-for="(p, i) in mapMarkers" :key="i">
              <circle v-if="p.kind === 'POINT'" :cx="p.x" :cy="p.y" r="9"
                      fill="rgba(56,189,248,.20)" stroke="#38bdf8" stroke-width="2"/>
              <circle v-if="p.kind === 'POINT'" :cx="p.x" :cy="p.y" r="3.4" fill="#7dd3fc"/>
              <template v-else>
                <circle :cx="p.x" :cy="p.y" r="7" fill="#fb923c" opacity=".9"/>
                <circle :cx="p.x" :cy="p.y" r="7" fill="none" stroke="#fb923c" stroke-width="1.6">
                  <animate attributeName="r" values="7;22" dur="2.4s" repeatCount="indefinite"/>
                  <animate attributeName="opacity" values=".9;0" dur="2.4s" repeatCount="indefinite"/>
                </circle>
              </template>
              <text :x="p.x" :y="p.y - 14" class="map-label" text-anchor="middle">{{ shortName(p.name) }}</text>
            </g>
          </svg>
        </div>

        <!-- KPI 环 -->
        <div class="kpi-row">
          <div v-for="k in kpiRings" :key="k.label" class="kpi">
            <svg viewBox="0 0 120 120" class="kpi-ring">
              <circle cx="60" cy="60" r="48" fill="none" stroke="rgba(56,189,248,.14)" stroke-width="9"/>
              <circle cx="60" cy="60" r="48" fill="none" :stroke="k.color" stroke-width="9"
                      stroke-linecap="round" :stroke-dasharray="k.dash" transform="rotate(-90 60 60)"/>
              <text x="60" y="62" class="kpi-num" text-anchor="middle">{{ k.value }}</text>
              <text x="60" y="80" class="kpi-sub" text-anchor="middle">{{ k.sub }}</text>
            </svg>
            <span class="kpi-label">{{ k.label }}</span>
          </div>
        </div>
      </section>

      <!-- ==================== 右列 ==================== -->
      <section class="col">
        <div class="panel">
          <div class="panel-hd">AI 识别类型</div>
          <div class="types">
            <div v-for="t in issueByType" :key="t.type" class="type-row">
              <span class="ty-name">{{ dictLabel(ISSUE_TYPE, t.type) }}</span>
              <span class="ty-bar"><i :style="{ width: barWidth(t.count, typeMax) }"></i></span>
              <span class="ty-val">{{ t.count }}</span>
            </div>
            <div v-if="!issueByType.length" class="empty">暂无识别记录</div>
          </div>
        </div>

        <div class="panel grow">
          <div class="panel-hd">
            近期问题
            <span class="hd-note">{{ recentIssues.length }} 条</span>
          </div>
          <div class="scroll">
            <div v-for="i in recentIssues" :key="i.id" class="issue-row">
              <div class="is-main">
                <span class="is-title">{{ i.title }}</span>
                <span class="is-sub">{{ i.pointName || i.address }} · {{ (i.foundAt || '').slice(5, 16) }}</span>
              </div>
              <el-tag size="small" :type="dictTag(ISSUE_STATUS, i.status)" effect="dark">
                {{ dictLabel(ISSUE_STATUS, i.status) }}
              </el-tag>
            </div>
            <div v-if="!recentIssues.length" class="empty">暂无问题</div>
          </div>
        </div>

        <div class="panel">
          <div class="panel-hd">
            实时画面
            <span class="hd-note">{{ onlineVideos.length }} 路在线</span>
          </div>
          <div class="video-box">
            <template v-if="onlineVideos.length">
              <div class="video-frame">
                <div class="vf-placeholder">
                  <span class="vf-name">{{ onlineVideos[0].name }}</span>
                  <span class="vf-url">{{ onlineVideos[0].streamUrl }}</span>
                  <span class="vf-tip">接入 RTMP / GB28181 流媒体服务后即可播放</span>
                </div>
                <span class="vf-badge">LIVE</span>
              </div>
              <div class="video-list">
                <span v-for="v in onlineVideos" :key="v.id" class="vl-item">{{ v.name }}</span>
              </div>
            </template>
            <div v-else class="empty">暂无在线通道</div>
          </div>
        </div>
      </section>
    </div>

    <div class="scr-foot">
      <span>数据每 30 秒自动刷新 · 最近更新 {{ updatedAt }}</span>
      <span class="foot-right" @click="$router.push('/dashboard')">进入管理后台 →</span>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted } from 'vue'
import http from '../api'
import { TASK_STATUS, ISSUE_TYPE, ISSUE_STATUS, SAFE_ALERT_STATUS, dictLabel, dictTag } from '../utils/dict'

const loading = ref(false)
const data = ref({})
const tasks = ref([])
const clock = ref('')
const updatedAt = ref('-')
let timer = null
let clockTimer = null

const REFRESH_MS = 30000

const kpi = computed(() => data.value.kpi || {})
const device = computed(() => data.value.device || {})
const issueByType = computed(() => data.value.issueByType || [])
const orderByDept = computed(() => data.value.orderByDept || [])
const recentIssues = computed(() => data.value.recentIssues || [])
const recentSafeAlerts = computed(() => data.value.recentSafeAlerts || [])
const safeAlertPending = computed(() => data.value.safeAlertPending ?? 0)
const onlineVideos = computed(() => data.value.onlineVideos || [])
const mapPoints = computed(() => data.value.mapPoints || [])

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

const deptMax = computed(() => Math.max(1, ...orderByDept.value.map((d) => d.count)))
const typeMax = computed(() => Math.max(1, ...issueByType.value.map((t) => t.count)))

/** 五个环形指标:周长 2πr ≈ 301.6 */
const RING = 2 * Math.PI * 48
const kpiRings = computed(() => {
  const k = kpi.value
  const pct = (a, b) => (b > 0 ? Math.min(1, a / b) : 0)
  return [
    { label: '巡检点位', value: k.pointTotal ?? 0, sub: '个', color: '#38bdf8',
      dash: `${RING * 0.82} ${RING}` },
    { label: '在线机场', value: k.dockOnline ?? 0, sub: `共 ${k.dockTotal ?? 0} 台`, color: '#22d3ee',
      dash: `${RING * pct(k.dockOnline, k.dockTotal)} ${RING}` },
    { label: '巡检任务', value: k.taskTotal ?? 0, sub: `完成 ${k.taskDone ?? 0}`, color: '#818cf8',
      dash: `${RING * pct(k.taskDone, k.taskTotal)} ${RING}` },
    { label: '已办结工单', value: k.orderDone ?? 0, sub: `共 ${k.orderTotal ?? 0} 单`, color: '#34d399',
      dash: `${RING * pct(k.orderDone, k.orderTotal)} ${RING}` },
    { label: '可调度飞手', value: k.pilotAvailable ?? 0, sub: '人', color: '#fbbf24',
      dash: `${RING * 0.7} ${RING}` }
  ]
})

/**
 * 经纬度 → 画布坐标。
 * 按当前数据的经纬度范围自适应铺满画布(留 8% 边距),这样换城市也不用改代码;
 * 纬度向北增大而 y 轴向下,故对纬度取反。
 */
const mapMarkers = computed(() => {
  const pts = mapPoints.value
  if (!pts.length) return []
  const lngs = pts.map((p) => Number(p.longitude))
  const lats = pts.map((p) => Number(p.latitude))
  const minLng = Math.min(...lngs), maxLng = Math.max(...lngs)
  const minLat = Math.min(...lats), maxLat = Math.max(...lats)
  const padLng = (maxLng - minLng) * 0.08 || 0.01
  const padLat = (maxLat - minLat) * 0.08 || 0.01
  const W = 1000, H = 620, PAD = 70
  return pts.map((p) => {
    const x = PAD + ((Number(p.longitude) - (minLng - padLng)) / (maxLng - minLng + padLng * 2)) * (W - PAD * 2)
    const y = PAD + (((maxLat + padLat) - Number(p.latitude)) / (maxLat - minLat + padLat * 2)) * (H - PAD * 2)
    return { ...p, x, y }
  })
})

/**
 * 辖区轮廓:取所有标记的包围盒外扩一圈,画成圆角矩形。
 * 用固定图形会与真实经纬度投影出来的标记错位,按包围盒算则换城市也不用改。
 */
const hullPath = computed(() => {
  const ms = mapMarkers.value
  if (!ms.length) return null
  const xs = ms.map((m) => m.x), ys = ms.map((m) => m.y)
  const pad = 74
  const x0 = Math.max(20, Math.min(...xs) - pad)
  const x1 = Math.min(980, Math.max(...xs) + pad)
  const y0 = Math.max(20, Math.min(...ys) - pad)
  const y1 = Math.min(600, Math.max(...ys) + pad)
  const r = Math.min(90, (x1 - x0) / 4, (y1 - y0) / 4)
  return `M${x0 + r} ${y0} H${x1 - r} Q${x1} ${y0}, ${x1} ${y0 + r}`
    + ` V${y1 - r} Q${x1} ${y1}, ${x1 - r} ${y1} H${x0 + r}`
    + ` Q${x0} ${y1}, ${x0} ${y1 - r} V${y0 + r} Q${x0} ${y0}, ${x0 + r} ${y0} Z`
})

function shortName(name) {
  if (!name) return ''
  return name.length > 7 ? name.slice(0, 7) + '…' : name
}

function barWidth(v, max) {
  return Math.max(6, Math.round((v / max) * 100)) + '%'
}

function tick() {
  const d = new Date()
  const p = (n) => String(n).padStart(2, '0')
  clock.value = `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())} ${p(d.getHours())}:${p(d.getMinutes())}:${p(d.getSeconds())}`
}

async function load() {
  loading.value = true
  try {
    const [overview, recent] = await Promise.all([
      http.get('/screen/overview'),
      // 取最近任务而非按 PENDING 过滤:逾期的任务同样是"待办",按状态筛会漏
      http.get('/tasks/recent', { params: { limit: 8 } })
    ])
    data.value = overview || {}
    tasks.value = recent || []
    const d = new Date()
    updatedAt.value = `${String(d.getHours()).padStart(2, '0')}:${String(d.getMinutes()).padStart(2, '0')}:${String(d.getSeconds()).padStart(2, '0')}`
  } finally { loading.value = false }
}

onMounted(() => {
  tick()
  load()
  clockTimer = setInterval(tick, 1000)
  timer = setInterval(load, REFRESH_MS)
})
onUnmounted(() => {
  clearInterval(clockTimer)
  clearInterval(timer)
})
</script>

<style scoped>
/* ===== 大屏:深色指挥中心配色,自成一套主题(不继承后台的亮色变量) ===== */
.screen {
  --scr-bg: #04102a;
  --scr-panel: rgba(9, 30, 62, .72);
  --scr-border: rgba(56, 189, 248, .22);
  --scr-text: #cfe6ff;
  --scr-dim: #7fa8d6;
  --scr-cyan: #38bdf8;

  height: 100vh; overflow: hidden;
  display: flex; flex-direction: column;
  color: var(--scr-text);
  background:
    radial-gradient(1200px 700px at 50% -10%, rgba(37, 99, 235, .38) 0%, transparent 62%),
    radial-gradient(900px 600px at 4% 100%, rgba(34, 211, 238, .14) 0%, transparent 58%),
    radial-gradient(900px 600px at 96% 100%, rgba(200, 16, 46, .12) 0%, transparent 58%),
    linear-gradient(170deg, #04102a 0%, #061a38 46%, #072248 100%);
}

/* ===== 顶栏 ===== */
.scr-head {
  position: relative; flex-shrink: 0; height: 64px;
  display: flex; align-items: center; justify-content: space-between;
  padding: 0 26px;
  background: linear-gradient(180deg, rgba(10, 40, 84, .9), rgba(6, 24, 52, .5));
  border-bottom: 1px solid var(--scr-border);
}
.scr-head::after {
  content: ''; position: absolute; left: 0; right: 0; bottom: -1px; height: 1px;
  background: linear-gradient(90deg, transparent, var(--scr-cyan) 50%, transparent);
  opacity: .7;
}
.head-title {
  font-size: 26px; font-weight: 800; letter-spacing: 8px; text-indent: 8px;
  background: linear-gradient(180deg, #ffffff 30%, #7dd3fc);
  -webkit-background-clip: text; background-clip: text; -webkit-text-fill-color: transparent;
  text-shadow: 0 0 30px rgba(56, 189, 248, .5);
}
.head-side { width: 300px; display: flex; align-items: center; gap: 12px; font-size: 13px; color: var(--scr-dim); }
.head-side.right { justify-content: flex-end; }
.weather i { color: #fbbf24; font-style: normal; margin-right: 5px; }
.live { display: inline-flex; align-items: center; gap: 6px; }
.live i { width: 7px; height: 7px; border-radius: 50%; background: #34d399; box-shadow: 0 0 8px #34d399; animation: blink 2s infinite; }
@keyframes blink { 0%, 100% { opacity: 1; } 50% { opacity: .3; } }
.clock { color: #7dd3fc; font-variant-numeric: tabular-nums; }

/* ===== 主体三列 ===== */
.scr-body {
  flex: 1; min-height: 0;
  display: grid; grid-template-columns: 340px 1fr 360px; gap: 12px;
  padding: 12px;
}
.col { display: flex; flex-direction: column; gap: 12px; min-height: 0; }
.col.center { gap: 10px; }

.panel {
  position: relative;
  background: var(--scr-panel);
  border: 1px solid var(--scr-border);
  border-radius: 8px;
  backdrop-filter: blur(8px);
  padding-bottom: 8px;
  display: flex; flex-direction: column;
  min-height: 0;
}
.panel.grow { flex: 1; }
/* 面板四角刻度,大屏常见收边 */
.panel::before, .panel::after {
  content: ''; position: absolute; width: 10px; height: 10px;
  border: 1px solid rgba(56, 189, 248, .6);
}
.panel::before { top: -1px; left: -1px; border-right: none; border-bottom: none; }
.panel::after { bottom: -1px; right: -1px; border-left: none; border-top: none; }

.panel-hd {
  display: flex; align-items: center; gap: 8px;
  padding: 10px 14px 8px;
  font-size: 14px; font-weight: 700; letter-spacing: 1.5px; color: #e0f2fe;
}
.panel-hd::before {
  content: ''; width: 3px; height: 13px; border-radius: 2px;
  background: linear-gradient(180deg, var(--scr-cyan), #2563eb);
}
.hd-note { margin-left: auto; font-size: 11.5px; font-weight: 400; color: var(--scr-dim); letter-spacing: 0; }

.scroll { flex: 1; overflow-y: auto; padding: 0 10px 4px; min-height: 0; }
.scroll::-webkit-scrollbar { width: 4px; }
.scroll::-webkit-scrollbar-thumb { background: rgba(56, 189, 248, .35); border-radius: 2px; }
.empty { padding: 26px 0; text-align: center; font-size: 12.5px; color: var(--scr-dim); }

/* ===== 左列 ===== */
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

.task-row {
  display: flex; align-items: center; justify-content: space-between; gap: 8px;
  padding: 7px 8px; margin-bottom: 5px; border-radius: 6px;
  background: rgba(56, 189, 248, .05);
  border-left: 2px solid rgba(56, 189, 248, .5);
}
.tr-main { min-width: 0; }
.tr-name { display: block; font-size: 12.5px; color: #e0f2fe; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.tr-sub { display: block; font-size: 11px; color: var(--scr-dim); margin-top: 2px; }
.tr-right { display: flex; flex-direction: column; align-items: flex-end; gap: 3px; flex-shrink: 0; }
.tr-time { font-size: 11px; color: #7dd3fc; font-variant-numeric: tabular-nums; }

.rank { padding: 0 12px 4px; }
.rank-row { display: flex; align-items: center; gap: 8px; padding: 5px 0; }
.rk-no {
  width: 17px; height: 17px; border-radius: 3px; flex-shrink: 0;
  display: flex; align-items: center; justify-content: center;
  font-size: 11px; font-weight: 700; color: #04102a; background: #64748b;
}
.rk-no.n1 { background: #fbbf24; }
.rk-no.n2 { background: #cbd5e1; }
.rk-no.n3 { background: #fb923c; }
.rk-name { width: 96px; font-size: 12px; color: var(--scr-text); white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.rk-bar { flex: 1; height: 6px; border-radius: 3px; background: rgba(56, 189, 248, .12); overflow: hidden; }
.rk-bar i { display: block; height: 100%; border-radius: 3px; background: linear-gradient(90deg, #2563eb, #38bdf8); transition: width .6s; }
.rk-val { width: 28px; text-align: right; font-size: 12px; font-weight: 700; color: #7dd3fc; font-variant-numeric: tabular-nums; }

/* ===== 中列 ===== */
.map-panel { padding-bottom: 0; }
.map { flex: 1; width: 100%; min-height: 0; }
.map-label { font-size: 11px; fill: #9ec9ee; }

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

/* ===== 右列 ===== */
.types { padding: 0 12px 4px; }
.type-row { display: flex; align-items: center; gap: 8px; padding: 4px 0; }
.ty-name { width: 78px; font-size: 12px; color: var(--scr-text); }
.ty-bar { flex: 1; height: 8px; border-radius: 4px; background: rgba(56, 189, 248, .12); overflow: hidden; }
.ty-bar i { display: block; height: 100%; border-radius: 4px; background: linear-gradient(90deg, #f97316, #fbbf24); transition: width .6s; }
.ty-val { width: 26px; text-align: right; font-size: 12px; font-weight: 700; color: #fbbf24; font-variant-numeric: tabular-nums; }

.issue-row {
  display: flex; align-items: center; justify-content: space-between; gap: 8px;
  padding: 7px 8px; margin-bottom: 5px; border-radius: 6px;
  background: rgba(56, 189, 248, .05);
  border-left: 2px solid rgba(249, 115, 22, .7);
}
.is-main { min-width: 0; }
.is-title { display: block; font-size: 12.5px; color: #e0f2fe; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.is-sub { display: block; font-size: 11px; color: var(--scr-dim); margin-top: 2px; }

/* 飞行安全预警:固定高度面板,超量滚动 */
.alert-panel { flex: 0 0 auto; max-height: 200px; display: flex; flex-direction: column; }
.alert-row {
  display: flex; align-items: center; justify-content: space-between; gap: 8px;
  padding: 7px 8px; margin-bottom: 5px; border-radius: 6px;
  background: rgba(56, 189, 248, .05);
  border-left: 2px solid rgba(248, 113, 113, .7);
}
.al-main { min-width: 0; }
.al-title {
  display: flex; align-items: center; gap: 6px;
  font-size: 12.5px; color: #e0f2fe; white-space: nowrap; overflow: hidden; text-overflow: ellipsis;
}
.al-sub { display: block; font-size: 11px; color: var(--scr-dim); margin-top: 2px; }
.al-dot { flex-shrink: 0; width: 7px; height: 7px; border-radius: 50%; }
.al-dot.err { background: #f87171; box-shadow: 0 0 6px #f87171; }
.al-dot.warn { background: #fbbf24; box-shadow: 0 0 6px #fbbf24; }

.video-box { padding: 0 12px 4px; }
.video-frame {
  position: relative; width: 100%; aspect-ratio: 16 / 9;
  border-radius: 6px; overflow: hidden;
  background: radial-gradient(circle at 50% 40%, #0d2b52, #020a18 70%);
  border: 1px solid rgba(56, 189, 248, .25);
}
.vf-placeholder {
  position: absolute; inset: 0;
  display: flex; flex-direction: column; align-items: center; justify-content: center; gap: 5px;
  padding: 12px; text-align: center;
}
.vf-name { font-size: 13px; font-weight: 700; color: #e0f2fe; }
.vf-url { font-size: 10.5px; color: #7dd3fc; font-family: monospace; word-break: break-all; }
.vf-tip { font-size: 10.5px; color: var(--scr-dim); }
.vf-badge {
  position: absolute; top: 7px; left: 8px;
  padding: 1px 7px; border-radius: 3px;
  font-size: 10px; font-weight: 700; letter-spacing: 1px;
  color: #04102a; background: #f04438;
}
.video-list { display: flex; flex-wrap: wrap; gap: 5px; margin-top: 7px; }
.vl-item {
  padding: 2px 8px; border-radius: 3px; font-size: 11px;
  color: #9ec9ee; background: rgba(56, 189, 248, .1);
  border: 1px solid rgba(56, 189, 248, .2);
}

/* ===== 底栏 ===== */
.scr-foot {
  flex-shrink: 0; height: 30px;
  display: flex; align-items: center; justify-content: space-between;
  padding: 0 26px; font-size: 11.5px; color: var(--scr-dim);
  border-top: 1px solid rgba(56, 189, 248, .14);
}
.foot-right { color: #7dd3fc; cursor: pointer; }
.foot-right:hover { text-decoration: underline; }

/* 窄屏:三列压成两列,再窄则纵向堆叠 */
@media (max-width: 1500px) {
  .scr-body { grid-template-columns: 300px 1fr 320px; }
  .head-title { font-size: 22px; letter-spacing: 5px; }
}
@media (max-width: 1200px) {
  .scr-body { grid-template-columns: 1fr 1fr; grid-auto-rows: minmax(0, 1fr); }
  .col.center { grid-column: 1 / -1; }
}
</style>
