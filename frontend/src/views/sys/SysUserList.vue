<template>
  <div class="page">
    <div class="page-header">
      <span class="page-title">人员管理</span>
      <div class="actions">
        <el-button type="primary" @click="openDialog()">新增人员</el-button>
      </div>
    </div>

    <div class="panel table-panel">
      <div class="toolbar">
        <el-input v-model="keyword" placeholder="账号 / 姓名 / 手机号" clearable style="width: 200px" @keyup.enter="search">
          <template #prefix><el-icon><Search /></el-icon></template>
        </el-input>
        <el-select v-model="roleFilter" clearable placeholder="角色" style="width: 150px">
          <el-option v-for="r in roles" :key="r.id" :label="r.name" :value="r.id" />
        </el-select>
        <el-tree-select v-model="orgFilter" :data="orgTree" clearable check-strictly placeholder="所属组织"
                        :props="{ label: 'name', value: 'id' }" node-key="id" style="width: 180px" />
        <el-select v-model="tenantFilter" clearable placeholder="所属租户" style="width: 150px">
          <el-option v-for="t in tenants" :key="t.id" :label="t.name" :value="t.id" />
        </el-select>
        <el-select v-model="statusFilter" clearable placeholder="状态" style="width: 110px">
          <el-option label="启用" value="ENABLED" />
          <el-option label="停用" value="DISABLED" />
        </el-select>
        <el-button @click="search">查询</el-button>
      </div>

      <el-table :data="rows" v-loading="loading" stripe height="100%" @sort-change="onSort">
        <el-table-column type="index" label="序号" width="56" :index="seq" />
        <el-table-column prop="username" label="账号" min-width="120" sortable="custom">
          <template #default="{ row }"><span class="name">{{ row.username }}</span></template>
        </el-table-column>
        <el-table-column prop="nickname" label="姓名" min-width="100" sortable="custom">
          <template #default="{ row }">{{ row.nickname || '-' }}</template>
        </el-table-column>
        <el-table-column prop="phone" label="手机号" min-width="120">
          <template #default="{ row }">{{ row.phone || '-' }}</template>
        </el-table-column>
        <el-table-column label="角色" width="130">
          <template #default="{ row }">
            <el-tag size="small" :type="row.roleCode === 'ADMIN' ? 'danger' : 'primary'" effect="light">
              {{ row.roleName || '未分配' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="所属组织" min-width="130">
          <template #default="{ row }">{{ row.orgName || '-' }}</template>
        </el-table-column>
        <el-table-column label="所属租户" min-width="110">
          <template #default="{ row }">{{ row.tenantName || '-' }}</template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="90" sortable="custom">
          <template #default="{ row }">
            <el-tag size="small" :type="row.status === 'ENABLED' ? 'success' : 'info'" effect="plain">
              {{ row.status === 'ENABLED' ? '启用' : '停用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="lastLoginAt" label="最近登录" min-width="155" sortable="custom">
          <template #default="{ row }">{{ row.lastLoginAt || '-' }}</template>
        </el-table-column>
        <el-table-column prop="createTime" label="创建时间" min-width="155" sortable="custom">
          <template #default="{ row }">{{ row.createTime || '-' }}</template>
        </el-table-column>
        <el-table-column label="操作" width="180" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="openDialog(row)">编辑</el-button>
            <el-popconfirm :title="`重置密码为 ${DEFAULT_PWD} ?`" @confirm="resetPwd(row)">
              <template #reference>
                <el-button link type="warning" size="small">重置密码</el-button>
              </template>
            </el-popconfirm>
            <el-popconfirm v-if="row.username !== 'admin'" title="确认删除该人员?" @confirm="remove(row.id)">
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

    <el-drawer v-model="dialog.visible" :title="dialog.form.id ? '编辑人员' : '新增人员'" direction="rtl" size="580px">
      <el-form :model="dialog.form" label-width="90px">
        <el-row :gutter="12">
          <el-col :span="12">
            <el-form-item label="账号" required>
              <el-input v-model="dialog.form.username" :disabled="!!dialog.form.id" placeholder="登录账号" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="姓名">
              <el-input v-model="dialog.form.nickname" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="12">
          <el-col :span="12">
            <el-form-item label="手机号">
              <el-input v-model="dialog.form.phone" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="角色">
              <el-select v-model="dialog.form.roleId" clearable style="width: 100%">
                <el-option v-for="r in roles" :key="r.id" :label="r.name + '(' + r.code + ')'" :value="r.id" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="12">
          <el-col :span="12">
            <el-form-item label="所属组织">
              <el-tree-select v-model="dialog.form.orgId" :data="orgTree" clearable check-strictly
                              :props="{ label: 'name', value: 'id' }" node-key="id" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="所属租户">
              <el-select v-model="dialog.form.tenantId" clearable style="width: 100%">
                <el-option v-for="t in tenants" :key="t.id" :label="t.name" :value="t.id" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="状态">
          <el-switch v-model="dialog.form.enabled" active-text="启用" inactive-text="停用" />
        </el-form-item>
        <el-alert v-if="!dialog.form.id" :title="`初始密码为 ${DEFAULT_PWD},请登录后尽快修改`" type="info" :closable="false" />
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

const DEFAULT_PWD = '123456'

const loading = ref(false)
const keyword = ref('')
const roleFilter = ref(null)
const orgFilter = ref(null)
const tenantFilter = ref(null)
const statusFilter = ref(null)
const rows = ref([])
const roles = ref([])
const orgTree = ref([])
const tenants = ref([])
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
    const [res] = await Promise.all([
      http.get('/users/page', {
        params: {
          page: pager.page, size: pager.size,
          sortBy: sort.sortBy || undefined,
          direction: sort.sortBy ? sort.direction : undefined,
          keyword: keyword.value || undefined,
          roleId: roleFilter.value || undefined,
          orgId: orgFilter.value || undefined,
          tenantId: tenantFilter.value || undefined,
          status: statusFilter.value || undefined
        }
      }),
      http.get('/roles').then((r) => { roles.value = r }),
      http.get('/orgs').then((o) => { orgTree.value = o }),
      http.get('/tenants').then((t) => { tenants.value = t })
    ])
    rows.value = res.rows || []
    pager.total = res.total || 0
  } finally { loading.value = false }
}

/** 筛选条件变化回到第一页再查;翻页保持当前页 */
watch([roleFilter, orgFilter, tenantFilter, statusFilter], () => { pager.page = 1; load() })

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
    ? { id: row.id, username: row.username, nickname: row.nickname, phone: row.phone,
        roleId: row.roleId || null, orgId: row.orgId || null, tenantId: row.tenantId || null,
        enabled: row.status === 'ENABLED' }
    : { id: null, username: '', nickname: '', phone: '', roleId: null, orgId: null, tenantId: null, enabled: true }
  dialog.visible = true
}

async function save() {
  const f = dialog.form
  if (!f.username) return ElMessage.warning('账号不能为空')
  dialog.saving = true
  try {
    // 入参按 SysUserForm:角色平铺 roleId,不传嵌套对象
    const body = {
      username: f.username, nickname: f.nickname, phone: f.phone,
      roleId: f.roleId, orgId: f.orgId, tenantId: f.tenantId,
      status: f.enabled ? 'ENABLED' : 'DISABLED'
    }
    if (f.id) await http.put(`/users/${f.id}`, body)
    else await http.post('/users', body)
    ElMessage.success('已保存')
    dialog.visible = false
    load()
  } finally { dialog.saving = false }
}

async function resetPwd(row) {
  const res = await http.post(`/users/${row.id}/reset-password`)
  ElMessage.success(`密码已重置为 ${res?.password || DEFAULT_PWD}`)
}

async function remove(id) {
  await http.delete(`/users/${id}`)
  ElMessage.success('已删除')
  load()
}
</script>

<style scoped>
.table-panel { height: calc(100% - 50px); padding: 8px; display: flex; flex-direction: column; }
.toolbar { display: flex; gap: 10px; padding: 8px 8px 12px; flex-wrap: wrap; }
.toolbar + .el-table { flex: 1; }
.pager-row { display: flex; justify-content: flex-end; padding: 10px 4px 2px; }
.actions { display: flex; gap: 10px; align-items: center; }
.name { font-weight: 600; }
</style>
