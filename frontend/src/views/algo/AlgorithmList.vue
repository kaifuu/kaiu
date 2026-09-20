<template>
  <div class="page">
    <div class="page-header">
      <span class="page-title">算法管理</span>
      <div class="actions">
        <el-button type="primary" @click="openOdor">
          <el-icon style="margin-right: 4px"><DataAnalysis /></el-icon>臭气分布图与溯源
        </el-button>
      </div>
    </div>

    <div class="stats">
      <div class="stat"><span class="stat-label">识别算法</span><b class="stat-value">{{ algorithms.length }}</b></div>
      <div class="stat"><span class="stat-label">启用中</span><b class="stat-value online">{{ enabledCount }}</b></div>
      <div class="stat"><span class="stat-label">待处置告警</span><b class="stat-value" :class="{ danger: pendingCount > 0 }">{{ pendingCount }}</b></div>
      <div class="stat"><span class="stat-label">告警总数</span><b class="stat-value">{{ alarmTotal }}</b></div>
    </div>

    <!-- 算法卡片:注册表 + 参数配置 + 手动执行 -->
    <div class="panel section-panel">
      <div class="panel-title">算法配置</div>
      <div class="algo-grid">
        <div v-for="a in algorithms" :key="a.id" class="algo-card" :class="{ off: !a.enabled }">
          <div class="algo-head">
            <el-tag size="small" :type="dictTag(ALGO_CODE, a.code)" effect="dark">{{ dictLabel(ALGO_CODE, a.code) }}</el-tag>
            <span class="algo-name">{{ a.name }}</span>
            <el-switch v-model="a.enabled" size="small" @change="saveAlgorithm(a)" />
          </div>
          <div class="algo-desc">{{ a.description }}</div>
          <div class="algo-meta">
            <span class="meta-item"><el-icon><Location /></el-icon>{{ a.scene }}</span>
            <el-tag v-if="a.requiresIr" size="small" type="danger" effect="plain">需红外</el-tag>
            <el-tag v-if="a.videoRecord" size="small" type="warning" effect="plain">自动录像</el-tag>
            <el-tag size="small" :type="dictTag(ALGO_LEVEL, a.alarmLevel)" effect="plain">
              {{ dictLabel(ALGO_LEVEL, a.alarmLevel) }}
            </el-tag>
          </div>
          <div class="algo-config">
            <div class="conf-item">
              <span class="conf-label">置信度阈值 {{ a.confidenceValue }}%</span>
              <el-slider v-model="a.confidenceValue" :min="50" :max="99" size="small" />
            </div>
            <div class="conf-item conf-level">
              <span class="conf-label">告警等级</span>
              <el-select v-model="a.alarmLevel" size="small" style="width: 110px">
                <el-option v-for="o in dictOptions(ALGO_LEVEL)" :key="o.value" :label="o.label" :value="o.value" />
              </el-select>
            </div>
          </div>
          <div class="algo-foot">
            <span class="dim">已执行 {{ a.runCount ?? 0 }} 次 · 上次 {{ fmtTime(a.lastRunAt) }}</span>
            <span>
              <el-button size="small" @click="saveAlgorithm(a)">保存配置</el-button>
              <el-button size="small" type="primary" :loading="runningId === a.id" :disabled="!a.enabled"
                         @click="runAlgorithm(a)">执行识别</el-button>
            </span>
          </div>
        </div>
      </div>
    </div>

    <!-- 告警记录 -->
    <div class="panel table-panel">
      <div class="toolbar">
        <el-select v-model="codeFilter" clearable placeholder="算法" style="width: 150px">
          <el-option v-for="o in dictOptions(ALGO_CODE)" :key="o.value" :label="o.label" :value="o.value" />
        </el-select>
        <el-select v-model="levelFilter" clearable placeholder="告警等级" style="width: 120px">
          <el-option v-for="o in dictOptions(ALGO_LEVEL)" :key="o.value" :label="o.label" :value="o.value" />
        </el-select>
        <el-select v-model="statusFilter" clearable placeholder="处置状态" style="width: 120px">
          <el-option v-for="o in dictOptions(ALGO_STATUS)" :key="o.value" :label="o.label" :value="o.value" />
        </el-select>
        <el-input v-model="keyword" placeholder="标题 / 位置" clearable style="width: 200px" @keyup.enter="search">
          <template #prefix><el-icon><Search /></el-icon></template>
        </el-input>
        <el-button @click="search">查询</el-button>
      </div>

      <el-table v-loading="loading" :data="rows" height="100%" size="default" @sort-change="onSort">
        <el-table-column prop="occurredAt" label="发生时间" width="150" sortable="custom">
          <template #default="{ row }">{{ fmtTime(row.occurredAt) }}</template>
        </el-table-column>
        <el-table-column prop="algorithmCode" label="算法" width="110">
          <template #default="{ row }">
            <el-tag size="small" :type="dictTag(ALGO_CODE, row.algorithmCode)" effect="plain">
              {{ dictLabel(ALGO_CODE, row.algorithmCode) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="title" label="告警内容" min-width="240" show-overflow-tooltip />
        <el-table-column prop="level" label="等级" width="90" sortable="custom">
          <template #default="{ row }">
            <el-tag size="small" :type="dictTag(ALGO_LEVEL, row.level)" effect="dark">
              {{ dictLabel(ALGO_LEVEL, row.level) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="confidence" label="置信度" width="90" sortable="custom">
          <template #default="{ row }">{{ row.confidence }}%</template>
        </el-table-column>
        <el-table-column prop="address" label="位置" min-width="190" show-overflow-tooltip />
        <el-table-column label="录像取证" width="100" align="center">
          <template #default="{ row }">
            <el-tag v-if="row.videoObjectKey" size="small" type="warning" effect="plain">
              <el-icon style="vertical-align: -1px"><VideoCamera /></el-icon> {{ row.videoSeconds }}s
            </el-tag>
            <span v-else class="dim">-</span>
          </template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="90">
          <template #default="{ row }">
            <el-tag size="small" :type="dictTag(ALGO_STATUS, row.status)" effect="plain">
              {{ dictLabel(ALGO_STATUS, row.status) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="130" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="openDetail(row)">详情</el-button>
            <el-button v-if="row.status === 'PENDING'" link type="warning" size="small" @click="openDetail(row)">处置</el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="pager-row">
        <el-pagination background layout="total, sizes, prev, pager, next" :total="pager.total"
                       v-model:current-page="pager.page" v-model:page-size="pager.size"
                       :page-sizes="[10, 20, 50]" @current-change="load" @size-change="onSizeChange" />
      </div>
    </div>

    <!-- 告警详情 + 处置 -->
    <el-drawer v-model="detail.visible" :title="`告警详情 · ${detail.row?.algorithmName || ''}`" direction="rtl" size="620px">
      <el-descriptions :column="2" border size="small">
        <el-descriptions-item label="算法">{{ detail.row?.algorithmName || '-' }}</el-descriptions-item>
        <el-descriptions-item label="等级">
          <el-tag size="small" :type="dictTag(ALGO_LEVEL, detail.row?.level)" effect="dark">
            {{ dictLabel(ALGO_LEVEL, detail.row?.level) }}
          </el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="发生时间">{{ fmtTime(detail.row?.occurredAt) }}</el-descriptions-item>
        <el-descriptions-item label="置信度">{{ detail.row?.confidence ?? '-' }}%</el-descriptions-item>
        <el-descriptions-item label="位置坐标" :span="2">
          {{ detail.row?.longitude }}, {{ detail.row?.latitude }}
        </el-descriptions-item>
        <el-descriptions-item label="详细地址" :span="2">{{ detail.row?.address || '-' }}</el-descriptions-item>
        <el-descriptions-item label="录像取证" :span="2">
          <span v-if="detail.row?.videoObjectKey" class="mono">{{ detail.row.videoObjectKey }}({{ detail.row.videoSeconds }}s)</span>
          <span v-else class="dim">无</span>
        </el-descriptions-item>
        <el-descriptions-item label="处置状态">
          <el-tag size="small" :type="dictTag(ALGO_STATUS, detail.row?.status)" effect="plain">
            {{ dictLabel(ALGO_STATUS, detail.row?.status) }}
          </el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="处置人">{{ detail.row?.handler || '-' }}</el-descriptions-item>
        <el-descriptions-item v-if="detail.row?.handleTime" label="处置时间">{{ fmtTime(detail.row.handleTime) }}</el-descriptions-item>
        <el-descriptions-item v-if="detail.row?.handleRemark" label="处置意见" :span="2">{{ detail.row.handleRemark }}</el-descriptions-item>
      </el-descriptions>

      <div class="panel-title sub">识别结论</div>
      <el-table :data="payloadRows" size="small" border>
        <el-table-column prop="label" label="项" width="150" />
        <el-table-column prop="value" label="值">
          <template #default="{ row }">
            <span :class="{ mono: row.mono }">{{ row.value }}</span>
          </template>
        </el-table-column>
      </el-table>

      <template v-if="detail.row?.status === 'PENDING'">
        <div class="panel-title sub">处置</div>
        <el-input v-model="detail.remark" type="textarea" :rows="3" maxlength="200" show-word-limit
                  placeholder="填写处置意见,如:已现场核实为虚警 / 已通知运行班组处置 ..." />
        <div class="handle-actions">
          <el-button type="primary" :loading="detail.saving" :disabled="!detail.remark.trim()" @click="handleAlarm">提交处置</el-button>
        </div>
      </template>
    </el-drawer>

    <!-- 臭气分布图与溯源 -->
    <el-dialog v-model="odor.visible" title="臭气告警分布图与扩散溯源" width="980px" top="4vh">
      <div class="odor-bar">
        <div class="odor-wind-now">
          区域风:<b>{{ odor.wind?.directionText || '-' }}风 {{ odor.wind?.speed ?? '-' }} m/s</b>
          <span class="dim">· 超标站点 {{ odor.mapData?.alarmStations ?? 0 }} / {{ odor.mapData?.stations?.length ?? 0 }}</span>
        </div>
        <div class="odor-wind-set">
          <span class="dim">改风重算:</span>
          <el-input-number v-model="odor.form.speed" :min="0" :max="17" :step="0.5" :controls="false" size="small" style="width: 76px" />
          <span class="dim">m/s</span>
          <el-input-number v-model="odor.form.direction" :min="0" :max="359" :controls="false" size="small" style="width: 76px" />
          <span class="dim">°</span>
          <el-select v-model="odor.form.direction" size="small" style="width: 96px" placeholder="方位">
            <el-option v-for="o in WIND_PRESET" :key="o.value" :label="o.label" :value="o.value" />
          </el-select>
          <el-button size="small" type="primary" :loading="odor.saving" @click="applyWind">应用</el-button>
          <el-button size="small" @click="loadOdor">刷新读数</el-button>
        </div>
      </div>

      <div v-loading="odor.loading" class="odor-map-wrap">
        <svg :viewBox="`0 0 ${MAP_W} ${MAP_H}`" class="odor-svg">
          <!-- 网格底图 -->
          <rect x="0" y="0" :width="MAP_W" :height="MAP_H" fill="#f8fafc" />
          <g stroke="#e2e8f0" stroke-width="1">
            <line v-for="i in 9" :key="'v' + i" :x1="(MAP_W / 10) * i" y1="0" :x2="(MAP_W / 10) * i" :y2="MAP_H" />
            <line v-for="i in 6" :key="'h' + i" x1="0" :y1="(MAP_H / 7) * i" :x2="MAP_W" :y2="(MAP_H / 7) * i" />
          </g>

          <!-- 扩散烟羽:分段加宽近似高斯烟羽锥形 + 中心线 -->
          <g v-if="odor.disp?.active">
            <line v-for="(seg, i) in plumeSegments" :key="'pl' + i"
                  :x1="seg.x1" :y1="seg.y1" :x2="seg.x2" :y2="seg.y2"
                  :stroke-width="seg.width" stroke="#f97316" stroke-opacity="0.16" stroke-linecap="butt" />
            <polyline :points="plumeCenterline" fill="none" stroke="#f97316" stroke-width="2"
                      stroke-dasharray="7 5" stroke-opacity="0.85" />
            <!-- 推定泄漏源:不确定度圆 + X 标记 -->
            <circle :cx="sourceXY.x" :cy="sourceXY.y" :r="sourceUncertaintyR"
                    fill="#f97316" fill-opacity="0.06" stroke="#f97316" stroke-width="1.5" stroke-dasharray="5 4" />
            <g stroke="#ea580c" stroke-width="3" stroke-linecap="round">
              <line :x1="sourceXY.x - 7" :y1="sourceXY.y - 7" :x2="sourceXY.x + 7" :y2="sourceXY.y + 7" />
              <line :x1="sourceXY.x - 7" :y1="sourceXY.y + 7" :x2="sourceXY.x + 7" :y2="sourceXY.y - 7" />
            </g>
            <text :x="sourceLabelXY.x" :y="sourceLabelXY.y - 4" text-anchor="middle" class="svg-label source-label">推定泄漏源</text>
            <text :x="sourceLabelXY.x" :y="sourceLabelXY.y + 12" text-anchor="middle" class="svg-label dim-svg">
              回溯 {{ odor.disp.source?.backtrackM }} m ± {{ odor.disp.source?.uncertaintyM }} m
            </text>
          </g>

          <!-- 监测站点 -->
          <g v-for="s in odor.mapData?.stations || []" :key="s.stationCode" class="station">
            <circle :cx="stXY(s).x" :cy="stXY(s).y" :r="stationR(s)" :fill="stationColor(s)"
                    stroke="#fff" stroke-width="2" stroke-opacity="0.9" />
            <circle :cx="stXY(s).x" :cy="stXY(s).y" :r="stationR(s) + 4" fill="none"
                    :stroke="stationColor(s)" stroke-opacity="0.25" stroke-width="3" />
            <text :x="stXY(s).x + stationR(s) + 8" :y="stXY(s).y - 2" class="svg-label">{{ s.stationName }}</text>
            <text :x="stXY(s).x + stationR(s) + 8" :y="stXY(s).y + 12" class="svg-label dim-svg">
              H2S {{ Number(s.h2sPpm).toFixed(3) }} ppm
            </text>
          </g>

          <!-- 风向标:箭头指向气流去向(风的来向 + 180°) -->
          <g class="wind-badge">
            <rect :x="MAP_W - 108" y="16" width="92" height="72" rx="10" fill="#fff" stroke="#e2e8f0" />
            <g :transform="`rotate(${(odor.wind?.direction ?? 0) + 180}, ${MAP_W - 62}, 44)`">
              <line :x1="MAP_W - 62" :y1="60" :x2="MAP_W - 62" :y2="30" stroke="#2563eb" stroke-width="3" stroke-linecap="round" />
              <polygon :points="`${MAP_W - 62},22 ${MAP_W - 68},34 ${MAP_W - 56},34`" fill="#2563eb" />
            </g>
            <text :x="MAP_W - 62" :y="80" text-anchor="middle" class="svg-label">
              {{ odor.wind?.directionText || '-' }}风 {{ odor.wind?.speed ?? '-' }} m/s
            </text>
          </g>

          <!-- 图例 -->
          <g class="legend" transform="translate(16, 16)">
            <rect x="0" y="0" width="190" height="76" rx="8" fill="#fff" stroke="#e2e8f0" />
            <circle cx="18" cy="18" r="6" fill="#67c23a" /><text x="32" y="22" class="svg-label">正常(&lt; 0.05 ppm)</text>
            <circle cx="18" cy="38" r="6" fill="#e6a23c" /><text x="32" y="42" class="svg-label">警告(≥ 0.05 ppm)</text>
            <circle cx="18" cy="58" r="6" fill="#f56c6c" /><text x="32" y="62" class="svg-label">严重(≥ 0.2 ppm)</text>
            <line x1="108" y1="18" x2="168" y2="18" stroke="#f97316" stroke-width="10" stroke-opacity="0.2" />
            <text x="108" y="38" class="svg-label dim-svg">烟羽扩散带</text>
            <text x="108" y="58" class="svg-label dim-svg">✕ 推定泄漏源</text>
          </g>

          <text v-if="odor.disp && !odor.disp.active" :x="MAP_W / 2" :y="MAP_H / 2"
                text-anchor="middle" class="svg-empty">{{ odor.disp.message }}</text>
        </svg>
      </div>

      <!-- 溯源结论 -->
      <div v-if="odor.disp?.active" class="odor-result">
        <div class="result-card">
          <div class="panel-title sub">溯源结论</div>
          <el-descriptions :column="1" border size="small">
            <el-descriptions-item label="峰值站点">{{ odor.disp.peakStation?.stationName }}({{ odor.disp.peakStation?.area }})</el-descriptions-item>
            <el-descriptions-item label="峰值浓度">H2S {{ Number(odor.disp.maxH2sPpm).toFixed(3) }} ppm / NH3 {{ Number(odor.disp.peakStation?.nh3Ppm).toFixed(2) }} ppm</el-descriptions-item>
            <el-descriptions-item label="推定泄漏源">{{ odor.disp.source?.longitude }}, {{ odor.disp.source?.latitude }}</el-descriptions-item>
            <el-descriptions-item label="回溯距离">{{ odor.disp.source?.backtrackM }} m(± {{ odor.disp.source?.uncertaintyM }} m)</el-descriptions-item>
            <el-descriptions-item label="轨迹长度">{{ odor.disp.trajectory?.length }} 个采样点,沿 {{ odor.wind?.directionText }}风向下游展开</el-descriptions-item>
          </el-descriptions>
        </div>
        <div class="result-card">
          <div class="panel-title sub">受影响站点</div>
          <el-table :data="odor.disp.affectedStations" size="small" border max-height="252">
            <el-table-column prop="stationName" label="站点" min-width="110" />
            <el-table-column label="H2S(ppm)" width="100">
              <template #default="{ row }">{{ Number(row.h2sPpm).toFixed(3) }}</template>
            </el-table-column>
            <el-table-column label="级别" width="80">
              <template #default="{ row }">
                <el-tag size="small" :type="row.level === 'ERROR' ? 'danger' : 'warning'" effect="plain">
                  {{ row.level === 'ERROR' ? '严重' : '警告' }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="距源点" width="90">
              <template #default="{ row }">{{ row.distanceToSourceM }} m</template>
            </el-table-column>
          </el-table>
        </div>
      </div>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, computed, watch, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { Search, Location, VideoCamera, DataAnalysis } from '@element-plus/icons-vue'
import http from '../../api'
import { ALGO_CODE, ALGO_LEVEL, ALGO_STATUS, dictLabel, dictTag, dictOptions } from '../../utils/dict'

/* ---------------- 算法注册表 ---------------- */
const algorithms = ref([])
const runningId = ref(null)
const enabledCount = computed(() => algorithms.value.filter((a) => a.enabled).length)

async function loadAlgorithms() {
  algorithms.value = (await http.get('/algorithms')) || []
}

/** 开关 / 置信度 / 等级统一走一个保存接口 */
async function saveAlgorithm(row) {
  await http.put(`/algorithms/${row.id}`, {
    enabled: row.enabled,
    confidenceValue: row.confidenceValue,
    alarmLevel: row.alarmLevel
  })
  ElMessage.success(`「${dictLabel(ALGO_CODE, row.code)}」配置已保存`)
}

/** 手动执行一次识别;航线任务执行中也会由后端定时自动识别 */
async function runAlgorithm(row) {
  runningId.value = row.id
  try {
    const alarm = await http.post(`/algorithms/${row.id}/run`)
    ElMessage.success(alarm?.videoObjectKey
      ? `命中并已录像取证:${alarm.title}(${alarm.videoSeconds}s)`
      : `命中:${alarm.title}`)
    await Promise.all([loadAlgorithms(), load(), loadPending()])
  } finally {
    runningId.value = null
  }
}

/* ---------------- 告警记录 ---------------- */
const loading = ref(false)
const rows = ref([])
const keyword = ref('')
const codeFilter = ref('')
const levelFilter = ref('')
const statusFilter = ref('')
const pager = reactive({ page: 1, size: 10, total: 0 })
const sort = reactive({ sortBy: '', direction: '' })
const pendingCount = ref(0)

/** 顶部统计是全局口径,不随列表筛选变化 */
const alarmTotal = ref(0)

function onSort({ prop, order }) {
  sort.sortBy = order ? prop : ''
  sort.direction = order === 'ascending' ? 'asc' : 'desc'
  pager.page = 1
  load()
}

async function load() {
  loading.value = true
  try {
    const res = await http.get('/algo-alarms/page', {
      params: {
        page: pager.page, size: pager.size,
        sortBy: sort.sortBy || undefined,
        direction: sort.sortBy ? sort.direction : undefined,
        algorithmCode: codeFilter.value || undefined,
        level: levelFilter.value || undefined,
        status: statusFilter.value || undefined,
        keyword: keyword.value || undefined
      }
    })
    rows.value = res.rows || []
    pager.total = res.total || 0
  } finally {
    loading.value = false
  }
}

async function loadPending() {
  const [pending, all] = await Promise.all([
    http.get('/algo-alarms/page', { params: { page: 1, size: 1, status: 'PENDING' } }),
    http.get('/algo-alarms/page', { params: { page: 1, size: 1 } })
  ])
  pendingCount.value = pending.total || 0
  alarmTotal.value = all.total || 0
}

watch([codeFilter, levelFilter, statusFilter], () => { pager.page = 1; load() })
function search() { pager.page = 1; load() }
function onSizeChange() { pager.page = 1; load() }

/* ---------------- 详情与处置 ---------------- */
const detail = reactive({ visible: false, saving: false, row: null, remark: '' })

/** 识别结论 payload 的中文标签(与后端 AiAlarmService 口径一致) */
const PAYLOAD_LABELS = {
  fireType: '烟火类型', irMaxTempC: '红外最高温(℃)', ambientTempC: '环境温度(℃)',
  tempPointLng: '高温点经度', tempPointLat: '高温点纬度',
  smokeColor: '烟羽颜色', opacityPercent: '不透光度(%)', plumeHeightM: '烟羽高度(m)',
  chimneyId: '排气筒', recordedAt: '取证时刻',
  dumpType: '倾倒类型', areaM2: '面积(㎡)', dwellMinutes: '停留时长(min)', vehiclePlate: '涉事车牌',
  anomalyType: '异常类型', membraneZone: '覆盖膜区域',
  leakType: '泄漏类型', spreadAreaM2: '扩散面积(㎡)', flowRateM3h: '流速(m³/h)',
  stationName: '超标站点', h2sPpm: 'H2S(ppm)', nh3Ppm: 'NH3(ppm)', odorUnit: '臭气浓度(无量纲)',
  sourceLng: '源点经度', sourceLat: '源点纬度', backtrackM: '回溯距离(m)',
  trajectoryPoints: '轨迹点数', windSpeedMps: '风速(m/s)', windDirectionDeg: '风向(°)',
  thumbObjectKey: '现场截图', videoObjectKey: '录像文件', videoSeconds: '录像时长(s)'
}
const FIRE_TYPE = { FLAME: '明火', SMOKE: '烟雾' }
const MONO_KEYS = new Set(['thumbObjectKey', 'videoObjectKey'])

const payloadRows = computed(() => {
  const p = detail.row?.payload
  if (!p) return []
  return Object.entries(p).map(([key, value]) => ({
    label: PAYLOAD_LABELS[key] || key,
    value: key === 'fireType' ? (FIRE_TYPE[value] || value) : (value ?? '-'),
    mono: MONO_KEYS.has(key)
  }))
})

async function openDetail(row) {
  detail.row = await http.get(`/algo-alarms/${row.id}`)
  detail.remark = ''
  detail.visible = true
}

async function handleAlarm() {
  if (!detail.remark.trim()) return
  detail.saving = true
  try {
    await http.post(`/algo-alarms/${detail.row.id}/handle`, { remark: detail.remark.trim() })
    ElMessage.success('处置完成')
    detail.visible = false
    await Promise.all([load(), loadPending()])
  } finally {
    detail.saving = false
  }
}

/* ---------------- 臭气分布图与溯源 ---------------- */
const odor = reactive({
  visible: false, loading: false, saving: false,
  mapData: null, disp: null, wind: null,
  form: { speed: 3.0, direction: 135 }
})
const WIND_PRESET = [
  { label: '北风', value: 0 }, { label: '东北风', value: 45 }, { label: '东风', value: 90 },
  { label: '东南风', value: 135 }, { label: '南风', value: 180 }, { label: '西南风', value: 225 },
  { label: '西风', value: 270 }, { label: '西北风', value: 315 }
]

const MAP_W = 900
const MAP_H = 460
const MAP_PAD = 56

/** 图幅范围:站点 + 轨迹 + 源点(含不确定度圆)全部纳入,防止画布外溢出 */
const mapBounds = computed(() => {
  const pts = []
  for (const s of odor.mapData?.stations || []) {
    pts.push([Number(s.longitude), Number(s.latitude)])
  }
  for (const p of odor.disp?.trajectory || []) {
    pts.push([Number(p.longitude), Number(p.latitude)])
  }
  if (odor.disp?.active && odor.disp.source) {
    const un = Number(odor.disp.source.uncertaintyM) / 111320
    pts.push([Number(odor.disp.source.longitude) - un, Number(odor.disp.source.latitude) - un])
    pts.push([Number(odor.disp.source.longitude) + un, Number(odor.disp.source.latitude) + un])
  }
  if (!pts.length) return null
  let minLng = Infinity, maxLng = -Infinity, minLat = Infinity, maxLat = -Infinity
  for (const [x, y] of pts) {
    minLng = Math.min(minLng, x); maxLng = Math.max(maxLng, x)
    minLat = Math.min(minLat, y); maxLat = Math.max(maxLat, y)
  }
  // 留 15% 视觉余量,范围过小时给最小跨度避免除零
  const lngSpan = Math.max((maxLng - minLng) * 1.15, 0.002)
  const latSpan = Math.max((maxLat - minLat) * 1.15, 0.002)
  return {
    minLng: (minLng + maxLng) / 2 - lngSpan / 2, lngSpan,
    minLat: (minLat + maxLat) / 2 - latSpan / 2, latSpan,
    avgLat: (minLat + maxLat) / 2
  }
})

function toXY(lng, lat) {
  const b = mapBounds.value
  if (!b) return { x: MAP_W / 2, y: MAP_H / 2 }
  return {
    x: MAP_PAD + ((lng - b.minLng) / b.lngSpan) * (MAP_W - 2 * MAP_PAD),
    y: MAP_H - MAP_PAD - ((lat - b.minLat) / b.latSpan) * (MAP_H - 2 * MAP_PAD)
  }
}

/** 每像素对应米数:烟羽宽度 / 不确定度半径按此换算成屏幕像素 */
const pxPerMeter = computed(() => {
  const b = mapBounds.value
  if (!b) return 0.1
  const mapWidthM = b.lngSpan * 111320 * Math.cos((b.avgLat * Math.PI) / 180)
  return (MAP_W - 2 * MAP_PAD) / Math.max(mapWidthM, 1)
})

const stXY = (s) => toXY(Number(s.longitude), Number(s.latitude))
const stationR = (s) => 6 + Math.min(8, Number(s.h2sPpm) * 22)
const stationColor = (s) => (s.level === 'ERROR' ? '#f56c6c' : s.level === 'WARN' ? '#e6a23c' : '#67c23a')

const sourceXY = computed(() => odor.disp?.source
  ? toXY(Number(odor.disp.source.longitude), Number(odor.disp.source.latitude))
  : { x: 0, y: 0 })
const sourceUncertaintyR = computed(() =>
  Math.max(14, Math.min(120, (odor.disp?.source?.uncertaintyM || 0) * pxPerMeter.value)))

/** 源点标签放上风向一侧(背离烟羽与超标站点),并收进画布,避免与峰值站点文字叠压 */
const sourceLabelXY = computed(() => {
  const s = sourceXY.value
  const rad = ((Number(odor.wind?.direction) || 0) * Math.PI) / 180
  const x = Math.min(MAP_W - 70, Math.max(70, s.x + Math.sin(rad) * 44))
  const y = Math.min(MAP_H - 26, Math.max(26, s.y - Math.cos(rad) * 44))
  return { x, y }
})

/** 分段加宽:每段线宽取两端 widthM 均值 × 每像素米数,近似锥形烟羽 */
const plumeSegments = computed(() => {
  const tr = odor.disp?.trajectory || []
  const segs = []
  for (let i = 0; i < tr.length - 1; i++) {
    const p1 = toXY(Number(tr[i].longitude), Number(tr[i].latitude))
    const p2 = toXY(Number(tr[i + 1].longitude), Number(tr[i + 1].latitude))
    const width = ((Number(tr[i].widthM) + Number(tr[i + 1].widthM)) / 2) * pxPerMeter.value
    segs.push({ ...p1, x2: p2.x, y2: p2.y, width: Math.max(4, width) })
  }
  return segs
})
const plumeCenterline = computed(() =>
  (odor.disp?.trajectory || []).map((p) => {
    const { x, y } = toXY(Number(p.longitude), Number(p.latitude))
    return `${x.toFixed(1)},${y.toFixed(1)}`
  }).join(' '))

function openOdor() {
  odor.form.speed = Number(odor.wind?.speed ?? 3)
  odor.form.direction = Number(odor.wind?.direction ?? 135)
  odor.visible = true
  loadOdor()
}

async function loadOdor() {
  odor.loading = true
  try {
    const [mapData, disp] = await Promise.all([
      http.get('/odor/map'),
      http.get('/odor/dispersion')
    ])
    odor.mapData = mapData
    odor.wind = mapData?.wind
    odor.disp = disp
  } finally {
    odor.loading = false
  }
}

/** 改风立即重算一批读数与溯源 */
async function applyWind() {
  odor.saving = true
  try {
    odor.wind = await http.put('/odor/wind', { speed: odor.form.speed, direction: odor.form.direction })
    await loadOdor()
    ElMessage.success('已按新风场重算扩散与溯源')
  } finally {
    odor.saving = false
  }
}

/* ---------------- 杂项 ---------------- */
function fmtTime(v) {
  if (!v) return '从未'
  return String(v).replace('T', ' ').slice(0, 19)
}

onMounted(async () => {
  await Promise.all([loadAlgorithms(), load(), loadPending()])
})
</script>

<style scoped>
.stats { display: grid; grid-template-columns: repeat(4, 1fr); gap: 10px; margin-bottom: 12px; }
.stat {
  display: flex; align-items: center; justify-content: space-between;
  padding: 10px 14px; border-radius: 10px;
  background: #fff; border: 1px solid var(--border);
}
.stat-label { font-size: 12.5px; color: var(--text-dim); }
.stat-value { font-size: 19px; font-weight: 700; color: var(--text); }
.stat-value.online { color: var(--success); }
.stat-value.danger { color: var(--danger, #f56c6c); }

.section-panel { margin-bottom: 12px; padding: 12px 14px; }
.algo-grid { display: grid; grid-template-columns: repeat(2, 1fr); gap: 12px; margin-top: 10px; }
@media (max-width: 1280px) { .algo-grid { grid-template-columns: 1fr; } }
.algo-card {
  border: 1px solid var(--border); border-radius: 10px; padding: 12px 14px;
  background: #fff; display: flex; flex-direction: column; gap: 8px;
}
.algo-card.off { background: #fafbfc; }
.algo-card.off .algo-name, .algo-card.off .algo-desc { opacity: 0.55; }
.algo-head { display: flex; align-items: center; gap: 8px; }
.algo-head .el-switch { margin-left: auto; }
.algo-name { font-weight: 600; font-size: 14px; }
.algo-desc { font-size: 12.5px; color: var(--text-dim); line-height: 1.6; }
.algo-meta { display: flex; align-items: center; gap: 6px; flex-wrap: wrap; }
.meta-item { display: inline-flex; align-items: center; gap: 3px; font-size: 12px; color: var(--text-dim); }
.algo-config { display: flex; gap: 16px; align-items: center; border-top: 1px dashed var(--border); padding-top: 10px; }
.conf-item { flex: 1; }
.conf-item.conf-level { flex: 0 0 auto; display: flex; align-items: center; gap: 8px; }
.conf-label { font-size: 12px; color: var(--text-dim); display: block; margin-bottom: 2px; }
.algo-foot { display: flex; align-items: center; justify-content: space-between; border-top: 1px dashed var(--border); padding-top: 10px; }
.dim { color: var(--text-faint); font-size: 12.5px; }

.table-panel { height: 560px; padding: 8px; display: flex; flex-direction: column; }
.toolbar { display: flex; gap: 10px; padding: 8px 8px 12px; flex-wrap: wrap; }
.toolbar + .el-table { flex: 1; }
.pager-row { display: flex; justify-content: flex-end; padding: 10px 4px 2px; }
.actions { display: flex; gap: 10px; }

.panel-title.sub { margin: 18px 0 10px; }
.mono { font-family: monospace; font-size: 12.5px; word-break: break-all; }
.handle-actions { margin-top: 10px; }

/* ---------------- 臭气分布图 ---------------- */
.odor-bar {
  display: flex; align-items: center; justify-content: space-between;
  flex-wrap: wrap; gap: 10px; margin-bottom: 10px;
}
.odor-wind-now { font-size: 13.5px; }
.odor-wind-now b { color: #2563eb; margin-right: 8px; }
.odor-wind-set { display: flex; align-items: center; gap: 6px; }
.odor-map-wrap { border: 1px solid var(--border); border-radius: 10px; overflow: hidden; }
.odor-svg { display: block; width: 100%; height: auto; }
/* 白色描边光晕:站点/源点文字压在烟羽或网格线上仍可读 */
.svg-label {
  font-size: 12px; fill: #475569;
  paint-order: stroke; stroke: #fff; stroke-width: 3px; stroke-linejoin: round;
}
.dim-svg { fill: #64748b; font-size: 11px; }
.source-label { font-weight: 700; fill: #ea580c; }
.svg-empty { font-size: 15px; fill: #94a3b8; letter-spacing: 2px; }
.station circle:first-child { cursor: default; }
.odor-result { display: grid; grid-template-columns: 1fr 1.1fr; gap: 12px; margin-top: 12px; }
.result-card { border: 1px solid var(--border); border-radius: 10px; padding: 10px 12px; }
</style>
