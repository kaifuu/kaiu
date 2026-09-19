<template>
  <div class="page">
    <div class="page-header">
      <span class="page-title">菜单管理</span>
      <div class="actions">
        <el-button type="primary" @click="openDialog(null, selectedMenuId)">新增菜单</el-button>
      </div>
    </div>

    <div class="menu-body">
      <!-- 左侧:菜单树导航 -->
      <div class="panel tree-panel">
        <div class="tree-head">菜单树</div>
        <el-scrollbar class="tree-scroll">
          <el-tree :data="treeData" node-key="id" default-expand-all highlight-current
                   :expand-on-click-node="false" :current-node-key="selected"
                   @current-change="onTreeSelect">
            <template #default="{ data }">
              <span class="tree-node">
                <el-icon v-if="data.id !== 'ALL'" :size="15" color="#475467"><component :is="data.icon || 'Menu'" /></el-icon>
                <el-icon v-else :size="15" color="#155eef"><Grid /></el-icon>
                <span class="tree-label" :class="{ all: data.id === 'ALL' }">{{ data.name }}</span>
                <span class="tree-count">{{ data.id === 'ALL' ? menuFlat.length : descendantIds(data.id).length }}</span>
              </span>
            </template>
          </el-tree>
        </el-scrollbar>
      </div>

      <!-- 右侧:详细列表配置 -->
      <div class="panel table-panel">
        <div class="toolbar">
          <el-input v-model="keyword" placeholder="菜单名称 / 路由路径" clearable style="width: 200px" :prefix-icon="Search" />
          <el-select v-model="groupFilter" clearable placeholder="分组" style="width: 120px">
            <el-option label="业务菜单" value="BIZ" />
            <el-option label="系统管理" value="SYS" />
          </el-select>
          <el-select v-model="enabledFilter" clearable placeholder="状态" style="width: 110px">
            <el-option label="启用" :value="true" />
            <el-option label="停用" :value="false" />
          </el-select>
          <span class="scope-tip">{{ scopeLabel }} · 命中 {{ filteredRows.length }} 条</span>
        </div>

        <el-table :data="pagedRows" v-loading="loading" stripe height="100%" @sort-change="onSort">
          <el-table-column type="index" label="序号" width="56" :index="seq" />
          <el-table-column label="图标" width="60">
            <template #default="{ row }">
              <el-icon :size="18" color="#475467"><component :is="row.icon" /></el-icon>
            </template>
          </el-table-column>
          <el-table-column prop="name" label="菜单名称" min-width="150" sortable="custom">
            <template #default="{ row }"><span class="name">{{ row.name }}</span></template>
          </el-table-column>
          <el-table-column prop="path" label="路由路径" width="150" sortable="custom">
            <template #default="{ row }"><span class="code">{{ row.path || '-' }}</span></template>
          </el-table-column>
          <el-table-column prop="icon" label="图标名" width="120" />
          <el-table-column prop="group" label="分组" width="96" sortable="custom">
            <template #default="{ row }">
              <el-tag size="small" :type="row.group === 'BIZ' ? 'primary' : 'warning'" effect="light">
                {{ row.group === 'BIZ' ? '业务菜单' : '系统管理' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="上级菜单" width="120">
            <template #default="{ row }">{{ menuById[row.parentId]?.name || '顶级' }}</template>
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
              <el-button link type="success" size="small" @click="openDialog(null, row.id)">加子菜单</el-button>
              <el-popconfirm title="确认删除该菜单?" @confirm="remove(row.id)">
                <template #reference>
                  <el-button link type="danger" size="small">删除</el-button>
                </template>
              </el-popconfirm>
            </template>
          </el-table-column>
        </el-table>
        <div class="pager-row">
          <el-pagination background layout="total, sizes, prev, pager, next" :total="filteredRows.length"
                         v-model:current-page="pager.page" v-model:page-size="pager.size"
                         :page-sizes="[10, 20, 50]" />
        </div>
      </div>
    </div>

    <el-drawer v-model="dialog.visible" :title="dialog.form.id ? '编辑菜单' : '新增菜单'" direction="rtl" size="540px">
      <el-form :model="dialog.form" label-width="90px">
        <el-form-item label="上级菜单">
          <el-tree-select v-model="dialog.form.parentId" :data="parentOptions" clearable check-strictly
                          :props="{ label: 'name', value: 'id' }" node-key="id" style="width: 100%"
                          placeholder="留空为顶级菜单" />
        </el-form-item>
        <el-row :gutter="12">
          <el-col :span="12">
            <el-form-item label="菜单名称" required>
              <el-input v-model="dialog.form.name" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="路由路径" required>
              <el-input v-model="dialog.form.path" placeholder="/sys/xxx" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="12">
          <el-col :span="12">
            <el-form-item label="分组">
              <el-select v-model="dialog.form.group" style="width: 100%">
                <el-option label="业务菜单" value="BIZ" />
                <el-option label="系统管理" value="SYS" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="排序">
              <el-input-number v-model="dialog.form.sort" :min="1" :max="99" style="width: 100%" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="图标">
          <el-select v-model="dialog.form.icon" filterable style="width: 100%">
            <el-option v-for="ic in ICONS" :key="ic" :label="ic" :value="ic">
              <span style="display:inline-flex;align-items:center;gap:8px">
                <el-icon><component :is="ic" /></el-icon>{{ ic }}
              </span>
            </el-option>
          </el-select>
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
import { ref, reactive, computed, watch, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { Search, Grid } from '@element-plus/icons-vue'
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

// 常用图标白名单(全部图标已在 main.js 全局注册);含内置菜单种子用到的图标
const ICONS = ['Monitor', 'Location', 'Calendar', 'List', 'Warning', 'Bell', 'UserFilled', 'Key', 'Menu',
  'OfficeBuilding', 'Files', 'Document', 'Setting', 'DataAnalysis', 'Grid', 'User', 'Aim',
  'TrendCharts', 'MapLocation', 'Connection', 'Cpu', 'Compass', 'Search']

const loading = ref(false)
const menuTree = ref([])
const keyword = ref('')
const groupFilter = ref('')
const enabledFilter = ref(null)
/** 左树选中:'ALL' 或菜单 id */
const selected = ref('ALL')
const pager = reactive({ page: 1, size: 10 })
const dialog = reactive({ visible: false, saving: false, form: {} })

/** 拉平后的全量节点(树序),用于范围圈定/上级名回查/候选树构建 */
const menuFlat = computed(() => {
  const out = []
  const walk = (nodes) => nodes.forEach((n) => { out.push(n); walk(n.children || []) })
  walk(menuTree.value)
  return out
})
const menuById = computed(() => new Map(menuFlat.value.map((n) => [n.id, n])))

/** 左树数据:顶部挂「全部菜单」伪根,其余按真实层级 */
const treeData = computed(() => [
  { id: 'ALL', name: '全部菜单', children: menuTree.value }
])

/** 选中节点自身 + 全部子孙 id 集合 */
function descendantIds(id) {
  const out = new Set([id])
  let grew = true
  while (grew) { // parent 是平列 id,按 parent->child 传播至收敛
    grew = false
    for (const n of menuFlat.value) {
      if (n.parentId != null && out.has(n.parentId) && !out.has(n.id)) { out.add(n.id); grew = true }
    }
  }
  return out
}

const selectedMenuId = computed(() => (selected.value === 'ALL' ? null : selected.value))

/** 右表范围:全部,或选中节点子树;再叠加工具栏筛选 */
const scopedRows = computed(() => selected.value === 'ALL'
  ? menuFlat.value
  : menuFlat.value.filter((n) => descendantIds(selected.value).has(n.id)))

const filteredRows = computed(() => scopedRows.value.filter((n) => {
  const kw = keyword.value?.trim()
  return (!kw || (n.name || '').includes(kw) || (n.path || '').includes(kw))
    && (!groupFilter.value || n.group === groupFilter.value)
    && ((enabledFilter.value !== true && enabledFilter.value !== false) || n.enabled === enabledFilter.value)
}))

const sortedRows = computed(() => {
  if (!sort.key) return filteredRows.value
  const k = sort.key, d = sort.dir
  return [...filteredRows.value].sort((a, b) => cmp(a[k], b[k]) * d)
})
const pagedRows = computed(() =>
  sortedRows.value.slice((pager.page - 1) * pager.size, pager.page * pager.size)
)

const scopeLabel = computed(() => selected.value === 'ALL' ? '全部菜单' : menuById.value.get(selected.value)?.name || '')

/** 范围/筛选变化后总页数收缩时,当前页夹回有效范围 */
watch(filteredRows, () => {
  const maxPage = Math.max(1, Math.ceil(filteredRows.value.length / pager.size))
  if (pager.page > maxPage) pager.page = maxPage
})
watch([selected, keyword, groupFilter, enabledFilter], () => { pager.page = 1 })

/** 上级菜单候选:编辑时剔除自己及其子孙,防成环 */
const parentOptions = computed(() => {
  if (!dialog.form.id) return menuTree.value
  const excluded = descendantIds(dialog.form.id)
  const rebuild = (nodes) => nodes
    .filter((n) => !excluded.has(n.id))
    .map((n) => ({ ...n, children: rebuild(n.children || []) }))
  return rebuild(menuTree.value)
})

onMounted(load)
async function load() {
  loading.value = true
  // 菜单树接口返回的节点用 group(/menus 全量列表才是 menuGroup),树形展示与提交做一次字段映射
  try { menuTree.value = await http.get('/menus/tree') }
  finally { loading.value = false }
  // 选中节点被删/不存在时回退到全部
  if (selected.value !== 'ALL' && !menuById.value.has(selected.value)) selected.value = 'ALL'
}

function onTreeSelect(data) {
  selected.value = data?.id ?? 'ALL'
}

function openDialog(row, parentId = null) {
  dialog.form = row
    ? { id: row.id, name: row.name, path: row.path, icon: row.icon, group: row.group,
        parentId: row.parentId || null, sort: row.sort, enabled: row.enabled }
    : { id: null, name: '', path: '', icon: 'Menu', group: 'BIZ', parentId, sort: 99, enabled: true }
  dialog.visible = true
}

async function save() {
  const f = dialog.form
  if (!f.name || !f.path) return ElMessage.warning('名称与路径不能为空')
  dialog.saving = true
  try {
    // 入参字段名与 SysMenu 实体一致:menuGroup;parentId=0 约定为顶级(显式清空上级的唯一通道)
    const body = {
      name: f.name, path: f.path, icon: f.icon, menuGroup: f.group,
      parentId: f.parentId ?? 0, sort: f.sort, enabled: f.enabled
    }
    if (f.id) await http.put(`/menus/${f.id}`, body)
    else await http.post('/menus', body)
    ElMessage.success('已保存')
    dialog.visible = false
    load()
  } finally { dialog.saving = false }
}

async function remove(id) {
  await http.delete(`/menus/${id}`)
  ElMessage.success('已删除')
  if (selected.value === id) selected.value = 'ALL'
  load()
}
</script>

<style scoped>
.menu-body { height: calc(100% - 50px); display: flex; gap: 12px; }
.tree-panel { width: 250px; display: flex; flex-direction: column; overflow: hidden; }
.tree-head { font-size: 13px; font-weight: 600; color: var(--text); padding: 10px 12px 6px; }
.tree-scroll { flex: 1; }
.table-panel { flex: 1; padding: 8px; min-width: 0; display: flex; flex-direction: column; }
.toolbar { display: flex; gap: 10px; padding: 8px 8px 12px; align-items: center; flex-wrap: wrap; }
.toolbar + .el-table { flex: 1; }
.pager-row { display: flex; justify-content: flex-end; padding: 10px 4px 2px; }
.scope-tip { font-size: 12px; color: var(--text-dim); margin-left: auto; }
.tree-node { display: inline-flex; align-items: center; gap: 6px; }
.tree-label.all { font-weight: 600; color: var(--primary); }
.tree-count { font-size: 11px; color: var(--text-dim); background: #f2f4f7; border-radius: 8px; padding: 0 7px; }
.actions { display: flex; gap: 10px; }
.name { font-weight: 600; }
.code { font-family: monospace; font-size: 13px; color: var(--primary); }

:deep(.el-tree) { --el-tree-node-content-height: 30px; background: transparent; }
:deep(.el-tree-node__content) { border-radius: 6px; }
</style>
