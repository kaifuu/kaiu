<template>
  <div class="firmware-wrap">
    <!-- 左:固件库 -->
    <div class="col-card">
      <div class="col-head">
        <span class="col-title">固件库</span>
        <el-button size="small" type="primary" @click="openDialog()">新增固件</el-button>
      </div>
      <el-table :data="firmwares" v-loading="fwLoading" stripe size="small" height="100%">
        <el-table-column prop="productType" label="类型" width="64">
          <template #default="{ row }">
            <el-tag size="small" :type="dictTag(DEVICE_TYPE, row.productType)" effect="plain">
              {{ dictLabel(DEVICE_TYPE, row.productType) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="version" label="版本" width="92" show-overflow-tooltip>
          <template #default="{ row }"><span class="mono">{{ row.version }}</span></template>
        </el-table-column>
        <el-table-column prop="deviceModel" label="适配机型" width="104" show-overflow-tooltip>
          <template #default="{ row }">{{ row.deviceModel || '-' }}</template>
        </el-table-column>
        <el-table-column prop="fileName" label="文件名" min-width="130" show-overflow-tooltip />
        <el-table-column prop="fileSize" label="大小" width="82" align="right">
          <template #default="{ row }">{{ fmtSize(row.fileSize) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="178" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" size="small" :disabled="offline" @click="openDeploy(row)">下发升级</el-button>
            <el-button link type="primary" size="small" @click="openDialog(row)">编辑</el-button>
            <el-popconfirm title="确认删除该固件?" @confirm="removeFirmware(row.id)">
              <template #reference>
                <el-button link type="danger" size="small">删除</el-button>
              </template>
            </el-popconfirm>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <!-- 右:升级任务 -->
    <div class="col-card">
      <div class="toolbar">
        <el-select v-model="taskStatus" clearable placeholder="任务状态" style="width: 130px"
                   @change="searchTasks">
          <el-option v-for="o in dictOptions(FIRMWARE_TASK_STATUS)" :key="o.value" :label="o.label" :value="o.value" />
        </el-select>
        <el-button @click="searchTasks">查询</el-button>
        <span class="dim-tip">任务每 4s 自动刷新</span>
      </div>

      <el-table :data="tasks" v-loading="taskLoading" stripe size="small" height="100%">
        <el-table-column type="index" label="序号" width="50"
                         :index="(i) => (taskPager.page - 1) * taskPager.size + i + 1" />
        <el-table-column prop="deviceName" label="设备" min-width="110" show-overflow-tooltip>
          <template #default="{ row }">{{ row.deviceName || '-' }}</template>
        </el-table-column>
        <el-table-column prop="deviceSn" label="序列号" width="160" show-overflow-tooltip>
          <template #default="{ row }"><span class="mono">{{ row.deviceSn }}</span></template>
        </el-table-column>
        <el-table-column prop="firmwareVersion" label="目标版本" width="96" show-overflow-tooltip>
          <template #default="{ row }"><span class="mono">{{ row.firmwareVersion || '-' }}</span></template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="92">
          <template #default="{ row }">
            <el-tag size="small" :type="dictTag(FIRMWARE_TASK_STATUS, row.status)" effect="plain">
              {{ dictLabel(FIRMWARE_TASK_STATUS, row.status) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="progress" label="进度" width="140">
          <template #default="{ row }">
            <el-progress :percentage="progressOf(row)" :stroke-width="6"
                         :color="row.status === 'FAILED' ? '#f04438' : row.status === 'SUCCESS' ? '#12b76a' : undefined" />
          </template>
        </el-table-column>
        <el-table-column prop="message" label="说明" min-width="130" show-overflow-tooltip>
          <template #default="{ row }">{{ row.message || '-' }}</template>
        </el-table-column>
        <el-table-column prop="createTime" label="下发时间" width="158" />
      </el-table>

      <div class="pager-row">
        <el-pagination background layout="total, sizes, prev, pager, next" :total="taskPager.total"
                       v-model:current-page="taskPager.page" v-model:page-size="taskPager.size"
                       :page-sizes="[10, 20, 50]" @current-change="loadTasks" @size-change="onSize" />
      </div>
    </div>

    <!-- 新增 / 编辑固件 -->
    <el-dialog v-model="dialog.visible" :title="dialog.form.id ? '编辑固件' : '新增固件'" width="560px"
               destroy-on-close>
      <el-form :model="dialog.form" label-width="96px">
        <el-row :gutter="12">
          <el-col :span="12">
            <el-form-item label="固件类型" required>
              <el-select v-model="dialog.form.productType" style="width: 100%">
                <el-option v-for="o in dictOptions(DEVICE_TYPE)" :key="o.value" :label="o.label" :value="o.value" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="适配机型" required>
              <el-input v-model="dialog.form.deviceModel" placeholder="如 DJI Dock 2" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="12">
          <el-col :span="12">
            <el-form-item label="版本号" required>
              <el-input v-model="dialog.form.version" placeholder="如 v1.2.3" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="大小(字节)" required>
              <el-input-number v-model="dialog.form.fileSize" :min="0" :step="1048576" style="width: 100%" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="文件名" required>
          <el-input v-model="dialog.form.fileName" placeholder="如 dock_v1.2.3.tar" />
        </el-form-item>
        <el-form-item label="MD5">
          <el-input v-model="dialog.form.fileMd5" placeholder="升级包校验值" />
        </el-form-item>
        <el-form-item label="下载地址" required>
          <el-input v-model="dialog.form.fileUrl" placeholder="http(s):// 或对象存储地址" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="dialog.form.remark" type="textarea" :rows="2" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialog.visible = false">取消</el-button>
        <el-button type="primary" :loading="dialog.saving" @click="save">保存</el-button>
      </template>
    </el-dialog>

    <!-- 下发升级 -->
    <el-dialog v-model="deployDialog.visible" :title="`下发升级 · ${deployDialog.firmware?.version || ''}`"
               width="480px" destroy-on-close>
      <div class="deploy-meta" v-if="deployDialog.firmware">
        <el-tag size="small" :type="dictTag(DEVICE_TYPE, deployDialog.firmware.productType)" effect="plain">
          {{ dictLabel(DEVICE_TYPE, deployDialog.firmware.productType) }}
        </el-tag>
        <span>{{ deployDialog.firmware.deviceModel }}</span>
        <span class="dim">{{ fmtSize(deployDialog.firmware.fileSize) }}</span>
      </div>
      <el-select v-model="deployDialog.deviceIds" multiple filterable placeholder="选择要升级的设备"
                 v-loading="deployDialog.loadingDevices" style="width: 100%">
        <el-option v-for="d in devices" :key="d.id" :label="`${d.name}(${d.deviceSn})`" :value="d.id" />
      </el-select>
      <el-alert type="warning" :closable="false" class="deploy-tip"
                title="升级过程中设备会自动重启并短暂离线,请在空闲时段执行" />
      <template #footer>
        <el-button @click="deployDialog.visible = false">取消</el-button>
        <el-button type="primary" :loading="deployDialog.saving" :disabled="offline" @click="confirmDeploy">
          下发升级
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted, onUnmounted } from 'vue'
import { ElMessage } from 'element-plus'
import http from '../../../api'
import { DEVICE_TYPE, FIRMWARE_TASK_STATUS, dictLabel, dictTag, dictOptions } from '../../../utils/dict'

const props = defineProps({ dock: { type: Object, default: null } })

/** 机场离线时禁用下发类操作 */
const offline = computed(() => props.dock?.status !== 'ONLINE')

/* ---------- 固件库 ---------- */
const firmwares = ref([])
const fwLoading = ref(false)
const dialog = reactive({ visible: false, saving: false, form: {} })

/* ---------- 升级任务 ---------- */
const tasks = ref([])
const taskLoading = ref(false)
const taskStatus = ref('')
const taskPager = reactive({ page: 1, size: 10, total: 0 })

/* ---------- 下发升级 ---------- */
const devices = ref([])
const deployDialog = reactive({ visible: false, saving: false, loadingDevices: false, firmware: null, deviceIds: [] })

const progressOf = (row) => Math.min(100, Math.max(0, Number(row.progress) || 0))

/** 字节 → MB 展示 */
function fmtSize(bytes) {
  if (bytes === null || bytes === undefined) return '-'
  return (bytes / 1024 / 1024).toFixed(2) + ' MB'
}

let timer = null
let taskFetching = false

onMounted(() => {
  loadFirmwares()
  loadTasks()
  timer = setInterval(() => loadTasks(true), 4000)
})
onUnmounted(() => clearInterval(timer))

async function loadFirmwares() {
  fwLoading.value = true
  try {
    firmwares.value = await http.get('/firmwares') || []
  } finally { fwLoading.value = false }
}

function searchTasks() { taskPager.page = 1; loadTasks() }
function onSize() { taskPager.page = 1; loadTasks() }

async function loadTasks(silent = false) {
  if (taskFetching) return
  taskFetching = true
  if (!silent) taskLoading.value = true
  try {
    const res = await http.get('/firmware-tasks/page', {
      params: {
        page: taskPager.page, size: taskPager.size,
        status: taskStatus.value || undefined
      }
    })
    tasks.value = res.rows || []
    taskPager.total = res.total || 0
  } finally { taskLoading.value = false; taskFetching = false }
}

/* ---------- 固件维护 ---------- */
function openDialog(row) {
  dialog.form = row
    ? {
        id: row.id, productType: row.productType, deviceModel: row.deviceModel || '',
        version: row.version || '', fileName: row.fileName || '', fileSize: row.fileSize ?? 0,
        fileMd5: row.fileMd5 || '', fileUrl: row.fileUrl || '', remark: row.remark || ''
      }
    : { id: null, productType: 'DOCK', deviceModel: '', version: '', fileName: '',
        fileSize: 0, fileMd5: '', fileUrl: '', remark: '' }
  dialog.visible = true
}

async function save() {
  const f = dialog.form
  if (!f.productType || !f.deviceModel || !f.version || !f.fileName || !f.fileUrl) {
    return ElMessage.warning('请填写完整的固件信息')
  }
  dialog.saving = true
  try {
    if (f.id) await http.put(`/firmwares/${f.id}`, f)
    else await http.post('/firmwares', f)
    ElMessage.success('固件已保存')
    dialog.visible = false
    loadFirmwares()
  } finally { dialog.saving = false }
}

async function removeFirmware(id) {
  await http.delete(`/firmwares/${id}`)
  ElMessage.success('固件已删除')
  loadFirmwares()
}

/* ---------- 下发升级 ---------- */
function openDeploy(row) {
  deployDialog.firmware = row
  deployDialog.deviceIds = []
  deployDialog.visible = true
  loadDevices()
}

/** 全量设备:机场与飞行器都可作为升级对象 */
async function loadDevices() {
  if (devices.value.length) return
  deployDialog.loadingDevices = true
  try {
    devices.value = await http.get('/devices') || []
  } finally { deployDialog.loadingDevices = false }
}

async function confirmDeploy() {
  if (!deployDialog.deviceIds.length) return ElMessage.warning('请选择要升级的设备')
  deployDialog.saving = true
  try {
    const created = await http.post(`/firmwares/${deployDialog.firmware.id}/deploy`, {
      deviceIds: deployDialog.deviceIds
    })
    ElMessage.success(`已下发 ${created?.length ?? deployDialog.deviceIds.length} 个升级任务`)
    deployDialog.visible = false
    taskPager.page = 1
    loadTasks()
  } finally { deployDialog.saving = false }
}
</script>

<style scoped>
/* 左固件库 + 右升级任务 */
.firmware-wrap {
  height: 100%; min-height: 0; overflow: hidden;
  display: grid; grid-template-columns: minmax(0, 23fr) minmax(0, 27fr); grid-template-rows: 100%; gap: 12px;
  background: #f6f9fd; border: 1px solid var(--border); border-radius: 12px; padding: 12px;
}
@media (max-width: 1280px) {
  .firmware-wrap { grid-template-columns: 1fr; grid-template-rows: minmax(240px, 44%) minmax(0, 56%); overflow-y: auto; }
}

.col-card {
  min-width: 0; min-height: 0;
  display: flex; flex-direction: column;
  background: #fff; border: 1px solid #dbe7f8; border-radius: 10px;
  padding: 10px 10px 8px;
}
.col-card > .el-table { flex: 1; min-height: 0; }
.col-head { display: flex; align-items: center; justify-content: space-between; padding-bottom: 8px; }
.col-title {
  font-size: 13px; font-weight: 600; color: var(--text);
  padding-left: 8px; border-left: 3px solid var(--primary);
}
.toolbar { display: flex; gap: 10px; padding: 2px 0 10px; flex-wrap: wrap; align-items: center; }
.dim-tip { font-size: 11.5px; color: var(--text-faint); }
.pager-row { display: flex; justify-content: flex-end; padding: 10px 0 2px; flex-shrink: 0; }

.mono { font-family: 'Consolas', 'Courier New', monospace; font-size: 12px; color: var(--primary); }

.deploy-meta { display: flex; align-items: center; gap: 8px; font-size: 12.5px; color: var(--text-dim); margin-bottom: 10px; }
.deploy-tip { margin-top: 12px; }
</style>
