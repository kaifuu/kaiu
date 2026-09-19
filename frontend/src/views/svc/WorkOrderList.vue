<template>
  <div class="page">
    <div class="page-header">
      <span class="page-title">工单管理</span>
    </div>

    <div class="stats">
      <div class="stat"><span class="stat-label">待派发</span><b class="stat-value pending">{{ stats.PENDING ?? 0 }}</b></div>
      <div class="stat"><span class="stat-label">处理中</span><b class="stat-value processing">{{ stats.PROCESSING ?? 0 }}</b></div>
      <div class="stat"><span class="stat-label">已处理</span><b class="stat-value handled">{{ stats.HANDLED ?? 0 }}</b></div>
      <div class="stat"><span class="stat-label">已结案</span><b class="stat-value closed">{{ stats.CLOSED ?? 0 }}</b></div>
    </div>

    <div class="panel table-panel">
      <div class="toolbar">
        <el-input v-model="keyword" placeholder="标题 / 编码 / 点位 / 处理人" clearable style="width: 220px" @keyup.enter="search">
          <template #prefix><el-icon><Search /></el-icon></template>
        </el-input>
        <el-select v-model="statusFilter" clearable placeholder="工单状态" style="width: 130px">
          <el-option v-for="o in dictOptions(WORK_ORDER_STATUS)" :key="o.value" :label="o.label" :value="o.value" />
        </el-select>
        <el-select v-model="priorityFilter" clearable placeholder="优先级" style="width: 120px">
          <el-option v-for="o in dictOptions(WORK_ORDER_PRIORITY)" :key="o.value" :label="o.label" :value="o.value" />
        </el-select>
        <!-- 需求部门 / 处理人后端均为精确匹配,且无字典来源,故用输入框 -->
        <el-input v-model="deptFilter" placeholder="需求部门" clearable style="width: 140px" @keyup.enter="search" />
        <el-input v-model="handlerFilter" placeholder="处理人" clearable style="width: 120px" @keyup.enter="search" />
        <el-button @click="search">查询</el-button>
      </div>

      <el-table :data="rows" v-loading="loading" stripe height="100%" @sort-change="onSort">
        <el-table-column type="index" label="序号" width="56" :index="seq" />
        <el-table-column prop="code" label="工单编码" width="130" sortable="custom">
          <template #default="{ row }"><span class="code">{{ row.code }}</span></template>
        </el-table-column>
        <el-table-column prop="title" label="标题" min-width="190" sortable="custom" show-overflow-tooltip>
          <template #default="{ row }"><span class="name">{{ row.title }}</span></template>
        </el-table-column>
        <el-table-column prop="issueType" label="问题类型" width="105">
          <template #default="{ row }">
            <el-tag v-if="row.issueType" size="small" :type="dictTag(ISSUE_TYPE, row.issueType)" effect="light">
              {{ dictLabel(ISSUE_TYPE, row.issueType) }}
            </el-tag>
            <span v-else class="dim">-</span>
          </template>
        </el-table-column>
        <el-table-column prop="pointName" label="点位" min-width="130" sortable="custom" show-overflow-tooltip>
          <template #default="{ row }">{{ row.pointName || '-' }}</template>
        </el-table-column>
        <el-table-column prop="dept" label="需求部门" width="110" sortable="custom" show-overflow-tooltip>
          <template #default="{ row }">{{ row.dept || '-' }}</template>
        </el-table-column>
        <el-table-column prop="handler" label="处理人" width="90" sortable="custom">
          <template #default="{ row }">{{ row.handler || '-' }}</template>
        </el-table-column>
        <!-- 处理部门不占列宽:与处理人重复度高,详情/派发弹窗已展示 -->
        <el-table-column prop="priority" label="优先级" width="100" sortable="custom">
          <template #default="{ row }">
            <el-tag size="small" :type="dictTag(WORK_ORDER_PRIORITY, row.priority)" effect="light">
              {{ dictLabel(WORK_ORDER_PRIORITY, row.priority) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="100" sortable="custom">
          <template #default="{ row }">
            <el-tag size="small" :type="dictTag(WORK_ORDER_STATUS, row.status)" effect="plain">
              {{ dictLabel(WORK_ORDER_STATUS, row.status) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="deadline" label="期限" width="145" sortable="custom">
          <template #default="{ row }">
            <span :class="{ overdue: isOverdue(row) }">{{ row.deadline || '-' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="200" fixed="right">
          <template #default="{ row }">
            <!-- 状态机:待派发 → 派发,处理中 → 处理,已处理 → 结案,按钮与后端校验一一对应 -->
            <el-button v-if="row.status === 'PENDING'" link type="primary" size="small"
                       @click="openDispatch(row)">派发</el-button>
            <el-button v-else-if="row.status === 'PROCESSING'" link type="warning" size="small"
                       @click="openHandle(row)">处理</el-button>
            <el-button v-else-if="row.status === 'HANDLED'" link type="success" size="small"
                       @click="closeOrder(row)">结案</el-button>
            <el-button link type="primary" size="small" :disabled="row.status === 'CLOSED'"
                       @click="openDialog(row)">编辑</el-button>
            <el-popconfirm title="确认删除该工单?" @confirm="remove(row.id)">
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

    <!-- 派发:指定处理人后工单进入处理中 -->
    <el-dialog v-model="dispatch.visible" title="工单派发" width="480px">
      <el-form :model="dispatch.form" label-width="90px">
        <el-form-item label="处理人" required>
          <el-input v-model="dispatch.form.handler" placeholder="必填,派发后工单进入处理中" />
        </el-form-item>
        <el-form-item label="联系电话">
          <el-input v-model="dispatch.form.handlerPhone" />
        </el-form-item>
        <el-form-item label="处理部门">
          <el-input v-model="dispatch.form.handleDept" />
        </el-form-item>
        <el-form-item label="期限">
          <el-date-picker v-model="dispatch.form.deadline" type="datetime" value-format="YYYY-MM-DD HH:mm:ss"
                          style="width: 100%" placeholder="选择处置期限" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dispatch.visible = false">取消</el-button>
        <el-button type="primary" :loading="dispatch.saving" @click="submitDispatch">派发</el-button>
      </template>
    </el-dialog>

    <!-- 处理:填写处理结果后工单进入已处理 -->
    <el-dialog v-model="handle.visible" title="工单处理" width="480px">
      <el-form :model="handle.form" label-width="90px">
        <el-form-item label="处理结果" required>
          <el-input v-model="handle.form.result" type="textarea" :rows="4" placeholder="处置措施与完成情况(必填)" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="handle.visible = false">取消</el-button>
        <el-button type="primary" :loading="handle.saving" @click="submitHandle">提交</el-button>
      </template>
    </el-dialog>

    <!-- 编辑工单 -->
    <el-drawer v-model="dialog.visible" title="编辑工单" direction="rtl" size="520px">
      <el-form :model="dialog.form" label-width="90px">
        <el-form-item label="标题" required>
          <el-input v-model="dialog.form.title" />
        </el-form-item>
        <el-form-item label="优先级">
          <el-select v-model="dialog.form.priority" style="width: 100%">
            <el-option v-for="o in dictOptions(WORK_ORDER_PRIORITY)" :key="o.value" :label="o.label" :value="o.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="需求部门">
          <el-input v-model="dialog.form.dept" placeholder="提出处置需求的部门" />
        </el-form-item>
        <el-form-item label="处理部门">
          <el-input v-model="dialog.form.handleDept" placeholder="实际承接处置的部门" />
        </el-form-item>
        <el-form-item label="期限">
          <el-date-picker v-model="dialog.form.deadline" type="datetime" value-format="YYYY-MM-DD HH:mm:ss"
                          style="width: 100%" placeholder="选择处置期限" />
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="dialog.form.description" type="textarea" :rows="4" />
        </el-form-item>
        <!-- 工单由问题派生,来源与点位不可改,只做只读回溯展示 -->
        <el-alert v-if="dialog.form.issueTitle" type="info" :closable="false"
                  :title="`来源问题:${dialog.form.issueTitle}`" />
      </el-form>
      <template #footer>
        <el-button @click="dialog.visible = false">取消</el-button>
        <el-button type="primary" :loading="dialog.saving" @click="save">保存</el-button>
      </template>
    </el-drawer>
  </div>
</template>

<script setup>
import { ref, reactive, watch, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Search } from '@element-plus/icons-vue'
import http from '../../api'
import {
  WORK_ORDER_STATUS, WORK_ORDER_PRIORITY, ISSUE_TYPE,
  dictLabel, dictTag, dictOptions
} from '../../utils/dict'

const loading = ref(false)
const keyword = ref('')
const statusFilter = ref('')
const priorityFilter = ref('')
const deptFilter = ref('')
const handlerFilter = ref('')
const rows = ref([])
const stats = reactive({})
const pager = reactive({ page: 1, size: 10, total: 0 })
/* 可排序列必须落在 WorkOrderService 的白名单内,否则后端静默回退默认排序 */
const sort = reactive({ sortBy: '', direction: '' })
const dialog = reactive({ visible: false, saving: false, form: {} })
const dispatch = reactive({ visible: false, saving: false, row: null, form: {} })
const handle = reactive({ visible: false, saving: false, row: null, form: { result: '' } })

function seq(i) { return (pager.page - 1) * pager.size + i + 1 }
function onSort({ prop, order }) {
  sort.sortBy = order ? prop : ''
  sort.direction = order === 'ascending' ? 'asc' : 'desc'
  pager.page = 1
  load()
}

/** 期限已过且工单尚未结案时高亮;时间串已是 yyyy-MM-dd HH:mm:ss,可直接字典序比较 */
function isOverdue(row) {
  if (!row.deadline || row.status === 'CLOSED' || row.status === 'HANDLED') return false
  const d = new Date()
  const p = (n) => String(n).padStart(2, '0')
  const now = `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())} ${p(d.getHours())}:${p(d.getMinutes())}:${p(d.getSeconds())}`
  return row.deadline < now
}

onMounted(load)

async function load() {
  loading.value = true
  try {
    const [res, st] = await Promise.all([
      http.get('/work-orders/page', {
        params: {
          page: pager.page, size: pager.size,
          sortBy: sort.sortBy || undefined,
          direction: sort.sortBy ? sort.direction : undefined,
          keyword: keyword.value || undefined,
          status: statusFilter.value || undefined,
          priority: priorityFilter.value || undefined,
          dept: deptFilter.value || undefined,
          handler: handlerFilter.value || undefined
        }
      }),
      http.get('/work-orders/stats')
    ])
    rows.value = res.rows || []
    pager.total = res.total || 0
    Object.assign(stats, st)
  } finally { loading.value = false }
}

watch([statusFilter, priorityFilter], () => { pager.page = 1; load() })
function search() { pager.page = 1; load() }
function onSizeChange() { pager.page = 1; load() }

function openDispatch(row) {
  dispatch.row = row
  dispatch.form = { handler: row.handler || '', handlerPhone: row.handlerPhone || '', handleDept: row.handleDept || '', deadline: row.deadline || null }
  dispatch.visible = true
}

async function submitDispatch() {
  if (!dispatch.form.handler?.trim()) return ElMessage.warning('请填写处理人')
  dispatch.saving = true
  try {
    await http.post(`/work-orders/${dispatch.row.id}/dispatch`, dispatch.form)
    ElMessage.success('已派发')
    dispatch.visible = false
    load()
  } finally { dispatch.saving = false }
}

function openHandle(row) {
  handle.row = row
  handle.form = { result: '' }
  handle.visible = true
}

async function submitHandle() {
  if (!handle.form.result?.trim()) return ElMessage.warning('请填写处理结果')
  handle.saving = true
  try {
    await http.post(`/work-orders/${handle.row.id}/handle`, handle.form)
    ElMessage.success('处理结果已提交')
    handle.visible = false
    load()
  } finally { handle.saving = false }
}

/** 结案会连带关闭来源问题,属于不可逆操作,先二次确认 */
async function closeOrder(row) {
  await ElMessageBox.confirm('结案后将同步关闭来源问题,且工单不可再修改,确认继续?', '工单结案', { type: 'warning' })
  await http.post(`/work-orders/${row.id}/close`)
  ElMessage.success('已结案')
  load()
}

function openDialog(row) {
  dialog.form = {
    id: row.id, title: row.title, priority: row.priority, dept: row.dept,
    handleDept: row.handleDept, description: row.description,
    deadline: row.deadline, issueTitle: row.issueTitle
  }
  dialog.visible = true
}

async function save() {
  const f = dialog.form
  if (!f.title) return ElMessage.warning('标题不能为空')
  dialog.saving = true
  try {
    await http.put(`/work-orders/${f.id}`, f)
    ElMessage.success('已保存')
    dialog.visible = false
    load()
  } finally { dialog.saving = false }
}

async function remove(id) {
  await http.delete(`/work-orders/${id}`)
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
.stat-value.processing { color: var(--primary); }
.stat-value.handled { color: var(--success); }
.stat-value.closed { color: var(--text-faint); }

.table-panel { height: calc(100% - 122px); padding: 8px; display: flex; flex-direction: column; }
.toolbar { display: flex; gap: 10px; padding: 8px 8px 12px; flex-wrap: wrap; }
.toolbar + .el-table { flex: 1; }
.pager-row { display: flex; justify-content: flex-end; padding: 10px 4px 2px; }
.name { font-weight: 600; }
.code { font-family: monospace; font-size: 12.5px; color: var(--primary); }
.dim { color: var(--text-faint); font-size: 12.5px; }
.overdue { color: var(--danger); font-weight: 600; }
</style>
