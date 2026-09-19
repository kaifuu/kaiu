<template>
  <div class="page">
    <div class="page-header">
      <span class="page-title">组织管理</span>
      <div class="actions">
        <el-button type="primary" @click="openDialog(null)">新增组织</el-button>
      </div>
    </div>

    <div class="panel table-panel">
      <div class="toolbar">
        <el-input v-model="keyword" placeholder="组织名称 / 编码" clearable style="width: 200px" :prefix-icon="Search" />
        <el-select v-model="enabledFilter" clearable placeholder="状态" style="width: 110px">
          <el-option label="启用" :value="true" />
          <el-option label="停用" :value="false" />
        </el-select>
        <span v-if="keyword || enabledFilter !== null" class="filter-tip">
          命中 {{ flatCount }} 个组织(已保留上级链路)
        </span>
      </div>

      <el-table :data="pagedTree" v-loading="loading" row-key="id" default-expand-all @sort-change="onSort"
                :tree-props="{ children: 'children' }" stripe height="100%">
        <el-table-column type="index" label="序号" width="56" :index="seq" />
        <el-table-column prop="name" label="组织名称" min-width="240" sortable="custom">
          <template #default="{ row }"><span class="name">{{ row.name }}</span></template>
        </el-table-column>
        <el-table-column prop="orgCode" label="组织编码" width="140" sortable="custom">
          <template #default="{ row }"><span class="code">{{ row.orgCode || '-' }}</span></template>
        </el-table-column>
        <el-table-column prop="sort" label="排序" width="70" sortable="custom" />
        <el-table-column prop="enabled" label="状态" width="80" sortable="custom">
          <template #default="{ row }">
            <el-tag size="small" :type="row.enabled ? 'success' : 'info'" effect="plain">{{ row.enabled ? '启用' : '停用' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="180" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="openDialog(row)">编辑</el-button>
            <el-button link type="success" size="small" @click="openDialog(null, row.id)">加子组织</el-button>
            <el-popconfirm title="确认删除该组织?" @confirm="remove(row.id)">
              <template #reference>
                <el-button link type="danger" size="small">删除</el-button>
              </template>
            </el-popconfirm>
          </template>
        </el-table-column>
      </el-table>

      <div class="pager-row">
        <el-pagination background layout="total, sizes, prev, pager, next" :total="filteredTree.length"
                       v-model:current-page="pager.page" v-model:page-size="pager.size"
                       :page-sizes="[5, 10, 20]" />
        <span class="pager-tip">按顶级组织分页,子组织随父级展示</span>
      </div>
    </div>

    <el-drawer v-model="dialog.visible" :title="dialog.form.id ? '编辑组织' : '新增组织'" direction="rtl" size="520px">
      <el-form :model="dialog.form" label-width="90px">
        <el-form-item label="组织名称" required>
          <el-input v-model="dialog.form.name" />
        </el-form-item>
        <el-form-item label="上级组织">
          <el-tree-select v-model="dialog.form.parentId" :data="orgTree" clearable check-strictly
                          :props="{ label: 'name', value: 'id' }" node-key="id" style="width: 100%"
                          placeholder="留空为顶级组织" />
        </el-form-item>
        <el-row :gutter="12">
          <el-col :span="12">
            <el-form-item label="组织编码">
              <el-input v-model="dialog.form.orgCode" placeholder="如 EM-04" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="排序">
              <el-input-number v-model="dialog.form.sort" :min="1" :max="99" style="width: 100%" />
            </el-form-item>
          </el-col>
        </el-row>
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
import { ref, reactive, computed, watch, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { Search } from '@element-plus/icons-vue'
import http from '../../api'
/* 客户端排序:全量列表排序后再分页(表头 sortable=custom) */
const sort = reactive({ key: '', dir: 1 })
function seq(i) { return (pager.page - 1) * pager.size + i + 1 }
function cmp(a, b) {
  if (a == null) return b == null ? 0 : -1
  if (b == null) return 1
  return typeof a === 'number' && typeof b === 'number' ? a - b : String(a).localeCompare(String(b), 'zh')
}
function onSort({ prop, order }) {
  sort.key = order ? prop : ''
  sort.dir = order === 'ascending' ? 1 : -1
  pager.page = 1
}

const loading = ref(false)
const orgTree = ref([])
const keyword = ref('')
const enabledFilter = ref(null)
const pager = reactive({ page: 1, size: 10 })
const dialog = reactive({ visible: false, saving: false, form: {} })

/** 拉平后的全量节点(带 parentId),用于命中判断与祖先链回溯 */
const orgFlat = computed(() => {
  const out = []
  const walk = (nodes) => nodes.forEach((n) => { out.push(n); walk(n.children || []) })
  walk(orgTree.value)
  return out
})

/** 筛选:命中节点 ∪ 其全部祖先;条件为空时返回原树 */
const filteredTree = computed(() => {
  const kw = keyword.value?.trim()
  const hasEnabled = enabledFilter.value === true || enabledFilter.value === false
  if (!kw && !hasEnabled) return orgTree.value
  const byId = new Map(orgFlat.value.map((o) => [o.id, o]))
  const keep = new Set()
  for (const o of orgFlat.value) {
    const hit = (!kw || (o.name || '').includes(kw) || (o.orgCode || '').includes(kw))
      && (!hasEnabled || o.enabled === enabledFilter.value)
    if (!hit) continue
    keep.add(o.id)
    let pid = o.parentId
    while (pid != null && !keep.has(pid)) { // 祖先全部保留,维持树形层级
      keep.add(pid)
      pid = byId.get(pid)?.parentId ?? null
    }
  }
  const rebuild = (nodes) => nodes
    .filter((n) => keep.has(n.id))
    .map((n) => ({ ...n, children: rebuild(n.children || []) }))
  return rebuild(orgTree.value)
})

const flatCount = computed(() => {
  let n = 0
  const count = (nodes) => nodes.forEach((o) => {
    const hit = (!keyword.value?.trim() || (o.name || '').includes(keyword.value.trim()) || (o.orgCode || '').includes(keyword.value.trim()))
      && ((enabledFilter.value !== true && enabledFilter.value !== false) || o.enabled === enabledFilter.value)
    if (hit) n++
    count(o.children || [])
  })
  count(orgTree.value)
  return n
})

/** 根节点分页:每页展示 N 个顶级组织,子组织随父级整树挂载 */
const sortedTree = computed(() => {
  if (!sort.key) return filteredTree.value
  const k = sort.key, d = sort.dir
  return [...filteredTree.value].sort((a, b) => cmp(a[k], b[k]) * d)
})
const pagedTree = computed(() =>
  sortedTree.value.slice((pager.page - 1) * pager.size, pager.page * pager.size))

/** 筛选后顶级总数收缩时,当前页夹回有效范围 */
watch(() => filteredTree.value.length, () => {
  const maxPage = Math.max(1, Math.ceil(filteredTree.value.length / pager.size))
  if (pager.page > maxPage) pager.page = maxPage
})
watch([keyword, enabledFilter], () => { pager.page = 1 })

onMounted(load)
async function load() {
  loading.value = true
  try { orgTree.value = await http.get('/orgs') }
  finally { loading.value = false }
}

function openDialog(row, parentId = null) {
  dialog.form = row
    ? { id: row.id, name: row.name, parentId: row.parentId, orgCode: row.orgCode, sort: row.sort, enabled: row.enabled }
    : { id: null, name: '', parentId, orgCode: '', sort: 9, enabled: true }
  dialog.visible = true
}

async function save() {
  const f = dialog.form
  if (!f.name) return ElMessage.warning('组织名称不能为空')
  dialog.saving = true
  try {
    if (f.id) await http.put(`/orgs/${f.id}`, f)
    else await http.post('/orgs', f)
    ElMessage.success('已保存')
    dialog.visible = false
    load()
  } finally { dialog.saving = false }
}

async function remove(id) {
  await http.delete(`/orgs/${id}`)
  ElMessage.success('已删除')
  load()
}
</script>

<style scoped>
.table-panel { height: calc(100% - 50px); padding: 8px; display: flex; flex-direction: column; }
.toolbar { display: flex; gap: 10px; padding: 8px 8px 12px; align-items: center; }
.toolbar + .el-table { flex: 1; }
.pager-row { display: flex; justify-content: flex-end; align-items: center; gap: 12px; padding: 10px 4px 2px; }
.pager-tip { font-size: 12px; color: var(--text-dim); }
.filter-tip { font-size: 12px; color: var(--text-dim); margin-left: auto; }
.actions { display: flex; gap: 10px; }
.name { font-weight: 600; }
.code { font-family: monospace; font-size: 13px; color: var(--primary); }
</style>
