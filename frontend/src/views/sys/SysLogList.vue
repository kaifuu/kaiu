<template>
  <div class="page">
    <div class="page-header">
      <span class="page-title">日志管理</span>
      <div class="actions">
        <el-radio-group v-model="typeFilter" @change="search">
          <el-radio-button value="">全部</el-radio-button>
          <el-radio-button value="OPERATE">操作日志</el-radio-button>
          <el-radio-button value="LOGIN">登录日志</el-radio-button>
          <el-radio-button value="DEVICE">设备日志</el-radio-button>
        </el-radio-group>
      </div>
    </div>

    <div class="panel table-panel">
      <div class="toolbar">
        <el-input v-model="keyword" placeholder="账号 / 动作 / 详情" clearable style="width: 240px" @keyup.enter="search">
          <template #prefix><el-icon><Search /></el-icon></template>
        </el-input>
        <el-select v-model="successFilter" clearable placeholder="结果" style="width: 110px">
          <el-option label="成功" :value="true" />
          <el-option label="失败" :value="false" />
        </el-select>
        <el-button @click="search">查询</el-button>
        <el-popconfirm title="确认清空全部日志(操作/登录/设备)?不可恢复"
                       width="260" @confirm="clearAll">
          <template #reference>
            <el-button type="danger" plain :loading="clearing" class="clear-btn">清空日志</el-button>
          </template>
        </el-popconfirm>
      </div>

      <el-table :data="rows" v-loading="loading" stripe height="100%" @sort-change="onSort">
        <el-table-column type="index" label="序号" width="56" :index="seq" />
        <el-table-column prop="type" label="类型" width="90" sortable="custom">
          <template #default="{ row }">
            <el-tag size="small" :type="dictTag(LOG_TYPE, row.type)" effect="light">
              {{ dictLabel(LOG_TYPE, row.type) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="username" label="账号/设备" width="140" sortable="custom">
          <template #default="{ row }">{{ row.username || '-' }}</template>
        </el-table-column>
        <el-table-column prop="action" label="动作" width="160" show-overflow-tooltip sortable="custom" />
        <el-table-column prop="detail" label="详情" min-width="260" show-overflow-tooltip>
          <template #default="{ row }">{{ row.detail || '-' }}</template>
        </el-table-column>
        <el-table-column prop="ip" label="IP" width="130" sortable="custom">
          <template #default="{ row }">{{ row.ip || '-' }}</template>
        </el-table-column>
        <el-table-column prop="success" label="结果" width="80" sortable="custom">
          <template #default="{ row }">
            <el-tag size="small" :type="row.success ? 'success' : 'danger'" effect="plain">{{ row.success ? '成功' : '失败' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createTime" label="时间" width="175" sortable="custom">
          <template #default="{ row }">{{ row.createTime || '-' }}</template>
        </el-table-column>
      </el-table>

      <div class="pager-row">
        <el-pagination background layout="total, sizes, prev, pager, next" :total="pager.total"
                       v-model:current-page="pager.page" v-model:page-size="pager.size"
                       :page-sizes="[20, 50, 100]" @current-change="load" @size-change="onSizeChange" />
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, watch, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { Search } from '@element-plus/icons-vue'
import http from '../../api'
import { LOG_TYPE, dictLabel, dictTag } from '../../utils/dict'

const loading = ref(false)
const typeFilter = ref('')
const successFilter = ref(null)
const keyword = ref('')
const rows = ref([])
const clearing = ref(false)
const pager = reactive({ page: 1, size: 20, total: 0 })
/* 序号(跨页连续) + 服务端排序(表头 sortable=custom,白名单见后端 PageSort) */
const sort = reactive({ sortBy: '', direction: '' })
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
    // /logs 直接返回 { rows, total },page 为 1 起
    const data = await http.get('/logs', {
      params: {
        type: typeFilter.value || undefined,
        keyword: keyword.value || undefined,
        success: successFilter.value === null || successFilter.value === '' ? undefined : successFilter.value,
        sortBy: sort.sortBy || undefined,
        direction: sort.sortBy ? sort.direction : undefined,
        page: pager.page, size: pager.size
      }
    })
    rows.value = data.rows || []
    pager.total = data.total || 0
  } finally { loading.value = false }
}

/** 筛选条件变化回到第一页再查;翻页保持当前页 */
watch(successFilter, () => { pager.page = 1; load() })

function search() {
  pager.page = 1
  load()
}

function onSizeChange() {
  pager.page = 1
  load()
}

/** 一键清空全部日志,回第 1 页刷新 */
async function clearAll() {
  clearing.value = true
  try {
    const removed = await http.delete('/logs')
    ElMessage.success(`已清空 ${removed} 条日志`)
    pager.page = 1
    load()
  } finally { clearing.value = false }
}
</script>

<style scoped>
.table-panel { height: calc(100% - 50px); padding: 8px; display: flex; flex-direction: column; }
.actions { display: flex; gap: 10px; align-items: center; }
.toolbar { display: flex; gap: 10px; padding: 8px 8px 12px; align-items: center; }
.clear-btn { margin-left: auto; }
.toolbar + .el-table { flex: 1; }
.pager-row { display: flex; justify-content: flex-end; padding: 10px 4px 2px; }
</style>
