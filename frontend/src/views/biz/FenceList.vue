<template>
  <div class="page">
    <div class="page-header">
      <span class="page-title">电子围栏</span>
      <div class="actions">
        <el-radio-group v-model="drawMode" size="small" @change="onModeChange">
          <el-radio-button value="view">查看</el-radio-button>
          <el-radio-button value="polygon">绘制多边形</el-radio-button>
          <el-radio-button value="circle">绘制圆形</el-radio-button>
        </el-radio-group>
        <el-button size="small" :disabled="!draftPoints.length && !draftCenter" @click="undo">撤销</el-button>
        <el-button size="small" :disabled="!draftPoints.length && !draftCenter" @click="clearDraft">清空</el-button>
        <el-button size="small" type="primary" :disabled="!polygonReady" @click="finishPolygon">完成多边形</el-button>
      </div>
    </div>

    <div class="panel map-panel">
      <svg ref="svgRef" class="fence-map" viewBox="0 0 1000 440" preserveAspectRatio="xMidYMid meet"
           :class="{ drawing: drawMode !== 'view' }" @click="onMapClick">
        <!-- 底图:细网格 + 经纬参考线 -->
        <defs>
          <pattern id="grid" width="50" height="50" patternUnits="userSpaceOnUse">
            <path d="M 50 0 L 0 0 0 50" fill="none" stroke="#e4efff" stroke-width="1" />
          </pattern>
        </defs>
        <rect width="1000" height="440" fill="url(#grid)" />
        <line v-for="i in 7" :key="'gv' + i" :x1="i * 125" y1="0" :x2="i * 125" y2="440" stroke="#d4e6fd" stroke-width="1" />
        <line v-for="i in 3" :key="'gh' + i" x1="0" :y1="i * 110" x2="1000" :y2="i * 110" stroke="#d4e6fd" stroke-width="1" />

        <!-- 已存围栏 -->
        <g v-for="f in fences" :key="f.id" :opacity="f.enabled ? 1 : 0.35">
          <polygon v-if="shapeOf(f) === 'POLYGON'" :points="polygonAttr(f)" :fill="fillOf(f)"
                   :stroke="strokeOf(f)" stroke-width="2" />
          <circle v-else :cx="cx(f)" :cy="cy(f)" :r="rPx(f)" :fill="fillOf(f)" :stroke="strokeOf(f)" stroke-width="2" />
          <text :x="labelX(f)" :y="labelY(f)" class="fence-label" :fill="strokeOf(f)">{{ f.name }}</text>
        </g>

        <!-- 绘制中的草稿 -->
        <polygon v-if="draftPoints.length > 1" :points="draftAttr" fill="rgba(14,165,233,.10)"
                 stroke="#0ea5e9" stroke-width="2" stroke-dasharray="8 4" />
        <circle v-if="draftCenter && draftRadiusLng" :cx="p(draftCenter).x" :cy="p(draftCenter).y"
                :r="draftRadiusPx" fill="rgba(14,165,233,.10)" stroke="#0ea5e9" stroke-width="2" stroke-dasharray="8 4" />
        <g v-for="(pt, i) in draftPoints" :key="'d' + i">
          <circle :cx="p(pt).x" :cy="p(pt).y" r="5" fill="#fff" stroke="#0ea5e9" stroke-width="2" />
          <text v-if="i === 0" :x="p(pt).x - 8" :y="p(pt).y - 10" class="draft-hint">起点</text>
        </g>
        <circle v-if="draftCenter" :cx="p(draftCenter).x" :cy="p(draftCenter).y" r="5" fill="#fff"
                stroke="#0ea5e9" stroke-width="2" />
      </svg>

      <div class="map-legend">
        <span><i class="lg" style="background: #f04438" />禁飞区</span>
        <span><i class="lg" style="background: #f79009" />限飞区</span>
        <span><i class="lg" style="background: #155eef" />作业区</span>
        <span class="tip">{{ drawHint }}</span>
      </div>
    </div>

    <div class="panel table-panel">
      <el-table :data="fences" v-loading="loading" stripe height="100%">
        <el-table-column type="index" label="序号" width="56" />
        <el-table-column prop="name" label="围栏名称" min-width="170">
          <template #default="{ row }"><span class="name">{{ row.name }}</span></template>
        </el-table-column>
        <el-table-column prop="fenceType" label="类型" width="100">
          <template #default="{ row }">
            <el-tag size="small" :type="dictTag(FENCE_TYPE, row.fenceType)" effect="light">
              {{ dictLabel(FENCE_TYPE, row.fenceType) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="shape" label="形状" width="90">
          <template #default="{ row }">{{ dictLabel(FENCE_SHAPE, row.shape) }}</template>
        </el-table-column>
        <el-table-column label="规模" min-width="150">
          <template #default="{ row }">{{ scaleOf(row) }}</template>
        </el-table-column>
        <el-table-column prop="maxAltitude" label="限高(m)" width="90">
          <template #default="{ row }">{{ row.maxAltitude ?? '不限' }}</template>
        </el-table-column>
        <el-table-column prop="remark" label="备注" min-width="200" show-overflow-tooltip>
          <template #default="{ row }">{{ row.remark || '-' }}</template>
        </el-table-column>
        <el-table-column prop="enabled" label="启用" width="80">
          <template #default="{ row }">
            <el-switch v-model="row.enabled" @change="toggle(row)" />
          </template>
        </el-table-column>
        <el-table-column label="操作" width="130" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="openEdit(row)">编辑</el-button>
            <el-popconfirm title="确认删除该围栏?" @confirm="remove(row.id)">
              <template #reference>
                <el-button link type="danger" size="small">删除</el-button>
              </template>
            </el-popconfirm>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <!-- 新建围栏:画完后补信息 -->
    <el-dialog v-model="saveDlg.visible" :title="saveDlg.form.id ? '编辑围栏' : '新建围栏'" width="480px">
      <el-form :model="saveDlg.form" label-width="90px">
        <el-form-item label="围栏名称" required>
          <el-input v-model="saveDlg.form.name" placeholder="如:东北高压走廊禁飞区" />
        </el-form-item>
        <el-form-item label="围栏类型">
          <el-select v-model="saveDlg.form.fenceType" style="width: 100%">
            <el-option v-for="o in dictOptions(FENCE_TYPE)" :key="o.value" :label="o.label" :value="o.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="限高(米)">
          <el-input-number v-model="saveDlg.form.maxAltitude" :min="0" :max="1000" style="width: 100%"
                           placeholder="留空不限高" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="saveDlg.form.remark" type="textarea" :rows="2" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="saveDlg.visible = false">取消</el-button>
        <el-button type="primary" :loading="saveDlg.saving" @click="save">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import http from '../../api'
import { FENCE_TYPE, FENCE_SHAPE, dictLabel, dictTag, dictOptions } from '../../utils/dict'

/* ---------------- 基础数据 ---------------- */

const loading = ref(false)
const fences = ref([])
const drawMode = ref('view')

onMounted(load)
async function load() {
  loading.value = true
  try {
    fences.value = await http.get('/fences')
  } finally { loading.value = false }
}

function parsePts(f) {
  try {
    const arr = JSON.parse(f.pointsJson || '[]')
    return Array.isArray(arr) ? arr.filter((p) => p && p.lng !== undefined) : []
  } catch (e) {
    return []
  }
}
const shapeOf = (f) => f.shape || 'POLYGON'

/* ---------------- 投影:包围盒自适应线性投影(小区域误差可忽略) ---------------- */

const W = 1000, H = 440, PAD = 90
/** 参与包围盒的点:已存围栏(圆按四向极值展开)+ 草稿点 */
const bboxPts = computed(() => {
  const pts = []
  fences.value.forEach((f) => {
    if (shapeOf(f) === 'CIRCLE') {
      const c = parsePts(f)[0]
      if (!c) return
      const dLat = (f.radius || 0) / 111320
      const dLng = (f.radius || 0) / (111320 * Math.cos((c.lat * Math.PI) / 180))
      pts.push({ lng: c.lng - dLng, lat: c.lat - dLat }, { lng: c.lng + dLng, lat: c.lat + dLat })
    } else {
      parsePts(f).forEach((p) => pts.push(p))
    }
  })
  draftPoints.value.forEach((p) => pts.push(p))
  if (draftCenter.value) pts.push(draftCenter.value)
  return pts
})

const bbox = computed(() => {
  const pts = bboxPts.value
  if (!pts.length) {
    return { minLng: 116.3900, maxLng: 116.4060, minLat: 39.9040, maxLat: 39.9140 }
  }
  let minLng = Infinity, maxLng = -Infinity, minLat = Infinity, maxLat = -Infinity
  pts.forEach((p) => {
    minLng = Math.min(minLng, p.lng); maxLng = Math.max(maxLng, p.lng)
    minLat = Math.min(minLat, p.lat); maxLat = Math.max(maxLat, p.lat)
  })
  // 单点 / 极小范围兜底跨度,避免除零
  if (maxLng - minLng < 1e-4) { minLng -= 5e-4; maxLng += 5e-4 }
  if (maxLat - minLat < 1e-4) { minLat -= 5e-4; maxLat += 5e-4 }
  return { minLng, maxLng, minLat, maxLat }
})

/** 等比缩放 + 居中:经度方向按 cos(lat) 折算再与纬度比宽高 */
const view = computed(() => {
  const b = bbox.value
  const latMid = (b.minLat + b.maxLat) / 2
  const spanLngPx = ((b.maxLng - b.minLng) * 111320 * Math.cos((latMid * Math.PI) / 180))
  const spanLatPx = (b.maxLat - b.minLat) * 111320
  const scale = Math.min((W - PAD * 2) / spanLngPx, (H - PAD * 2) / spanLatPx)
  const offX = (W - spanLngPx * scale) / 2
  const offY = (H - spanLatPx * scale) / 2
  return {
    x: (lng) => offX + (lng - b.minLng) * 111320 * Math.cos((latMid * Math.PI) / 180) * scale,
    y: (lat) => H - (offY + (lat - b.minLat) * 111320 * scale),
    ix: (x) => (x - offX) / (111320 * Math.cos((latMid * Math.PI) / 180) * scale) + b.minLng,
    iy: (y) => b.minLat + (H - y - offY) / (111320 * scale),
    mScale: scale
  }
})

const p = (pt) => ({ x: view.value.x(pt.lng), y: view.value.y(pt.lat) })

/* ---------------- 围栏渲染 ---------------- */

const TYPE_COLOR = { NO_FLY: '#f04438', LIMIT: '#f79009', WORK: '#155eef' }
// 填充透明度:限飞区略浓一档,浅橙在大面积白底上才不至于看不见
const TYPE_FILL_ALPHA = { NO_FLY: '1a', LIMIT: '26', WORK: '1a' }
const strokeOf = (f) => TYPE_COLOR[f.fenceType] || '#155eef'
const fillOf = (f) => (TYPE_COLOR[f.fenceType] || '#155eef') + (TYPE_FILL_ALPHA[f.fenceType] || '1a')

function polygonAttr(f) {
  return parsePts(f).map((pt) => `${p(pt).x},${p(pt).y}`).join(' ')
}

function circlePx(f) {
  const c = parsePts(f)[0]
  if (!c) return { cx: 0, cy: 0, r: 0 }
  const c1 = p(c)
  const edge = p({ lng: c.lng + (f.radius || 0) / (111320 * Math.cos((c.lat * Math.PI) / 180)), lat: c.lat })
  return { cx: c1.x, cy: c1.y, r: Math.hypot(edge.x - c1.x, edge.y - c1.y) }
}
const cx = (f) => circlePx(f).cx
const cy = (f) => circlePx(f).cy
const rPx = (f) => circlePx(f).r

/** 名称标注:多边形取首点右上,圆取圆心上方 */
function labelX(f) {
  if (shapeOf(f) === 'CIRCLE') return cx(f)
  const pts = parsePts(f)
  return pts.length ? p(pts[0]).x : 0
}
function labelY(f) {
  if (shapeOf(f) === 'CIRCLE') return cy(f) - rPx(f) - 8
  const pts = parsePts(f)
  return pts.length ? p(pts[0]).y - 10 : 0
}

/** 规模:圆 = 半径;多边形 = 周长估算 */
const M_PER_LNG = (lat) => 111320 * Math.cos((lat * Math.PI) / 180)
function havM(lng1, lat1, lng2, lat2) {
  const rad = Math.PI / 180
  const dLat = (lat2 - lat1) * rad, dLng = (lng2 - lng1) * rad
  const a = Math.sin(dLat / 2) ** 2 + Math.cos(lat1 * rad) * Math.cos(lat2 * rad) * Math.sin(dLng / 2) ** 2
  return 2 * 6371008.8 * Math.asin(Math.sqrt(a))
}
function scaleOf(f) {
  if (shapeOf(f) === 'CIRCLE') return `半径 ${f.radius ?? '-'} m`
  const pts = parsePts(f)
  if (pts.length < 2) return `${pts.length} 个顶点`
  let peri = 0
  for (let i = 0; i < pts.length; i++) {
    const a = pts[i], b = pts[(i + 1) % pts.length]
    peri += havM(a.lng, a.lat, b.lng, b.lat)
  }
  return `${pts.length} 顶点 · 周长约 ${Math.round(peri)} m`
}

/* ---------------- 绘制交互:多边形 = 单击加点 + 完成按钮;圆 = 两击定心定径 ---------------- */

const draftPoints = ref([])
const draftCenter = ref(null)
const draftRadiusLng = ref(0)

const drawHint = computed(() => {
  if (drawMode.value === 'polygon') return '单击地图添加顶点(至少 3 个),点击「完成多边形」结束'
  if (drawMode.value === 'circle') return draftCenter.value ? '再次单击确定半径' : '单击地图确定圆心'
  return '切换到绘制模式可在地图上新增围栏'
})

const polygonReady = computed(() => drawMode.value === 'polygon' && draftPoints.value.length >= 3)

function onModeChange() {
  clearDraft()
}

function onMapClick(e) {
  if (drawMode.value === 'view') return
  const rect = e.currentTarget.getBoundingClientRect()
  // viewBox 等比缩放:先归一到 0-1 再放大到 viewBox 坐标
  const sx = (e.clientX - rect.left) / rect.width * W
  const sy = (e.clientY - rect.top) / rect.height * H
  const lng = view.value.ix(sx), lat = view.value.iy(sy)
  if (drawMode.value === 'polygon') {
    draftPoints.value.push({ lng: round6(lng), lat: round6(lat) })
  } else if (!draftCenter.value) {
    draftCenter.value = { lng: round6(lng), lat: round6(lat) }
    draftRadiusLng.value = 0
  } else {
    const rM = havM(draftCenter.value.lng, draftCenter.value.lat, lng, lat)
    if (rM < 10) return ElMessage.warning('半径过小,请重新点击')
    draftRadiusLng.value = rM / M_PER_LNG(lat)
    openSave({ shape: 'CIRCLE', points: [draftCenter.value], radius: Math.round(rM) })
  }
}

function finishPolygon() {
  const pts = draftPoints.value
  // 双击完成时结尾可能出现重复点,去掉
  if (pts.length >= 2) {
    const a = pts[pts.length - 1], b = pts[pts.length - 2]
    if (Math.abs(a.lng - b.lng) < 1e-6 && Math.abs(a.lat - b.lat) < 1e-6) pts.pop()
  }
  if (pts.length < 3) return ElMessage.warning('多边形至少需要 3 个顶点')
  openSave({ shape: 'POLYGON', points: [...pts], radius: null })
}

function undo() {
  if (drawMode.value === 'circle' && draftCenter.value) {
    draftCenter.value = null
    draftRadiusLng.value = 0
  } else {
    draftPoints.value.pop()
  }
}

function clearDraft() {
  draftPoints.value = []
  draftCenter.value = null
  draftRadiusLng.value = 0
}

const round6 = (v) => Math.round(v * 1e6) / 1e6

/** 草稿圆的像素半径 */
const draftRadiusPx = computed(() => {
  if (!draftCenter.value || !draftRadiusLng.value) return 0
  const c = p(draftCenter.value)
  const edge = p({ lng: draftCenter.value.lng + draftRadiusLng.value, lat: draftCenter.value.lat })
  return Math.hypot(edge.x - c.x, edge.y - c.y)
})
const draftAttr = computed(() => draftPoints.value.map((pt) => `${p(pt).x},${p(pt).y}`).join(' '))

/* ---------------- 保存 / 编辑 / 删除 ---------------- */

const saveDlg = reactive({ visible: false, saving: false, form: {}, geometry: null })

function openSave(geometry) {
  saveDlg.geometry = geometry
  saveDlg.form = { id: null, name: '', fenceType: 'NO_FLY', maxAltitude: null, remark: '' }
  saveDlg.visible = true
}

async function save() {
  const f = saveDlg.form
  if (!f.name) return ElMessage.warning('围栏名称不能为空')
  saveDlg.saving = true
  try {
    if (f.id) {
      // 编辑:只改信息字段,几何保持原样
      await http.put(`/fences/${f.id}`, {
        name: f.name, fenceType: f.fenceType,
        maxAltitude: f.maxAltitude ?? null, remark: f.remark || null
      })
      ElMessage.success('围栏已更新')
    } else {
      const g = saveDlg.geometry
      await http.post('/fences', {
        name: f.name, fenceType: f.fenceType, shape: g.shape,
        pointsJson: JSON.stringify(g.points), radius: g.radius,
        maxAltitude: f.maxAltitude ?? null, remark: f.remark || null, enabled: true
      })
      ElMessage.success('围栏已创建')
      drawMode.value = 'view'
      clearDraft()
    }
    saveDlg.visible = false
    load()
  } finally { saveDlg.saving = false }
}

function openEdit(row) {
  saveDlg.geometry = null
  saveDlg.form = {
    id: row.id, name: row.name, fenceType: row.fenceType,
    maxAltitude: row.maxAltitude ?? null, remark: row.remark || ''
  }
  saveDlg.visible = true
}

async function toggle(row) {
  try {
    await http.put(`/fences/${row.id}`, { enabled: row.enabled })
    ElMessage.success(row.enabled ? '已启用' : '已停用')
  } catch (e) {
    row.enabled = !row.enabled
  }
}

async function remove(id) {
  await http.delete(`/fences/${id}`)
  ElMessage.success('已删除')
  load()
}
</script>

<style scoped>
.map-panel {
  padding: 0;
  overflow: hidden;
  margin-bottom: 14px;
}

.fence-map {
  display: block;
  width: 100%;
  height: 440px;
  background: linear-gradient(160deg, #fbfdff 0%, #f3f8ff 100%);
  cursor: default;
}

.fence-map.drawing {
  cursor: crosshair;
}

.fence-label {
  font-size: 15px;
  font-weight: 600;
  paint-order: stroke;
  stroke: #ffffff;
  stroke-width: 4px;
}

.draft-hint {
  font-size: 13px;
  fill: #0ea5e9;
}

.map-legend {
  display: flex;
  align-items: center;
  gap: 18px;
  padding: 10px 16px;
  border-top: 1px solid var(--border, #eef2f7);
  color: var(--text-2, #667085);
  font-size: 13px;
}

.map-legend .lg {
  display: inline-block;
  width: 10px;
  height: 10px;
  border-radius: 3px;
  margin-right: 5px;
  vertical-align: -1px;
}

.map-legend .tip {
  margin-left: auto;
  color: #0ea5e9;
}

/* 编辑围栏对话框关闭后仅恢复查看态;列表与地图同页联动 */
.table-panel {
  flex: 1;
  min-height: 260px;
}
</style>
