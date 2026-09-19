<template>
  <div class="page">
    <div class="page-header">
      <span class="page-title">问题清单</span>
      <div class="actions">
        <el-button type="primary" @click="openDialog()">新增问题</el-button>
      </div>
    </div>

    <div class="panel table-panel">
      <div class="toolbar">
        <el-input v-model="keyword" placeholder="标题 / 点位 / 设备 / 地点" clearable style="width: 220px" @keyup.enter="search">
          <template #prefix><el-icon><Search /></el-icon></template>
        </el-input>
        <el-select v-model="typeFilter" clearable placeholder="问题类型" style="width: 140px">
          <el-option v-for="o in dictOptions(ISSUE_TYPE)" :key="o.value" :label="o.label" :value="o.value" />
        </el-select>
        <el-select v-model="statusFilter" clearable placeholder="状态" style="width: 130px">
          <el-option v-for="o in dictOptions(ISSUE_STATUS)" :key="o.value" :label="o.label" :value="o.value" />
        </el-select>
        <el-select v-model="sourceFilter" clearable placeholder="来源" style="width: 130px">
          <el-option v-for="o in dictOptions(ISSUE_SOURCE)" :key="o.value" :label="o.label" :value="o.value" />
        </el-select>
        <!-- 需求部门后端是精确匹配,且无部门字典可选项,故用输入框而非下拉 -->
        <el-input v-model="deptFilter" placeholder="需求部门" clearable style="width: 140px" @keyup.enter="search" />
        <el-button @click="search">查询</el-button>
      </div>

      <el-table :data="rows" v-loading="loading" stripe height="100%" @sort-change="onSort">
        <el-table-column type="index" label="序号" width="56" :index="seq" />
        <el-table-column prop="title" label="标题" min-width="190" sortable="custom" show-overflow-tooltip>
          <template #default="{ row }"><span class="name">{{ row.title }}</span></template>
        </el-table-column>
        <el-table-column prop="issueType" label="问题类型" width="110" sortable="custom">
          <template #default="{ row }">
            <el-tag size="small" :type="dictTag(ISSUE_TYPE, row.issueType)" effect="light">
              {{ dictLabel(ISSUE_TYPE, row.issueType) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="pointName" label="点位" min-width="130" sortable="custom" show-overflow-tooltip>
          <template #default="{ row }">{{ row.pointName || '-' }}</template>
        </el-table-column>
        <el-table-column prop="address" label="地点" min-width="140" show-overflow-tooltip>
          <template #default="{ row }">{{ row.address || '-' }}</template>
        </el-table-column>
        <el-table-column prop="deviceName" label="设备名称" min-width="120" sortable="custom" show-overflow-tooltip>
          <template #default="{ row }">{{ row.deviceName || '-' }}</template>
        </el-table-column>
        <el-table-column prop="dept" label="需求部门" width="110" sortable="custom" show-overflow-tooltip>
          <template #default="{ row }">{{ row.dept || '-' }}</template>
        </el-table-column>
        <el-table-column prop="foundAt" label="发现时间" width="150" sortable="custom">
          <template #default="{ row }">{{ row.foundAt || '-' }}</template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="110" sortable="custom">
          <template #default="{ row }">
            <el-tag size="small" :type="dictTag(ISSUE_STATUS, row.status)" effect="plain">
              {{ dictLabel(ISSUE_STATUS, row.status) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="190" fixed="right">
          <template #default="{ row }">
            <!-- 已生成工单/已结案的问题后端会拒绝再次派单,前端同步禁用避免无谓的 400 -->
            <el-tooltip :disabled="canDispatch(row)" :content="dispatchTip(row)" placement="top">
              <span class="tip-wrap">
                <el-button link type="warning" size="small" :disabled="!canDispatch(row)"
                           @click="createWorkOrder(row)">生成工单</el-button>
              </span>
            </el-tooltip>
            <el-button link type="primary" size="small" :disabled="row.status === 'CLOSED'"
                       @click="openDialog(row)">编辑</el-button>
            <!-- 已生成工单的问题须由工单结案后回写状态,直接结案会被后端 400 拒绝 -->
            <el-tooltip v-if="row.status === 'WORK_ORDER'" content="已生成工单,请在工单中结案" placement="top">
              <span class="tip-wrap">
                <el-button link type="success" size="small" disabled>结案</el-button>
              </span>
            </el-tooltip>
            <el-button v-else-if="row.status !== 'CLOSED'" link type="success" size="small"
                       @click="closeIssue(row)">结案</el-button>
            <el-popconfirm title="确认删除该问题记录?" @confirm="remove(row.id)">
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

    <!-- 上报 / 编辑问题 -->
    <el-drawer v-model="dialog.visible" :title="dialog.form.id ? '编辑问题' : '新增问题'" direction="rtl" size="580px">
      <el-form :model="dialog.form" label-width="90px">
        <el-form-item label="标题" required>
          <el-input v-model="dialog.form.title" placeholder="如 疑似河道违建" />
        </el-form-item>
        <el-row :gutter="12">
          <el-col :span="12">
            <el-form-item label="问题类型">
              <el-select v-model="dialog.form.issueType" style="width: 100%">
                <el-option v-for="o in dictOptions(ISSUE_TYPE)" :key="o.value" :label="o.label" :value="o.value" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="来源">
              <el-select v-model="dialog.form.source" style="width: 100%">
                <el-option v-for="o in dictOptions(ISSUE_SOURCE)" :key="o.value" :label="o.label" :value="o.value" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>
        <!-- 选中点位后由后端回填点位名与经纬度,这里同步带出地点便于人工核对/微调 -->
        <el-form-item label="点位">
          <el-select v-model="dialog.form.pointId" clearable filterable style="width: 100%" @change="onPointChange">
            <el-option v-for="p in points" :key="p.id" :label="`${p.name}(${p.code})`" :value="p.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="地点">
          <el-input v-model="dialog.form.address" placeholder="留空则取所选点位的地址" />
        </el-form-item>
        <el-row :gutter="12">
          <el-col :span="12">
            <el-form-item label="经度">
              <el-input-number v-model="dialog.form.longitude" :precision="6" :controls="false"
                               style="width: 100%" placeholder="如 116.123456" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="纬度">
              <el-input-number v-model="dialog.form.latitude" :precision="6" :controls="false"
                               style="width: 100%" placeholder="如 39.123456" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="设备名称">
          <el-input v-model="dialog.form.deviceName" placeholder="发现该问题的设备(可选)" />
        </el-form-item>
        <el-row :gutter="12">
          <el-col :span="12">
            <el-form-item label="需求部门">
              <el-input v-model="dialog.form.dept" placeholder="如 水务局" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="发现时间">
              <el-date-picker v-model="dialog.form.foundAt" type="datetime" value-format="YYYY-MM-DD HH:mm:ss"
                              style="width: 100%" placeholder="默认当前时间" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="描述">
          <el-input v-model="dialog.form.description" type="textarea" :rows="4" placeholder="现场情况与判定依据" />
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
import { ElMessage, ElMessageBox } from 'element-plus'
import { Search } from '@element-plus/icons-vue'
import http from '../../api'
import { ISSUE_TYPE, ISSUE_STATUS, ISSUE_SOURCE, dictLabel, dictTag, dictOptions } from '../../utils/dict'

const loading = ref(false)
const keyword = ref('')
const typeFilter = ref('')
const statusFilter = ref('')
const sourceFilter = ref('')
const deptFilter = ref('')
const rows = ref([])
const points = ref([])
const pager = reactive({ page: 1, size: 10, total: 0 })
/* 序号(跨页连续) + 服务端排序;可排序列必须落在 IssueService 的白名单内 */
const sort = reactive({ sortBy: '', direction: '' })
const dialog = reactive({ visible: false, saving: false, form: {} })

function seq(i) { return (pager.page - 1) * pager.size + i + 1 }
function onSort({ prop, order }) {
  sort.sortBy = order ? prop : ''
  sort.direction = order === 'ascending' ? 'asc' : 'desc'
  pager.page = 1
  load()
}

/** 已生成工单的问题不能再派单;已结案的问题后端也会拒绝 */
function canDispatch(row) { return row.status !== 'WORK_ORDER' && row.status !== 'CLOSED' }
function dispatchTip(row) {
  return row.status === 'WORK_ORDER' ? '该问题已生成过工单,请勿重复派发' : '已结案的问题无法再生成工单'
}

onMounted(load)

async function load() {
  loading.value = true
  try {
    const [res] = await Promise.all([
      http.get('/issues/page', {
        params: {
          page: pager.page, size: pager.size,
          sortBy: sort.sortBy || undefined,
          direction: sort.sortBy ? sort.direction : undefined,
          keyword: keyword.value || undefined,
          issueType: typeFilter.value || undefined,
          status: statusFilter.value || undefined,
          source: sourceFilter.value || undefined,
          dept: deptFilter.value || undefined
        }
      }),
      // 点位下拉为全量接口,只需拉一次
      http.get('/points').then((p) => { points.value = p })
    ])
    rows.value = res.rows || []
    pager.total = res.total || 0
  } finally { loading.value = false }
}

/** 筛选条件变化回到第一页再查,避免停留在越界页码上出现空列表 */
watch([typeFilter, statusFilter, sourceFilter], () => { pager.page = 1; load() })
function search() { pager.page = 1; load() }
function onSizeChange() { pager.page = 1; load() }

/** 选点位时把地址/经纬度带进表单,省去人工抄录(用户仍可改) */
function onPointChange(id) {
  const p = points.value.find((x) => x.id === id)
  if (!p) return
  dialog.form.address = p.address || dialog.form.address
  dialog.form.longitude = p.longitude ?? dialog.form.longitude
  dialog.form.latitude = p.latitude ?? dialog.form.latitude
}

function openDialog(row) {
  dialog.form = row
    ? {
        id: row.id, title: row.title, issueType: row.issueType, pointId: row.pointId,
        address: row.address, longitude: row.longitude, latitude: row.latitude,
        deviceName: row.deviceName, dept: row.dept, source: row.source,
        foundAt: row.foundAt, description: row.description
      }
    : {
        id: null, title: '', issueType: 'OTHER', pointId: null, address: '',
        longitude: null, latitude: null, deviceName: '', dept: '',
        source: 'MANUAL', foundAt: null, description: ''
      }
  dialog.visible = true
}

async function save() {
  const f = dialog.form
  if (!f.title) return ElMessage.warning('标题不能为空')
  dialog.saving = true
  try {
    if (f.id) await http.put(`/issues/${f.id}`, f)
    else await http.post('/issues', f)
    ElMessage.success('已保存')
    dialog.visible = false
    load()
  } finally { dialog.saving = false }
}

/** 问题转工单:后端自动带过点位/部门/类型并回写问题状态 */
async function createWorkOrder(row) {
  const res = await http.post(`/work-orders/from-issue/${row.id}`, {})
  ElMessage.success(`已生成工单 ${res?.code || ''}`)
  load()
}

async function closeIssue(row) {
  await ElMessageBox.confirm('结案后该问题将不再流转,确认继续?', '问题结案', { type: 'warning' })
  await http.post(`/issues/${row.id}/close`)
  ElMessage.success('已结案')
  load()
}

async function remove(id) {
  await http.delete(`/issues/${id}`)
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
/* el-button 禁用后不派发鼠标事件,包一层 span 才能让 tooltip 正常弹出 */
.tip-wrap { display: inline-block; }
</style>
