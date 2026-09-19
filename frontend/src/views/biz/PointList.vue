<template>
  <div class="page">
    <div class="page-header">
      <span class="page-title">巡检点位</span>
      <div class="actions">
        <el-button type="primary" @click="openDialog()">新增点位</el-button>
      </div>
    </div>

    <div class="panel table-panel">
      <div class="toolbar">
        <el-input v-model="keyword" placeholder="名称 / 编码 / 地址" clearable style="width: 200px" @keyup.enter="search">
          <template #prefix><el-icon><Search /></el-icon></template>
        </el-input>
        <el-select v-model="categoryFilter" clearable placeholder="点位类型" style="width: 130px">
          <el-option v-for="o in dictOptions(POINT_CATEGORY)" :key="o.value" :label="o.label" :value="o.value" />
        </el-select>
        <el-select v-model="riskFilter" clearable placeholder="风险等级" style="width: 120px">
          <el-option v-for="o in dictOptions(RISK_LEVEL)" :key="o.value" :label="o.label" :value="o.value" />
        </el-select>
        <el-select v-model="statusFilter" clearable placeholder="状态" style="width: 110px">
          <el-option v-for="o in dictOptions(ENABLE_STATUS)" :key="o.value" :label="o.label" :value="o.value" />
        </el-select>
        <el-select v-model="areaFilter" clearable filterable placeholder="所属区域" style="width: 150px">
          <el-option v-for="a in areas" :key="a" :label="a" :value="a" />
        </el-select>
        <el-button @click="search">查询</el-button>
      </div>

      <el-table :data="rows" v-loading="loading" stripe height="100%" @sort-change="onSort">
        <el-table-column type="index" label="序号" width="56" :index="seq" />
        <el-table-column prop="name" label="点位名称" min-width="160" sortable="custom" show-overflow-tooltip>
          <template #default="{ row }"><span class="name">{{ row.name }}</span></template>
        </el-table-column>
        <el-table-column prop="code" label="点位编码" width="130" sortable="custom">
          <template #default="{ row }"><span class="code">{{ row.code }}</span></template>
        </el-table-column>
        <el-table-column prop="category" label="点位类型" width="110" sortable="custom">
          <template #default="{ row }">
            <el-tag size="small" :type="dictTag(POINT_CATEGORY, row.category)" effect="light">
              {{ dictLabel(POINT_CATEGORY, row.category) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="riskLevel" label="风险等级" width="100" sortable="custom">
          <template #default="{ row }">
            <el-tag size="small" :type="dictTag(RISK_LEVEL, row.riskLevel)" effect="light">
              {{ dictLabel(RISK_LEVEL, row.riskLevel) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="area" label="所属区域" width="130" sortable="custom">
          <template #default="{ row }">{{ row.area || '-' }}</template>
        </el-table-column>
        <el-table-column prop="address" label="详细地址" min-width="170" show-overflow-tooltip>
          <template #default="{ row }">{{ row.address || '-' }}</template>
        </el-table-column>
        <el-table-column label="责任人" width="130">
          <template #default="{ row }">
            <div>{{ row.manager || '-' }}</div>
            <div class="sub">{{ row.managerPhone || '-' }}</div>
          </template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="90" sortable="custom">
          <template #default="{ row }">
            <el-tag size="small" :type="dictTag(ENABLE_STATUS, row.status)" effect="plain">
              {{ dictLabel(ENABLE_STATUS, row.status) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createTime" label="创建时间" width="155" sortable="custom">
          <template #default="{ row }">{{ row.createTime || '-' }}</template>
        </el-table-column>
        <el-table-column label="操作" width="120" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="openDialog(row)">编辑</el-button>
            <el-popconfirm title="确认删除该点位?" @confirm="remove(row.id)">
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

    <el-drawer v-model="dialog.visible" :title="dialog.form.id ? '编辑点位' : '新增点位'" direction="rtl" size="580px">
      <el-form :model="dialog.form" label-width="90px">
        <el-row :gutter="12">
          <el-col :span="12">
            <el-form-item label="点位名称" required>
              <el-input v-model="dialog.form.name" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="点位编码" required>
              <el-input v-model="dialog.form.code" :disabled="!!dialog.form.id" placeholder="如 PT-001" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="12">
          <el-col :span="12">
            <el-form-item label="点位类型">
              <el-select v-model="dialog.form.category" style="width: 100%">
                <el-option v-for="o in dictOptions(POINT_CATEGORY)" :key="o.value" :label="o.label" :value="o.value" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="风险等级">
              <el-select v-model="dialog.form.riskLevel" style="width: 100%">
                <el-option v-for="o in dictOptions(RISK_LEVEL)" :key="o.value" :label="o.label" :value="o.value" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="所属区域">
          <el-input v-model="dialog.form.area" placeholder="如 高新区 / 城东网格" />
        </el-form-item>
        <el-form-item label="详细地址">
          <el-input v-model="dialog.form.address" />
        </el-form-item>
        <el-row :gutter="12">
          <el-col :span="12">
            <el-form-item label="经度">
              <el-input-number v-model="dialog.form.longitude" :precision="6" :step="0.000001"
                               :controls="false" style="width: 100%" placeholder="如 118.123456" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="纬度">
              <el-input-number v-model="dialog.form.latitude" :precision="6" :step="0.000001"
                               :controls="false" style="width: 100%" placeholder="如 32.123456" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="经纬度">
          <span class="form-tip">经度与纬度需同时填写,仅填一项服务端会拒绝</span>
        </el-form-item>
        <el-row :gutter="12">
          <el-col :span="12">
            <el-form-item label="责任人">
              <el-input v-model="dialog.form.manager" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="联系电话">
              <el-input v-model="dialog.form.managerPhone" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="状态">
          <el-switch v-model="dialog.form.enabled" active-text="启用" inactive-text="停用" />
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
import { ref, reactive, computed, watch, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { Search } from '@element-plus/icons-vue'
import http from '../../api'
import {
  POINT_CATEGORY, RISK_LEVEL, ENABLE_STATUS,
  dictLabel, dictTag, dictOptions
} from '../../utils/dict'

const loading = ref(false)
const keyword = ref('')
const categoryFilter = ref(null)
const riskFilter = ref(null)
const statusFilter = ref(null)
const areaFilter = ref(null)
const rows = ref([])
const allPoints = ref([])
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

/** 区域筛选走服务端精确匹配,候选项从全量点位去重得到 */
const areas = computed(() => [...new Set(allPoints.value.map((p) => p.area).filter(Boolean))].sort())

const dialog = reactive({ visible: false, saving: false, form: {} })

onMounted(load)
async function load() {
  loading.value = true
  try {
    const [res] = await Promise.all([
      http.get('/points/page', {
        params: {
          page: pager.page, size: pager.size,
          sortBy: sort.sortBy || undefined,
          direction: sort.sortBy ? sort.direction : undefined,
          keyword: keyword.value || undefined,
          category: categoryFilter.value || undefined,
          riskLevel: riskFilter.value || undefined,
          status: statusFilter.value || undefined,
          area: areaFilter.value || undefined
        }
      }),
      http.get('/points').then((p) => { allPoints.value = p })
    ])
    rows.value = res.rows || []
    pager.total = res.total || 0
  } finally { loading.value = false }
}

/** 筛选条件变化回到第一页再查;翻页保持当前页 */
watch([categoryFilter, riskFilter, statusFilter, areaFilter], () => { pager.page = 1; load() })

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
        id: row.id, name: row.name, code: row.code, category: row.category, riskLevel: row.riskLevel,
        area: row.area, address: row.address, longitude: row.longitude, latitude: row.latitude,
        manager: row.manager, managerPhone: row.managerPhone,
        enabled: row.status === 'ENABLED', remark: row.remark
      }
    : {
        id: null, name: '', code: '', category: 'OTHER', riskLevel: 'LOW',
        area: '', address: '', longitude: null, latitude: null,
        manager: '', managerPhone: '', enabled: true, remark: ''
      }
  dialog.visible = true
}

async function save() {
  const f = dialog.form
  if (!f.name || !f.code) return ElMessage.warning('名称与编码不能为空')
  // 经纬度必须成对出现(服务端同样校验并 400)
  const hasLng = f.longitude !== null && f.longitude !== undefined && f.longitude !== ''
  const hasLat = f.latitude !== null && f.latitude !== undefined && f.latitude !== ''
  if (hasLng !== hasLat) return ElMessage.warning('经度与纬度需同时填写')
  dialog.saving = true
  try {
    const body = {
      name: f.name, code: f.code, category: f.category, riskLevel: f.riskLevel,
      area: f.area, address: f.address,
      longitude: hasLng ? f.longitude : null, latitude: hasLat ? f.latitude : null,
      manager: f.manager, managerPhone: f.managerPhone,
      status: f.enabled ? 'ENABLED' : 'DISABLED', remark: f.remark
    }
    if (f.id) await http.put(`/points/${f.id}`, body)
    else await http.post('/points', body)
    ElMessage.success('已保存')
    dialog.visible = false
    load()
  } finally { dialog.saving = false }
}

async function remove(id) {
  await http.delete(`/points/${id}`)
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
.sub { font-size: 12px; color: var(--text-faint); margin-top: 2px; }
.form-tip { font-size: 12px; color: var(--text-faint); line-height: 1.6; }
</style>
