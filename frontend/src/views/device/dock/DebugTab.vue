<template>
  <div class="debug-wrap">
    <!-- 左:指令目录(按 group 分组) -->
    <div class="col-card cat-col">
      <div class="col-head">
        <span class="col-title">指令目录</span>
        <span class="cat-count">{{ services.length }} 条</span>
      </div>
      <div class="cat-scroll">
        <div v-for="g in groups" :key="g.name" class="cat-group">
          <div class="cat-group-title">{{ g.name }}</div>
          <div v-for="s in g.items" :key="s.method" class="cat-item"
               :class="{ active: selectedMethod === s.method, danger: s.danger }" @click="select(s)">
            <i v-if="s.danger" class="dot-danger" title="危险操作"></i>
            <span class="cat-label">{{ s.label }}</span>
            <span class="cat-method">{{ s.method }}</span>
          </div>
        </div>
      </div>
    </div>

    <!-- 右:调试试验台 -->
    <div class="col-card bench-col">
      <template v-if="selected">
        <div class="bench-head">
          <div class="bench-title">
            <span class="bench-label">{{ selected.label }}</span>
            <span class="bench-method">{{ selected.method }}</span>
            <el-tag v-if="selected.danger" size="small" type="danger" effect="light">危险操作</el-tag>
          </div>
          <el-button type="primary" size="small" :loading="executing" :disabled="offline" @click="execute">
            执行指令
          </el-button>
        </div>

        <el-alert v-if="offline" type="warning" :closable="false"
                  title="机场离线,指令无法下发" class="offline-alert" />

        <div class="bench-body">
          <el-form v-if="paramList.length" :model="form" label-width="110px" class="param-form">
            <el-form-item v-for="p in paramList" :key="p.key" :label="p.label" :required="p.required">
              <el-input-number v-if="p.type === 'number'" v-model="form[p.key]"
                               :placeholder="p.placeholder" :controls="false" style="width: 240px" />
              <el-input v-else v-model="form[p.key]" :placeholder="p.placeholder" style="width: 240px" />
            </el-form-item>
          </el-form>
          <el-alert v-else type="info" :closable="false" title="该指令无需参数,可直接执行" />

          <div class="result-panel" v-if="result.tid">
            <div class="rp-title">执行结果</div>
            <div class="rp-row">
              <span>tid</span>
              <b class="mono">{{ result.tid }}</b>
            </div>
            <div class="rp-row">
              <span>状态</span>
              <el-tag size="small" :type="dictTag(COMMAND_STATUS, result.status)" effect="plain">
                {{ dictLabel(COMMAND_STATUS, result.status) }}
              </el-tag>
              <i v-if="polling" class="rp-polling">轮询设备回复中…</i>
            </div>
            <div class="rp-row">
              <span>设备回复</span>
              <pre class="mono rp-json">{{ result.replyJson || '-' }}</pre>
            </div>
          </div>
        </div>
      </template>
      <el-empty v-else description="从左侧指令目录选择要执行的指令" :image-size="88" />
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onUnmounted, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import http from '../../../api'
import { COMMAND_STATUS, dictLabel, dictTag } from '../../../utils/dict'

const props = defineProps({ dock: { type: Object, default: null } })

/** 机场离线时禁用下发 */
const offline = computed(() => props.dock?.status !== 'ONLINE')

const services = ref([])
const selectedMethod = ref('')
const selected = computed(() => services.value.find((s) => s.method === selectedMethod.value) || null)
const paramList = computed(() => selected.value?.params || [])

/** 动态参数表单:key = 参数名 */
const form = reactive({})
const executing = ref(false)
const polling = ref(false)
const result = reactive({ tid: '', status: '', replyJson: '' })

/** 按 group 分组,保持目录声明顺序 */
const groups = computed(() => {
  const map = new Map()
  for (const s of services.value) {
    const name = s.group || '其他'
    if (!map.has(name)) map.set(name, [])
    map.get(name).push(s)
  }
  return [...map.entries()].map(([name, items]) => ({ name, items }))
})

let alive = true
onUnmounted(() => { alive = false })

watch(() => props.dock?.id, async (id) => {
  if (!id) return
  services.value = await http.get(`/devices/${id}/services`) || []
}, { immediate: true })

function select(s) {
  selectedMethod.value = s.method
  for (const k of Object.keys(form)) delete form[k]
  for (const p of s.params || []) {
    if (p.type === 'number') {
      const n = Number(p.defaultValue)
      form[p.key] = (p.defaultValue === null || p.defaultValue === undefined
        || p.defaultValue === '' || Number.isNaN(n)) ? null : n
    } else {
      form[p.key] = p.defaultValue ?? null
    }
  }
  result.tid = ''; result.status = ''; result.replyJson = ''
}

async function execute() {
  const s = selected.value
  for (const p of paramList.value) {
    const v = form[p.key]
    if (p.required && (v === null || v === undefined || v === '')) {
      return ElMessage.warning(`请填写「${p.label}」`)
    }
  }
  if (s.danger) {
    try {
      await ElMessageBox.confirm(`确认执行「${s.label}」?该操作有副作用`, '危险操作',
        { type: 'warning', confirmButtonText: '执行', cancelButtonText: '取消' })
    } catch (e) { return }
  }
  // 只上送已填写的参数,空值不透传
  const data = {}
  for (const p of paramList.value) {
    const v = form[p.key]
    if (v !== null && v !== undefined && v !== '') data[p.key] = v
  }
  executing.value = true
  try {
    const cmd = await http.post(`/devices/${props.dock.id}/commands`, { method: s.method, data })
    ElMessage.success(`「${s.label}」已下发`)
    result.tid = cmd.tid
    result.status = cmd.status || 'SENT'
    result.replyJson = ''
    pollResult(cmd.tid, s.label)
  } finally { executing.value = false }
}

/** 参照 DockControl.issue():轮询指令记录按 tid 找设备回复 */
async function pollResult(tid, label) {
  polling.value = true
  try {
    for (let i = 0; i < 8 && alive; i++) {
      await new Promise((r) => setTimeout(r, 800))
      if (!alive) return
      const res = await http.get('/devices/commands/page', {
        params: { deviceSn: props.dock?.deviceSn, page: 1, size: 10 }
      })
      const cur = (res.rows || []).find((c) => c.tid === tid)
      if (cur) {
        result.status = cur.status
        result.replyJson = cur.replyJson || ''
        if (cur.status !== 'SENT') {
          if (cur.status === 'OK') ElMessage.success(`「${label}」执行成功`)
          else ElMessage.warning(`「${label}」${dictLabel(COMMAND_STATUS, cur.status)}`)
          return
        }
      }
    }
  } finally { polling.value = false }
}
</script>

<style scoped>
/* 左 260px 指令目录 + 右试验台 */
.debug-wrap {
  height: 100%; min-height: 0; overflow: hidden;
  display: grid; grid-template-columns: 260px minmax(0, 1fr); grid-template-rows: 100%; gap: 12px;
  background: #f6f9fd; border: 1px solid var(--border); border-radius: 12px; padding: 12px;
}
@media (max-width: 1280px) {
  .debug-wrap { grid-template-columns: 1fr; grid-template-rows: minmax(220px, 38%) minmax(0, 62%); overflow-y: auto; }
}

.col-card {
  min-width: 0; min-height: 0;
  display: flex; flex-direction: column;
  background: #fff; border: 1px solid #dbe7f8; border-radius: 10px;
  padding: 10px 10px 8px;
}
.col-head { display: flex; align-items: center; justify-content: space-between; padding-bottom: 8px; }
.col-title {
  font-size: 13px; font-weight: 600; color: var(--text);
  padding-left: 8px; border-left: 3px solid var(--primary);
}
.cat-count { font-size: 12px; color: var(--text-faint); }

/* 目录滚动区 */
.cat-scroll { flex: 1; min-height: 0; overflow-y: auto; padding-right: 2px; }
.cat-scroll::-webkit-scrollbar { width: 4px; }
.cat-scroll::-webkit-scrollbar-thumb { background: #c9daf3; border-radius: 2px; }
.cat-group { margin-bottom: 10px; }
.cat-group-title {
  font-size: 11px; font-weight: 700; letter-spacing: 1px; color: var(--text-faint);
  padding: 2px 0 4px;
}
.cat-item {
  display: flex; align-items: center; gap: 6px;
  padding: 5px 8px; border-radius: 7px; cursor: pointer;
  font-size: 12.5px; transition: background .2s;
  border: 1px solid transparent;
}
.cat-item:hover { background: rgba(24, 119, 230, .06); }
.cat-item.active {
  background: var(--primary-soft); border-color: #b8cffb;
}
.cat-item.danger .cat-label { color: #d92d20; }
.cat-label { font-weight: 600; color: var(--text); flex-shrink: 0; }
.cat-method {
  font-family: 'Consolas', monospace; font-size: 10.5px; color: var(--text-faint);
  overflow: hidden; text-overflow: ellipsis; white-space: nowrap;
}
.dot-danger { width: 6px; height: 6px; border-radius: 50%; background: #f04438; flex-shrink: 0; }

/* 试验台 */
.bench-head { display: flex; align-items: center; justify-content: space-between; gap: 10px; flex-wrap: wrap; }
.bench-title { display: flex; align-items: center; gap: 8px; flex-wrap: wrap; }
.bench-label { font-size: 15px; font-weight: 700; color: var(--text); }
.bench-method {
  font-family: 'Consolas', monospace; font-size: 12px; color: var(--primary);
  background: var(--primary-soft); border-radius: 4px; padding: 2px 6px;
}
.offline-alert { margin-top: 8px; }

.bench-body { flex: 1; min-height: 0; overflow-y: auto; margin-top: 10px; }
.param-form { max-width: 460px; padding-top: 4px; }

/* 执行结果面板 */
.result-panel {
  margin-top: 14px; padding: 10px 12px;
  background: #f7f9fc; border: 1px solid var(--border); border-radius: 10px;
}
.rp-title {
  font-size: 12px; font-weight: 700; letter-spacing: 1px; color: var(--text-dim);
  margin-bottom: 8px; padding-bottom: 6px; border-bottom: 1px dashed var(--border-strong);
}
.rp-row { display: flex; align-items: flex-start; gap: 10px; font-size: 12.5px; margin-bottom: 6px; }
.rp-row > span { color: var(--text-dim); flex-shrink: 0; width: 56px; }
.rp-row > b { color: var(--text); font-weight: 600; }
.rp-polling { font-style: normal; font-size: 11.5px; color: var(--primary); }
.mono { font-family: 'Consolas', 'Courier New', monospace; }
.rp-json {
  margin: 0; flex: 1; max-height: 180px; overflow: auto;
  font-size: 12px; line-height: 1.6; color: var(--text);
  background: #fff; border: 1px solid var(--border); border-radius: 6px; padding: 8px 10px;
  white-space: pre-wrap; word-break: break-all;
}
</style>
