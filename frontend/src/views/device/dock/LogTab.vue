<template>
  <div class="log-wrap">
    <div class="toolbar">
      <el-select v-model="moduleFilter" clearable placeholder="日志归属" style="width: 130px">
        <el-option v-for="o in dictOptions(DEVICE_TYPE)" :key="o.value" :label="o.label" :value="o.value" />
      </el-select>
      <el-button type="primary" :loading="syncing" :disabled="offline" @click="syncList">拉取日志列表</el-button>
      <el-button :loading="uploading" :disabled="offline || !selection.length" @click="uploadSelected">
        上传所选{{ selection.length ? `(${selection.length})` : '' }}
      </el-button>
      <span class="dim-tip" v-if="pollingOn">上传进行中,2s 自动刷新…</span>
    </div>

    <el-table :data="filteredRows" v-loading="loading" stripe size="small" height="100%"
              :row-key="(r) => r.id" @selection-change="onSelectionChange">
      <el-table-column type="selection" width="42" reserve-selection
                       :selectable="(row) => row.status !== 'UPLOADING'" />
      <el-table-column prop="name" label="文件名" min-width="260" show-overflow-tooltip />
      <el-table-column prop="module" label="归属" width="76">
        <template #default="{ row }">
          <el-tag v-if="row.module" size="small" :type="dictTag(DEVICE_TYPE, row.module)" effect="plain">
            {{ dictLabel(DEVICE_TYPE, row.module) }}
          </el-tag>
          <span v-else>-</span>
        </template>
      </el-table-column>
      <el-table-column prop="size" label="大小" width="90" align="right">
        <template #default="{ row }">{{ fmtKb(row.size) }}</template>
      </el-table-column>
      <el-table-column prop="fileTime" label="日志时间" width="158">
        <template #default="{ row }">{{ row.fileTime || '-' }}</template>
      </el-table-column>
      <el-table-column prop="status" label="状态" width="86">
        <template #default="{ row }">
          <el-tag size="small" :type="dictTag(DEVICE_LOG_STATUS, row.status)" effect="plain">
            {{ dictLabel(DEVICE_LOG_STATUS, row.status) }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="进度 / 位置" min-width="170">
        <template #default="{ row }">
          <el-progress v-if="row.status === 'UPLOADING'" :percentage="percentOf(row)" :stroke-width="6" />
          <span v-else-if="row.status === 'UPLOADED'" class="obj-key" :title="row.objectKey">
            {{ row.objectKey || '-' }}
          </span>
          <span v-else class="dim">-</span>
        </template>
      </el-table-column>
    </el-table>

    <div class="stat-row">
      <span>共 {{ filteredRows.length }} 个文件 · 已上传 {{ uploadedCount }}</span>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onUnmounted, watch } from 'vue'
import { ElMessage } from 'element-plus'
import http from '../../../api'
import { DEVICE_TYPE, DEVICE_LOG_STATUS, dictLabel, dictTag, dictOptions } from '../../../utils/dict'

const props = defineProps({ dock: { type: Object, default: null } })

/** 拉取/上传都是下发类操作,机场离线时禁用 */
const offline = computed(() => props.dock?.status !== 'ONLINE')

const rows = ref([])
const loading = ref(false)
const moduleFilter = ref('')
const selection = ref([])
const syncing = ref(false)
const uploading = ref(false)
const pollingOn = ref(false)

/** module 为本地筛选,不打后端 */
const filteredRows = computed(() =>
  rows.value.filter((r) => !moduleFilter.value || r.module === moduleFilter.value))
const uploadedCount = computed(() => filteredRows.value.filter((r) => r.status === 'UPLOADED').length)

let timer = null
let syncTimer = null
let fetching = false

onUnmounted(() => {
  clearInterval(timer)
  clearTimeout(syncTimer)
})

watch(() => props.dock?.id, (id) => { if (id) load() }, { immediate: true })

async function load(silent = false) {
  if (!props.dock?.id || fetching) return
  fetching = true
  if (!silent) loading.value = true
  try {
    rows.value = await http.get(`/devices/${props.dock.id}/logs`) || []
    schedulePoll()
  } finally { loading.value = false; fetching = false }
}

/** 仅当存在上传中文件时 2s 轮询,否则停止 */
function schedulePoll() {
  const uploadingNow = rows.value.some((r) => r.status === 'UPLOADING')
  pollingOn.value = uploadingNow
  if (uploadingNow && !timer) timer = setInterval(() => load(true), 2000)
  if (!uploadingNow && timer) { clearInterval(timer); timer = null }
}

function onSelectionChange(val) { selection.value = val }

/** 拉取日志列表:设备异步回传,1.5s 后刷新查看结果 */
async function syncList() {
  syncing.value = true
  try {
    await http.post(`/devices/${props.dock.id}/logs/sync`)
    ElMessage.success('已发起日志列表拉取')
    clearTimeout(syncTimer)
    syncTimer = setTimeout(() => load(), 1500)
  } finally { syncing.value = false }
}

async function uploadSelected() {
  if (!selection.value.length) return
  uploading.value = true
  try {
    await http.post(`/devices/${props.dock.id}/logs/upload`, {
      fileIds: selection.value.map((r) => r.fileId)
    })
    ElMessage.success(`已发起 ${selection.value.length} 个日志文件上传`)
    load()
  } finally { uploading.value = false }
}

const fmtKb = (bytes) => {
  if (bytes === null || bytes === undefined) return '-'
  return (bytes / 1024).toFixed(1) + ' KB'
}
const percentOf = (row) => Math.min(100, Math.max(0, Number(row.percent) || 0))
</script>

<style scoped>
.log-wrap {
  height: 100%; min-height: 0; overflow: hidden;
  display: flex; flex-direction: column;
  padding-top: 6px;
}
.toolbar { display: flex; gap: 10px; padding: 4px 0 12px; flex-wrap: wrap; align-items: center; }
.dim-tip { font-size: 11.5px; color: var(--primary); }

.log-wrap .el-table { flex: 1; min-height: 0; }

.stat-row {
  flex-shrink: 0; display: flex; justify-content: flex-end;
  padding: 10px 2px 2px; font-size: 12.5px; color: var(--text-dim);
}

.obj-key {
  font-family: 'Consolas', 'Courier New', monospace; font-size: 11.5px; color: var(--text-dim);
  display: inline-block; max-width: 100%;
  overflow: hidden; text-overflow: ellipsis; white-space: nowrap; vertical-align: middle;
}
.dim { color: var(--text-faint); }
</style>
