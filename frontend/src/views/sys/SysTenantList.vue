<template>
  <div class="page">
    <div class="page-header">
      <span class="page-title">租户管理</span>
      <div class="actions">
        <el-button type="primary" @click="openDialog()">新增租户</el-button>
      </div>
    </div>

    <div class="panel table-panel">
      <div class="toolbar">
        <el-input v-model="keyword" placeholder="租户名称 / 编码" clearable style="width: 200px" @keyup.enter="search">
          <template #prefix><el-icon><Search /></el-icon></template>
        </el-input>
        <el-select v-model="enabledFilter" clearable placeholder="状态" style="width: 110px">
          <el-option label="启用" :value="true" />
          <el-option label="停用" :value="false" />
        </el-select>
        <el-button @click="search">查询</el-button>
      </div>

      <el-table :data="rows" v-loading="loading" stripe height="100%" @sort-change="onSort">
        <el-table-column type="index" label="序号" width="56" :index="seq" />
        <el-table-column prop="name" label="租户名称" width="200" sortable="custom">
          <template #default="{ row }"><span class="name">{{ row.name }}</span></template>
        </el-table-column>
        <el-table-column prop="code" label="租户编码" width="140" sortable="custom">
          <template #default="{ row }"><span class="code">{{ row.code }}</span></template>
        </el-table-column>
        <el-table-column prop="remark" label="备注" min-width="200" show-overflow-tooltip />
        <el-table-column prop="enabled" label="状态" width="80" sortable="custom">
          <template #default="{ row }">
            <el-tag size="small" :type="row.enabled ? 'success' : 'info'" effect="plain">{{ row.enabled ? '启用' : '停用' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createTime" label="创建时间" width="165" sortable="custom">
          <template #default="{ row }">{{ row.createTime || '-' }}</template>
        </el-table-column>
        <el-table-column label="操作" width="120" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="openDialog(row)">编辑</el-button>
            <el-popconfirm title="确认删除该租户?" @confirm="remove(row.id)">
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

    <el-drawer v-model="dialog.visible" :title="dialog.form.id ? '编辑租户' : '新增租户'" direction="rtl" size="520px">
      <el-form :model="dialog.form" label-width="90px">
        <el-form-item label="租户名称" required>
          <el-input v-model="dialog.form.name" />
        </el-form-item>
        <el-form-item label="租户编码" required>
          <el-input v-model="dialog.form.code" :disabled="!!dialog.form.id" placeholder="如 PARK-01" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="dialog.form.remark" type="textarea" :rows="2" />
        </el-form-item>
        <el-form-item label="状态">
          <el-switch v-model="dialog.form.enabled" active-text="启用" inactive-text="停用" />
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

const loading = ref(false)
const keyword = ref('')
const enabledFilter = ref(null)
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

onMounted(load)
async function load() {
  loading.value = true
  try {
    const res = await http.get('/tenants/page', {
      params: {
        page: pager.page, size: pager.size,
        sortBy: sort.sortBy || undefined,
        direction: sort.sortBy ? sort.direction : undefined,
        keyword: keyword.value || undefined,
        enabled: enabledFilter.value === null || enabledFilter.value === '' ? undefined : enabledFilter.value
      }
    })
    rows.value = res.rows || []
    pager.total = res.total || 0
  } finally { loading.value = false }
}

/** 筛选条件变化回到第一页再查;翻页保持当前页 */
watch(enabledFilter, () => { pager.page = 1; load() })

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
    ? { id: row.id, name: row.name, code: row.code, remark: row.remark, enabled: row.enabled }
    : { id: null, name: '', code: '', remark: '', enabled: true }
  dialog.visible = true
}

async function save() {
  const f = dialog.form
  if (!f.name || !f.code) return ElMessage.warning('名称与编码不能为空')
  dialog.saving = true
  try {
    if (f.id) await http.put(`/tenants/${f.id}`, f)
    else await http.post('/tenants', f)
    ElMessage.success('已保存')
    dialog.visible = false
    load()
  } finally { dialog.saving = false }
}

async function remove(id) {
  await http.delete(`/tenants/${id}`)
  ElMessage.success('已删除')
  load()
}
</script>

<style scoped>
.table-panel { height: calc(100% - 50px); padding: 8px; display: flex; flex-direction: column; }
.toolbar { display: flex; gap: 10px; padding: 8px 8px 12px; }
.toolbar + .el-table { flex: 1; }
.pager-row { display: flex; justify-content: flex-end; padding: 10px 4px 2px; }
.actions { display: flex; gap: 10px; }
.name { font-weight: 600; }
.code { font-family: monospace; font-size: 13px; color: var(--primary); }
</style>
