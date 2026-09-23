<template>
  <div class="page">
    <div class="page-header">
      <span class="page-title">机场管理</span>
      <div class="actions">
        <el-button type="primary" @click="openDialog()">新增机场</el-button>
      </div>
    </div>

    <div class="stats-row">
      <div class="stat-card"><span class="num">{{ stats.dockTotal ?? 0 }}</span><span class="lbl">机场总数</span></div>
      <div class="stat-card ok"><span class="num">{{ stats.online ?? 0 }}</span><span class="lbl">在线</span></div>
      <div class="stat-card off"><span class="num">{{ stats.offline ?? 0 }}</span><span class="lbl">离线</span></div>
      <div class="stat-card fly"><span class="num">{{ stats.droneMounted ?? 0 }}</span><span class="lbl">已挂载无人机</span></div>
      <div class="stat-card warn"><span class="num">{{ stats.droneUnmounted ?? 0 }}</span><span class="lbl">未挂载无人机</span></div>
    </div>

    <div class="panel table-panel">
      <div class="toolbar">
        <el-input v-model="keyword" placeholder="名称 / 序列号 / 机型" clearable style="width: 200px" @keyup.enter="search" />
        <el-select v-model="statusFilter" clearable placeholder="在线状态" style="width: 120px">
          <el-option v-for="o in dictOptions(DEVICE_STATUS)" :key="o.value" :label="o.label" :value="o.value" />
        </el-select>
        <el-button @click="search">查询</el-button>
      </div>

      <el-table :data="rows" v-loading="loading" stripe height="100%" @sort-change="onSort">
        <el-table-column type="index" label="序号" width="56" :index="seq" />
        <el-table-column label="图标" width="54" align="center">
          <template #default="{ row }">
            <img class="row-icon" :src="resolveDeviceIcon(row, { online: row.status === 'ONLINE' })"
                 :class="{ custom: !!customDeviceIcon(row) }" title="设备图标" />
          </template>
        </el-table-column>
        <el-table-column prop="name" label="名称" min-width="110" sortable="custom" show-overflow-tooltip />
        <el-table-column prop="deviceSn" label="序列号" width="144" sortable="custom" show-overflow-tooltip>
          <template #default="{ row }"><span class="code">{{ row.deviceSn }}</span></template>
        </el-table-column>
        <el-table-column prop="manufacturer" label="厂商" width="62" show-overflow-tooltip>
          <template #default="{ row }">{{ row.manufacturer || '-' }}</template>
        </el-table-column>
        <el-table-column prop="deviceModel" label="机型" width="108" show-overflow-tooltip>
          <template #default="{ row }">{{ row.deviceModel || '-' }}</template>
        </el-table-column>
        <el-table-column label="部署位置" width="146">
          <template #default="{ row }">
            <span v-if="row.homeLng != null" class="coord">{{ Number(row.homeLng).toFixed(4) }}, {{ Number(row.homeLat).toFixed(4) }}</span>
            <span v-else class="unbound">未标定</span>
          </template>
        </el-table-column>
        <el-table-column label="挂载无人机" width="94">
          <template #default="{ row }">
            <el-tooltip v-if="mountedNames(row.deviceSn).length" :content="mountedNames(row.deviceSn).join('、')" placement="top">
              <span class="mount">{{ mountedNames(row.deviceSn).length }} 台</span>
            </el-tooltip>
            <span v-else class="unbound">无</span>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="76">
          <template #default="{ row }">
            <span class="status" :class="'ds-' + deriveStatus(row).toLowerCase()"><i />{{ statusLabel(deriveStatus(row)) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="接入" width="70">
          <template #default="{ row }">
            <el-tag size="small" :type="row.virtual ? 'info' : 'success'" effect="plain">
              {{ row.virtual ? '虚拟' : '真机' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="启停" width="62">
          <template #default="{ row }">
            <el-switch :model-value="row.enabled !== false" size="small"
                       title="停用后拒绝该设备接入" @change="(v) => toggleEnabled(row, v)" />
          </template>
        </el-table-column>
        <el-table-column prop="lastOnlineAt" label="最近在线" width="148" sortable="custom">
          <template #default="{ row }">{{ fmt(row.lastOnlineAt) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="200" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="$router.push(`/docks/${row.id}`)">控制台</el-button>
            <el-button link type="success" size="small" @click="openDetail(row)">遥测</el-button>
            <el-button link type="primary" size="small" @click="openDialog(row)">编辑</el-button>
            <el-popconfirm title="确认删除该机场?(挂载无人机时将被拒绝)" @confirm="remove(row.id)">
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

    <el-drawer v-model="dialog.visible" :title="dialog.form.id ? '编辑机场' : '新增机场'" direction="rtl" size="640px">
      <el-form :model="dialog.form" label-width="100px">
        <el-row :gutter="12">
          <el-col :span="12">
            <el-form-item label="机场名称" required>
              <el-input v-model="dialog.form.name" placeholder="如 潮白河巡检机场" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="序列号" required>
              <el-input v-model="dialog.form.deviceSn" :disabled="!!dialog.form.id"
                        placeholder="与机场上报的 SN 完全一致" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="12">
          <el-col :span="12">
            <el-form-item label="厂商">
              <el-input v-model="dialog.form.manufacturer" placeholder="如 大疆" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="机型">
              <el-input v-model="dialog.form.deviceModel" placeholder="如 DJI Dock 2" />
            </el-form-item>
          </el-col>
        </el-row>

        <el-divider content-position="left">部署位置(机场静态坐标,地图与围栏判定按此落点)</el-divider>
        <el-row :gutter="12">
          <el-col :span="12">
            <el-form-item label="部署经度">
              <el-input-number v-model="dialog.form.homeLng" :precision="6" :step="0.001"
                               :min="-180" :max="180" :controls="false" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="部署纬度">
              <el-input-number v-model="dialog.form.homeLat" :precision="6" :step="0.001"
                               :min="-90" :max="90" :controls="false" style="width: 100%" />
            </el-form-item>
          </el-col>
        </el-row>

        <el-divider content-position="left">台账信息</el-divider>
        <el-row :gutter="12">
          <el-col :span="12">
            <el-form-item label="固件版本">
              <el-input v-model="dialog.form.firmwareVersion" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="启停">
              <el-switch v-model="dialog.form.enabled" active-text="启用" inactive-text="停用" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="地图图标">
          <div class="icon-field">
            <div class="preset-grid">
              <div v-for="p in ICON_PRESETS" :key="p.key" class="preset-item"
                   :class="{ active: dialog.form.icon === 'preset:' + p.key }"
                   :title="p.label" @click="dialog.form.icon = 'preset:' + p.key">
                <img :src="deviceSvg(p.key, { online: true })" />
                <span>{{ p.label }}</span>
              </div>
            </div>
            <div class="icon-custom">
              <img class="icon-preview" :src="resolveDeviceIcon(dialog.form, { online: true })"
                   :class="{ custom: !!customDeviceIcon(dialog.form) }" />
              <el-upload :auto-upload="false" :show-file-list="false" accept="image/*" :on-change="onIconChange">
                <el-button size="small">上传图片</el-button>
              </el-upload>
              <el-button size="small" :disabled="!dialog.form.icon" @click="dialog.form.icon = ''">恢复默认</el-button>
            </div>
          </div>
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="dialog.form.remark" type="textarea" :rows="2" />
        </el-form-item>
        <el-alert type="info" :closable="false"
                  title="机场是网关设备,无人机挂载在其下;机场接入后会自动上报拓扑,未登记的序列号也会被自动纳管。在线状态由 MQTT 连接维护,不在此手工设置。" />
      </el-form>
      <template #footer>
        <el-button @click="dialog.visible = false">取消</el-button>
        <el-button type="primary" :loading="dialog.saving" @click="save">保存</el-button>
      </template>
    </el-drawer>

    <DeviceInfoDialog v-model="detail.visible" :device="detail.device" />
  </div>
</template>

<script setup>
import { ref, reactive, watch, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import http from '../../api'
import { DEVICE_STATUS, dictOptions } from '../../utils/dict'
import { resolveDeviceIcon, customDeviceIcon, deviceSvg, ICON_PRESETS, deriveStatus, statusLabel } from '../../utils/deviceIcon'
import DeviceInfoDialog from '../../components/DeviceInfoDialog.vue'

const loading = ref(false)
const keyword = ref('')
const statusFilter = ref('')
const rows = ref([])
const drones = ref([])
const stats = reactive({})
const pager = reactive({ page: 1, size: 10, total: 0 })
const sort = reactive({ sortBy: '', direction: '' })
const dialog = reactive({ visible: false, saving: false, form: {} })
const detail = reactive({ visible: false, device: null })

/** 某机场下挂载的无人机名称(悬停展示;上云 API 中无人机是机场子设备) */
const mountedNames = (dockSn) =>
  drones.value.filter((d) => d.gatewaySn === dockSn).map((d) => d.name || d.deviceSn)

function seq(i) { return (pager.page - 1) * pager.size + i + 1 }
function onSort({ prop, order }) {
  sort.sortBy = order ? prop : ''
  sort.direction = order === 'ascending' ? 'asc' : 'desc'
  pager.page = 1
  load()
}

/** 时间戳兼容三态:ISO 串 / "YYYY-MM-DD HH:mm:ss" / epoch 毫秒 */
function fmt(t) {
  if (!t) return '-'
  const d = typeof t === 'number' ? new Date(t) : new Date(String(t).replace(' ', 'T'))
  if (Number.isNaN(d.getTime())) return String(t)
  const p = (n) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())} ${p(d.getHours())}:${p(d.getMinutes())}`
}

onMounted(load)

async function load() {
  loading.value = true
  try {
    const [res, st, dr] = await Promise.all([
      http.get('/devices/page', {
        params: {
          page: pager.page, size: pager.size, deviceType: 'DOCK',
          sortBy: sort.sortBy || undefined,
          direction: sort.sortBy ? sort.direction : undefined,
          keyword: keyword.value || undefined,
          status: statusFilter.value || undefined
        }
      }),
      http.get('/devices/stats'),
      http.get('/devices/drones')
    ])
    rows.value = res.rows || []
    pager.total = res.total || 0
    Object.assign(stats, st)
    drones.value = dr || []
  } finally { loading.value = false }
}

watch(statusFilter, () => { pager.page = 1; load() })
function search() { pager.page = 1; load() }
function onSizeChange() { pager.page = 1; load() }

function openDialog(row) {
  dialog.form = row
    ? { id: row.id, name: row.name, deviceSn: row.deviceSn, deviceModel: row.deviceModel,
        manufacturer: row.manufacturer, firmwareVersion: row.firmwareVersion,
        homeLng: row.homeLng != null ? Number(row.homeLng) : null,
        homeLat: row.homeLat != null ? Number(row.homeLat) : null,
        enabled: row.enabled !== false, icon: row.icon || '',
        remark: row.remark, deviceType: 'DOCK' }
    : { id: null, name: '', deviceSn: '', deviceModel: 'DJI Dock 2', manufacturer: '大疆',
        firmwareVersion: '', homeLng: null, homeLat: null,
        enabled: true, icon: '', remark: '', deviceType: 'DOCK' }
  dialog.visible = true
}

/** 上传自定义地图图标:校验类型/大小后转 dataURL 存表 */
function onIconChange(file) {
  const raw = file.raw
  if (!raw) return
  if (!raw.type.startsWith('image/')) return ElMessage.warning('仅支持图片文件(PNG/SVG/JPG)')
  if (raw.size > 200 * 1024) return ElMessage.warning('图片过大(>200KB),请压缩后上传')
  const reader = new FileReader()
  reader.onload = () => {
    dialog.form.icon = reader.result
    ElMessage.success('图标已载入,保存后生效')
  }
  reader.readAsDataURL(raw)
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

/** 启停:停用后该设备接入被拒绝(后端 markOnline 拦截) */
async function toggleEnabled(row, v) {
  try {
    await http.put(`/devices/${row.id}`, { enabled: v })
    row.enabled = v
    ElMessage.success(v ? `${row.name} 已启用` : `${row.name} 已停用,将拒绝接入`)
  } catch (e) { /* 拦截器已提示;开关由 model-value 回落 */ }
}

function openDetail(row) {
  detail.device = row
  detail.visible = true
}

async function remove(id) {
  await http.delete(`/devices/${id}`)
  ElMessage.success('已删除')
  load()
}
</script>

<style scoped>
.table-panel { height: calc(100% - 50px - 76px); padding: 8px; display: flex; flex-direction: column; }
.actions { display: flex; gap: 10px; align-items: center; }
.toolbar { display: flex; gap: 10px; padding: 8px 8px 12px; }
.toolbar + .el-table { flex: 1; }
.pager-row { display: flex; justify-content: flex-end; padding: 10px 4px 2px; }

.stats-row { display: flex; gap: 12px; margin-bottom: 10px; }
.stat-card {
  flex: 1; background: #fff; border: 1px solid var(--border); border-radius: 10px;
  padding: 10px 16px; display: flex; align-items: baseline; gap: 8px;
}
.stat-card .num { font-size: 22px; font-weight: 700; color: #101828; }
.stat-card .lbl { font-size: 12px; color: #667085; }
.stat-card.ok .num { color: #12b76a; }
.stat-card.fly .num { color: #155eef; }
.stat-card.warn .num { color: #dc6803; }
.stat-card.off .num { color: #98a2b3; }

.code { color: var(--primary); font-size: 13px; font-weight: 600; }
.coord { font-family: Consolas, monospace; font-size: 12px; color: #475467; }
.mount { color: #155eef; font-weight: 600; cursor: default; }
.unbound { color: #98a2b3; }

.row-icon { width: 26px; height: 26px; vertical-align: middle; }
.row-icon.custom { border-radius: 5px; border: 1px solid var(--border); background: #fff; object-fit: contain; }
.icon-field { display: flex; flex-direction: column; gap: 10px; }
.preset-grid { display: flex; gap: 8px; flex-wrap: wrap; }
.preset-item {
  display: flex; flex-direction: column; align-items: center; gap: 2px;
  width: 62px; padding: 6px 2px 4px; cursor: pointer;
  border: 1.5px solid var(--border); border-radius: 9px; background: #fff;
  transition: all .15s;
}
.preset-item img { width: 32px; height: 32px; }
.preset-item span { font-size: 11px; color: var(--text-dim); }
.preset-item:hover { border-color: #b8ccf7; }
.preset-item.active { border-color: var(--primary); background: #f0f5ff; box-shadow: 0 0 0 2px rgba(21, 94, 239, .12); }
.icon-custom { display: flex; align-items: center; gap: 10px; flex-wrap: wrap; }
.icon-preview { width: 40px; height: 40px; }
.icon-preview.custom { border-radius: 8px; border: 1px solid var(--border); background: #fff; object-fit: contain; }

.status { display: inline-flex; align-items: center; gap: 6px; font-size: 12px; }
.status i { width: 7px; height: 7px; border-radius: 50%; }
.ds-online i { background: #12b76a; box-shadow: 0 0 8px #12b76a; }
.ds-idle i { background: #5d76a8; }
.ds-flying i { background: #155eef; box-shadow: 0 0 8px #155eef; animation: pulse-glow 1.6s infinite; }
.ds-maintenance i { background: #dc6803; }
.ds-offline i { background: #d0d5dd; }

@keyframes pulse-glow { 0%, 100% { opacity: 1; } 50% { opacity: .4; } }
</style>
