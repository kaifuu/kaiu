<template>
  <div class="page">
    <div class="page-header">
      <span class="page-title">巡检任务</span>
      <div class="actions">
        <el-button type="primary" @click="openDialog()">新增任务</el-button>
      </div>
    </div>

    <div class="panel table-panel">
      <div class="toolbar">
        <el-input v-model="keyword" placeholder="任务名 / 点位名 / 执行人" clearable style="width: 200px" @keyup.enter="search">
          <template #prefix><el-icon><Search /></el-icon></template>
        </el-input>
        <el-select v-model="planFilter" clearable filterable placeholder="所属计划" style="width: 180px">
          <el-option v-for="p in plans" :key="p.id" :label="p.name" :value="p.id" />
        </el-select>
        <el-select v-model="pointFilter" clearable filterable placeholder="巡检点位" style="width: 180px">
          <el-option v-for="p in points" :key="p.id" :label="p.name" :value="p.id" />
        </el-select>
        <el-select v-model="statusFilter" clearable placeholder="任务状态" style="width: 120px">
          <el-option v-for="o in dictOptions(TASK_STATUS)" :key="o.value" :label="o.label" :value="o.value" />
        </el-select>
        <el-select v-model="resultFilter" clearable placeholder="巡检结论" style="width: 120px">
          <el-option v-for="o in dictOptions(TASK_RESULT)" :key="o.value" :label="o.label" :value="o.value" />
        </el-select>
        <el-input v-model="executorFilter" placeholder="执行人(精确匹配)" clearable style="width: 150px" @keyup.enter="search" />
        <el-date-picker v-model="dateRange" type="daterange" value-format="YYYY-MM-DD" unlink-panels
                        range-separator="至" start-placeholder="计划开始" end-placeholder="计划结束"
                        style="width: 250px" @change="search" />
        <el-button @click="search">查询</el-button>
      </div>

      <el-table :data="rows" v-loading="loading" stripe height="100%" @sort-change="onSort">
        <el-table-column type="index" label="序号" width="56" :index="seq" />
        <el-table-column prop="name" label="任务名称" min-width="220" sortable="custom" show-overflow-tooltip>
          <template #default="{ row }"><span class="name">{{ row.name }}</span></template>
        </el-table-column>
        <el-table-column prop="pointName" label="巡检点位" min-width="150" sortable="custom" show-overflow-tooltip>
          <template #default="{ row }">{{ row.pointName || '-' }}</template>
        </el-table-column>
        <el-table-column prop="executor" label="执行人" width="115" sortable="custom">
          <template #default="{ row }">
            <div>{{ row.executor || '-' }}</div>
            <div class="sub">{{ row.executorPhone || '-' }}</div>
          </template>
        </el-table-column>
        <!-- 计划起止合并为一列:三列时间会把「状态/结论」挤出可视区(表宽超出时被右侧固定列盖住) -->
        <el-table-column prop="planStart" label="计划时间" width="175" sortable="custom">
          <template #default="{ row }">
            <div>{{ row.planStart || '-' }}</div>
            <div class="sub">{{ row.planEnd || '-' }}</div>
          </template>
        </el-table-column>
        <el-table-column prop="actualEnd" label="实际完成" width="155" sortable="custom">
          <template #default="{ row }">{{ row.actualEnd || '-' }}</template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="100" sortable="custom">
          <template #default="{ row }">
            <el-tag size="small" :type="dictTag(TASK_STATUS, row.status)" effect="plain">
              {{ dictLabel(TASK_STATUS, row.status) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="result" label="巡检结论" width="100" sortable="custom">
          <template #default="{ row }">
            <el-tag v-if="row.result" size="small" :type="dictTag(TASK_RESULT, row.result)" effect="light">
              {{ dictLabel(TASK_RESULT, row.result) }}
            </el-tag>
            <span v-else class="sub">-</span>
          </template>
        </el-table-column>
        <!-- 备注不占列宽:详情抽屉已完整展示,留在表里只会挤压状态列 -->
        <el-table-column label="操作" width="250" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="openDetail(row)">详情</el-button>
            <el-button v-if="row.status === 'PENDING' || row.status === 'OVERDUE'" link type="success" size="small"
                       @click="start(row)">开始执行</el-button>
            <el-button v-if="row.status === 'RUNNING'" link type="success" size="small"
                       @click="openFinish(row)">完成</el-button>
            <el-button v-if="row.status !== 'DONE'" link type="warning" size="small"
                       @click="cancel(row)">取消</el-button>
            <el-button link type="primary" size="small" @click="openDialog(row)">编辑</el-button>
            <el-popconfirm title="确认删除该任务?" @confirm="remove(row.id)">
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

    <!-- 编辑/新增任务 -->
    <el-drawer v-model="dialog.visible" :title="dialog.form.id ? '编辑任务' : '新增任务'" direction="rtl" size="560px">
      <el-form :model="dialog.form" label-width="90px">
        <el-form-item label="任务名称" required>
          <el-input v-model="dialog.form.name" />
        </el-form-item>
        <el-form-item label="巡检点位">
          <el-select v-model="dialog.form.pointId" clearable filterable style="width: 100%">
            <el-option v-for="p in points" :key="p.id" :label="`${p.name}(${p.code})`" :value="p.id" />
          </el-select>
        </el-form-item>
        <el-row :gutter="12">
          <el-col :span="12">
            <el-form-item label="执行人">
              <el-input v-model="dialog.form.executor" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="联系电话">
              <el-input v-model="dialog.form.executorPhone" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="12">
          <el-col :span="12">
            <el-form-item label="计划开始">
              <el-date-picker v-model="dialog.form.planStart" type="datetime" value-format="YYYY-MM-DD HH:mm:ss"
                              style="width: 100%" placeholder="选择时间" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="计划结束">
              <el-date-picker v-model="dialog.form.planEnd" type="datetime" value-format="YYYY-MM-DD HH:mm:ss"
                              style="width: 100%" placeholder="选择时间" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="备注">
          <el-input v-model="dialog.form.remark" type="textarea" :rows="2" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialog.visible = false">取消</el-button>
        <el-button type="primary" :loading="dialog.saving" @click="save">保存</el-button>
      </template>
    </el-drawer>

    <!-- 完成任务:必须给出巡检结论 -->
    <el-dialog v-model="finish.visible" title="完成任务" width="460px">
      <el-form :model="finish.form" label-width="90px">
        <el-form-item label="巡检结论" required>
          <el-radio-group v-model="finish.form.result">
            <el-radio value="NORMAL">正常</el-radio>
            <el-radio value="ABNORMAL">异常</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="finish.form.remark" type="textarea" :rows="3" placeholder="巡检情况说明(可选)" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="finish.visible = false">取消</el-button>
        <el-button type="primary" :loading="finish.saving" @click="submitFinish">提交</el-button>
      </template>
    </el-dialog>

    <!-- 任务详情 + 关联隐患 -->
    <el-drawer v-model="detail.visible" title="任务详情" direction="rtl" size="620px">
      <el-descriptions :column="2" border size="small" v-loading="detail.loading">
        <el-descriptions-item label="任务名称" :span="2">{{ detail.row.name }}</el-descriptions-item>
        <el-descriptions-item label="巡检点位">{{ detail.row.pointName || '-' }}</el-descriptions-item>
        <el-descriptions-item label="执行人">{{ detail.row.executor || '-' }}</el-descriptions-item>
        <el-descriptions-item label="联系电话">{{ detail.row.executorPhone || '-' }}</el-descriptions-item>
        <el-descriptions-item label="任务状态">
          <el-tag size="small" :type="dictTag(TASK_STATUS, detail.row.status)" effect="plain">
            {{ dictLabel(TASK_STATUS, detail.row.status) }}
          </el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="计划开始">{{ detail.row.planStart || '-' }}</el-descriptions-item>
        <el-descriptions-item label="计划结束">{{ detail.row.planEnd || '-' }}</el-descriptions-item>
        <el-descriptions-item label="实际开始">{{ detail.row.actualStart || '-' }}</el-descriptions-item>
        <el-descriptions-item label="实际完成">{{ detail.row.actualEnd || '-' }}</el-descriptions-item>
        <el-descriptions-item label="巡检结论">
          <el-tag v-if="detail.row.result" size="small" :type="dictTag(TASK_RESULT, detail.row.result)" effect="light">
            {{ dictLabel(TASK_RESULT, detail.row.result) }}
          </el-tag>
          <span v-else>-</span>
        </el-descriptions-item>
        <el-descriptions-item label="备注" :span="2">{{ detail.row.remark || '-' }}</el-descriptions-item>
      </el-descriptions>

      <el-divider content-position="left">关联隐患({{ detail.hazards.length }})</el-divider>
      <div v-if="!detail.hazards.length" class="empty">该任务下暂无隐患上报</div>
      <div v-for="h in detail.hazards" :key="h.id" class="hazard-card">
        <div class="hc-head">
          <span class="hc-title">{{ h.title }}</span>
          <el-tag size="small" :type="dictTag(HAZARD_LEVEL, h.level)" effect="light">
            {{ dictLabel(HAZARD_LEVEL, h.level) }}
          </el-tag>
        </div>
        <div class="hc-sub">上报人 {{ h.reporter || '-' }} · {{ h.reportTime || '-' }}</div>
        <div class="hc-desc">{{ h.description || '无描述' }}</div>
        <div class="hc-foot">
          <el-tag size="small" :type="dictTag(HAZARD_STATUS, h.status)" effect="plain">
            {{ dictLabel(HAZARD_STATUS, h.status) }}
          </el-tag>
          <span class="hc-result">{{ h.handleResult || '待处理' }}</span>
        </div>
      </div>
    </el-drawer>
  </div>
</template>

<script setup>
import { ref, reactive, watch, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { Search } from '@element-plus/icons-vue'
import http from '../../api'
import {
  TASK_STATUS, TASK_RESULT, HAZARD_LEVEL, HAZARD_STATUS,
  dictLabel, dictTag, dictOptions
} from '../../utils/dict'

const loading = ref(false)
const keyword = ref('')
const planFilter = ref(null)
const pointFilter = ref(null)
const statusFilter = ref(null)
const resultFilter = ref(null)
const executorFilter = ref('')
/** 日期区间按计划开始时间过滤,提交为 YYYY-MM-DD */
const dateRange = ref([])
const rows = ref([])
const plans = ref([])
const points = ref([])
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
const finish = reactive({ visible: false, saving: false, row: null, form: { result: 'NORMAL', remark: '' } })
const detail = reactive({ visible: false, loading: false, row: {}, hazards: [] })

onMounted(load)
async function load() {
  loading.value = true
  try {
    const [res] = await Promise.all([
      http.get('/tasks/page', {
        params: {
          page: pager.page, size: pager.size,
          sortBy: sort.sortBy || undefined,
          direction: sort.sortBy ? sort.direction : undefined,
          keyword: keyword.value || undefined,
          planId: planFilter.value || undefined,
          pointId: pointFilter.value || undefined,
          status: statusFilter.value || undefined,
          result: resultFilter.value || undefined,
          executor: executorFilter.value || undefined,
          startDate: dateRange.value?.[0] || undefined,
          endDate: dateRange.value?.[1] || undefined
        }
      }),
      http.get('/plans').then((p) => { plans.value = p }),
      http.get('/points').then((p) => { points.value = p })
    ])
    rows.value = res.rows || []
    pager.total = res.total || 0
  } finally { loading.value = false }
}

/** 筛选条件变化回到第一页再查;翻页保持当前页 */
watch([planFilter, pointFilter, statusFilter, resultFilter], () => { pager.page = 1; load() })

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
        id: row.id, name: row.name, pointId: row.pointId, executor: row.executor,
        executorPhone: row.executorPhone, planStart: row.planStart, planEnd: row.planEnd, remark: row.remark
      }
    : { id: null, name: '', pointId: null, executor: '', executorPhone: '', planStart: null, planEnd: null, remark: '' }
  dialog.visible = true
}

async function save() {
  const f = dialog.form
  if (!f.name) return ElMessage.warning('任务名称不能为空')
  if (f.planStart && f.planEnd && f.planEnd < f.planStart) return ElMessage.warning('计划结束不能早于开始')
  dialog.saving = true
  try {
    if (f.id) await http.put(`/tasks/${f.id}`, f)
    else await http.post('/tasks', f)
    ElMessage.success('已保存')
    dialog.visible = false
    load()
  } finally { dialog.saving = false }
}

/** 开始执行:PENDING / OVERDUE → RUNNING */
async function start(row) {
  await http.post(`/tasks/${row.id}/start`)
  ElMessage.success('任务已开始执行')
  load()
}

function openFinish(row) {
  finish.row = row
  finish.form = { result: 'NORMAL', remark: '' }
  finish.visible = true
}

async function submitFinish() {
  if (!finish.form.result) return ElMessage.warning('请选择巡检结论')
  finish.saving = true
  try {
    await http.post(`/tasks/${finish.row.id}/finish`, finish.form)
    ElMessage.success('任务已完成')
    finish.visible = false
    load()
  } finally { finish.saving = false }
}

async function cancel(row) {
  await http.post(`/tasks/${row.id}/cancel`)
  ElMessage.success('任务已取消')
  load()
}

async function remove(id) {
  await http.delete(`/tasks/${id}`)
  ElMessage.success('已删除')
  load()
}

/** 详情抽屉:任务字段 + 该任务下已上报的隐患 */
async function openDetail(row) {
  detail.row = row
  detail.hazards = []
  detail.visible = true
  detail.loading = true
  try {
    detail.hazards = await http.get(`/tasks/${row.id}/hazards`)
  } finally { detail.loading = false }
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
.empty { padding: 24px 0; text-align: center; color: var(--text-faint); font-size: 12.5px; }

.hazard-card {
  border: 1px solid var(--border); border-radius: 10px;
  padding: 10px 12px; margin-bottom: 10px;
}
.hc-head { display: flex; align-items: center; justify-content: space-between; gap: 10px; }
.hc-title { font-size: 13.5px; font-weight: 600; color: var(--text); }
.hc-sub { font-size: 11.5px; color: var(--text-faint); margin-top: 4px; }
.hc-desc { font-size: 12.5px; color: var(--text-dim); line-height: 1.6; margin-top: 6px; }
.hc-foot { display: flex; align-items: center; gap: 10px; margin-top: 8px; }
.hc-result { font-size: 12px; color: var(--text-dim); }
</style>
