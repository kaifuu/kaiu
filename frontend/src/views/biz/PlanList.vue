<template>
  <div class="page">
    <div class="page-header">
      <span class="page-title">巡检计划</span>
      <div class="actions">
        <el-button type="primary" @click="openDialog()">新增计划</el-button>
      </div>
    </div>

    <div class="panel table-panel">
      <div class="toolbar">
        <el-input v-model="keyword" placeholder="名称 / 编码 / 负责人" clearable style="width: 200px" @keyup.enter="search">
          <template #prefix><el-icon><Search /></el-icon></template>
        </el-input>
        <el-select v-model="categoryFilter" clearable placeholder="计划类别" style="width: 130px">
          <el-option v-for="o in dictOptions(PLAN_CATEGORY)" :key="o.value" :label="o.label" :value="o.value" />
        </el-select>
        <el-select v-model="statusFilter" clearable placeholder="状态" style="width: 120px">
          <el-option v-for="o in dictOptions(PLAN_STATUS)" :key="o.value" :label="o.label" :value="o.value" />
        </el-select>
        <el-input v-model="ownerFilter" placeholder="负责人(精确匹配)" clearable style="width: 160px" @keyup.enter="search" />
        <el-button @click="search">查询</el-button>
      </div>

      <el-table :data="rows" v-loading="loading" stripe height="100%" @sort-change="onSort">
        <el-table-column type="index" label="序号" width="56" :index="seq" />
        <el-table-column prop="name" label="计划名称" min-width="180" sortable="custom" show-overflow-tooltip>
          <template #default="{ row }"><span class="name">{{ row.name }}</span></template>
        </el-table-column>
        <el-table-column prop="code" label="计划编码" width="130" sortable="custom">
          <template #default="{ row }"><span class="code">{{ row.code }}</span></template>
        </el-table-column>
        <el-table-column prop="category" label="计划类别" width="110" sortable="custom">
          <template #default="{ row }">
            <el-tag size="small" :type="dictTag(PLAN_CATEGORY, row.category)" effect="light">
              {{ dictLabel(PLAN_CATEGORY, row.category) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="巡检周期" width="110">
          <template #default="{ row }">{{ cycleText(row.cycleType, row.cycleValue) }}</template>
        </el-table-column>
        <el-table-column prop="startDate" label="计划起止" width="200" sortable="custom">
          <template #default="{ row }">
            <span class="sub">{{ row.startDate || '-' }} ~ {{ row.endDate || '-' }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="owner" label="负责人" width="130">
          <template #default="{ row }">
            <div>{{ row.owner || '-' }}</div>
            <div class="sub">{{ row.ownerPhone || '-' }}</div>
          </template>
        </el-table-column>
        <el-table-column label="覆盖点位" width="100">
          <template #default="{ row }">
            <span class="count">{{ pointCount(row) }}</span> 个
          </template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="100" sortable="custom">
          <template #default="{ row }">
            <el-tag size="small" :type="dictTag(PLAN_STATUS, row.status)" effect="plain">
              {{ dictLabel(PLAN_STATUS, row.status) }}
            </el-tag>
          </template>
        </el-table-column>
        <!-- 创建时间不占列宽:计划列表按业务字段扫描,表宽超出时它会挤掉状态列 -->
        <el-table-column label="操作" width="190" fixed="right">
          <template #default="{ row }">
            <el-button v-if="row.status === 'ENABLED'" link type="success" size="small"
                       :loading="generatingId === row.id" @click="generate(row)">生成任务</el-button>
            <el-button link type="primary" size="small" @click="openDialog(row)">编辑</el-button>
            <el-popconfirm title="确认删除该计划?" @confirm="remove(row.id)">
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

    <el-drawer v-model="dialog.visible" :title="dialog.form.id ? '编辑计划' : '新增计划'" direction="rtl" size="620px">
      <el-form :model="dialog.form" label-width="90px">
        <el-row :gutter="12">
          <el-col :span="12">
            <el-form-item label="计划名称" required>
              <el-input v-model="dialog.form.name" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="计划编码" required>
              <el-input v-model="dialog.form.code" :disabled="!!dialog.form.id" placeholder="如 PL-2026-004" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="12">
          <el-col :span="12">
            <el-form-item label="计划类别">
              <el-select v-model="dialog.form.category" style="width: 100%">
                <el-option v-for="o in dictOptions(PLAN_CATEGORY)" :key="o.value" :label="o.label" :value="o.value" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="状态">
              <el-select v-model="dialog.form.status" style="width: 100%">
                <el-option v-for="o in dictOptions(PLAN_STATUS)" :key="o.value" :label="o.label" :value="o.value" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="12">
          <el-col :span="12">
            <el-form-item label="周期单位">
              <el-select v-model="dialog.form.cycleType" style="width: 100%">
                <el-option v-for="o in dictOptions(CYCLE_TYPE)" :key="o.value" :label="o.label" :value="o.value" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="周期值">
              <el-input-number v-model="dialog.form.cycleValue" :min="1" :max="365"
                               :disabled="dialog.form.cycleType === 'ONCE'" style="width: 100%" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="12">
          <el-col :span="12">
            <el-form-item label="开始日期">
              <el-date-picker v-model="dialog.form.startDate" type="date" value-format="YYYY-MM-DD"
                              style="width: 100%" placeholder="选择日期" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="结束日期">
              <el-date-picker v-model="dialog.form.endDate" type="date" value-format="YYYY-MM-DD"
                              style="width: 100%" placeholder="选择日期" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="12">
          <el-col :span="12">
            <el-form-item label="负责人">
              <el-input v-model="dialog.form.owner" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="联系电话">
              <el-input v-model="dialog.form.ownerPhone" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="覆盖点位">
          <el-select v-model="dialog.pointIdList" multiple filterable collapse-tags collapse-tags-tooltip
                     placeholder="选择本计划覆盖的巡检点位" style="width: 100%">
            <el-option v-for="p in allPoints" :key="p.id" :label="`${p.name}(${p.code})`" :value="p.id" />
          </el-select>
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
  </div>
</template>

<script setup>
import { ref, reactive, watch, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { Search } from '@element-plus/icons-vue'
import http from '../../api'
import {
  PLAN_CATEGORY, PLAN_STATUS, CYCLE_TYPE,
  dictLabel, dictTag, dictOptions, cycleText, parseIdList
} from '../../utils/dict'

const loading = ref(false)
const keyword = ref('')
const categoryFilter = ref(null)
const statusFilter = ref(null)
const ownerFilter = ref('')
const rows = ref([])
const allPoints = ref([])
const generatingId = ref(null)
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

/** pointIds 是 JSON 数组字符串,列表只展示覆盖数量 */
function pointCount(row) { return parseIdList(row.pointIds).length }

const dialog = reactive({ visible: false, saving: false, form: {}, pointIdList: [] })

onMounted(load)
async function load() {
  loading.value = true
  try {
    const [res] = await Promise.all([
      http.get('/plans/page', {
        params: {
          page: pager.page, size: pager.size,
          sortBy: sort.sortBy || undefined,
          direction: sort.sortBy ? sort.direction : undefined,
          keyword: keyword.value || undefined,
          category: categoryFilter.value || undefined,
          status: statusFilter.value || undefined,
          owner: ownerFilter.value || undefined
        }
      }),
      http.get('/points').then((p) => { allPoints.value = p })
    ])
    rows.value = res.rows || []
    pager.total = res.total || 0
  } finally { loading.value = false }
}

/** 筛选条件变化回到第一页再查;翻页保持当前页 */
watch([categoryFilter, statusFilter], () => { pager.page = 1; load() })

function search() {
  pager.page = 1
  load()
}

function onSizeChange() {
  pager.page = 1
  load()
}

function openDialog(row) {
  if (row) {
    dialog.form = {
      id: row.id, name: row.name, code: row.code, category: row.category,
      cycleType: row.cycleType, cycleValue: row.cycleValue,
      startDate: row.startDate, endDate: row.endDate,
      owner: row.owner, ownerPhone: row.ownerPhone,
      status: row.status, remark: row.remark
    }
    dialog.pointIdList = parseIdList(row.pointIds)
  } else {
    dialog.form = {
      id: null, name: '', code: '', category: 'DAILY',
      cycleType: 'DAY', cycleValue: 1,
      startDate: null, endDate: null,
      owner: '', ownerPhone: '', status: 'DRAFT', remark: ''
    }
    dialog.pointIdList = []
  }
  dialog.visible = true
}

async function save() {
  const f = dialog.form
  if (!f.name || !f.code) return ElMessage.warning('名称与编码不能为空')
  if (f.startDate && f.endDate && f.endDate < f.startDate) return ElMessage.warning('结束日期不能早于开始日期')
  dialog.saving = true
  try {
    // pointIds 与 menuIdsJson 同款约定:提交字符串形式的 JSON 数组
    const body = { ...f, pointIds: JSON.stringify(dialog.pointIdList) }
    if (f.id) await http.put(`/plans/${f.id}`, body)
    else await http.post('/plans', body)
    ElMessage.success('已保存')
    dialog.visible = false
    load()
  } finally { dialog.saving = false }
}

/** 按覆盖点位一键生成待执行任务(仅已启用计划可生成) */
async function generate(row) {
  generatingId.value = row.id
  try {
    const res = await http.post(`/plans/${row.id}/generate-tasks`)
    ElMessage.success(`已生成 ${res?.created ?? 0} 条任务`)
    load()
  } finally { generatingId.value = null }
}

async function remove(id) {
  await http.delete(`/plans/${id}`)
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
.code { font-family: monospace; font-size: 13px; color: var(--primary); }
.sub { font-size: 12px; color: var(--text-faint); }
.count { font-weight: 700; color: var(--primary); }
</style>
