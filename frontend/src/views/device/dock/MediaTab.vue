<template>
  <div class="media-wrap">
    <!-- 上:上传优先级 -->
    <div class="prio-card">
      <div class="card-head">
        <span class="col-title">上传优先级</span>
        <div class="prio-current">
          <span class="prio-label">当前优先任务</span>
          <span class="mono">{{ priority.flightId || '-' }}</span>
          <span class="dim-tip" v-if="priority.updateTime">更新于 {{ priority.updateTime }}</span>
        </div>
      </div>

      <div class="toolbar">
        <el-select v-model="priorityTarget" filterable placeholder="选择任务(按任务号优先上传)" style="width: 320px"
                   no-data-text="暂无航线任务">
          <el-option v-for="j in jobOptions" :key="j.value" :label="j.label" :value="j.value" />
        </el-select>
        <el-button type="primary" :loading="prioritizing" :disabled="offline || !priorityTarget"
                   @click="prioritize">设为优先</el-button>
        <span class="dim-tip">下发 upload_flighttask_media_prioritize,设备优先上传该任务的媒体</span>
      </div>
    </div>

    <!-- 下:媒体文件 -->
    <div class="file-card">
      <div class="toolbar">
        <el-select v-model="filter.isOriginal" clearable placeholder="原始/预览" style="width: 110px" @change="search">
          <el-option label="原始" :value="true" />
          <el-option label="预览" :value="false" />
        </el-select>
        <el-select v-model="filter.flightId" clearable filterable placeholder="归属任务" style="width: 220px" @change="search">
          <el-option v-for="j in jobOptions" :key="j.value" :label="j.label" :value="j.value" />
        </el-select>
        <el-button @click="search">查询</el-button>
        <span class="dim-tip">媒体上传回调入库,每 4s 自动刷新</span>
      </div>

      <el-table :data="rows" v-loading="loading" stripe size="small" height="100%">
        <el-table-column type="index" label="序号" width="50"
                         :index="(i) => (pager.page - 1) * pager.size + i + 1" />
        <el-table-column prop="isOriginal" label="类型" width="70" align="center">
          <template #default="{ row }">
            <el-tag size="small" :type="row.isOriginal ? 'success' : 'info'" effect="plain">
              {{ row.isOriginal ? '原始' : '预览' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="name" label="文件名" min-width="170" show-overflow-tooltip>
          <template #default="{ row }">{{ row.name || '-' }}</template>
        </el-table-column>
        <el-table-column prop="flightId" label="归属任务" width="130" show-overflow-tooltip>
          <template #default="{ row }">
            <span class="mono">{{ row.flightName || (row.flightId || '').slice(0, 8) || '-' }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="subFileType" label="子类型" width="70" align="center">
          <template #default="{ row }">{{ row.subFileType ?? '-' }}</template>
        </el-table-column>
        <el-table-column label="拍摄坐标" width="170">
          <template #default="{ row }">
            <span v-if="row.longitude != null || row.latitude != null" class="mono">
              {{ fmtCoord(row.longitude) }}, {{ fmtCoord(row.latitude) }}
            </span>
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column prop="relativeAltitude" label="高度(m)" width="84" align="right">
          <template #default="{ row }">{{ row.relativeAltitude ?? '-' }}</template>
        </el-table-column>
        <el-table-column prop="gimbalYawDegree" label="云台角(°)" width="88" align="right">
          <template #default="{ row }">{{ row.gimbalYawDegree ?? '-' }}</template>
        </el-table-column>
        <el-table-column prop="takenAt" label="拍摄时间" width="160">
          <template #default="{ row }">{{ row.takenAt || '-' }}</template>
        </el-table-column>
        <el-table-column prop="objectKey" label="对象键" min-width="180" show-overflow-tooltip>
          <template #default="{ row }"><span class="mono">{{ row.objectKey || '-' }}</span></template>
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
import http from '../../../api'

const props = defineProps({ dock: { type: Object, default: null } })

/** 改写优先级是指令下发,机场离线时禁用 */
const offline = computed(() => props.dock?.status !== 'ONLINE')

/* ---------- 上传优先级 ---------- */
const priority = reactive({ flightId: '', updateTime: '' })
const priorityTarget = ref('')
const prioritizing = ref(false)

/* ---------- 最近任务(优先级选择 + 任务筛选共用) ---------- */
const jobOptions = ref([])

/* ---------- 媒体文件 ---------- */
const rows = ref([])
const loading = ref(false)
const filter = reactive({ flightId: '', isOriginal: null })
const pager = reactive({ page: 1, size: 10, total: 0 })

let timer = null
let fetching = false

onMounted(() => { timer = setInterval(() => load(true), 4000) })
onUnmounted(() => clearInterval(timer))

/* 优先级 / 媒体表依赖机场 id,任务下拉依赖 deviceSn,各自到位后拉取 */
watch(() => [props.dock?.id, props.dock?.deviceSn], ([id, sn]) => {
  if (id) { loadPriority(); load() }
  if (sn) loadJobs()
}, { immediate: true })

async function loadPriority() {
  const p = await http.get(`/devices/${props.dock.id}/media/priority`)
  priority.flightId = p?.flightId || ''
  priority.updateTime = p?.updateTime || ''
}

async function loadJobs() {
  const res = await http.get('/wayline-jobs/page', {
    params: { dockSn: props.dock.deviceSn, size: 20 }
  })
  jobOptions.value = (res.rows || []).map((j) => ({
    value: j.flightId,
    label: `${j.waylineName || j.flightId} · ${(j.flightId || '').slice(0, 8)}`
  }))
}

async function prioritize() {
  if (!priorityTarget.value) return
  prioritizing.value = true
  try {
    await http.post(`/devices/${props.dock.id}/media/prioritize`, { flightId: priorityTarget.value })
    ElMessage.success('已下发优先上传指令')
    loadPriority()
  } finally { prioritizing.value = false }
}

/* ---------- 媒体分页 ---------- */
function search() { pager.page = 1; load() }
function onSize() { pager.page = 1; load() }

async function load(silent = false) {
  if (!props.dock?.id || fetching) return
  fetching = true
  if (!silent) loading.value = true
  try {
    const res = await http.get(`/devices/${props.dock.id}/media/page`, {
      params: {
        page: pager.page, size: pager.size,
        flightId: filter.flightId || undefined,
        isOriginal: filter.isOriginal === null || filter.isOriginal === '' ? undefined : filter.isOriginal
      }
    })
    rows.value = res.rows || []
    pager.total = res.total || 0
  } finally { loading.value = false; fetching = false }
}

/** 坐标保留 1 位小数(表列空间有限) */
const fmtCoord = (v) => v === null || v === undefined ? '-' : Number(v).toFixed(1)
</script>

<style scoped>
/* 上优先级卡 + 下媒体表,一屏内展示 */
.media-wrap {
  height: 100%; min-height: 0; overflow: hidden;
  display: flex; flex-direction: column; gap: 10px;
  padding-top: 6px;
}

.prio-card {
  flex-shrink: 0;
  background: #fff; border: 1px solid var(--border); border-radius: 10px;
  padding: 10px 12px;
}
.card-head { display: flex; align-items: center; justify-content: space-between; }
.col-title {
  font-size: 13px; font-weight: 600; color: var(--text);
  padding-left: 8px; border-left: 3px solid var(--primary);
}
.prio-current { display: flex; align-items: center; gap: 10px; }
.prio-label { font-size: 12.5px; color: var(--text-dim); }
.dim-tip { font-size: 11.5px; color: var(--text-faint); }

.toolbar { display: flex; gap: 10px; padding: 8px 0 2px; flex-wrap: wrap; align-items: center; }

.file-card { flex: 1; min-height: 0; display: flex; flex-direction: column; }
.file-card .toolbar { padding: 2px 0 10px; }
.file-card > .el-table { flex: 1; min-height: 0; }
.pager-row { display: flex; justify-content: flex-end; padding: 10px 0 2px; flex-shrink: 0; }

.mono { font-family: 'Consolas', 'Courier New', monospace; font-size: 12px; color: var(--primary); }
</style>
