<template>
  <div class="page">
    <div class="page-header">
      <span class="page-title">安全预警</span>
      <div class="actions">
        <el-button @click="load">刷新</el-button>
      </div>
    </div>

    <div class="panel table-panel">
      <div class="toolbar">
        <el-select v-model="typeFilter" clearable placeholder="预警类型" style="width: 150px">
          <el-option v-for="o in dictOptions(SAFE_ALERT_TYPE)" :key="o.value" :label="o.label" :value="o.value" />
        </el-select>
        <el-select v-model="levelFilter" clearable placeholder="预警等级" style="width: 120px">
          <el-option v-for="o in dictOptions(SAFE_ALERT_LEVEL)" :key="o.value" :label="o.label" :value="o.value" />
        </el-select>
        <el-select v-model="statusFilter" clearable placeholder="处理状态" style="width: 120px">
          <el-option v-for="o in dictOptions(SAFE_ALERT_STATUS)" :key="o.value" :label="o.label" :value="o.value" />
        </el-select>
        <el-input v-model="keyword" placeholder="标题 / 设备 SN" clearable style="width: 200px" @keyup.enter="search">
          <template #prefix><el-icon><Search /></el-icon></template>
        </el-input>
        <el-button @click="search">查询</el-button>
      </div>

      <el-table :data="rows" v-loading="loading" stripe height="100%">
        <el-table-column type="index" label="序号" width="56" :index="seq" />
        <el-table-column prop="title" label="预警标题" min-width="170">
          <template #default="{ row }"><span class="name">{{ row.title }}</span></template>
        </el-table-column>
        <el-table-column prop="alertType" label="类型" width="120">
          <template #default="{ row }">
            <el-tag size="small" :type="dictTag(SAFE_ALERT_TYPE, row.alertType)" effect="light">
              {{ dictLabel(SAFE_ALERT_TYPE, row.alertType) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="level" label="等级" width="90">
          <template #default="{ row }">
            <el-tag size="small" :type="dictTag(SAFE_ALERT_LEVEL, row.level)" effect="plain">
              {{ dictLabel(SAFE_ALERT_LEVEL, row.level) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="deviceSn" label="设备 SN" width="140" show-overflow-tooltip>
          <template #default="{ row }">{{ row.deviceSn || '-' }}</template>
        </el-table-column>
        <el-table-column prop="message" label="预警内容" min-width="240" show-overflow-tooltip>
          <template #default="{ row }">{{ row.message || '-' }}</template>
        </el-table-column>
        <el-table-column prop="occurredAt" label="发生时间" width="160">
          <template #default="{ row }">{{ row.occurredAt || '-' }}</template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="92">
          <template #default="{ row }">
            <el-tag size="small" :type="dictTag(SAFE_ALERT_STATUS, row.status)" effect="plain">
              {{ dictLabel(SAFE_ALERT_STATUS, row.status) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="130" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="openDetail(row)">详情</el-button>
            <el-button v-if="row.status === 'PENDING'" link type="success" size="small"
                       @click="openHandle(row)">处置</el-button>
          </template>
        </el-table-column>
      </el-table>
      <div class="pager-row">
        <el-pagination background layout="total, sizes, prev, pager, next" :total="pager.total"
                       v-model:current-page="pager.page" v-model:page-size="pager.size"
                       :page-sizes="[10, 20, 50]" @current-change="load" @size-change="onSizeChange" />
      </div>
    </div>

    <!-- 详情抽屉:预警全要素 + 位置坐标 -->
    <el-drawer v-model="detail.visible" title="预警详情" direction="rtl" size="460px">
      <el-descriptions :column="1" border>
        <el-descriptions-item label="预警标题">{{ detail.row?.title || '-' }}</el-descriptions-item>
        <el-descriptions-item label="预警类型">
          <el-tag size="small" :type="dictTag(SAFE_ALERT_TYPE, detail.row?.alertType)" effect="light">
            {{ dictLabel(SAFE_ALERT_TYPE, detail.row?.alertType) }}
          </el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="预警等级">
          <el-tag size="small" :type="dictTag(SAFE_ALERT_LEVEL, detail.row?.level)" effect="plain">
            {{ dictLabel(SAFE_ALERT_LEVEL, detail.row?.level) }}
          </el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="设备 SN">{{ detail.row?.deviceSn || '-' }}</el-descriptions-item>
        <el-descriptions-item label="预警内容">{{ detail.row?.message || '-' }}</el-descriptions-item>
        <el-descriptions-item label="发生位置">
          {{ detail.row?.longitude || '-' }}, {{ detail.row?.latitude || '-' }}
          <span v-if="detail.row?.height !== null && detail.row?.height !== undefined" class="sub">
            (高度 {{ detail.row.height }} m)
          </span>
        </el-descriptions-item>
        <el-descriptions-item label="发生时间">{{ detail.row?.occurredAt || '-' }}</el-descriptions-item>
        <el-descriptions-item label="处置状态">
          {{ dictLabel(SAFE_ALERT_STATUS, detail.row?.status) }}
        </el-descriptions-item>
        <el-descriptions-item label="处置人">{{ detail.row?.handler || '-' }}</el-descriptions-item>
        <el-descriptions-item label="处置说明">{{ detail.row?.handleRemark || '-' }}</el-descriptions-item>
        <el-descriptions-item label="处置时间">{{ detail.row?.handleTime || '-' }}</el-descriptions-item>
      </el-descriptions>
    </el-drawer>

    <!-- 处置闭环 -->
    <el-dialog v-model="handle.visible" title="预警处置" width="480px">
      <el-form :model="handle.form" label-width="90px">
        <el-form-item label="处置人">
          <el-input v-model="handle.form.handler" placeholder="留空则记为当前登录账号" />
        </el-form-item>
        <el-form-item label="处置说明" required>
          <el-input v-model="handle.form.remark" type="textarea" :rows="4"
                    placeholder="核实结果与处理措施(必填)" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="handle.visible = false">取消</el-button>
        <el-button type="primary" :loading="handle.saving" @click="submitHandle">提交</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, watch, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { Search } from '@element-plus/icons-vue'
import http from '../../api'
import {
  SAFE_ALERT_TYPE, SAFE_ALERT_LEVEL, SAFE_ALERT_STATUS,
  dictLabel, dictTag, dictOptions
} from '../../utils/dict'

const loading = ref(false)
const keyword = ref('')
const typeFilter = ref(null)
const levelFilter = ref(null)
const statusFilter = ref(null)
const rows = ref([])
const pager = reactive({ page: 1, size: 10, total: 0 })

const detail = reactive({ visible: false, row: null })
const handle = reactive({ visible: false, saving: false, row: null, form: { handler: '', remark: '' } })

onMounted(load)
async function load() {
  loading.value = true
  try {
    const res = await http.get('/safe-alerts/page', {
      params: {
        page: pager.page, size: pager.size,
        alertType: typeFilter.value || undefined,
        level: levelFilter.value || undefined,
        status: statusFilter.value || undefined,
        keyword: keyword.value || undefined
      }
    })
    rows.value = res.rows || []
    pager.total = res.total || 0
  } finally { loading.value = false }
}

watch([typeFilter, levelFilter, statusFilter], () => { pager.page = 1; load() })

function search() {
  pager.page = 1
  load()
}

function onSizeChange() {
  pager.page = 1
  load()
}

function seq(i) { return (pager.page - 1) * pager.size + i + 1 }

function openDetail(row) {
  detail.row = row
  detail.visible = true
}

function openHandle(row) {
  handle.row = row
  handle.form = { handler: localStorage.getItem('nickname') || '', remark: '' }
  handle.visible = true
}

async function submitHandle() {
  if (!handle.form.remark) return ElMessage.warning('处置说明不能为空')
  handle.saving = true
  try {
    await http.post(`/safe-alerts/${handle.row.id}/handle`, {
      handler: handle.form.handler || null,
      remark: handle.form.remark
    })
    ElMessage.success('处置完成')
    handle.visible = false
    load()
  } finally { handle.saving = false }
}
</script>
