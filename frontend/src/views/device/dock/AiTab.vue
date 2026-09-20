<template>
  <div class="ai-wrap">
    <!-- 上:识别配置 -->
    <div class="cfg-card">
      <div class="cfg-head">
        <span class="col-title">AI 识别配置</span>
        <div class="cfg-head-right">
          <el-tag v-if="synced === true" size="small" type="success" effect="plain">已同步到设备</el-tag>
          <el-tag v-else-if="synced === false" size="small" type="warning" effect="plain">设备不在线,未同步</el-tag>
          <span v-if="form.updateTime" class="dim-tip">更新于 {{ form.updateTime }}</span>
          <el-button type="primary" size="small" :loading="saving" @click="save">保存并下发</el-button>
        </div>
      </div>

      <div class="cfg-grid">
        <div class="cfg-item">
          <span class="cfg-label">AI 识别</span>
          <el-switch v-model="form.enabled" />
        </div>
        <div class="cfg-item">
          <span class="cfg-label">AI 跟随</span>
          <el-switch v-model="form.followEnabled" />
        </div>
        <div class="cfg-item">
          <span class="cfg-label">识别模型</span>
          <el-input v-model="form.model" placeholder="如 person-vehicle-boat" style="width: 200px" />
        </div>
        <div class="cfg-item">
          <span class="cfg-label">置信度模式</span>
          <el-radio-group v-model="form.confidenceMode">
            <el-radio v-for="o in dictOptions(AI_CONFIDENCE_MODE)" :key="o.value" :value="o.value">
              {{ o.label }}
            </el-radio>
          </el-radio-group>
        </div>
        <div class="cfg-item">
          <span class="cfg-label">置信度阈值</span>
          <div class="cfg-slider">
            <el-slider v-model="form.confidenceValue" :min="50" :max="99"
                       :disabled="form.confidenceMode !== 'CUSTOM'" />
            <span class="fixed-tip" v-if="form.confidenceMode !== 'CUSTOM'">
              {{ FIXED_THRESHOLD[form.confidenceMode] || '当前模式使用固定阈值' }}
            </span>
          </div>
        </div>
        <div class="cfg-item">
          <span class="cfg-label">目标过滤</span>
          <el-checkbox-group v-model="form.filterTypes">
            <el-checkbox v-for="o in dictOptions(AI_TARGET_TYPE)" :key="o.value" :value="o.value">
              {{ o.label }}
            </el-checkbox>
          </el-checkbox-group>
        </div>
      </div>
    </div>

    <!-- 下:识别记录 -->
    <div class="rec-card">
      <div class="toolbar">
        <el-select v-model="typeFilter" clearable placeholder="目标类型" style="width: 130px" @change="search">
          <el-option v-for="o in dictOptions(AI_TARGET_TYPE)" :key="o.value" :label="o.label" :value="o.value" />
        </el-select>
        <el-button @click="search">查询</el-button>
        <span class="dim-tip">记录每 4s 自动刷新</span>
      </div>

      <el-table :data="rows" v-loading="loading" stripe size="small" height="100%">
        <el-table-column type="index" label="序号" width="50"
                         :index="(i) => (pager.page - 1) * pager.size + i + 1" />
        <el-table-column prop="targetType" label="目标类型" width="100">
          <template #default="{ row }">
            <el-tag size="small" :type="dictTag(AI_TARGET_TYPE, row.targetType)" effect="light">
              {{ dictLabel(AI_TARGET_TYPE, row.targetType) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="confidence" label="置信度" width="100" align="center">
          <template #default="{ row }">
            <span :class="{ hot: row.confidence >= 90 }">{{ row.confidence }}%</span>
          </template>
        </el-table-column>
        <el-table-column prop="longitude" label="经度" width="130">
          <template #default="{ row }"><span class="mono">{{ row.longitude ?? '-' }}</span></template>
        </el-table-column>
        <el-table-column prop="latitude" label="纬度" width="130">
          <template #default="{ row }"><span class="mono">{{ row.latitude ?? '-' }}</span></template>
        </el-table-column>
        <el-table-column prop="eventTime" label="识别时间" width="170" />
        <el-table-column prop="createTime" label="入库时间" width="170">
          <template #default="{ row }">{{ row.createTime || '-' }}</template>
        </el-table-column>
      </el-table>

      <div class="pager-row">
        <el-pagination background layout="total, sizes, prev, pager, next" :total="pager.total"
                       v-model:current-page="pager.page" v-model:page-size="pager.size"
                       :page-sizes="[10, 20, 50]" @current-change="load" @size-change="onSize" />
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted, onUnmounted, watch } from 'vue'
import { ElMessage } from 'element-plus'
import http from '../../../api'
import {
  AI_TARGET_TYPE, AI_CONFIDENCE_MODE,
  dictLabel, dictTag, dictOptions, parseIdList
} from '../../../utils/dict'

const props = defineProps({ dock: { type: Object, default: null } })

/** 非自定义模式下的固定阈值说明 */
const FIXED_THRESHOLD = {
  COUNT: '计数模式 · 固定阈值 65%',
  RESCUE: '搜救模式 · 固定阈值 50%'
}

/* ---------- 配置 ---------- */
const form = reactive({
  enabled: false, followEnabled: false, model: '',
  confidenceMode: 'COUNT', confidenceValue: 70,
  filterTypes: [], updateTime: ''
})
const saving = ref(false)
const synced = ref(null)

/* ---------- 识别记录 ---------- */
const rows = ref([])
const loading = ref(false)
const typeFilter = ref('')
const pager = reactive({ page: 1, size: 10, total: 0 })

let timer = null
let fetching = false

onMounted(() => {
  timer = setInterval(() => load(true), 4000)
})
onUnmounted(() => clearInterval(timer))

watch(() => props.dock?.id, (id) => { if (id) { loadConfig(); load() } }, { immediate: true })

function applyConfig(cfg) {
  form.enabled = !!cfg.enabled
  form.followEnabled = !!cfg.followEnabled
  form.model = cfg.model || ''
  form.confidenceMode = cfg.confidenceMode || 'COUNT'
  form.confidenceValue = cfg.confidenceValue ?? 70
  // 后端返回实体字段 filterTypesJson(库内 JSON 字符串),兼容契约命名 filterTypes
  form.filterTypes = parseIdList(cfg.filterTypes ?? cfg.filterTypesJson)
  form.updateTime = cfg.updateTime || ''
}

async function loadConfig() {
  const cfg = await http.get(`/devices/${props.dock.id}/ai/config`)
  applyConfig(cfg || {})
}

/** 保存并经 property/set 下发;设备离线时仅保存(synced=false) */
async function save() {
  saving.value = true
  try {
    const cfg = await http.put(`/devices/${props.dock.id}/ai/config`, {
      enabled: form.enabled,
      followEnabled: form.followEnabled,
      model: form.model,
      confidenceMode: form.confidenceMode,
      confidenceValue: form.confidenceValue,
      filterTypes: form.filterTypes
    })
    applyConfig(cfg || {})
    synced.value = !!cfg?.synced
    if (cfg?.synced) ElMessage.success('已保存并同步到设备')
    else ElMessage.warning('已保存,设备不在线暂未同步')
  } finally { saving.value = false }
}

/* ---------- 识别记录分页 ---------- */
function search() { pager.page = 1; load() }
function onSize() { pager.page = 1; load() }

async function load(silent = false) {
  if (!props.dock?.id || fetching) return
  fetching = true
  if (!silent) loading.value = true
  try {
    const res = await http.get(`/devices/${props.dock.id}/ai/targets/page`, {
      params: {
        page: pager.page, size: pager.size,
        type: typeFilter.value || undefined
      }
    })
    rows.value = res.rows || []
    pager.total = res.total || 0
  } finally { loading.value = false; fetching = false }
}
</script>

<style scoped>
/* 上配置卡 + 下识别记录,一屏内展示 */
.ai-wrap {
  height: 100%; min-height: 0; overflow: hidden;
  display: flex; flex-direction: column; gap: 10px;
  padding-top: 6px;
}

.cfg-card {
  flex-shrink: 0;
  background: #fff; border: 1px solid var(--border); border-radius: 10px;
  padding: 10px 12px;
}
.cfg-head { display: flex; align-items: center; justify-content: space-between; margin-bottom: 8px; }
.cfg-head-right { display: flex; align-items: center; gap: 10px; }
.col-title {
  font-size: 13px; font-weight: 600; color: var(--text);
  padding-left: 8px; border-left: 3px solid var(--primary);
}

/* 配置两排:label + 控件 */
.cfg-grid {
  display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 10px 24px;
}
@media (max-width: 1280px) { .cfg-grid { grid-template-columns: 1fr; } }
.cfg-item { display: flex; align-items: center; gap: 12px; min-height: 32px; }
.cfg-label { width: 76px; flex-shrink: 0; font-size: 13px; color: var(--text-dim); }
.cfg-slider { flex: 1; display: flex; align-items: center; gap: 12px; min-width: 0; }
.cfg-slider .el-slider { flex: 1; min-width: 0; }
.fixed-tip { flex-shrink: 0; font-size: 12px; color: var(--text-faint); }

.rec-card {
  flex: 1; min-height: 0;
  display: flex; flex-direction: column;
}
.rec-card > .el-table { flex: 1; min-height: 0; }
.toolbar { display: flex; gap: 10px; padding: 4px 0 12px; flex-wrap: wrap; align-items: center; }
.dim-tip { font-size: 11.5px; color: var(--text-faint); }
.pager-row { display: flex; justify-content: flex-end; padding: 12px 0 2px; flex-shrink: 0; }

.mono { font-family: 'Consolas', 'Courier New', monospace; font-size: 12px; color: var(--primary); }
/* 高置信度目标红色加粗 */
.hot { color: var(--danger); font-weight: 700; }
</style>
