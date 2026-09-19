<template>
  <div class="page">
    <div class="page-header">
      <span class="page-title">隐患上报</span>
      <div class="actions">
        <el-button type="primary" @click="openDialog()">新增隐患</el-button>
      </div>
    </div>

    <div class="panel table-panel">
      <div class="toolbar">
        <el-input v-model="keyword" placeholder="标题 / 点位名 / 上报人" clearable style="width: 200px" @keyup.enter="search">
          <template #prefix><el-icon><Search /></el-icon></template>
        </el-input>
        <el-select v-model="levelFilter" clearable placeholder="隐患等级" style="width: 130px">
          <el-option v-for="o in dictOptions(HAZARD_LEVEL)" :key="o.value" :label="o.label" :value="o.value" />
        </el-select>
        <el-select v-model="statusFilter" clearable placeholder="处理状态" style="width: 120px">
          <el-option v-for="o in dictOptions(HAZARD_STATUS)" :key="o.value" :label="o.label" :value="o.value" />
        </el-select>
        <el-select v-model="pointFilter" clearable filterable placeholder="巡检点位" style="width: 180px">
          <el-option v-for="p in points" :key="p.id" :label="p.name" :value="p.id" />
        </el-select>
        <el-button @click="search">查询</el-button>
      </div>

      <el-table :data="rows" v-loading="loading" stripe height="100%" @sort-change="onSort">
        <el-table-column type="index" label="序号" width="56" :index="seq" />
        <el-table-column prop="title" label="隐患标题" min-width="200" sortable="custom" show-overflow-tooltip>
          <template #default="{ row }"><span class="name">{{ row.title }}</span></template>
        </el-table-column>
        <el-table-column prop="pointName" label="巡检点位" min-width="130" sortable="custom" show-overflow-tooltip>
          <template #default="{ row }">{{ row.pointName || '-' }}</template>
        </el-table-column>
        <el-table-column prop="level" label="隐患等级" width="110" sortable="custom">
          <template #default="{ row }">
            <el-tag size="small" :type="dictTag(HAZARD_LEVEL, row.level)" effect="light">
              {{ dictLabel(HAZARD_LEVEL, row.level) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="reporter" label="上报人" width="100" sortable="custom">
          <template #default="{ row }">{{ row.reporter || '-' }}</template>
        </el-table-column>
        <el-table-column prop="reportTime" label="上报时间" width="155" sortable="custom">
          <template #default="{ row }">{{ row.reportTime || '-' }}</template>
        </el-table-column>
        <el-table-column prop="deadline" label="整改期限" width="155" sortable="custom">
          <template #default="{ row }">
            <span :class="{ overdue: isOverdue(row) }">{{ row.deadline || '-' }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="100" sortable="custom">
          <template #default="{ row }">
            <el-tag size="small" :type="dictTag(HAZARD_STATUS, row.status)" effect="plain">
              {{ dictLabel(HAZARD_STATUS, row.status) }}
            </el-tag>
          </template>
        </el-table-column>
        <!-- 处理人不占列宽:处理说明比处理人信息量大,两者只能留一个时留后者 -->
        <el-table-column prop="handleResult" label="处理说明" min-width="150" show-overflow-tooltip>
          <template #default="{ row }">
            <el-tooltip v-if="row.handleResult" :content="row.handleResult" placement="top" :show-after="300">
              <span class="ellipsis">{{ row.handleResult }}</span>
            </el-tooltip>
            <span v-else class="sub">-</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="190" fixed="right">
          <template #default="{ row }">
            <el-button v-if="row.status !== 'CLOSED'" link type="success" size="small"
                       @click="openHandle(row)">处理/整改</el-button>
            <el-button link type="primary" size="small" @click="openDialog(row)">编辑</el-button>
            <el-popconfirm title="确认删除该隐患记录?" @confirm="remove(row.id)">
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

    <!-- 上报 / 编辑隐患 -->
    <el-drawer v-model="dialog.visible" :title="dialog.form.id ? '编辑隐患' : '上报隐患'" direction="rtl" size="580px">
      <el-form :model="dialog.form" label-width="90px">
        <el-form-item label="隐患标题" required>
          <el-input v-model="dialog.form.title" />
        </el-form-item>
        <el-row :gutter="12">
          <el-col :span="12">
            <el-form-item label="巡检点位">
              <el-select v-model="dialog.form.pointId" clearable filterable style="width: 100%">
                <el-option v-for="p in points" :key="p.id" :label="`${p.name}(${p.code})`" :value="p.id" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="关联任务">
              <el-select v-model="dialog.form.taskId" clearable filterable style="width: 100%"
                         placeholder="可选,用于任务详情回溯">
                <el-option v-for="t in tasks" :key="t.id" :label="t.name" :value="t.id" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="12">
          <el-col :span="12">
            <el-form-item label="隐患等级">
              <el-select v-model="dialog.form.level" style="width: 100%">
                <el-option v-for="o in dictOptions(HAZARD_LEVEL)" :key="o.value" :label="o.label" :value="o.value" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="整改期限">
              <el-date-picker v-model="dialog.form.deadline" type="datetime" value-format="YYYY-MM-DD HH:mm:ss"
                              style="width: 100%" placeholder="选择时间" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="隐患描述">
          <el-input v-model="dialog.form.description" type="textarea" :rows="4" placeholder="现场情况、风险后果与建议措施" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialog.visible = false">取消</el-button>
        <el-button type="primary" :loading="dialog.saving" @click="save">保存</el-button>
      </template>
    </el-drawer>

    <!-- 整改流转:目标状态不能是待处理,处理说明必填 -->
    <el-dialog v-model="handle.visible" title="隐患处理 / 整改" width="480px">
      <el-form :model="handle.form" label-width="90px">
        <el-form-item label="目标状态" required>
          <el-select v-model="handle.form.status" style="width: 100%">
            <el-option v-for="o in handleStatusOptions" :key="o.value" :label="o.label" :value="o.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="处理人">
          <el-input v-model="handle.form.operator" placeholder="留空则记为当前登录账号" />
        </el-form-item>
        <el-form-item label="处理说明" required>
          <el-input v-model="handle.form.content" type="textarea" :rows="4" placeholder="整改措施与完成情况(必填)" />
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
  HAZARD_LEVEL, HAZARD_STATUS,
  dictLabel, dictTag, dictOptions
} from '../../utils/dict'

const loading = ref(false)
const keyword = ref('')
const levelFilter = ref(null)
const statusFilter = ref(null)
const pointFilter = ref(null)
const rows = ref([])
const points = ref([])
const tasks = ref([])
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

/** 整改期限已过且未闭环时高亮 */
function isOverdue(row) {
  return row.deadline && row.status !== 'CLOSED' && row.status !== 'RECTIFIED' && row.deadline < nowText()
}
function nowText() {
  const d = new Date()
  const p = (n) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())} ${p(d.getHours())}:${p(d.getMinutes())}:${p(d.getSeconds())}`
}

const dialog = reactive({ visible: false, saving: false, form: {} })
const handle = reactive({ visible: false, saving: false, row: null, form: { status: 'PROCESSING', operator: '', content: '' } })
/** 处理/整改只能流转到这三个状态(待处理不可回退,已关闭不可再处理) */
const handleStatusOptions = computed(() => dictOptions(HAZARD_STATUS)
  .filter((o) => ['PROCESSING', 'RECTIFIED', 'CLOSED'].includes(o.value)))

onMounted(load)
async function load() {
  loading.value = true
  try {
    const [res] = await Promise.all([
      http.get('/hazards/page', {
        params: {
          page: pager.page, size: pager.size,
          sortBy: sort.sortBy || undefined,
          direction: sort.sortBy ? sort.direction : undefined,
          keyword: keyword.value || undefined,
          level: levelFilter.value || undefined,
          status: statusFilter.value || undefined,
          pointId: pointFilter.value || undefined
        }
      }),
      http.get('/points').then((p) => { points.value = p }),
      // 关联任务为可选字段:任务无全量列表接口,取一页足够宽的候选集
      http.get('/tasks/page', { params: { page: 1, size: 200 } }).then((t) => { tasks.value = t.rows || [] })
    ])
    rows.value = res.rows || []
    pager.total = res.total || 0
  } finally { loading.value = false }
}

/** 筛选条件变化回到第一页再查;翻页保持当前页 */
watch([levelFilter, statusFilter, pointFilter], () => { pager.page = 1; load() })

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
        id: row.id, title: row.title, pointId: row.pointId, taskId: row.taskId,
        level: row.level, description: row.description, deadline: row.deadline
      }
    : { id: null, title: '', pointId: null, taskId: null, level: 'GENERAL', description: '', deadline: null }
  dialog.visible = true
}

async function save() {
  const f = dialog.form
  if (!f.title) return ElMessage.warning('隐患标题不能为空')
  dialog.saving = true
  try {
    if (f.id) await http.put(`/hazards/${f.id}`, f)
    else await http.post('/hazards', f)
    ElMessage.success('已保存')
    dialog.visible = false
    load()
  } finally { dialog.saving = false }
}

function openHandle(row) {
  handle.row = row
  handle.form = { status: 'PROCESSING', operator: row.handler || '', content: '' }
  handle.visible = true
}

async function submitHandle() {
  if (!handle.form.status) return ElMessage.warning('请选择目标状态')
  if (!handle.form.content?.trim()) return ElMessage.warning('请填写处理说明')
  handle.saving = true
  try {
    await http.post(`/hazards/${handle.row.id}/handle`, handle.form)
    ElMessage.success('整改流转已提交')
    handle.visible = false
    load()
  } finally { handle.saving = false }
}

async function remove(id) {
  await http.delete(`/hazards/${id}`)
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
.overdue { color: var(--danger); font-weight: 600; }
.ellipsis { display: inline-block; max-width: 100%; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; vertical-align: bottom; }
</style>
