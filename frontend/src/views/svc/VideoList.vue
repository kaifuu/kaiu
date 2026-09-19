<template>
  <div class="page">
    <div class="page-header">
      <span class="page-title">视频管理</span>
      <div class="actions">
        <el-button type="primary" @click="openDialog()">新增通道</el-button>
      </div>
    </div>

    <div class="stats">
      <div class="stat"><span class="stat-label">通道总数</span><b class="stat-value">{{ total }}</b></div>
      <div class="stat"><span class="stat-label">在线</span><b class="stat-value online">{{ stats.ONLINE ?? 0 }}</b></div>
      <div class="stat"><span class="stat-label">离线</span><b class="stat-value offline">{{ stats.OFFLINE ?? 0 }}</b></div>
    </div>

    <div class="panel table-panel">
      <div class="toolbar">
        <el-input v-model="keyword" placeholder="名称 / 编码 / 设备" clearable style="width: 200px" @keyup.enter="search">
          <template #prefix><el-icon><Search /></el-icon></template>
        </el-input>
        <el-select v-model="statusFilter" clearable placeholder="在线状态" style="width: 130px">
          <el-option v-for="o in dictOptions(VIDEO_STATUS)" :key="o.value" :label="o.label" :value="o.value" />
        </el-select>
        <el-select v-model="protocolFilter" clearable placeholder="协议" style="width: 130px">
          <el-option v-for="o in dictOptions(VIDEO_PROTOCOL)" :key="o.value" :label="o.label" :value="o.value" />
        </el-select>
        <el-select v-model="typeFilter" clearable placeholder="通道类型" style="width: 120px">
          <el-option v-for="o in dictOptions(VIDEO_TYPE)" :key="o.value" :label="o.label" :value="o.value" />
        </el-select>
        <el-button @click="search">查询</el-button>
      </div>

      <el-table :data="rows" v-loading="loading" stripe height="100%" @sort-change="onSort">
        <el-table-column type="index" label="序号" width="56" :index="seq" />
        <el-table-column prop="code" label="通道编码" width="120" sortable="custom">
          <template #default="{ row }"><span class="code">{{ row.code }}</span></template>
        </el-table-column>
        <el-table-column prop="name" label="通道名称" min-width="170" sortable="custom" show-overflow-tooltip>
          <template #default="{ row }"><span class="name">{{ row.name }}</span></template>
        </el-table-column>
        <el-table-column prop="deviceName" label="所属设备" min-width="160" sortable="custom" show-overflow-tooltip>
          <template #default="{ row }">
            <span v-if="row.deviceName">{{ row.deviceName }}</span>
            <span v-else-if="row.deviceSn" class="code">{{ row.deviceSn }}</span>
            <span v-else class="dim">未绑定</span>
          </template>
        </el-table-column>
        <el-table-column prop="channelType" label="类型" width="90">
          <template #default="{ row }">
            <el-tag size="small" :type="dictTag(VIDEO_TYPE, row.channelType)" effect="light">
              {{ dictLabel(VIDEO_TYPE, row.channelType) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="protocol" label="协议" width="110" sortable="custom">
          <template #default="{ row }">
            <el-tag size="small" :type="dictTag(VIDEO_PROTOCOL, row.protocol)" effect="plain">
              {{ dictLabel(VIDEO_PROTOCOL, row.protocol) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="resolution" label="分辨率" width="110">
          <template #default="{ row }">{{ row.resolution || '-' }}</template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="90" sortable="custom">
          <template #default="{ row }">
            <el-tag size="small" :type="dictTag(VIDEO_STATUS, row.status)" effect="plain">
              {{ dictLabel(VIDEO_STATUS, row.status) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="lastFrameAt" label="最后画面时间" width="160" sortable="custom">
          <template #default="{ row }">{{ row.lastFrameAt || '-' }}</template>
        </el-table-column>
        <el-table-column label="操作" width="220" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="openPreview(row)">预览</el-button>
            <el-button link size="small" :type="row.status === 'ONLINE' ? 'info' : 'success'"
                       @click="toggleStatus(row)">{{ row.status === 'ONLINE' ? '下线' : '上线' }}</el-button>
            <el-button link type="primary" size="small" @click="openDialog(row)">编辑</el-button>
            <el-popconfirm title="确认删除该视频通道?" @confirm="remove(row.id)">
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

    <!-- 预览:平台只维护台账与在线状态,这里只做占位展示,不引入播放器 -->
    <el-drawer v-model="preview.visible" :title="`视频预览 · ${preview.row?.name || ''}`" direction="rtl" size="640px">
      <div class="player-box">
        <template v-if="preview.row?.status === 'ONLINE'">
          <div class="player-label">流地址</div>
          <div class="player-url">{{ preview.row?.streamUrl || '未配置流地址' }}</div>
          <div class="player-note">实际播放需接入 RTMP/GB28181 流媒体服务(如 SRS / ZLMediaKit)</div>
        </template>
        <div v-else class="player-offline">通道离线</div>
      </div>
      <el-descriptions :column="1" border class="player-meta">
        <el-descriptions-item label="通道编码">{{ preview.row?.code || '-' }}</el-descriptions-item>
        <el-descriptions-item label="所属设备">{{ preview.row?.deviceName || preview.row?.deviceSn || '-' }}</el-descriptions-item>
        <el-descriptions-item label="协议">{{ dictLabel(VIDEO_PROTOCOL, preview.row?.protocol) }}</el-descriptions-item>
        <el-descriptions-item label="分辨率">{{ preview.row?.resolution || '-' }}</el-descriptions-item>
        <el-descriptions-item label="最后画面时间">{{ preview.row?.lastFrameAt || '-' }}</el-descriptions-item>
      </el-descriptions>
    </el-drawer>

    <!-- 新增 / 编辑通道 -->
    <el-drawer v-model="dialog.visible" :title="dialog.form.id ? '编辑视频通道' : '新增视频通道'" direction="rtl" size="560px">
      <el-form :model="dialog.form" label-width="90px">
        <el-form-item label="通道名称" required>
          <el-input v-model="dialog.form.name" placeholder="如 潮白河机场 01 直播" />
        </el-form-item>
        <el-form-item label="通道编码">
          <el-input v-model="dialog.form.code" :disabled="!!dialog.form.id" placeholder="留空则由后端生成" />
        </el-form-item>
        <el-form-item label="所属设备">
          <el-select v-model="dialog.form.deviceSn" clearable filterable style="width: 100%"
                     placeholder="选择机场或无人机" @change="onDeviceChange">
            <el-option v-for="d in devices" :key="d.deviceSn" :label="`${d.name}(${d.deviceSn})`" :value="d.deviceSn" />
          </el-select>
        </el-form-item>
        <el-row :gutter="12">
          <el-col :span="12">
            <el-form-item label="类型">
              <el-select v-model="dialog.form.channelType" style="width: 100%">
                <el-option v-for="o in dictOptions(VIDEO_TYPE)" :key="o.value" :label="o.label" :value="o.value" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="协议">
              <el-select v-model="dialog.form.protocol" style="width: 100%">
                <el-option v-for="o in dictOptions(VIDEO_PROTOCOL)" :key="o.value" :label="o.label" :value="o.value" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="流地址">
          <el-input v-model="dialog.form.streamUrl" placeholder="如 http://media.example.com/live/dock01.flv" />
        </el-form-item>
        <el-row :gutter="12">
          <el-col :span="12">
            <el-form-item label="分辨率">
              <el-input v-model="dialog.form.resolution" placeholder="如 1920x1080" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="状态">
              <el-select v-model="dialog.form.status" style="width: 100%">
                <el-option v-for="o in dictOptions(VIDEO_STATUS)" :key="o.value" :label="o.label" :value="o.value" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="备注">
          <el-input v-model="dialog.form.remark" type="textarea" :rows="2" />
        </el-form-item>
        <el-alert type="info" :closable="false"
                  title="平台只维护通道台账与在线状态;真正的拉流播放需要外部流媒体服务(SRS / ZLMediaKit),因此列表里的状态可手动切换。" />
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
import { VIDEO_STATUS, VIDEO_PROTOCOL, VIDEO_TYPE, dictLabel, dictTag, dictOptions } from '../../utils/dict'

const loading = ref(false)
const keyword = ref('')
const statusFilter = ref('')
const protocolFilter = ref('')
const typeFilter = ref('')
const rows = ref([])
const devices = ref([])
const stats = reactive({})
const pager = reactive({ page: 1, size: 10, total: 0 })
/* 可排序列必须落在 VideoChannelService 的白名单内(类型/分辨率不在其中) */
const sort = reactive({ sortBy: '', direction: '' })
const dialog = reactive({ visible: false, saving: false, form: {} })
const preview = reactive({ visible: false, row: null })

const total = computed(() => (stats.ONLINE ?? 0) + (stats.OFFLINE ?? 0))

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
    const [res, st, devs] = await Promise.all([
      http.get('/videos/page', {
        params: {
          page: pager.page, size: pager.size,
          sortBy: sort.sortBy || undefined,
          direction: sort.sortBy ? sort.direction : undefined,
          keyword: keyword.value || undefined,
          status: statusFilter.value || undefined,
          protocol: protocolFilter.value || undefined,
          channelType: typeFilter.value || undefined
        }
      }),
      http.get('/videos/stats'),
      // 所属设备下拉为全量接口,只需拉一次
      http.get('/devices').then((d) => { devices.value = d || [] })
    ])
    rows.value = res.rows || []
    pager.total = res.total || 0
    Object.assign(stats, st)
  } finally { loading.value = false }
}

watch([statusFilter, protocolFilter, typeFilter], () => { pager.page = 1; load() })
function search() { pager.page = 1; load() }
function onSizeChange() { pager.page = 1; load() }

/** 通道只存 deviceSn + deviceName 两个冗余字段,选设备时把名称一并带过来 */
function onDeviceChange(sn) {
  const d = devices.value.find((x) => x.deviceSn === sn)
  dialog.form.deviceName = d ? d.name : ''
}

function openPreview(row) {
  preview.row = row
  preview.visible = true
}

async function toggleStatus(row) {
  const online = row.status !== 'ONLINE'
  await http.post(`/videos/${row.id}/status`, null, { params: { online } })
  ElMessage.success(online ? '已置为在线' : '已置为离线')
  load()
}

function openDialog(row) {
  dialog.form = row
    ? {
        id: row.id, name: row.name, code: row.code, deviceSn: row.deviceSn, deviceName: row.deviceName,
        channelType: row.channelType, protocol: row.protocol, streamUrl: row.streamUrl,
        resolution: row.resolution, status: row.status, remark: row.remark
      }
    : {
        id: null, name: '', code: '', deviceSn: '', deviceName: '', channelType: 'LIVE',
        protocol: 'FLV', streamUrl: '', resolution: '1920x1080', status: 'OFFLINE', remark: ''
      }
  dialog.visible = true
}

async function save() {
  const f = dialog.form
  if (!f.name) return ElMessage.warning('通道名称不能为空')
  dialog.saving = true
  try {
    if (f.id) await http.put(`/videos/${f.id}`, f)
    else await http.post('/videos', f)
    ElMessage.success('已保存')
    dialog.visible = false
    load()
  } finally { dialog.saving = false }
}

async function remove(id) {
  await http.delete(`/videos/${id}`)
  ElMessage.success('已删除')
  load()
}
</script>

<style scoped>
.stats { display: grid; grid-template-columns: repeat(3, 1fr); gap: 10px; margin-bottom: 12px; }
.stat {
  display: flex; align-items: center; justify-content: space-between;
  padding: 10px 14px; border-radius: 10px;
  background: #fff; border: 1px solid var(--border);
}
.stat-label { font-size: 12.5px; color: var(--text-dim); }
.stat-value { font-size: 19px; font-weight: 700; color: var(--text); }
.stat-value.online { color: var(--success); }
.stat-value.offline { color: var(--text-faint); }

.table-panel { height: calc(100% - 122px); padding: 8px; display: flex; flex-direction: column; }
.toolbar { display: flex; gap: 10px; padding: 8px 8px 12px; flex-wrap: wrap; }
.toolbar + .el-table { flex: 1; }
.pager-row { display: flex; justify-content: flex-end; padding: 10px 4px 2px; }
.actions { display: flex; gap: 10px; }
.name { font-weight: 600; }
.code { font-family: monospace; font-size: 12.5px; color: var(--primary); }
.dim { color: var(--text-faint); font-size: 12.5px; }

/* 纯 CSS 的 16:9 播放占位:项目未引入播放器,这里只说明流地址与接入前提 */
.player-box {
  aspect-ratio: 16 / 9;
  width: 100%;
  background: #0b1220;
  border-radius: 10px;
  display: flex; flex-direction: column;
  align-items: center; justify-content: center;
  gap: 8px; padding: 16px; text-align: center;
}
.player-label { color: #64748b; font-size: 12.5px; letter-spacing: 1px; }
.player-url {
  color: #e2e8f0; font-family: monospace; font-size: 13px;
  word-break: break-all; max-width: 100%;
}
.player-note { color: #94a3b8; font-size: 12.5px; }
.player-offline { color: #94a3b8; font-size: 15px; letter-spacing: 1px; }
.player-meta { margin-top: 14px; }
</style>
