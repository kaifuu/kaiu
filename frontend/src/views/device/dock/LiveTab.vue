<template>
  <div class="live-wrap">
    <!-- 上:发起直播 -->
    <div class="start-card">
      <div class="card-head">
        <span class="col-title">发起直播</span>
        <span class="dim-tip" v-if="!videoOptions.length">等待设备上报直播能力</span>
      </div>

      <div class="toolbar">
        <el-select v-model="form.videoId" filterable placeholder="视频源" style="width: 330px"
                   no-data-text="等待设备上报直播能力">
          <el-option v-for="o in videoOptions" :key="o.value" :label="o.label" :value="o.value" />
        </el-select>
        <el-select v-model="form.urlType" placeholder="推流协议" style="width: 120px">
          <el-option v-for="o in dictOptions(LIVE_URL_TYPE)" :key="o.value" :label="o.label" :value="o.value" />
        </el-select>
        <el-input v-model="form.url" placeholder="rtmp://media.local/live/dock1"
                  style="width: 250px" clearable />
        <el-select v-model="form.videoQuality" placeholder="清晰度" style="width: 100px">
          <el-option v-for="o in dictOptions(LIVE_QUALITY)" :key="o.value" :label="o.label" :value="o.value" />
        </el-select>
        <el-select v-model="form.videoType" placeholder="镜头" style="width: 100px">
          <el-option v-for="o in dictOptions(LIVE_VIDEO_TYPE)" :key="o.value" :label="o.label" :value="o.value" />
        </el-select>
        <el-button type="primary" :loading="starting" :disabled="offline" @click="startLive">开始直播</el-button>
      </div>

      <!-- 直播中快捷操作:镜头切换 -->
      <div class="lens-row" v-if="pushingCount">
        <span class="lens-label">直播中 · 切镜头</span>
        <el-button v-for="o in dictOptions(LIVE_VIDEO_TYPE)" :key="o.value" size="small" plain
                   :type="dictTag(LIVE_VIDEO_TYPE, o.value)"
                   :disabled="offline || lensBusy === o.value"
                   @click="changeLens(o.value)">
          {{ o.label }}
        </el-button>
        <span class="dim-tip">{{ pushingCount }} 路推流中</span>
      </div>
    </div>

    <!-- 下:直播会话 -->
    <div class="rec-card">
      <div class="toolbar">
        <span class="col-title">直播会话</span>
        <span class="dim-tip">会话每 4s 自动刷新</span>
      </div>

      <el-table :data="rows" v-loading="loading" stripe size="small" height="100%">
        <el-table-column type="index" label="序号" width="50"
                         :index="(i) => (pager.page - 1) * pager.size + i + 1" />
        <el-table-column prop="videoId" label="视频源" min-width="200" show-overflow-tooltip>
          <template #default="{ row }"><span class="mono">{{ row.videoId }}</span></template>
        </el-table-column>
        <el-table-column prop="videoType" label="镜头" width="80">
          <template #default="{ row }">
            <el-tag size="small" :type="dictTag(LIVE_VIDEO_TYPE, row.videoType)" effect="plain">
              {{ dictLabel(LIVE_VIDEO_TYPE, row.videoType) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="urlType" label="协议" width="100">
          <template #default="{ row }">
            <el-tag size="small" :type="dictTag(LIVE_URL_TYPE, row.urlType)" effect="plain">
              {{ dictLabel(LIVE_URL_TYPE, row.urlType) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="videoQuality" label="清晰度" width="80" align="center">
          <template #default="{ row }">{{ dictLabel(LIVE_QUALITY, row.videoQuality) }}</template>
        </el-table-column>
        <el-table-column prop="url" label="推流地址" min-width="180" show-overflow-tooltip>
          <template #default="{ row }">{{ row.url || '-' }}</template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="90">
          <template #default="{ row }">
            <el-tag size="small" :type="dictTag(LIVE_STREAM_STATUS, row.status)"
                    :effect="row.status === 'PUSHING' ? 'light' : 'plain'">
              {{ dictLabel(LIVE_STREAM_STATUS, row.status) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="startedAt" label="开始时间" width="160">
          <template #default="{ row }">{{ row.startedAt || '-' }}</template>
        </el-table-column>
        <el-table-column label="操作" width="200" fixed="right">
          <template #default="{ row }">
            <el-button v-if="row.status === 'PUSHING'" link type="danger" size="small"
                       :disabled="offline" @click="stopLive(row)">停止</el-button>
            <el-dropdown v-if="row.status === 'PUSHING'" :disabled="offline"
                         @command="(q) => setQuality(row, q)">
              <el-button link type="primary" size="small">
                清晰度<el-icon class="el-icon--right"><ArrowDown /></el-icon>
              </el-button>
              <template #dropdown>
                <el-dropdown-menu>
                  <el-dropdown-item v-for="o in dictOptions(LIVE_QUALITY)" :key="o.value" :command="o.value">
                    {{ o.label }}
                  </el-dropdown-item>
                </el-dropdown-menu>
              </template>
            </el-dropdown>
            <el-dropdown v-if="row.status === 'PUSHING'" :disabled="offline"
                         @command="(p) => changeCamera(row, p)">
              <el-button link type="primary" size="small">
                相机位<el-icon class="el-icon--right"><ArrowDown /></el-icon>
              </el-button>
              <template #dropdown>
                <el-dropdown-menu>
                  <el-dropdown-item :command="0">舱内</el-dropdown-item>
                  <el-dropdown-item :command="1">舱外</el-dropdown-item>
                </el-dropdown-menu>
              </template>
            </el-dropdown>
          </template>
        </el-table-column>
      </el-table>

      <div class="pager-row">
        <el-pagination background layout="total, sizes, prev, pager, next" :total="pager.total"
                       v-model:current-page="pager.page" v-model:page-size="pager.size"
                       :page-sizes="[10, 20, 50]" @current-change="load" @size-change="onSize" />
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted, onUnmounted, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { ArrowDown } from '@element-plus/icons-vue'
import http from '../../../api'
import {
  LIVE_URL_TYPE, LIVE_QUALITY, LIVE_VIDEO_TYPE, LIVE_STREAM_STATUS,
  dictLabel, dictTag, dictOptions
} from '../../../utils/dict'

const props = defineProps({ dock: { type: Object, default: null } })

/** 开流 / 停流 / 直播中调整都是下发类操作,机场离线时禁用 */
const offline = computed(() => props.dock?.status !== 'ONLINE')

/* ---------- 发起直播 ---------- */
const form = reactive({ videoId: '', urlType: 'RTMP', url: '', videoQuality: '0', videoType: 'normal' })
const videoOptions = ref([])
const starting = ref(false)
const lensBusy = ref('')

/* ---------- 会话列表 ---------- */
const rows = ref([])
const loading = ref(false)
const pager = reactive({ page: 1, size: 10, total: 0 })
const pushingCount = computed(() => rows.value.filter((r) => r.status === 'PUSHING').length)

let timer = null
let fetching = false

onMounted(() => { timer = setInterval(() => load(true), 4000) })
onUnmounted(() => clearInterval(timer))

watch(() => props.dock?.id, (id) => {
  if (id) { loadCapacity(); load() }
}, { immediate: true })

/** 解析设备上报的 live_capacity(原文 JSON 字符串)→ 视频源下拉项 */
async function loadCapacity() {
  const cap = await http.get(`/devices/${props.dock.id}/live/capacity`)
  const opts = []
  try {
    const parsed = cap?.capacityJson ? JSON.parse(cap.capacityJson) : null
    for (const d of parsed?.capacity?.device || []) {
      for (const cam of d.cameras || []) {
        opts.push({
          label: `${d.sn}/${cam.camera_index}/${cam.video_index}（镜头: ${(cam.video_types || []).join('/')}）`,
          value: `${d.sn}/${cam.camera_index}/${cam.video_index}`
        })
      }
    }
  } catch (e) {
    // capacity 脏数据按下拉为空处理
  }
  videoOptions.value = opts
  if (opts.length && !videoOptions.value.some((o) => o.value === form.videoId)) {
    form.videoId = opts[0].value
  }
}

async function startLive() {
  if (!form.videoId) return ElMessage.warning('请选择直播视频源')
  if (form.urlType !== 'WEBRTC' && !form.url) return ElMessage.warning('请填写推流地址')
  starting.value = true
  try {
    await http.post(`/devices/${props.dock.id}/live/start`, {
      videoId: form.videoId,
      urlType: form.urlType,
      url: form.url || undefined,
      videoQuality: Number(form.videoQuality),
      videoType: form.videoType
    })
    ElMessage.success('已发起直播推流')
    pager.page = 1
    load()
  } finally { starting.value = false }
}

/** 快捷切镜头:作用于该机场当前全部推流会话 */
async function changeLens(videoType) {
  lensBusy.value = videoType
  try {
    await http.post(`/devices/${props.dock.id}/live/lens`, { videoType })
    ElMessage.success(`已切换镜头:${dictLabel(LIVE_VIDEO_TYPE, videoType)}`)
    load()
  } finally { lensBusy.value = '' }
}

/* ---------- 会话分页 ---------- */
function onSize() { pager.page = 1; load() }

async function load(silent = false) {
  if (!props.dock?.id || fetching) return
  fetching = true
  if (!silent) loading.value = true
  try {
    const res = await http.get(`/devices/${props.dock.id}/live/streams/page`, {
      params: { page: pager.page, size: pager.size }
    })
    rows.value = res.rows || []
    pager.total = res.total || 0
  } finally { loading.value = false; fetching = false }
}

async function stopLive(row) {
  await http.post(`/devices/${props.dock.id}/live/streams/${row.id}/stop`)
  ElMessage.success('已发起停止推流')
  load()
}

async function setQuality(row, quality) {
  await http.post(`/devices/${props.dock.id}/live/streams/${row.id}/quality`, {
    videoQuality: Number(quality)
  })
  ElMessage.success(`清晰度已切换:${dictLabel(LIVE_QUALITY, Number(quality))}`)
  load()
}

async function changeCamera(row, cameraPosition) {
  await http.post(`/devices/${props.dock.id}/live/streams/${row.id}/camera`, {
    cameraPosition: Number(cameraPosition)
  })
  ElMessage.success(`相机位已切换:${Number(cameraPosition) === 0 ? '舱内' : '舱外'}`)
  load()
}
</script>

<style scoped>
/* 上发起直播卡 + 下会话表,一屏内展示 */
.live-wrap {
  height: 100%; min-height: 0; overflow: hidden;
  display: flex; flex-direction: column; gap: 10px;
  padding-top: 6px;
}

.start-card {
  flex-shrink: 0;
  background: #fff; border: 1px solid var(--border); border-radius: 10px;
  padding: 10px 12px;
}
.card-head { display: flex; align-items: center; justify-content: space-between; }
.col-title {
  font-size: 13px; font-weight: 600; color: var(--text);
  padding-left: 8px; border-left: 3px solid var(--primary);
}
.toolbar { display: flex; gap: 10px; padding: 8px 0 2px; flex-wrap: wrap; align-items: center; }
.dim-tip { font-size: 11.5px; color: var(--text-faint); }

/* 直播中快捷操作行 */
.lens-row { display: flex; gap: 8px; align-items: center; flex-wrap: wrap; padding-top: 10px; }
.lens-label { font-size: 12.5px; color: var(--text-dim); }
.lens-row .dim-tip { margin-left: auto; }

.rec-card { flex: 1; min-height: 0; display: flex; flex-direction: column; }
.rec-card .toolbar { padding: 2px 0 10px; }
.rec-card > .el-table { flex: 1; min-height: 0; }
.pager-row { display: flex; justify-content: flex-end; padding: 10px 0 2px; flex-shrink: 0; }

.mono { font-family: 'Consolas', 'Courier New', monospace; font-size: 12px; color: var(--primary); }
</style>
