<template>
  <div class="page">
    <div class="page-header">
      <span class="page-title">角色管理</span>
      <div class="actions">
        <el-button type="primary" @click="openDialog()">新增角色</el-button>
      </div>
    </div>

    <div class="panel table-panel">
      <div class="toolbar">
        <el-input v-model="keyword" placeholder="角色名称 / 编码" clearable style="width: 200px" @keyup.enter="search">
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
        <el-table-column prop="name" label="角色名称" width="160" sortable="custom">
          <template #default="{ row }"><span class="name">{{ row.name }}</span></template>
        </el-table-column>
        <el-table-column prop="code" label="编码" width="140" sortable="custom">
          <template #default="{ row }">
            <el-tag size="small" :type="row.code === 'ADMIN' ? 'danger' : 'primary'" effect="plain">{{ row.code }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="remark" label="备注" min-width="180" show-overflow-tooltip />
        <el-table-column label="菜单权限" min-width="280">
          <template #default="{ row }">
            <span v-if="row.code === 'ADMIN'" class="admin-all">全部菜单(内置超管)</span>
            <div v-else class="menu-chips">
              <el-tag v-for="m in menusOf(row)" :key="m.id" size="small" effect="light" class="chip">{{ m.name }}</el-tag>
              <span v-if="!menusOf(row).length" class="dim">未授权</span>
            </div>
          </template>
        </el-table-column>
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
            <el-popconfirm v-if="row.code !== 'ADMIN'" title="确认删除该角色?" @confirm="remove(row.id)">
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

    <el-drawer v-model="dialog.visible" :title="dialog.form.id ? '编辑角色' : '新增角色'" direction="rtl" size="620px">
      <el-form :model="dialog.form" label-width="90px">
        <el-row :gutter="12">
          <el-col :span="12">
            <el-form-item label="角色名称" required>
              <el-input v-model="dialog.form.name" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="角色编码" required>
              <el-input v-model="dialog.form.code" :disabled="!!dialog.form.id" placeholder="如 DISPATCHER" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="备注">
          <el-input v-model="dialog.form.remark" type="textarea" :rows="2" />
        </el-form-item>
        <el-form-item label="菜单授权">
          <div class="menu-tree">
            <el-tree ref="treeRef" :data="menuTreeData" show-checkbox node-key="id"
                     default-expand-all :props="{ label: 'name' }"
                     :default-checked-keys="dialog.checkedIds" />
            <div class="tree-tip">勾选该角色可见的菜单(ADMIN 角色固定拥有全部)</div>
          </div>
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
import { ref, reactive, computed, watch, onMounted, nextTick } from 'vue'
import { ElMessage } from 'element-plus'
import { Search } from '@element-plus/icons-vue'
import http from '../../api'
import { parseIdList } from '../../utils/dict'

const loading = ref(false)
const keyword = ref('')
const enabledFilter = ref(null)
const rows = ref([])
const menus = ref([])
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

const treeRef = ref(null)
const dialog = reactive({ visible: false, saving: false, form: {}, checkedIds: [] })

/** /menus 全量列表的字段是 menuGroup(不是 /mine、/tree 的 group),授权树按它分两组 */
const menuTreeData = computed(() => ([
  { id: -1, name: '业务菜单', children: menus.value.filter((m) => m.menuGroup === 'BIZ') },
  { id: -2, name: '系统管理', children: menus.value.filter((m) => m.menuGroup === 'SYS') }
]))

function menusOf(role) {
  const ids = parseIdList(role.menuIdsJson)
  return menus.value.filter((m) => ids.includes(m.id))
}

onMounted(load)
async function load() {
  loading.value = true
  try {
    const [res] = await Promise.all([
      http.get('/roles/page', {
        params: {
          page: pager.page, size: pager.size,
          sortBy: sort.sortBy || undefined,
          direction: sort.sortBy ? sort.direction : undefined,
          keyword: keyword.value || undefined,
          enabled: enabledFilter.value === null || enabledFilter.value === '' ? undefined : enabledFilter.value
        }
      }),
      http.get('/menus').then((m) => { menus.value = m })
    ])
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
  if (row) {
    dialog.form = { id: row.id, name: row.name, code: row.code, remark: row.remark, enabled: row.enabled }
    dialog.checkedIds = parseIdList(row.menuIdsJson)
  } else {
    dialog.form = { id: null, name: '', code: '', remark: '', enabled: true }
    dialog.checkedIds = []
  }
  dialog.visible = true
  // 分组伪根 id 为负数,不参与回填
  nextTick(() => treeRef.value?.setCheckedKeys(dialog.checkedIds.filter((id) => id > 0)))
}

async function save() {
  const f = dialog.form
  if (!f.name || !f.code) return ElMessage.warning('名称与编码不能为空')
  dialog.saving = true
  try {
    const ids = (treeRef.value?.getCheckedKeys() || []).filter((id) => id > 0)
    const body = { ...f, menuIdsJson: JSON.stringify(ids) }
    if (f.id) await http.put(`/roles/${f.id}`, body)
    else await http.post('/roles', body)
    ElMessage.success('已保存')
    dialog.visible = false
    load()
  } finally { dialog.saving = false }
}

async function remove(id) {
  await http.delete(`/roles/${id}`)
  ElMessage.success('已删除')
  load()
}
</script>

<style scoped>
.table-panel { height: calc(100% - 50px); padding: 8px; display: flex; flex-direction: column; }
.toolbar { display: flex; gap: 10px; padding: 8px 8px 12px; }
.toolbar + .el-table { flex: 1; }
.pager-row { display: flex; justify-content: flex-end; padding: 10px 4px 2px; }
.name { font-weight: 600; }
.admin-all { color: #d92d20; font-weight: 600; font-size: 13px; }
.menu-chips { display: flex; flex-wrap: wrap; gap: 4px; }
.dim { color: #98a2b3; font-size: 12px; }
.menu-tree { width: 100%; border: 1px solid var(--border); border-radius: 8px; padding: 8px; }
.tree-tip { font-size: 12px; color: var(--text-dim); margin-top: 6px; }
</style>
