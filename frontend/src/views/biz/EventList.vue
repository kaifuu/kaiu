<template>
  <div class="page">
    <div class="page-header">
      <span class="page-title">应急事件</span>
      <div class="actions">
        <el-button type="primary" @click="openDialog()">事件接报</el-button>
      </div>
    </div>

    <div class="panel table-panel">
      <div class="toolbar">
        <el-input v-model="keyword" placeholder="标题 / 地点 / 上报人" clearable style="width: 200px" @keyup.enter="search">
          <template #prefix><el-icon><Search /></el-icon></template>
        </el-input>
        <el-select v-model="categoryFilter" clearable placeholder="事件类别" style="width: 130px">
          <el-option v-for="o in dictOptions(EVENT_CATEGORY)" :key="o.value" :label="o.label" :value="o.value" />
        </el-select>
        <el-select v-model="levelFilter" clearable placeholder="事件等级" style="width: 150px">
          <el-option v-for="o in dictOptions(EVENT_LEVEL)" :key="o.value" :label="o.label" :value="o.value" />
        </el-select>
        <el-select v-model="statusFilter" clearable placeholder="处置状态" style="width: 120px">
          <el-option v-for="o in dictOptions(EVENT_STATUS)" :key="o.value" :label="o.label" :value="o.value" />
        </el-select>
        <el-button @click="search">查询</el-button>
      </div>

      <el-table :data="rows" v-loading="loading" stripe height="100%" @sort-change="onSort">
        <el-table-column type="index" label="序号" width="56" :index="seq" />
        <el-table-column prop="title" label="事件标题" min-width="200" sortable="custom" show-overflow-tooltip>
          <template #default="{ row }"><span class="name">{{ row.title }}</span></template>
        </el-table-column>
        <el-table-column prop="category" label="事件类别" width="110" sortable="custom">
          <template #default="{ row }">
            <el-tag size="small" :type="dictTag(EVENT_CATEGORY, row.category)" effect="light">
              {{ dictLabel(EVENT_CATEGORY, row.category) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="level" label="事件等级" width="110" sortable="custom">
          <template #default="{ row }">
            <el-tag size="small" :type="dictTag(EVENT_LEVEL, row.level)" effect="light">
              {{ dictLabel(EVENT_LEVEL, row.level) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="address" label="事发地点" min-width="180" show-overflow-tooltip>
          <template #default="{ row }">{{ row.address || '-' }}</template>
        </el-table-column>
        <el-table-column prop="occurTime" label="发生时间" width="155" sortable="custom">
          <template #default="{ row }">{{ row.occurTime || '-' }}</template>
        </el-table-column>
        <el-table-column label="上报人" width="120">
          <template #default="{ row }">
            <div>{{ row.reporter || '-' }}</div>
            <div class="sub">{{ row.reporterPhone || '-' }}</div>
          </template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="100" sortable="custom">
          <template #default="{ row }">
            <el-tag size="small" :type="dictTag(EVENT_STATUS, row.status)" effect="plain">
              {{ dictLabel(EVENT_STATUS, row.status) }}
            </el-tag>
          </template>
        </el-table-column>
        <!-- 现场指挥不占列宽:处置完成时间比指挥姓名更常用于列表扫描 -->
        <el-table-column prop="finishTime" label="处置完成" width="155">
          <template #default="{ row }">{{ row.finishTime || '-' }}</template>
        </el-table-column>
        <el-table-column label="操作" width="190" fixed="right">
          <template #default="{ row }">
            <el-button v-if="row.status !== 'ARCHIVED'" link type="success" size="small"
                       @click="openHandle(row)">处置</el-button>
            <el-button link type="primary" size="small" @click="openDialog(row)">编辑</el-button>
            <el-popconfirm title="确认删除该事件?" @confirm="remove(row.id)">
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

    <!-- 接报 / 编辑事件 -->
    <el-drawer v-model="dialog.visible" :title="dialog.form.id ? '编辑事件' : '事件接报'" direction="rtl" size="600px">
      <el-form :model="dialog.form" label-width="90px">
        <el-form-item label="事件标题" required>
          <el-input v-model="dialog.form.title" />
        </el-form-item>
        <el-row :gutter="12">
          <el-col :span="12">
            <el-form-item label="事件类别">
              <el-select v-model="dialog.form.category" style="width: 100%">
                <el-option v-for="o in dictOptions(EVENT_CATEGORY)" :key="o.value" :label="o.label" :value="o.value" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="事件等级">
              <el-select v-model="dialog.form.level" style="width: 100%">
                <el-option v-for="o in dictOptions(EVENT_LEVEL)" :key="o.value" :label="o.label" :value="o.value" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="事发地点">
          <el-input v-model="dialog.form.address" />
        </el-form-item>
        <el-row :gutter="12">
          <el-col :span="12">
            <el-form-item label="经度">
              <el-input-number v-model="dialog.form.longitude" :precision="6" :step="0.000001"
                               :controls="false" style="width: 100%" placeholder="如 116.418200" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="纬度">
              <el-input-number v-model="dialog.form.latitude" :precision="6" :step="0.000001"
                               :controls="false" style="width: 100%" placeholder="如 39.950100" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="12">
          <el-col :span="12">
            <el-form-item label="发生时间">
              <el-date-picker v-model="dialog.form.occurTime" type="datetime" value-format="YYYY-MM-DD HH:mm:ss"
                              style="width: 100%" placeholder="留空取当前时间" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="上报人">
              <el-input v-model="dialog.form.reporter" placeholder="留空记为当前账号" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="联系电话">
          <el-input v-model="dialog.form.reporterPhone" />
        </el-form-item>
        <el-form-item label="事件描述">
          <el-input v-model="dialog.form.description" type="textarea" :rows="4" placeholder="现场态势、影响范围与初步研判" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialog.visible = false">取消</el-button>
        <el-button type="primary" :loading="dialog.saving" @click="save">保存</el-button>
      </template>
    </el-drawer>

    <!-- 处置流转:处置措施必填,归档后不可再流转 -->
    <el-dialog v-model="handle.visible" title="事件处置" width="480px">
      <el-form :model="handle.form" label-width="90px">
        <el-form-item label="目标状态" required>
          <el-select v-model="handle.form.status" style="width: 100%">
            <el-option v-for="o in handleStatusOptions" :key="o.value" :label="o.label" :value="o.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="现场指挥">
          <el-input v-model="handle.form.operator" placeholder="留空则记为当前登录账号" />
        </el-form-item>
        <el-form-item label="处置措施" required>
          <el-input v-model="handle.form.content" type="textarea" :rows="4" placeholder="力量调度、处置动作与阶段结果(必填)" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="handle.visible = false">取消</el-button>
        <el-button type="primary" :loading="handle.saving" @click="submitHandle">提交</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, computed, watch, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { Search } from '@element-plus/icons-vue'
import http from '../../api'
import {
  EVENT_CATEGORY, EVENT_LEVEL, EVENT_STATUS,
  dictLabel, dictTag, dictOptions
} from '../../utils/dict'

const loading = ref(false)
const keyword = ref('')
const categoryFilter = ref(null)
const levelFilter = ref(null)
const statusFilter = ref(null)
const rows = ref([])
const pager = reactive({ page: 1, size: 10, total: 0 })
/* 序号(跨页连续) + 服务端排序(表头 sortable=custom,白名单见后端 PageSort) */
const sort = reactive({ sortBy: '', direction: '' })
function seq(i) { return (pager.page - 1) * pager.size + i + 1 }
function onSort({ prop, order }) {
  sort.sortBy = order ? prop : ''
  sort.direction = order === 'ascending' ? 'asc' : 'desc'
  pager.page = 1
  load()
}

const dialog = reactive({ visible: false, saving: false, form: {} })
const handle = reactive({ visible: false, saving: false, row: null, form: { status: 'RESPONDING', operator: '', content: '' } })
/** 处置只能流转到响应中/已处置/已归档 */
const handleStatusOptions = computed(() => dictOptions(EVENT_STATUS)
  .filter((o) => ['RESPONDING', 'HANDLED', 'ARCHIVED'].includes(o.value)))

onMounted(load)
async function load() {
  loading.value = true
  try {
    const res = await http.get('/events/page', {
      params: {
        page: pager.page, size: pager.size,
        sortBy: sort.sortBy || undefined,
        direction: sort.sortBy ? sort.direction : undefined,
        keyword: keyword.value || undefined,
        category: categoryFilter.value || undefined,
        level: levelFilter.value || undefined,
        status: statusFilter.value || undefined
      }
    })
    rows.value = res.rows || []
    pager.total = res.total || 0
  } finally { loading.value = false }
}

/** 筛选条件变化回到第一页再查;翻页保持当前页 */
watch([categoryFilter, levelFilter, statusFilter], () => { pager.page = 1; load() })

function search() {
  pager.page = 1
  load()
}

function onSizeChange() {
  pager.page = 1
  load()
}

function openDialog(row) {
  dialog.form = row
    ? {
        id: row.id, title: row.title, category: row.category, level: row.level,
        address: row.address, longitude: row.longitude, latitude: row.latitude,
        occurTime: row.occurTime, reporter: row.reporter, reporterPhone: row.reporterPhone,
        description: row.description
      }
    : {
        id: null, title: '', category: 'OTHER', level: 'IV',
        address: '', longitude: null, latitude: null,
        occurTime: null, reporter: '', reporterPhone: '', description: ''
      }
  dialog.visible = true
}

async function save() {
  const f = dialog.form
  if (!f.title) return ElMessage.warning('事件标题不能为空')
  // 经纬度成对出现(服务端同样校验)
  const hasLng = f.longitude !== null && f.longitude !== undefined && f.longitude !== ''
  const hasLat = f.latitude !== null && f.latitude !== undefined && f.latitude !== ''
  if (hasLng !== hasLat) return ElMessage.warning('经度与纬度需同时填写')
  dialog.saving = true
  try {
    const body = { ...f, longitude: hasLng ? f.longitude : null, latitude: hasLat ? f.latitude : null }
    if (f.id) await http.put(`/events/${f.id}`, body)
    else await http.post('/events', body)
    ElMessage.success('已保存')
    dialog.visible = false
    load()
  } finally { dialog.saving = false }
}

function openHandle(row) {
  handle.row = row
  handle.form = { status: 'RESPONDING', operator: row.commander || '', content: '' }
  handle.visible = true
}

async function submitHandle() {
  if (!handle.form.status) return ElMessage.warning('请选择目标状态')
  if (!handle.form.content?.trim()) return ElMessage.warning('请填写处置措施')
  handle.saving = true
  try {
    await http.post(`/events/${handle.row.id}/handle`, handle.form)
    ElMessage.success('处置流转已提交')
    handle.visible = false
    load()
  } finally { handle.saving = false }
}

async function remove(id) {
  await http.delete(`/events/${id}`)
  ElMessage.success('已删除')
  load()
}
</script>

<style scoped>
.table-panel { height: calc(100% - 50px); padding: 8px; display: flex; flex-direction: column; }
.toolbar { display: flex; gap: 10px; padding: 8px 8px 12px; flex-wrap: wrap; }
.toolbar + .el-table { flex: 1; }
.pager-row { display: flex; justify-content: flex-end; padding: 10px 4px 2px; }
.actions { display: flex; gap: 10px; }
.name { font-weight: 600; }
.sub { font-size: 12px; color: var(--text-faint); }
</style>
