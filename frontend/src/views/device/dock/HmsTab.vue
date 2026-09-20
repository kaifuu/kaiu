<template>
  <div class="hms-wrap">
    <div class="toolbar">
      <el-select v-model="levelFilter" clearable placeholder="告警等级" style="width: 120px" @change="search">
        <el-option v-for="o in dictOptions(HMS_LEVEL)" :key="o.value" :label="o.label" :value="o.value" />
      </el-select>
      <el-button @click="search">查询</el-button>
      <el-button :loading="loading" @click="load()">刷新</el-button>
    </div>

    <el-table :data="rows" v-loading="loading" stripe size="small" height="100%"
              empty-text="暂无健康告警,设备运行正常">
      <el-table-column type="index" label="序号" width="50"
                       :index="(i) => (pager.page - 1) * pager.size + i + 1" />
      <el-table-column prop="level" label="等级" width="90">
        <template #default="{ row }">
          <el-tag size="small" :type="dictTag(HMS_LEVEL, row.level)" effect="light">
            {{ dictLabel(HMS_LEVEL, row.level) }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="code" label="告警码" width="200" show-overflow-tooltip>
        <template #default="{ row }"><span class="mono">{{ row.code || '-' }}</span></template>
      </el-table-column>
      <el-table-column prop="message" label="描述" min-width="240" show-overflow-tooltip>
        <template #default="{ row }">{{ row.message || '-' }}</template>
      </el-table-column>
      <el-table-column prop="moduleIndex" label="模块索引" width="90" align="center">
        <template #default="{ row }">{{ row.moduleIndex ?? '-' }}</template>
      </el-table-column>
      <el-table-column prop="eventTime" label="发生时间" width="170">
        <template #default="{ row }">{{ row.eventTime || '-' }}</template>
      </el-table-column>
    </el-table>

    <div class="pager-row">
      <el-pagination background layout="total, sizes, prev, pager, next" :total="pager.total"
                     v-model:current-page="pager.page" v-model:page-size="pager.size"
                     :page-sizes="[10, 20, 50]" @current-change="load" @size-change="onSize" />
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, watch } from 'vue'
import http from '../../../api'
import { HMS_LEVEL, dictLabel, dictTag, dictOptions } from '../../../utils/dict'

const props = defineProps({ dock: { type: Object, default: null } })

const rows = ref([])
const loading = ref(false)
const levelFilter = ref('')
const pager = reactive({ page: 1, size: 10, total: 0 })

/* 告警是事件型数据,不做自动轮询,手动刷新即可 */
watch(() => props.dock?.id, (id) => { if (id) load() }, { immediate: true })

function search() { pager.page = 1; load() }
function onSize() { pager.page = 1; load() }

async function load() {
  if (!props.dock?.id) return
  loading.value = true
  try {
    const res = await http.get(`/devices/${props.dock.id}/hms/page`, {
      params: {
        page: pager.page, size: pager.size,
        level: levelFilter.value || undefined
      }
    })
    rows.value = res.rows || []
    pager.total = res.total || 0
  } finally { loading.value = false }
}
</script>

<style scoped>
.hms-wrap {
  height: 100%; min-height: 0; overflow: hidden;
  display: flex; flex-direction: column;
  padding-top: 6px;
}
.toolbar { display: flex; gap: 10px; padding: 4px 0 12px; flex-wrap: wrap; align-items: center; }
.hms-wrap > .el-table { flex: 1; min-height: 0; }
.pager-row { display: flex; justify-content: flex-end; padding: 12px 0 2px; flex-shrink: 0; }

.mono { font-family: 'Consolas', 'Courier New', monospace; font-size: 12px; color: var(--primary); }
</style>
