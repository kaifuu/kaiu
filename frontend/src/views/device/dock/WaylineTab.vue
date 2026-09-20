<template>
  <div class="wayline-wrap">
    <!-- 左:航线库 -->
    <div class="col-card">
      <div class="col-head">
        <span class="col-title">航线库</span>
        <el-button size="small" type="primary" @click="openDialog()">新增航线</el-button>
      </div>
      <el-table :data="waylines" v-loading="wlLoading" stripe size="small" height="100%">
        <el-table-column prop="name" label="名称" min-width="104" show-overflow-tooltip>
          <template #default="{ row }"><span class="wl-name">{{ row.name }}</span></template>
        </el-table-column>
        <el-table-column prop="templateTypes" label="模板" width="84">
          <template #default="{ row }">
            <el-tag size="small" :type="dictTag(WAYLINE_TEMPLATE, row.templateTypes)" effect="plain">
              {{ dictLabel(WAYLINE_TEMPLATE, row.templateTypes) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="waypointCount" label="航点" width="52" align="center">
          <template #default="{ row }">{{ row.waypointCount ?? '-' }}</template>
        </el-table-column>
        <el-table-column label="操作" width="86" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="openDialog(row)">编辑</el-button>
            <el-popconfirm title="确认删除该航线?" @confirm="removeWayline(row.id)">
              <template #reference>
                <el-button link type="danger" size="small">删除</el-button>
              </template>
            </el-popconfirm>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <!-- 右:任务区 -->
    <div class="col-card">
      <div class="toolbar">
        <el-select v-model="dispatch.waylineId" filterable placeholder="选择航线" style="width: 200px">
          <el-option v-for="w in waylines" :key="w.id" :label="w.name" :value="w.id" />
        </el-select>
        <el-radio-group v-model="dispatch.jobType">
          <el-radio-button v-for="o in dictOptions(WAYLINE_JOB_TYPE)" :key="o.value" :value="o.value">
            {{ o.label }}
          </el-radio-button>
        </el-radio-group>
        <el-date-picker v-if="dispatch.jobType === 'TIMED'" v-model="dispatch.executeTime" type="datetime"
                        value-format="YYYY-MM-DD HH:mm:ss" placeholder="选择执行时间" style="width: 195px" />
        <span class="rth-field">返航
          <el-input-number v-model="dispatch.rthAltitude" :min="30" :max="500" :controls="false"
                           style="width: 72px" />
          m
        </span>
        <el-button type="primary" :loading="dispatching" :disabled="offline" @click="dispatchJob">下发任务</el-button>
        <el-button type="danger" plain :disabled="offline" @click="returnHomeNow">一键返航</el-button>
        <el-button type="warning" plain :disabled="offline" @click="inFlightDispatch">空中下发</el-button>
      </div>

      <!-- 条件任务配置区:替代执行时间选择器 -->
      <div class="cond-bar" v-if="dispatch.jobType === 'CONDITION'">
        <span class="cond-label">就绪条件</span>
        <span class="cond-item">电量 ≥
          <el-input-number v-model="dispatch.batteryCapacity" :min="50" :max="100" :controls="false"
                           style="width: 64px" /> %
        </span>
        <span class="cond-item">时间窗
          <el-date-picker v-model="dispatch.beginTime" type="datetime" placeholder="起始(可选)"
                          value-format="YYYY-MM-DD HH:mm:ss" style="width: 185px" />
          ~
          <el-date-picker v-model="dispatch.endTime" type="datetime" placeholder="截止(可选)"
                          value-format="YYYY-MM-DD HH:mm:ss" style="width: 185px" />
        </span>
        <span class="dim-tip">条件满足后设备回报 flighttask_ready 自动执行</span>
      </div>

      <el-table :data="jobs" v-loading="jobLoading" stripe size="small" height="100%" @sort-change="onSort">
        <el-table-column type="index" label="序号" width="50"
                         :index="(i) => (jobPager.page - 1) * jobPager.size + i + 1" />
        <el-table-column prop="flightId" label="任务号" width="150" show-overflow-tooltip>
          <template #default="{ row }"><span class="mono">{{ row.flightId }}</span></template>
        </el-table-column>
        <el-table-column prop="waylineName" label="航线" min-width="110" show-overflow-tooltip>
          <template #default="{ row }">{{ row.waylineName || '-' }}</template>
        </el-table-column>
        <el-table-column prop="jobType" label="类型" width="88">
          <template #default="{ row }">
            <el-tag size="small" :type="dictTag(WAYLINE_JOB_TYPE, row.jobType)" effect="plain">
              {{ dictLabel(WAYLINE_JOB_TYPE, row.jobType) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="jobChannel" label="通道" width="70">
          <template #default="{ row }">
            <el-tag size="small" :type="row.jobChannel === 'IN_FLIGHT' ? 'warning' : 'info'" effect="plain">
              {{ row.jobChannel === 'IN_FLIGHT' ? '空中' : '常规' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="executeTime" label="执行时间" width="160" sortable="custom">
          <template #default="{ row }">{{ row.executeTime || row.dispatchedAt || '-' }}</template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="92" sortable="custom">
          <template #default="{ row }">
            <el-tag size="small" :type="dictTag(WAYLINE_JOB_STATUS, row.status)" effect="plain">
              {{ dictLabel(WAYLINE_JOB_STATUS, row.status) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="progress" label="进度" width="140">
          <template #default="{ row }">
            <el-progress :percentage="progressOf(row)" :stroke-width="6" :color="progressColor(row)" />
          </template>
        </el-table-column>
        <el-table-column prop="mediaCount" label="媒体" width="56" align="center">
          <template #default="{ row }">{{ row.mediaCount ?? 0 }}</template>
        </el-table-column>
        <el-table-column label="操作" width="190" fixed="right">
          <template #default="{ row }">
            <el-button v-if="canResume(row)" link type="warning" size="small"
                       :disabled="offline" @click="resumeJob(row)">断点续飞</el-button>
            <el-button v-if="canPause(row)" link type="warning" size="small"
                       :disabled="offline" @click="pauseJob(row)">暂停</el-button>
            <el-button v-if="canHover(row)" link type="warning" size="small"
                       :disabled="offline" @click="hoverJob(row)">悬停</el-button>
            <el-button v-if="canRecovery(row)" link type="warning" size="small"
                       :disabled="offline" @click="recoverJob(row)">恢复</el-button>
            <el-button v-if="canCancel(row)" link type="danger" size="small"
                       @click="cancelJob(row)">取消</el-button>
            <el-button v-if="canShowRth(row)" link type="primary" size="small"
                       @click="showRthTrack(row)">返航轨迹</el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="pager-row">
        <el-pagination background layout="total, sizes, prev, pager, next" :total="jobPager.total"
                       v-model:current-page="jobPager.page" v-model:page-size="jobPager.size"
                       :page-sizes="[10, 20, 50]" @current-change="loadJobs" @size-change="onSize" />
      </div>
    </div>

    <!-- 新增 / 编辑航线 -->
    <el-dialog v-model="dialog.visible" :title="dialog.form.id ? '编辑航线' : '新增航线'" width="740px"
               destroy-on-close>
      <el-form :model="dialog.form" label-width="88px">
        <el-row :gutter="12">
          <el-col :span="12">
            <el-form-item label="航线名称" required>
              <el-input v-model="dialog.form.name" placeholder="如 潮白河日常巡检航线" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="模板类型" required>
              <el-select v-model="dialog.form.templateTypes" style="width: 100%">
                <el-option v-for="o in dictOptions(WAYLINE_TEMPLATE)" :key="o.value" :label="o.label" :value="o.value" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="12">
          <el-col :span="12">
            <el-form-item label="高度(m)">
              <el-input-number v-model="dialog.form.alt" :min="0" :max="5000" :precision="1" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="速度(m/s)">
              <el-input-number v-model="dialog.form.speed" :min="0" :max="20" :precision="1" :step="0.5"
                               style="width: 100%" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="备注">
          <el-input v-model="dialog.form.remark" type="textarea" :rows="1" />
        </el-form-item>
        <el-form-item label="航点" required>
          <div class="wp-box">
            <el-table :data="dialog.form.waypoints" size="small" max-height="236" border>
              <el-table-column type="index" label="#" width="42" align="center" />
              <el-table-column label="经度" min-width="130">
                <template #default="{ row }">
                  <el-input-number v-model="row.longitude" :precision="6" :step="0.0001" :controls="false"
                                   size="small" placeholder="经度" class="wp-num" />
                </template>
              </el-table-column>
              <el-table-column label="纬度" min-width="130">
                <template #default="{ row }">
                  <el-input-number v-model="row.latitude" :precision="6" :step="0.0001" :controls="false"
                                   size="small" placeholder="纬度" class="wp-num" />
                </template>
              </el-table-column>
              <el-table-column label="高度(m)" width="118">
                <template #default="{ row }">
                  <el-input-number v-model="row.height" :min="0" :max="5000" :precision="1" :controls="false"
                                   size="small" class="wp-num" />
                </template>
              </el-table-column>
              <el-table-column label="速度(m/s)" width="118">
                <template #default="{ row }">
                  <el-input-number v-model="row.speed" :min="0" :max="20" :precision="1" :controls="false"
                                   size="small" class="wp-num" />
                </template>
              </el-table-column>
              <el-table-column label="操作" width="52" align="center">
                <template #default="{ $index }">
                  <el-button link type="danger" size="small"
                             :disabled="dialog.form.waypoints.length <= 1" @click="removeWp($index)">删</el-button>
                </template>
              </el-table-column>
            </el-table>
            <el-button size="small" plain class="wp-add" @click="addWp">+ 添加航点</el-button>
          </div>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialog.visible = false">取消</el-button>
        <el-button type="primary" :loading="dialog.saving" @click="saveWayline">保存</el-button>
      </template>
    </el-dialog>

    <!-- 返航轨迹:return_home_info 事件上报的规划点 -->
    <el-dialog v-model="rthDialog.visible" title="返航轨迹" width="560px">
      <p class="rth-tip" v-if="rthDialog.job">
        任务「{{ rthDialog.job.waylineName || rthDialog.job.flightId }}」共 {{ rthDialog.points.length }} 个规划点
      </p>
      <el-table :data="rthDialog.points" size="small" max-height="360" border>
        <el-table-column type="index" label="#" width="46" align="center" />
        <el-table-column prop="longitude" label="经度" />
        <el-table-column prop="latitude" label="纬度" />
        <el-table-column prop="height" label="高度(m)" width="100" align="right" />
      </el-table>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted, onUnmounted, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import http from '../../../api'
import {
  WAYLINE_TEMPLATE, WAYLINE_JOB_TYPE, WAYLINE_JOB_STATUS,
  dictLabel, dictTag, dictOptions, parseIdList
} from '../../../utils/dict'

const props = defineProps({ dock: { type: Object, default: null } })

/** 机场离线时禁用下发类操作 */
const offline = computed(() => props.dock?.status !== 'ONLINE')

/* ---------- 航线库 ---------- */
const waylines = ref([])
const wlLoading = ref(false)

/* ---------- 任务下发 ---------- */
const dispatching = ref(false)
const dispatch = reactive({
  waylineId: null, jobType: 'IMMEDIATE', executeTime: '',
  rthAltitude: 120, batteryCapacity: 80, beginTime: '', endTime: ''
})

/* ---------- 任务列表 ---------- */
const jobs = ref([])
const jobLoading = ref(false)
const jobPager = reactive({ page: 1, size: 10, total: 0 })
const jobSort = reactive({ sortBy: '', direction: '' })

/* ---------- 航线编辑 ---------- */
const dialog = reactive({ visible: false, saving: false, form: { waypoints: [] } })

/** 活跃(可取消)状态 */
const ACTIVE_JOB = new Set(['SENT', 'READY', 'QUEUED', 'RUNNING', 'PAUSED'])
const canCancel = (row) => ACTIVE_JOB.has(row.status)
/** 失败/取消且设备带回断点信息才可续飞 */
const canResume = (row) => (row.status === 'FAILED' || row.status === 'CANCELED') && !!row.breakpointJson
/** 暂停 / 悬停 / 恢复:常规通道走 flighttask_pause / recovery,空中通道走 in-flight 系列 */
const canPause = (row) => row.status === 'RUNNING' && row.jobChannel !== 'IN_FLIGHT'
const canHover = (row) => row.status === 'RUNNING' && row.jobChannel === 'IN_FLIGHT'
const canRecovery = (row) => row.status === 'PAUSED'
/** 已完成且设备上报过返航轨迹才可查看 */
const canShowRth = (row) => row.status === 'SUCCESS' && !!row.returnHomeJson

const progressOf = (row) => Math.min(100, Math.max(0, Number(row.progress) || 0))
const progressColor = (row) =>
  row.status === 'FAILED' ? '#f04438' : row.status === 'SUCCESS' ? '#12b76a' : undefined

let timer = null
let jobFetching = false

onMounted(async () => {
  await loadWaylines()
  timer = setInterval(() => loadJobs(true), 4000)
})
onUnmounted(() => clearInterval(timer))

/* 任务依赖机场 SN,机场对象异步加载完成后补拉一次 */
watch(() => props.dock?.deviceSn, (sn) => { if (sn) loadJobs() }, { immediate: true })

async function loadWaylines() {
  wlLoading.value = true
  try {
    waylines.value = await http.get('/waylines') || []
    // 当前选中航线被删时重置;为空时默认带出第一条
    if (dispatch.waylineId && !waylines.value.some((w) => w.id === dispatch.waylineId)) {
      dispatch.waylineId = null
    }
    if (!dispatch.waylineId && waylines.value.length) dispatch.waylineId = waylines.value[0].id
  } finally { wlLoading.value = false }
}

function onSize() { jobPager.page = 1; loadJobs() }
function onSort({ prop, order }) {
  jobSort.sortBy = order ? prop : ''
  jobSort.direction = order === 'ascending' ? 'asc' : 'desc'
  jobPager.page = 1
  loadJobs()
}

async function loadJobs(silent = false) {
  if (!props.dock?.deviceSn || jobFetching) return
  jobFetching = true
  if (!silent) jobLoading.value = true
  try {
    const res = await http.get('/wayline-jobs/page', {
      params: {
        dockSn: props.dock.deviceSn,
        page: jobPager.page, size: jobPager.size,
        sortBy: jobSort.sortBy || undefined,
        direction: jobSort.sortBy ? jobSort.direction : undefined
      }
    })
    jobs.value = res.rows || []
    jobPager.total = res.total || 0
  } finally { jobLoading.value = false; jobFetching = false }
}

/* ---------- 任务下发 / 取消 / 续飞 ---------- */
async function dispatchJob() {
  if (!dispatch.waylineId) return ElMessage.warning('请先选择航线')
  if (dispatch.jobType === 'TIMED' && !dispatch.executeTime) return ElMessage.warning('定时任务需选择执行时间')
  dispatching.value = true
  try {
    await http.post('/wayline-jobs', {
      waylineId: dispatch.waylineId,
      dockId: props.dock.id,
      jobType: dispatch.jobType,
      executeTime: dispatch.jobType === 'TIMED' ? dispatch.executeTime : undefined,
      rthAltitude: dispatch.rthAltitude,
      readyConditionsJson: dispatch.jobType === 'CONDITION' ? buildReadyConditions() : undefined
    })
    ElMessage.success('航线任务已下发')
    jobPager.page = 1
    loadJobs()
  } finally { dispatching.value = false }
}

/** 条件任务就绪条件:只含有值字段,时间为毫秒时间戳 */
function buildReadyConditions() {
  const cond = { battery_capacity: dispatch.batteryCapacity }
  if (dispatch.beginTime) cond.begin_time = new Date(dispatch.beginTime).getTime()
  if (dispatch.endTime) cond.end_time = new Date(dispatch.endTime).getTime()
  return JSON.stringify(cond)
}

/** 一键返航:优先执行中的任务,否则取最近的排队 / 执行 / 暂停任务 */
async function returnHomeNow() {
  const target = jobs.value.find((j) => j.status === 'RUNNING')
    || jobs.value.find((j) => ['QUEUED', 'RUNNING', 'PAUSED'].includes(j.status))
  if (!target) return ElMessage.warning('当前没有执行中的任务')
  try {
    await ElMessageBox.confirm(
      `确认对任务「${target.waylineName || target.flightId}」一键返航?飞行器将中止任务并自动返航`,
      '一键返航', { type: 'warning', confirmButtonText: '返航', cancelButtonText: '再想想' })
  } catch (e) { return }
  await http.post(`/wayline-jobs/${target.id}/return-home`)
  ElMessage.success('已发起一键返航')
  loadJobs()
}

/** 空中下发:跳过 prepare/execute,直接向飞行中的飞行器投递选中航线 */
async function inFlightDispatch() {
  if (!dispatch.waylineId) return ElMessage.warning('请先选择航线')
  const wayline = waylines.value.find((w) => w.id === dispatch.waylineId)
  try {
    await ElMessageBox.confirm(
      `确认空中下发航线「${wayline?.name || ''}」?将直接投递给飞行中的飞行器`,
      '空中下发', { type: 'warning', confirmButtonText: '下发', cancelButtonText: '再想想' })
  } catch (e) { return }
  await http.post('/wayline-jobs/in-flight', { dockId: props.dock.id, waylineId: dispatch.waylineId })
  ElMessage.success('空中下发成功,飞行器已接收新航线')
  jobPager.page = 1
  loadJobs()
}

async function cancelJob(row) {
  try {
    await ElMessageBox.confirm(`确认取消任务「${row.waylineName || row.flightId}」?`, '取消任务',
      { type: 'warning', confirmButtonText: '取消任务', cancelButtonText: '再想想' })
  } catch (e) { return }
  await http.post(`/wayline-jobs/${row.id}/undo`)
  ElMessage.success('已发起任务取消')
  loadJobs()
}

async function resumeJob(row) {
  await http.post(`/wayline-jobs/${row.id}/resume`)
  ElMessage.success('已发起断点续飞')
  loadJobs()
}

/* ---------- 暂停 / 恢复 / 悬停(Dock 3) ---------- */
async function pauseJob(row) {
  await http.post(`/wayline-jobs/${row.id}/pause`)
  ElMessage.success('任务已暂停')
  loadJobs()
}

/** 恢复已暂停的任务:常规通道 recovery / 空中通道 in-flight/recover */
async function recoverJob(row) {
  await http.post(`/wayline-jobs/${row.id}/${row.jobChannel === 'IN_FLIGHT' ? 'in-flight/recover' : 'recovery'}`)
  ElMessage.success('任务已恢复执行')
  loadJobs()
}

/** 空中下发任务的悬停(in_flight_wayline_stop) */
async function hoverJob(row) {
  await http.post(`/wayline-jobs/${row.id}/in-flight/stop`)
  ElMessage.success('已发起空中悬停')
  loadJobs()
}

/* ---------- 返航轨迹 ---------- */
const rthDialog = reactive({ visible: false, job: null, points: [] })

function showRthTrack(row) {
  rthDialog.job = row
  rthDialog.points = parseIdList(row.returnHomeJson)
  rthDialog.visible = true
}

/* ---------- 航线库维护 ---------- */
function blankWp() {
  return {
    longitude: 116.397428, latitude: 39.90923,
    height: dialog.form.alt ?? 100, speed: dialog.form.speed ?? 5
  }
}

function openDialog(row) {
  dialog.form = row
    ? {
        id: row.id, name: row.name, templateTypes: row.templateTypes,
        alt: row.alt ?? 100, speed: row.speed ?? 5, remark: row.remark || '',
        waypoints: parseIdList(row.waypointsJson).map((w) => ({
          longitude: w.longitude ?? 0, latitude: w.latitude ?? 0,
          height: w.height ?? row.alt ?? 100, speed: w.speed ?? row.speed ?? 5
        }))
      }
    : { id: null, name: '', templateTypes: 'WAYPOINT', alt: 100, speed: 5, remark: '',
        waypoints: [{ longitude: 116.397428, latitude: 39.90923, height: 100, speed: 5 }] }
  dialog.visible = true
}

function addWp() { dialog.form.waypoints.push(blankWp()) }
function removeWp(i) { dialog.form.waypoints.splice(i, 1) }

async function saveWayline() {
  const f = dialog.form
  if (!f.name) return ElMessage.warning('航线名称不能为空')
  if (!f.templateTypes) return ElMessage.warning('请选择模板类型')
  if (!f.waypoints.length) return ElMessage.warning('至少需要一个航点')
  dialog.saving = true
  try {
    const body = {
      name: f.name, templateTypes: f.templateTypes, alt: f.alt, speed: f.speed,
      remark: f.remark, waypoints: f.waypoints
    }
    if (f.id) await http.put(`/waylines/${f.id}`, body)
    else await http.post('/waylines', body)
    ElMessage.success('航线已保存')
    dialog.visible = false
    loadWaylines()
  } finally { dialog.saving = false }
}

async function removeWayline(id) {
  await http.delete(`/waylines/${id}`)
  ElMessage.success('航线已删除')
  if (dispatch.waylineId === id) dispatch.waylineId = null
  loadWaylines()
}
</script>

<style scoped>
/* 左右分栏:左 340px 航线库、右任务区;grid-template-rows:100% 防内容撑破行高 */
.wayline-wrap {
  height: 100%; min-height: 0; overflow: hidden;
  display: grid; grid-template-columns: 340px minmax(0, 1fr); grid-template-rows: 100%; gap: 12px;
  background: #f6f9fd; border: 1px solid var(--border); border-radius: 12px; padding: 12px;
}
@media (max-width: 1280px) {
  .wayline-wrap { grid-template-columns: 1fr; grid-template-rows: minmax(240px, 40%) minmax(0, 60%); overflow-y: auto; }
}

.col-card {
  min-width: 0; min-height: 0;
  display: flex; flex-direction: column;
  background: #fff; border: 1px solid #dbe7f8; border-radius: 10px;
  padding: 10px 10px 8px;
}
.col-card > .el-table { flex: 1; min-height: 0; }
.col-head { display: flex; align-items: center; justify-content: space-between; padding-bottom: 8px; }
.col-title {
  font-size: 13px; font-weight: 600; color: var(--text);
  padding-left: 8px; border-left: 3px solid var(--primary);
}
.toolbar { display: flex; gap: 10px; padding: 2px 0 10px; flex-wrap: wrap; align-items: center; }
.pager-row { display: flex; justify-content: flex-end; padding: 10px 0 2px; flex-shrink: 0; }

/* 返航高度输入:与工具栏同行,弱化为辅助字段 */
.rth-field { display: inline-flex; align-items: center; gap: 5px; font-size: 12.5px; color: var(--text-dim); }

/* 条件任务配置区:类型切到「条件」时出现 */
.cond-bar {
  display: flex; gap: 14px; align-items: center; flex-wrap: wrap;
  padding: 6px 10px; margin-bottom: 10px;
  background: #fffdf5; border: 1px solid #f5e6b8; border-radius: 8px;
  font-size: 12.5px; color: var(--text-dim);
}
.cond-label { font-weight: 600; color: var(--text); }
.cond-item { display: inline-flex; align-items: center; gap: 6px; }
.dim-tip { font-size: 11.5px; color: var(--text-faint); }

/* 返航轨迹弹窗提示行 */
.rth-tip { margin: 0 0 10px; font-size: 13px; color: var(--text-dim); }

.wl-name { font-weight: 600; }
.mono { font-family: 'Consolas', 'Courier New', monospace; font-size: 12px; color: var(--primary); }

/* 航点编辑小表格 */
.wp-box { width: 100%; }
.wp-num { width: 100%; }
.wp-add { margin-top: 8px; }
</style>
