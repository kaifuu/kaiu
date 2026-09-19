<template>
  <div class="page">
    <div class="page-header">
      <span class="page-title">需求管理</span>
      <div class="actions">
        <el-button type="primary" @click="openDialog()">提报需求</el-button>
      </div>
    </div>

    <div class="stats">
      <div class="stat"><span class="stat-label">待执行</span><b class="stat-value pending">{{ stats.PENDING ?? 0 }}</b></div>
      <div class="stat"><span class="stat-label">已执行</span><b class="stat-value executed">{{ stats.EXECUTED ?? 0 }}</b></div>
      <div class="stat"><span class="stat-label">已超时</span><b class="stat-value overdue">{{ stats.OVERDUE ?? 0 }}</b></div>
      <div class="stat"><span class="stat-label">已取消</span><b class="stat-value canceled">{{ stats.CANCELED ?? 0 }}</b></div>
    </div>

    <div class="panel table-panel">
      <div class="toolbar">
        <el-input v-model="keyword" placeholder="名称 / 编码 / 来源部门" clearable style="width: 220px" @keyup.enter="search">
          <template #prefix><el-icon><Search /></el-icon></template>
        </el-input>
        <el-select v-model="statusFilter" clearable placeholder="状态" style="width: 130px">
          <el-option v-for="o in dictOptions(DEMAND_STATUS)" :key="o.value" :label="o.label" :value="o.value" />
        </el-select>
        <el-select v-model="categoryFilter" clearable placeholder="巡检类别" style="width: 140px">
          <el-option v-for="o in dictOptions(DEMAND_CATEGORY)" :key="o.value" :label="o.label" :value="o.value" />
        </el-select>
        <!-- 来源部门后端为精确匹配,且无部门字典来源,故用输入框 -->
        <el-input v-model="deptFilter" placeholder="来源部门" clearable style="width: 150px" @keyup.enter="search" />
        <el-button @click="search">查询</el-button>
      </div>

      <el-table :data="rows" v-loading="loading" stripe height="100%" @sort-change="onSort">
        <el-table-column type="index" label="序号" width="56" :index="seq" />
        <el-table-column prop="code" label="需求编码" width="130">
          <template #default="{ row }"><span class="code">{{ row.code }}</span></template>
        </el-table-column>
        <el-table-column prop="title" label="需求名称" min-width="200" sortable="custom" show-overflow-tooltip>
          <template #default="{ row }"><span class="name">{{ row.title }}</span></template>
        </el-table-column>
        <el-table-column prop="sourceDept" label="需求来源部门" width="150" sortable="custom" show-overflow-tooltip>
          <template #default="{ row }">{{ row.sourceDept || '-' }}</template>
        </el-table-column>
        <el-table-column prop="category" label="巡检类别" width="120" sortable="custom">
          <template #default="{ row }">
            <el-tag size="small" :type="dictTag(DEMAND_CATEGORY, row.category)" effect="light">
              {{ dictLabel(DEMAND_CATEGORY, row.category) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="expectDate" label="期望执行日期" width="130" sortable="custom">
          <template #default="{ row }">
            <span :class="{ overdue: isOverdue(row) }">{{ row.expectDate || '-' }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="submittedAt" label="提报时间" width="155" sortable="custom">
          <template #default="{ row }">{{ row.submittedAt || '-' }}</template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="100" sortable="custom">
          <template #default="{ row }">
            <el-tag size="small" :type="dictTag(DEMAND_STATUS, row.status)" effect="plain">
              {{ dictLabel(DEMAND_STATUS, row.status) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="executor" label="执行人" width="110">
          <template #default="{ row }">{{ row.executor || '-' }}</template>
        </el-table-column>
        <el-table-column label="操作" width="200" fixed="right">
          <template #default="{ row }">
            <!-- 待执行 / 已超时仍可补执行;已执行、已取消为终态,只读 -->
            <el-button v-if="row.status === 'PENDING' || row.status === 'OVERDUE'" link type="success" size="small"
                       @click="openExecute(row)">执行</el-button>
            <el-button v-if="row.status === 'PENDING' || row.status === 'OVERDUE'" link type="warning" size="small"
                       @click="openCancel(row)">取消</el-button>
            <el-button link type="primary" size="small" :disabled="row.status === 'EXECUTED'"
                       @click="openDialog(row)">编辑</el-button>
            <el-popconfirm title="确认删除该需求?" @confirm="remove(row.id)">
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

    <!-- 提报 / 编辑需求 -->
    <el-drawer v-model="dialog.visible" :title="dialog.form.id ? '编辑需求' : '提报需求'" direction="rtl" size="560px">
      <el-form :model="dialog.form" label-width="110px">
        <el-form-item label="需求名称" required>
          <el-input v-model="dialog.form.title" placeholder="如 潮白河重点断面加密巡检" />
        </el-form-item>
        <el-row :gutter="12">
          <el-col :span="12">
            <el-form-item label="来源部门" required>
              <el-input v-model="dialog.form.sourceDept" placeholder="如 区水务局" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="巡检类别">
              <el-select v-model="dialog.form.category" style="width: 100%">
                <el-option v-for="o in dictOptions(DEMAND_CATEGORY)" :key="o.value" :label="o.label" :value="o.value" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="期望执行日期">
          <el-date-picker v-model="dialog.form.expectDate" type="date" value-format="YYYY-MM-DD"
                          style="width: 100%" placeholder="超过该日期未执行将自动标记为已超时" />
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="dialog.form.description" type="textarea" :rows="4" placeholder="巡检范围、关注对象与交付要求" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="dialog.form.remark" type="textarea" :rows="2" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialog.visible = false">取消</el-button>
        <el-button type="primary" :loading="dialog.saving" @click="save">保存</el-button>
      </template>
    </el-drawer>

    <!-- 执行:登记执行人 -->
    <el-dialog v-model="execute.visible" title="需求执行" width="440px">
      <el-form :model="execute.form" label-width="80px">
        <el-form-item label="执行人">
          <el-input v-model="execute.form.executor" placeholder="留空则记为当前登录账号" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="execute.visible = false">取消</el-button>
        <el-button type="primary" :loading="execute.saving" @click="submitExecute">确认执行</el-button>
      </template>
    </el-dialog>

    <!-- 取消:原因写入备注,便于后续回溯 -->
    <el-dialog v-model="cancel.visible" title="取消需求" width="440px">
      <el-form :model="cancel.form" label-width="80px">
        <el-form-item label="取消原因" required>
          <el-input v-model="cancel.form.remark" type="textarea" :rows="3" placeholder="将写入需求备注" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="cancel.visible = false">返回</el-button>
        <el-button type="danger" :loading="cancel.saving" @click="submitCancel">确认取消</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, watch, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { Search } from '@element-plus/icons-vue'
import http from '../../api'
import { DEMAND_STATUS, DEMAND_CATEGORY, dictLabel, dictTag, dictOptions } from '../../utils/dict'

const loading = ref(false)
const keyword = ref('')
const statusFilter = ref('')
const categoryFilter = ref('')
const deptFilter = ref('')
const rows = ref([])
const stats = reactive({})
const pager = reactive({ page: 1, size: 10, total: 0 })
/* 可排序列必须落在 DemandService 的白名单内(注意:code / executor 不在其中) */
const sort = reactive({ sortBy: '', direction: '' })
const dialog = reactive({ visible: false, saving: false, form: {} })
const execute = reactive({ visible: false, saving: false, row: null, form: { executor: '' } })
const cancel = reactive({ visible: false, saving: false, row: null, form: { remark: '' } })

function seq(i) { return (pager.page - 1) * pager.size + i + 1 }
function onSort({ prop, order }) {
  sort.sortBy = order ? prop : ''
  sort.direction = order === 'ascending' ? 'asc' : 'desc'
  pager.page = 1
  load()
}

/** 期望执行日期已过且未执行时高亮(与后端每小时的逾期标记口径一致) */
function isOverdue(row) {
  if (!row.expectDate || row.status === 'EXECUTED' || row.status === 'CANCELED') return false
  const d = new Date()
  const p = (n) => String(n).padStart(2, '0')
  return row.expectDate < `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())}`
}

onMounted(load)

async function load() {
  loading.value = true
  try {
    const [res, st] = await Promise.all([
      http.get('/demands/page', {
        params: {
          page: pager.page, size: pager.size,
          sortBy: sort.sortBy || undefined,
          direction: sort.sortBy ? sort.direction : undefined,
          keyword: keyword.value || undefined,
          status: statusFilter.value || undefined,
          sourceDept: deptFilter.value || undefined,
          category: categoryFilter.value || undefined
        }
      }),
      http.get('/demands/stats')
    ])
    rows.value = res.rows || []
    pager.total = res.total || 0
    Object.assign(stats, st)
  } finally { loading.value = false }
}

watch([statusFilter, categoryFilter], () => { pager.page = 1; load() })
function search() { pager.page = 1; load() }
function onSizeChange() { pager.page = 1; load() }

function openDialog(row) {
  dialog.form = row
    ? {
        id: row.id, title: row.title, sourceDept: row.sourceDept, category: row.category,
        description: row.description, expectDate: row.expectDate, remark: row.remark
      }
    : { id: null, title: '', sourceDept: '', category: 'DAILY', description: '', expectDate: null, remark: '' }
  dialog.visible = true
}

async function save() {
  const f = dialog.form
  if (!f.title) return ElMessage.warning('需求名称不能为空')
  if (!f.sourceDept) return ElMessage.warning('请填写需求来源部门')
  dialog.saving = true
  try {
    if (f.id) await http.put(`/demands/${f.id}`, f)
    else await http.post('/demands', f)
    ElMessage.success('已保存')
    dialog.visible = false
    load()
  } finally { dialog.saving = false }
}

function openExecute(row) {
  execute.row = row
  execute.form = { executor: '' }
  execute.visible = true
}

async function submitExecute() {
  execute.saving = true
  try {
    // executor 留空时后端取当前登录人,这里不额外兜底
    await http.post(`/demands/${execute.row.id}/execute`, execute.form)
    ElMessage.success('已登记执行')
    execute.visible = false
    load()
  } finally { execute.saving = false }
}

function openCancel(row) {
  cancel.row = row
  cancel.form = { remark: '' }
  cancel.visible = true
}

async function submitCancel() {
  if (!cancel.form.remark?.trim()) return ElMessage.warning('请填写取消原因')
  cancel.saving = true
  try {
    await http.post(`/demands/${cancel.row.id}/cancel`, cancel.form)
    ElMessage.success('已取消')
    cancel.visible = false
    load()
  } finally { cancel.saving = false }
}

async function remove(id) {
  await http.delete(`/demands/${id}`)
  ElMessage.success('已删除')
  load()
}
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
.stat-value.pending { color: var(--warning); }
.stat-value.executed { color: var(--success); }
.stat-value.overdue { color: var(--danger); }
.stat-value.canceled { color: var(--text-faint); }

.table-panel { height: calc(100% - 122px); padding: 8px; display: flex; flex-direction: column; }
.toolbar { display: flex; gap: 10px; padding: 8px 8px 12px; flex-wrap: wrap; }
.toolbar + .el-table { flex: 1; }
.pager-row { display: flex; justify-content: flex-end; padding: 10px 4px 2px; }
.actions { display: flex; gap: 10px; }
.name { font-weight: 600; }
.code { font-family: monospace; font-size: 12.5px; color: var(--primary); }
.overdue { color: var(--danger); font-weight: 600; }
</style>
