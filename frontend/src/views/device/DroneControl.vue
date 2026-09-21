<template>
  <div class="page" v-loading="loading">
    <div class="page-header">
      <div class="head-left">
        <el-button link @click="$router.push('/drones')">
          <el-icon><ArrowLeft /></el-icon>返回
        </el-button>
        <span class="page-title">{{ drone?.name || '无人机控制' }}</span>
        <el-tag size="small" :type="dictTag(DEVICE_STATUS, drone?.status)" effect="plain">
          {{ dictLabel(DEVICE_STATUS, drone?.status) }}
        </el-tag>
        <span class="sn">{{ drone?.deviceSn }}</span>
        <span class="model" v-if="drone?.deviceModel">{{ drone?.deviceModel }}</span>
        <span class="gateway" v-if="drone?.gatewayName">挂载于 {{ drone.gatewayName }}</span>
      </div>
      <div class="actions">
        <span class="poll-tip"><i :class="{ on: polling }"></i>{{ polling ? '遥测刷新中(3s)' : '已暂停' }}</span>
        <el-button size="small" @click="togglePoll">{{ polling ? '暂停刷新' : '继续刷新' }}</el-button>
        <el-button size="small" @click="openReplay">航迹回放</el-button>
        <el-button size="small" type="primary" @click="loadAll">刷新</el-button>
      </div>
    </div>

    <div class="grid">
      <!-- 姿态与飞行 -->
      <div class="panel flight-panel">
        <div class="panel-title">飞行姿态</div>
        <div class="flight-body">
          <!-- 罗盘:指针指向机头朝向 -->
          <svg class="compass" viewBox="0 0 200 200" fill="none">
            <circle cx="100" cy="100" r="86" fill="#f7f9fc" stroke="#e2e9f4" stroke-width="2"/>
            <circle cx="100" cy="100" r="68" fill="none" stroke="#eef2f8" stroke-width="1"/>
            <circle cx="100" cy="100" r="46" fill="none" stroke="#eef2f8" stroke-width="1"/>
            <g fill="#98a2b3" font-size="11" text-anchor="middle" font-family="sans-serif">
              <text x="100" y="26">N</text><text x="180" y="104">E</text>
              <text x="100" y="184">S</text><text x="20" y="104">W</text>
            </g>
            <g stroke="#d4dde9" stroke-width="1">
              <path d="M100 16 V28"/><path d="M100 172 V184"/>
              <path d="M16 100 H28"/><path d="M172 100 H184"/>
            </g>
            <!-- 机头指针 -->
            <g :style="{ transform: `rotate(${attitude}deg)`, transformOrigin: '100px 100px', transition: 'transform .6s ease' }">
              <path d="M100 34 L112 100 L100 88 L88 100 Z" fill="#1877e6"/>
              <path d="M100 166 L108 100 L100 108 L92 100 Z" fill="#c3d6ee"/>
            </g>
            <circle cx="100" cy="100" r="6" fill="#ffffff" stroke="#1877e6" stroke-width="2"/>
          </svg>

          <div class="flight-nums">
            <div class="big">
              <span class="b-label">相对高度</span>
              <b class="b-value">{{ t?.height ?? '-' }}<i>m</i></b>
            </div>
            <div class="big">
              <span class="b-label">海拔高度</span>
              <b class="b-value">{{ t?.elevation ?? '-' }}<i>m</i></b>
            </div>
            <div class="row">
              <span>水平速度</span><b>{{ t?.horizontalSpeed ?? '-' }} m/s</b>
            </div>
            <div class="row">
              <span>垂直速度</span><b>{{ t?.verticalSpeed ?? '-' }} m/s</b>
            </div>
            <div class="row">
              <span>机头朝向</span><b>{{ attitude }}°</b>
            </div>
            <div class="row">
              <span>起落架</span>
              <b>{{ t?.gear === 1 ? '放下' : t?.gear === 0 ? '收起' : '-' }}</b>
            </div>
          </div>
        </div>
      </div>

      <!-- 电池与定位 -->
      <div class="panel">
        <div class="panel-title">电池与定位</div>
        <div class="batt-wrap">
          <svg class="batt-ring" viewBox="0 0 140 140" fill="none">
            <circle cx="70" cy="70" r="56" fill="none" stroke="#eef2f8" stroke-width="12"/>
            <circle cx="70" cy="70" r="56" fill="none" :stroke="batteryColor" stroke-width="12"
                    stroke-linecap="round" :stroke-dasharray="batteryDash" transform="rotate(-90 70 70)"
                    style="transition: stroke-dasharray .6s ease"/>
            <text x="70" y="68" text-anchor="middle" font-size="26" font-weight="700" fill="#101828"
                  font-family="sans-serif">{{ t?.batteryPercent ?? '-' }}</text>
            <text x="70" y="88" text-anchor="middle" font-size="11" fill="#98a2b3" font-family="sans-serif">电量 %</text>
          </svg>

          <div class="batt-info">
            <div class="row"><span>工作状态</span>
              <el-tag size="small" :type="modeTag('DRONE', t?.modeCode)" effect="light">
                {{ t?.modeLabel || '-' }}
              </el-tag>
            </div>
            <div class="row"><span>电池电压</span><b>{{ battery?.voltage ? (battery.voltage / 1000).toFixed(2) + ' V' : '-' }}</b></div>
            <div class="row"><span>经度</span><b>{{ t?.longitude ?? '-' }}</b></div>
            <div class="row"><span>纬度</span><b>{{ t?.latitude ?? '-' }}</b></div>
            <div class="row"><span>卫星数</span><b>{{ position?.gps_number ?? '-' }}</b></div>
            <div class="row"><span>定位质量</span>
              <el-tag size="small" :type="position?.is_fixed === 1 ? 'success' : 'warning'" effect="plain">
                {{ position?.is_fixed === 1 ? '已定位' : '未定位' }}
                <template v-if="position?.quality !== undefined">· {{ position.quality }}</template>
              </el-tag>
            </div>
            <div class="row"><span>存储</span>
              <b>{{ storage ? usedStorage : '-' }}</b>
            </div>
          </div>
        </div>
        <p class="update-time" v-if="t?.updateTime">遥测更新于 {{ t.updateTime }}</p>
      </div>
    </div>

    <!-- 指令 -->
    <div class="panel">
      <div class="panel-title">指令下发</div>
      <el-alert type="info" :closable="false" style="margin: 0 16px 12px"
                title="上云 API 中飞行器挂载在机场下,指令经所属机场转发;离线或机场不在线时无法下发。" />
      <div class="svc-wrap">
        <div v-for="grp in serviceGroups" :key="grp.name" class="svc-group">
          <div class="svc-title">{{ grp.name }}</div>
          <div class="svc-btns">
            <el-popconfirm v-for="s in grp.items" :key="s.method"
                           :title="s.danger ? `确认执行「${s.label}」?该操作有副作用` : `确认下发「${s.label}」?`"
                           width="250" @confirm="send(s)">
              <template #reference>
                <el-button size="small" :type="s.danger ? 'danger' : 'default'" plain
                           :disabled="drone?.status !== 'ONLINE'" :loading="sending === s.method">
                  {{ s.label }}
                </el-button>
              </template>
            </el-popconfirm>
          </div>
        </div>
      </div>
    </div>

    <div class="grid2">
      <div class="panel">
        <div class="panel-title">指令记录</div>
        <el-table :data="commands" size="small" max-height="300">
          <el-table-column prop="method" label="指令" width="150" />
          <el-table-column prop="status" label="结果" width="90">
            <template #default="{ row }">
              <el-tag size="small" :type="dictTag(COMMAND_STATUS, row.status)" effect="plain">
                {{ dictLabel(COMMAND_STATUS, row.status) }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="sentAt" label="下发时间" min-width="150" />
          <el-table-column prop="replyAt" label="回复时间" min-width="150">
            <template #default="{ row }">{{ row.replyAt || '-' }}</template>
          </el-table-column>
        </el-table>
      </div>
      <div class="panel">
        <div class="panel-title">设备事件</div>
        <el-table :data="events" size="small" max-height="300">
          <el-table-column prop="eventType" label="类型" width="100">
            <template #default="{ row }">
              <el-tag size="small" :type="dictTag(DEVICE_EVENT_TYPE, row.eventType)" effect="light">
                {{ dictLabel(DEVICE_EVENT_TYPE, row.eventType) }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="message" label="说明" min-width="200" show-overflow-tooltip />
          <el-table-column prop="createTime" label="时间" width="155" />
        </el-table>
      </div>
    </div>

    <!-- 航迹回放:近 N 分钟轨迹 + 时间轴播放 -->
    <el-drawer v-model="replay.visible" title="航迹回放" direction="rtl" size="720px" @closed="stopPlay">
      <div class="replay-wrap" v-loading="replay.loading">
        <div class="replay-toolbar">
          <el-select v-model="replay.minutes" size="small" style="width: 110px" @change="loadTrack">
            <el-option :value="30" label="近 30 分钟" />
            <el-option :value="60" label="近 1 小时" />
            <el-option :value="120" label="近 2 小时" />
            <el-option :value="240" label="近 4 小时" />
          </el-select>
          <el-button size="small" @click="loadTrack">重新加载</el-button>
          <div class="spacer" />
          <el-button size="small" circle type="primary" @click="togglePlay">
            <el-icon><VideoPlay v-if="!replay.playing" /><VideoPause v-else /></el-icon>
          </el-button>
          <el-select v-model="replay.speed" size="small" style="width: 84px">
            <el-option :value="1" label="1x" />
            <el-option :value="2" label="2x" />
            <el-option :value="4" label="4x" />
            <el-option :value="8" label="8x" />
          </el-select>
        </div>

        <div class="replay-legend">
          <span><i class="lg-all" />全轨迹</span>
          <span><i class="lg-flown" />已飞段</span>
          <span><i class="lg-start" />起点</span>
          <span><i class="lg-end" />终点</span>
          <span><i class="lg-cur" />当前帧</span>
        </div>

        <svg class="replay-map" viewBox="0 0 640 400" preserveAspectRatio="xMidYMid meet">
          <defs>
            <pattern id="rgrid" width="40" height="40" patternUnits="userSpaceOnUse">
              <path d="M 40 0 L 0 0 0 40" fill="none" stroke="#e4efff" stroke-width="1" />
            </pattern>
          </defs>
          <rect width="640" height="400" fill="url(#rgrid)" />
          <!-- 全轨迹:灰虚线 -->
          <polyline :points="replayAllAttr" fill="none" stroke="#b9c7da" stroke-width="2"
                    stroke-dasharray="6 5" />
          <!-- 已飞段:亮蓝 -->
          <polyline :points="replayFlownAttr" fill="none" stroke="#155eef" stroke-width="3"
                    stroke-linejoin="round" stroke-linecap="round" />
          <!-- 起点 / 终点 -->
          <g v-if="replay.points.length">
            <circle :cx="rp(replay.points[0]).x" :cy="rp(replay.points[0]).y" r="6" fill="#12b76a"
                    stroke="#fff" stroke-width="2" />
            <circle :cx="rp(replay.points[replay.points.length - 1]).x"
                    :cy="rp(replay.points[replay.points.length - 1]).y" r="6" fill="#f04438"
                    stroke="#fff" stroke-width="2" />
          </g>
          <!-- 当前帧无人机图标:按 heading 旋转 -->
          <g v-if="replayCur" :transform="`translate(${rp(replayCur).x},${rp(replayCur).y}) rotate(${replayCur.heading || 0})`">
            <path d="M 12 0 L -8 -8 L -4 0 L -8 8 Z" fill="#0ea5e9" stroke="#fff" stroke-width="1.5" />
          </g>
        </svg>

        <el-slider v-model="replay.idx" :min="0" :max="Math.max(replay.points.length - 1, 0)"
                   :disabled="!replay.points.length" @input="onScrub" />
        <div class="replay-stats">
          <span>轨迹点 <b>{{ replay.points.length }}</b></span>
          <span>航程 <b>{{ replayDistanceKm }}</b> km</span>
          <span v-if="replayCur">时刻 <b>{{ replayCur.ts || '-' }}</b></span>
          <span v-if="replayCur">高度 <b>{{ replayCur.height ?? '-' }}</b> m</span>
          <span v-if="replayCur">速度 <b>{{ replayCur.speed ?? '-' }}</b> m/s</span>
          <span v-if="replayCur?.battery !== null && replayCur?.battery !== undefined">
            电量 <b>{{ replayCur.battery }}</b>%
          </span>
        </div>
        <el-empty v-if="!replay.loading && !replay.points.length" description="该时间窗内暂无飞行轨迹,可切换时间范围或先执行一次飞行任务" />
      </div>
    </el-drawer>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted, onUnmounted } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { ArrowLeft, VideoPlay, VideoPause } from '@element-plus/icons-vue'
import http from '../../api'
import { DEVICE_STATUS, COMMAND_STATUS, DEVICE_EVENT_TYPE, dictLabel, dictTag, modeTag } from '../../utils/dict'

const route = useRoute()
const id = route.params.id

const loading = ref(false)
const drone = ref(null)
const t = ref(null)
const services = ref([])
const commands = ref([])
const events = ref([])
const sending = ref('')
const polling = ref(true)
let timer = null

const attitude = computed(() => Math.round(t.value?.attitudeHead ?? 0))
const battery = computed(() => t.value?.battery ?? {})
const position = computed(() => t.value?.positionState ?? {})
const storage = computed(() => t.value?.storage ?? null)

/** 电池环:周长 2πr ≈ 351.86,按电量取比例 */
const RING = 2 * Math.PI * 56
const batteryDash = computed(() => {
  const p = Math.max(0, Math.min(100, t.value?.batteryPercent ?? 0))
  return `${(RING * p) / 100} ${RING}`
})
const batteryColor = computed(() => {
  const p = t.value?.batteryPercent ?? 100
  if (p <= 20) return '#f04438'
  if (p <= 50) return '#f79009'
  return '#12b76a'
})
const usedStorage = computed(() => {
  const s = storage.value
  if (!s || s.total === undefined) return '-'
  const gb = (n) => (n / 1024).toFixed(1)
  return `${gb(s.used)} / ${gb(s.total)} GB`
})

const serviceGroups = computed(() => {
  const map = new Map()
  for (const s of services.value) {
    if (!map.has(s.group)) map.set(s.group, { name: s.group, items: [] })
    map.get(s.group).items.push(s)
  }
  return [...map.values()]
})

onMounted(async () => {
  await loadAll()
  timer = setInterval(() => { if (polling.value) refreshRuntime() }, 3000)
})
onUnmounted(() => clearInterval(timer))

function togglePoll() { polling.value = !polling.value }

async function loadAll() {
  loading.value = true
  try {
    const [d, svcs] = await Promise.all([
      http.get(`/devices/${id}`),
      http.get(`/devices/${id}/services`)
    ])
    drone.value = d
    services.value = svcs || []
    await refreshRuntime()
  } finally { loading.value = false }
}

async function refreshRuntime() {
  const [tele, cmds, evts] = await Promise.all([
    http.get(`/devices/${id}/telemetry`),
    http.get(`/devices/${id}/commands`, { params: { limit: 20 } }),
    http.get(`/devices/${id}/events`, { params: { limit: 20 } })
  ])
  t.value = tele
  commands.value = cmds || []
  events.value = evts || []
  if (drone.value) drone.value.status = tele.status
}

async function send(svc) {
  sending.value = svc.method
  try {
    const cmd = await http.post(`/devices/${id}/commands`, { method: svc.method })
    ElMessage.success(`「${svc.label}」已下发`)
    for (let i = 0; i < 8; i++) {
      await new Promise((r) => setTimeout(r, 800))
      const cmds = await http.get(`/devices/${id}/commands`, { params: { limit: 20 } })
      commands.value = cmds || []
      const cur = commands.value.find((c) => c.tid === cmd.tid)
      if (cur && cur.status !== 'SENT') {
        cur.status === 'OK'
          ? ElMessage.success(`「${svc.label}」执行成功`)
          : ElMessage.warning(`「${svc.label}」${dictLabel(COMMAND_STATUS, cur.status)}`)
        break
      }
    }
    refreshRuntime()
  } finally { sending.value = '' }
}

/* ==================== 航迹回放 ==================== */

const replay = reactive({
  visible: false, loading: false, points: [],
  idx: 0, playing: false, speed: 2, minutes: 120
})
let replayTimer = null

function openReplay() {
  replay.visible = true
  loadTrack()
}

async function loadTrack() {
  stopPlay()
  replay.loading = true
  try {
    const pts = await http.get(`/devices/${id}/track`, {
      params: { minutes: replay.minutes, limit: 3000 }
    })
    replay.points = pts || []
    // 初始定位到末点:打开即见完整蓝色已飞段与降落点统计(idx=0 时已飞段只有 1 个点画不出线)
    replay.idx = Math.max(replay.points.length - 1, 0)
  } finally { replay.loading = false }
}

/** 当前帧样本 */
const replayCur = computed(() => replay.points[replay.idx] || null)

/** 播放步长:倍速越高越快,下限 40ms(参考 10_WRJ Monitor 回放节奏) */
function togglePlay() {
  if (!replay.points.length) return
  replay.playing = !replay.playing
  if (replay.playing) {
    if (replay.idx >= replay.points.length - 1) replay.idx = 0
    replayTimer = setInterval(() => {
      if (replay.idx >= replay.points.length - 1) {
        stopPlay()
        return
      }
      replay.idx++
    }, Math.max(40, 500 / replay.speed))
  } else {
    stopPlay()
  }
}

function stopPlay() {
  replay.playing = false
  if (replayTimer) { clearInterval(replayTimer); replayTimer = null }
}

function onScrub() {
  stopPlay()
}

/* 回放投影:轨迹包围盒自适应(带边距) */
const RW = 640, RH = 400, R_PAD = 40
const rview = computed(() => {
  const pts = replay.points
  if (!pts.length) return null
  let minLng = Infinity, maxLng = -Infinity, minLat = Infinity, maxLat = -Infinity
  pts.forEach((q) => {
    minLng = Math.min(minLng, q.longitude); maxLng = Math.max(maxLng, q.longitude)
    minLat = Math.min(minLat, q.latitude); maxLat = Math.max(maxLat, q.latitude)
  })
  if (maxLng - minLng < 1e-5) { minLng -= 1e-4; maxLng += 1e-4 }
  if (maxLat - minLat < 1e-5) { minLat -= 1e-4; maxLat += 1e-4 }
  const latMid = (minLat + maxLat) / 2
  const kx = 111320 * Math.cos((latMid * Math.PI) / 180)
  const scale = Math.min((RW - R_PAD * 2) / ((maxLng - minLng) * kx), (RH - R_PAD * 2) / ((maxLat - minLat) * 111320))
  const offX = (RW - (maxLng - minLng) * kx * scale) / 2
  const offY = (RH - (maxLat - minLat) * 111320 * scale) / 2
  return {
    x: (lng) => offX + (lng - minLng) * kx * scale,
    y: (lat) => RH - (offY + (lat - minLat) * 111320 * scale)
  }
})

const rp = (q) => {
  const v = rview.value
  return v ? { x: v.x(q.longitude), y: v.y(q.latitude) } : { x: 0, y: 0 }
}

const replayAllAttr = computed(() =>
  replay.points.map((q) => `${rp(q).x},${rp(q).y}`).join(' '))
const replayFlownAttr = computed(() =>
  replay.points.slice(0, replay.idx + 1).map((q) => `${rp(q).x},${rp(q).y}`).join(' '))

/** 总航程(haversine 累加) */
const replayDistanceKm = computed(() => {
  let m = 0
  for (let i = 1; i < replay.points.length; i++) {
    const a = replay.points[i - 1], b = replay.points[i]
    const rad = Math.PI / 180
    const dLat = (b.latitude - a.latitude) * rad, dLng = (b.longitude - a.longitude) * rad
    const h = Math.sin(dLat / 2) ** 2
      + Math.cos(a.latitude * rad) * Math.cos(b.latitude * rad) * Math.sin(dLng / 2) ** 2
    m += 2 * 6371008.8 * Math.asin(Math.sqrt(h))
  }
  return (m / 1000).toFixed(2)
})

onUnmounted(stopPlay)
</script>

<style scoped>
.page { padding-bottom: 20px; }
.head-left { display: flex; align-items: center; gap: 12px; }
.sn { font-family: monospace; font-size: 13px; color: var(--primary); }
.model, .gateway { font-size: 12.5px; color: var(--text-dim); }
.actions { display: flex; align-items: center; gap: 10px; }
.poll-tip { display: inline-flex; align-items: center; gap: 6px; font-size: 12px; color: var(--text-dim); }
.poll-tip i { width: 7px; height: 7px; border-radius: 50%; background: #98a2b3; }
.poll-tip i.on { background: #12b76a; box-shadow: 0 0 7px #12b76a; }

.grid { display: grid; grid-template-columns: 1fr 1fr; gap: 12px; margin-bottom: 12px; }
.grid2 { display: grid; grid-template-columns: 1fr 1fr; gap: 12px; }
@media (max-width: 1280px) { .grid, .grid2 { grid-template-columns: 1fr; } }

.panel { padding-bottom: 14px; }
.flight-panel { padding: 0 16px 14px; }
.flight-body { display: flex; gap: 20px; align-items: center; }
.compass { width: 200px; height: 200px; flex-shrink: 0; }
.flight-nums { flex: 1; min-width: 0; }
.big { margin-bottom: 12px; }
.b-label { display: block; font-size: 12px; color: var(--text-dim); margin-bottom: 2px; }
.b-value { font-size: 30px; font-weight: 700; color: var(--text); line-height: 1.1; }
.b-value i { font-size: 13px; font-weight: 500; color: var(--text-dim); font-style: normal; margin-left: 3px; }
.row {
  display: flex; align-items: center; justify-content: space-between; gap: 10px;
  padding: 6px 0; border-bottom: 1px dashed var(--border);
  font-size: 12.5px; color: var(--text-dim);
}
.row b { color: var(--text); font-weight: 600; font-size: 13px; }

.batt-wrap { display: flex; gap: 20px; align-items: center; padding: 0 16px; }
.batt-ring { width: 140px; height: 140px; flex-shrink: 0; }
.batt-info { flex: 1; min-width: 0; }
.update-time { margin: 12px 16px 0; font-size: 12px; color: var(--text-faint); text-align: right; }

.svc-wrap { padding: 0 16px; }
.svc-group { margin-bottom: 14px; }
.svc-title {
  font-size: 12.5px; font-weight: 600; color: var(--text-dim);
  margin-bottom: 8px; padding-left: 8px; border-left: 3px solid var(--primary);
}
.svc-btns { display: flex; flex-wrap: wrap; gap: 8px; }

/* 航迹回放抽屉 */
.replay-wrap { padding: 0 4px; }
.replay-toolbar { display: flex; align-items: center; gap: 10px; margin-bottom: 10px; }
.replay-toolbar .spacer { flex: 1; }
.replay-map {
  /* 按 viewBox 比例撑满抽屉宽度,固定高度会在 preserveAspectRatio=meet 下两侧留白 */
  display: block; width: 100%; aspect-ratio: 640 / 400; height: auto;
  background: linear-gradient(160deg, #fbfdff 0%, #f3f8ff 100%);
  border-radius: 8px;
}
.replay-legend {
  display: flex; flex-wrap: wrap; gap: 4px 16px; margin-bottom: 8px;
  font-size: 11.5px; color: var(--text-dim);
}
.replay-legend span { display: inline-flex; align-items: center; gap: 5px; }
.replay-legend i { width: 16px; height: 0; border-top: 2px solid; border-radius: 1px; }
.replay-legend i.lg-all { border-top-style: dashed; border-color: #b9c7da; }
.replay-legend i.lg-flown { border-color: #155eef; }
.replay-legend i.lg-start, .replay-legend i.lg-end, .replay-legend i.lg-cur {
  width: 8px; height: 8px; border: none; border-radius: 50%;
}
.replay-legend i.lg-start { background: #12b76a; }
.replay-legend i.lg-end { background: #f04438; }
.replay-legend i.lg-cur { background: #0ea5e9; border-radius: 2px; }
.replay-stats {
  display: flex; flex-wrap: wrap; gap: 6px 18px; margin-top: 8px;
  font-size: 12.5px; color: var(--text-dim);
}
.replay-stats b { color: var(--text); font-weight: 600; }
</style>
