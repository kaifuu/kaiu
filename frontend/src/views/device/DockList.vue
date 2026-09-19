<template>
  <div class="page">
    <div class="page-header">
      <span class="page-title">机场管理</span>
      <div class="actions">
        <el-button type="primary" @click="openDialog()">新增机场</el-button>
      </div>
    </div>

    <div class="stats">
      <div class="stat"><span class="stat-label">机场总数</span><b class="stat-value">{{ stats.total ?? 0 }}</b></div>
      <div class="stat"><span class="stat-label">在线</span><b class="stat-value online">{{ onlineCount }}</b></div>
      <div class="stat"><span class="stat-label">离线</span><b class="stat-value offline">{{ (stats.total ?? 0) - onlineCount }}</b></div>
      <div class="stat"><span class="stat-label">挂载无人机</span><b class="stat-value">{{ stats.droneTotal ?? 0 }}</b></div>
      <div class="stat"><span class="stat-label">MQTT 连接</span><b class="stat-value">{{ stats.mqttOnline ?? 0 }}</b></div>
    </div>

    <div class="panel table-panel">
      <div class="toolbar">
        <el-input v-model="keyword" placeholder="名称 / 序列号 / 机型" clearable style="width: 220px" @keyup.enter="search">
          <template #prefix><el-icon><Search /></el-icon></template>
        </el-input>
        <el-select v-model="statusFilter" clearable placeholder="在线状态" style="width: 130px">
          <el-option v-for="o in dictOptions(DEVICE_STATUS)" :key="o.value" :label="o.label" :value="o.value" />
        </el-select>
        <el-button @click="search">查询</el-button>
      </div>

      <el-table :data="rows" v-loading="loading" stripe height="100%" @sort-change="onSort">
        <el-table-column type="index" label="序号" width="56" :index="seq" />
        <el-table-column prop="name" label="机场名称" min-width="170" sortable="custom" show-overflow-tooltip>
          <template #default="{ row }"><span class="name">{{ row.name }}</span></template>
        </el-table-column>
        <el-table-column prop="deviceSn" label="序列号" width="180" sortable="custom" show-overflow-tooltip>
          <template #default="{ row }"><span class="code">{{ row.deviceSn }}</span></template>
        </el-table-column>
        <el-table-column prop="deviceModel" label="机型" width="130" show-overflow-tooltip>
          <template #default="{ row }">{{ row.deviceModel || '-' }}</template>
        </el-table-column>
        <el-table-column label="挂载无人机" width="120">
          <template #default="{ row }">
            <el-tag v-if="row.subDeviceCount" size="small" effect="plain">{{ row.subDeviceCount }} 台</el-tag>
            <span v-else class="dim">未挂载</span>
          </template>
        </el-table-column>
        <el-table-column prop="firmwareVersion" label="固件版本" width="110">
          <template #default="{ row }">{{ row.firmwareVersion || '-' }}</template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="90" sortable="custom">
          <template #default="{ row }">
            <el-tag size="small" :type="dictTag(DEVICE_STATUS, row.status)" effect="plain">
              {{ dictLabel(DEVICE_STATUS, row.status) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="lastOnlineAt" label="最近在线" width="155" sortable="custom">
          <template #default="{ row }">{{ row.lastOnlineAt || '-' }}</template>
        </el-table-column>
        <el-table-column label="操作" width="200" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="$router.push(`/docks/${row.id}`)">控制</el-button>
            <el-button link type="primary" size="small" @click="openDialog(row)">编辑</el-button>
            <el-popconfirm title="确认删除该机场?" @confirm="remove(row.id)">
              <template #reference>
                <el-button link type="danger" size="small">删除</el-button>
              </template>
            </el-popconfirm>
          </template>
        </el-table-column>
      </el-table>

      <div class="pager-row">
        <el-pagination background layout="total, sizes, prev, pager, next" :total="pager.total"
                       v-model:current-page="pager.page" v-model:page-size="pager.size"
                       :page-sizes="[10, 20, 50]" @current-change="load" @size-change="onSizeChange" />
      </div>
    </div>

    <el-drawer v-model="dialog.visible" :title="dialog.form.id ? '编辑机场' : '新增机场'" direction="rtl" size="520px">
      <el-form :model="dialog.form" label-width="100px">
        <el-form-item label="机场名称" required>
          <el-input v-model="dialog.form.name" placeholder="如 潮白河巡检机场" />
        </el-form-item>
        <el-form-item label="设备序列号" required>
          <el-input v-model="dialog.form.deviceSn" :disabled="!!dialog.form.id"
                    placeholder="与机场上报的 SN 完全一致" />
        </el-form-item>
        <el-form-item label="机型">
          <el-input v-model="dialog.form.deviceModel" placeholder="如 DJI Dock 2" />
        </el-form-item>
        <el-form-item label="固件版本">
          <el-input v-model="dialog.form.firmwareVersion" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="dialog.form.remark" type="textarea" :rows="2" />
        </el-form-item>
        <el-alert type="info" :closable="false"
                  title="机场是网关设备,无人机挂载在其下;机场接入后会自动上报拓扑,未登记的序列号也会被自动纳管。" />
      </el-form>
      <template #footer>
        <el-button @click="dialog.visible = false">取消</el-button>
        <el-button type="primary" :loading="dialog.saving" @click="save">保存</el-button>
      </template>
    </el-drawer>
  </div>
</template>

<script setup>
import { ref, reactive, computed, watch, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { Search } from '@element-plus/icons-vue'
import http from '../../api'
import { DEVICE_STATUS, dictLabel, dictTag, dictOptions } from '../../utils/dict'

const loading = ref(false)
const keyword = ref('')
const statusFilter = ref('')
const rows = ref([])
const stats = reactive({})
const pager = reactive({ page: 1, size: 10, total: 0 })
const sort = reactive({ sortBy: '', direction: '' })
const dialog = reactive({ visible: false, saving: false, form: {} })

const onlineCount = computed(() => rows.value.filter((r) => r.status === 'ONLINE').length)

function seq(i) { return (pager.page - 1) * pager.size + i + 1 }
function onSort({ prop, order }) {
  sort.sortBy = order ? prop : ''
  sort.direction = order === 'ascending' ? 'asc' : 'desc'
  pager.page = 1
  load()
}

onMounted(load)

async function load() {
  loading.value = true
  try {
    const [res, st] = await Promise.all([
      http.get('/devices/page', {
        params: {
          page: pager.page, size: pager.size, deviceType: 'DOCK',
          sortBy: sort.sortBy || undefined,
          direction: sort.sortBy ? sort.direction : undefined,
          keyword: keyword.value || undefined,
          status: statusFilter.value || undefined
        }
      }),
      http.get('/devices/stats')
    ])
    rows.value = res.rows || []
    pager.total = res.total || 0
    Object.assign(stats, st)
  } finally { loading.value = false }
}

watch(statusFilter, () => { pager.page = 1; load() })
function search() { pager.page = 1; load() }
function onSizeChange() { pager.page = 1; load() }

function openDialog(row) {
  dialog.form = row
    ? { id: row.id, name: row.name, deviceSn: row.deviceSn, deviceModel: row.deviceModel,
        firmwareVersion: row.firmwareVersion, remark: row.remark, deviceType: 'DOCK' }
    : { id: null, name: '', deviceSn: '', deviceModel: 'DJI Dock 2', firmwareVersion: '', remark: '', deviceType: 'DOCK' }
  dialog.visible = true
}

async function save() {
  const f = dialog.form
  if (!f.name || !f.deviceSn) return ElMessage.warning('名称与序列号不能为空')
  dialog.saving = true
  try {
    if (f.id) await http.put(`/devices/${f.id}`, f)
    else await http.post('/devices', f)
    ElMessage.success('已保存')
    dialog.visible = false
    load()
  } finally { dialog.saving = false }
}

async function remove(id) {
  await http.delete(`/devices/${id}`)
  ElMessage.success('已删除')
  load()
}
</script>

<style scoped>
.stats { display: grid; grid-template-columns: repeat(5, 1fr); gap: 10px; margin-bottom: 12px; }
.stat {
  display: flex; align-items: center; justify-content: space-between;
  padding: 10px 14px; border-radius: 10px;
  background: #fff; border: 1px solid var(--border);
}
.stat-label { font-size: 12.5px; color: var(--text-dim); }
.stat-value { font-size: 19px; font-weight: 700; color: var(--text); }
.stat-value.online { color: #12b76a; }
.stat-value.offline { color: #98a2b3; }

.table-panel { height: calc(100% - 122px); padding: 8px; display: flex; flex-direction: column; }
.toolbar { display: flex; gap: 10px; padding: 8px 8px 12px; flex-wrap: wrap; }
.toolbar + .el-table { flex: 1; }
.pager-row { display: flex; justify-content: flex-end; padding: 10px 4px 2px; }
.actions { display: flex; gap: 10px; }
.name { font-weight: 600; }
.code { font-family: monospace; font-size: 12.5px; color: var(--primary); }
.dim { color: var(--text-faint); font-size: 12.5px; }
</style>
