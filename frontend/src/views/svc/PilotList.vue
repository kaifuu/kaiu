<template>
  <div class="page">
    <div class="page-header">
      <span class="page-title">飞手管理</span>
      <div class="actions">
        <el-button type="primary" @click="openDialog()">新增飞手</el-button>
      </div>
    </div>

    <div class="stats">
      <div class="stat"><span class="stat-label">可调度</span><b class="stat-value available">{{ stats.AVAILABLE ?? 0 }}</b></div>
      <div class="stat"><span class="stat-label">执行中</span><b class="stat-value on-task">{{ stats.ON_TASK ?? 0 }}</b></div>
      <div class="stat"><span class="stat-label">休假</span><b class="stat-value leave">{{ stats.LEAVE ?? 0 }}</b></div>
      <div class="stat"><span class="stat-label">停用</span><b class="stat-value disabled">{{ stats.DISABLED ?? 0 }}</b></div>
    </div>

    <div class="panel table-panel">
      <div class="toolbar">
        <el-input v-model="keyword" placeholder="姓名 / 手机号 / 片区" clearable style="width: 200px" @keyup.enter="search">
          <template #prefix><el-icon><Search /></el-icon></template>
        </el-input>
        <el-select v-model="statusFilter" clearable placeholder="状态" style="width: 120px">
          <el-option v-for="o in dictOptions(PILOT_STATUS)" :key="o.value" :label="o.label" :value="o.value" />
        </el-select>
        <el-select v-model="certFilter" clearable placeholder="证书类型" style="width: 140px">
          <el-option v-for="o in dictOptions(PILOT_CERT)" :key="o.value" :label="o.label" :value="o.value" />
        </el-select>
        <!-- 片区无字典表:从全量飞手里取去重值当选项,保证与后端精确匹配的取值一致 -->
        <el-select v-model="areaFilter" clearable filterable placeholder="所属片区" style="width: 150px">
          <el-option v-for="a in areas" :key="a" :label="a" :value="a" />
        </el-select>
        <el-button @click="search">查询</el-button>
      </div>

      <el-table :data="rows" v-loading="loading" stripe height="100%" @sort-change="onSort">
        <el-table-column type="index" label="序号" width="56" :index="seq" />
        <el-table-column prop="name" label="姓名" width="110" sortable="custom">
          <template #default="{ row }"><span class="name">{{ row.name }}</span></template>
        </el-table-column>
        <el-table-column prop="phone" label="手机号" width="130">
          <template #default="{ row }"><span class="code">{{ row.phone || '-' }}</span></template>
        </el-table-column>
        <el-table-column prop="age" label="年龄" width="80">
          <template #default="{ row }">{{ row.age ?? '-' }}</template>
        </el-table-column>
        <el-table-column prop="experienceYears" label="经验" width="100" sortable="custom">
          <template #default="{ row }">{{ row.experienceYears != null ? `${row.experienceYears} 年` : '-' }}</template>
        </el-table-column>
        <el-table-column prop="area" label="所属片区" min-width="150" sortable="custom" show-overflow-tooltip>
          <template #default="{ row }">{{ row.area || '-' }}</template>
        </el-table-column>
        <el-table-column prop="certType" label="证书类型" width="140" sortable="custom">
          <template #default="{ row }">
            <el-tag size="small" :type="dictTag(PILOT_CERT, row.certType)" effect="light">
              {{ dictLabel(PILOT_CERT, row.certType) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="certOrg" label="发证机构" min-width="140" show-overflow-tooltip>
          <template #default="{ row }">{{ row.certOrg || '-' }}</template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="100" sortable="custom">
          <template #default="{ row }">
            <el-tag size="small" :type="dictTag(PILOT_STATUS, row.status)" effect="plain">
              {{ dictLabel(PILOT_STATUS, row.status) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="140" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="openDialog(row)">编辑</el-button>
            <el-popconfirm title="确认删除该飞手?" @confirm="remove(row.id)">
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

    <el-drawer v-model="dialog.visible" :title="dialog.form.id ? '编辑飞手' : '新增飞手'" direction="rtl" size="560px">
      <el-form :model="dialog.form" label-width="90px">
        <el-form-item label="姓名" required>
          <el-input v-model="dialog.form.name" />
        </el-form-item>
        <el-row :gutter="12">
          <el-col :span="12">
            <el-form-item label="手机号">
              <el-input v-model="dialog.form.phone" maxlength="11" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="年龄">
              <el-input-number v-model="dialog.form.age" :min="16" :max="80" :controls="false" style="width: 100%" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="12">
          <el-col :span="12">
            <el-form-item label="经验年限">
              <el-input-number v-model="dialog.form.experienceYears" :min="0" :max="60" :controls="false" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="所属片区">
              <el-input v-model="dialog.form.area" placeholder="如 潮白河片区" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="12">
          <el-col :span="12">
            <el-form-item label="证书类型">
              <el-select v-model="dialog.form.certType" style="width: 100%">
                <el-option v-for="o in dictOptions(PILOT_CERT)" :key="o.value" :label="o.label" :value="o.value" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="状态">
              <el-select v-model="dialog.form.status" style="width: 100%">
                <el-option v-for="o in dictOptions(PILOT_STATUS)" :key="o.value" :label="o.label" :value="o.value" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="发证机构">
          <el-input v-model="dialog.form.certOrg" placeholder="如 中国民用航空局" />
        </el-form-item>
        <el-form-item label="证书编号">
          <el-input v-model="dialog.form.certNo" />
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
import { PILOT_STATUS, PILOT_CERT, dictLabel, dictTag, dictOptions } from '../../utils/dict'

const loading = ref(false)
const keyword = ref('')
const statusFilter = ref('')
const certFilter = ref('')
const areaFilter = ref('')
const rows = ref([])
const allPilots = ref([])
const stats = reactive({})
const pager = reactive({ page: 1, size: 10, total: 0 })
/* 可排序列必须落在 PilotService 的白名单内(年龄/发证机构不在其中) */
const sort = reactive({ sortBy: '', direction: '' })
const dialog = reactive({ visible: false, saving: false, form: {} })

/** 片区候选值由全量飞手去重得到,避免手输片区名与库里对不上导致精确匹配查不到 */
const areas = computed(() => [...new Set(allPilots.value.map((p) => p.area).filter(Boolean))].sort())

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
    const [res, st, all] = await Promise.all([
      http.get('/pilots/page', {
        params: {
          page: pager.page, size: pager.size,
          sortBy: sort.sortBy || undefined,
          direction: sort.sortBy ? sort.direction : undefined,
          keyword: keyword.value || undefined,
          status: statusFilter.value || undefined,
          certType: certFilter.value || undefined,
          area: areaFilter.value || undefined
        }
      }),
      http.get('/pilots/stats'),
      http.get('/pilots').then((p) => { allPilots.value = p || [] })
    ])
    rows.value = res.rows || []
    pager.total = res.total || 0
    Object.assign(stats, st)
  } finally { loading.value = false }
}

watch([statusFilter, certFilter, areaFilter], () => { pager.page = 1; load() })
function search() { pager.page = 1; load() }
function onSizeChange() { pager.page = 1; load() }

function openDialog(row) {
  dialog.form = row
    ? {
        id: row.id, name: row.name, phone: row.phone, age: row.age,
        experienceYears: row.experienceYears, area: row.area, certType: row.certType,
        certOrg: row.certOrg, certNo: row.certNo, status: row.status, remark: row.remark
      }
    : {
        id: null, name: '', phone: '', age: null, experienceYears: null, area: '',
        certType: 'CAAC', certOrg: '', certNo: '', status: 'AVAILABLE', remark: ''
      }
  dialog.visible = true
}

async function save() {
  const f = dialog.form
  if (!f.name) return ElMessage.warning('姓名不能为空')
  dialog.saving = true
  try {
    if (f.id) await http.put(`/pilots/${f.id}`, f)
    else await http.post('/pilots', f)
    ElMessage.success('已保存')
    dialog.visible = false
    load()
  } finally { dialog.saving = false }
}

async function remove(id) {
  await http.delete(`/pilots/${id}`)
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
.stat-value.available { color: var(--success); }
.stat-value.on-task { color: var(--primary); }
.stat-value.leave { color: var(--warning); }
.stat-value.disabled { color: var(--text-faint); }

.table-panel { height: calc(100% - 122px); padding: 8px; display: flex; flex-direction: column; }
.toolbar { display: flex; gap: 10px; padding: 8px 8px 12px; flex-wrap: wrap; }
.toolbar + .el-table { flex: 1; }
.pager-row { display: flex; justify-content: flex-end; padding: 10px 4px 2px; }
.actions { display: flex; gap: 10px; }
.name { font-weight: 600; }
.code { font-family: monospace; font-size: 12.5px; color: var(--primary); }
</style>
