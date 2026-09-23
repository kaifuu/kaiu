<template>
  <el-drawer v-model="visible" size="660px" direction="rtl" destroy-on-close
             :title="device ? `${device.name} · ${device.deviceSn}` : '设备详情'"
             @opened="initChart" @closed="disposeChart">
    <template v-if="device">
      <!-- 基础信息 -->
      <el-descriptions :column="2" size="small" border class="did-desc">
        <el-descriptions-item label="设备类型">{{ device.deviceType === 'DOCK' ? '机场' : '无人机' }}</el-descriptions-item>
        <el-descriptions-item label="状态">
          <span class="did-status" :class="online ? 'on' : 'off'">{{ statusLabel(deriveStatus(device)) }}</span>
        </el-descriptions-item>
        <el-descriptions-item label="厂商">{{ device.manufacturer || '—' }}</el-descriptions-item>
        <el-descriptions-item label="机型">{{ device.deviceModel || '—' }}</el-descriptions-item>
        <el-descriptions-item v-if="device.usage" label="用途">{{ device.usage }}</el-descriptions-item>
        <el-descriptions-item label="接入方式">{{ device.virtual ? '虚拟设备' : '真机' }}</el-descriptions-item>
        <el-descriptions-item v-if="device.gatewayName" label="所属机场">{{ device.gatewayName }}</el-descriptions-item>
        <el-descriptions-item v-if="device.pilotName" label="绑定飞手">{{ device.pilotName }}</el-descriptions-item>
        <el-descriptions-item label="归航/部署坐标">
          <span v-if="device.homeLng != null">{{ Number(device.homeLng).toFixed(6) }}, {{ Number(device.homeLat).toFixed(6) }}</span>
          <span v-else>—</span>
        </el-descriptions-item>
        <el-descriptions-item label="航高 / 续航">
          <span v-if="device.maxAltitude != null || device.maxEndurance != null">
            {{ device.maxAltitude != null ? `${Number(device.maxAltitude)} m` : '—' }} /
            {{ device.maxEndurance != null ? `${Number(device.maxEndurance)} min` : '—' }}
          </span>
          <span v-else>—</span>
        </el-descriptions-item>
        <el-descriptions-item label="固件版本">{{ device.firmwareVersion || '—' }}</el-descriptions-item>
        <el-descriptions-item label="启停">{{ device.enabled === false ? '已停用(拒绝接入)' : '已启用' }}</el-descriptions-item>
      </el-descriptions>

      <!-- 最新遥测 -->
      <div v-if="chips.length" class="did-latest">
        <span class="did-lt-title">最新数据</span>
        <span v-for="c in chips" :key="c.label" class="did-chip"><i>{{ c.label }}</i>{{ c.value }}</span>
      </div>

      <!-- 历史曲线 -->
      <div class="did-chart-title">历史数据(近 {{ minutes }} 分钟)</div>
      <div ref="chartRef" class="did-chart" v-loading="historyLoading" />
      <div v-if="!historyLoading && !track.length" class="did-nochart">
        该设备暂无历史轨迹(仅飞行器在飞行中产生轨迹点)
      </div>
    </template>
  </el-drawer>
</template>

<script setup>
import { ref, computed, watch, nextTick } from 'vue'
import * as echarts from 'echarts/core'
import { LineChart } from 'echarts/charts'
import { TooltipComponent, GridComponent, LegendComponent } from 'echarts/components'
import { CanvasRenderer } from 'echarts/renderers'
import http from '../api'
import { deriveStatus, statusLabel } from '../utils/deviceIcon'

echarts.use([LineChart, TooltipComponent, GridComponent, LegendComponent, CanvasRenderer])

const props = defineProps({
  device: { type: Object, default: null }
})
const visible = defineModel({ type: Boolean, default: false })

const minutes = 60
const telemetry = ref(null)
const track = ref([])
const historyLoading = ref(false)
const chartRef = ref(null)
let chart = null

const online = computed(() => ['ONLINE', 'FLYING', 'IDLE'].includes(deriveStatus(props.device)))

/** 最新遥测展示项:只取标量字段,对象型(充电/网络/子设备)在控制台页看 */
const chips = computed(() => {
  const t = telemetry.value
  if (!t) return []
  const out = []
  const push = (label, v, unit = '') => {
    if (v !== null && v !== undefined && v !== '') out.push({ label, value: `${v}${unit}` })
  }
  push('工作模式', t.modeLabel)
  push('电量', t.batteryPercent, '%')
  push('经纬度', t.longitude != null ? `${Number(t.longitude).toFixed(5)}, ${Number(t.latitude).toFixed(5)}` : null)
  push('舱盖', t.coverState != null ? (t.coverState === 1 ? '开启' : '闭合') : null)
  push('推杆', t.putterState != null ? (t.putterState === 1 ? '已推' : '收回') : null)
  push('舱内飞行器', t.droneInDock != null ? (t.droneInDock === 1 ? '在位' : '离舱') : null)
  push('环境温度', t.environmentTemperature, ' ℃')
  push('风速', t.windSpeed, ' m/s')
  push('雨量', t.rainfall)
  push('媒体文件', t.mediaFileCount, ' 个')
  return out
})

watch(visible, async (open) => {
  if (!open || !props.device) return
  telemetry.value = null
  track.value = []
  const id = props.device.id
  historyLoading.value = true
  try {
    const [t, tr] = await Promise.all([
      http.get(`/devices/${id}/telemetry`),
      http.get(`/devices/${id}/track`, { params: { minutes, limit: 2000 } })
    ])
    telemetry.value = t
    track.value = tr || []
  } catch (e) { /* 拦截器已提示;详情照常展示 */ } finally {
    historyLoading.value = false
  }
  await nextTick()
  renderChart()
})

function initChart() {
  if (!chartRef.value) return
  chart = echarts.init(chartRef.value)
  renderChart()
}

function renderChart() {
  if (!chart) return
  const pts = track.value
  if (!pts.length) {
    chart.clear()
    return
  }
  const label = (p) => (p.ts || '').slice(11, 16)
  chart.setOption({
    tooltip: { trigger: 'axis' },
    legend: { right: 8, top: 0, itemWidth: 14, itemHeight: 8, textStyle: { fontSize: 11 } },
    grid: { left: 8, right: 12, top: 30, bottom: 4, containLabel: true },
    xAxis: {
      type: 'category', boundaryGap: false, axisTick: { show: false },
      axisLabel: { fontSize: 11, color: '#667085' },
      data: pts.map(label)
    },
    yAxis: [
      { type: 'value', name: '高度(m)', nameTextStyle: { fontSize: 10, color: '#667085' },
        splitLine: { lineStyle: { color: '#eef2f7' } }, axisLabel: { fontSize: 11, color: '#667085' } },
      { type: 'value', name: '电量(%)', min: 0, max: 100, nameTextStyle: { fontSize: 10, color: '#667085' },
        splitLine: { show: false }, axisLabel: { fontSize: 11, color: '#667085' } }
    ],
    series: [
      { name: '高度', type: 'line', smooth: true, symbol: 'none', yAxisIndex: 0,
        lineStyle: { width: 2, color: '#155eef' }, areaStyle: { color: 'rgba(21,94,239,.08)' },
        data: pts.map((p) => p.height) },
      { name: '电量', type: 'line', smooth: true, symbol: 'none', yAxisIndex: 1,
        lineStyle: { width: 2, color: '#12b76a' }, data: pts.map((p) => p.battery) }
    ]
  })
  chart.resize()
}

function disposeChart() {
  chart?.dispose()
  chart = null
}
</script>

<style scoped>
.did-desc { margin-bottom: 12px; }

.did-status { font-weight: 700; }
.did-status.on { color: #12b76a; }
.did-status.off { color: #98a2b3; }

.did-latest { display: flex; flex-wrap: wrap; gap: 6px; align-items: center; margin-bottom: 12px; }
.did-lt-title { font-size: 12.5px; font-weight: 700; color: #344054; margin-right: 4px; }
.did-chip {
  font-size: 12px; color: #101828; font-weight: 600;
  padding: 2px 9px; border-radius: 7px;
  background: #f2f7ff; border: 1px solid #d6e4ff;
}
.did-chip i { font-style: normal; color: var(--text-dim); font-weight: 400; margin-right: 5px; }

.did-chart-title { font-size: 12.5px; font-weight: 700; color: #344054; margin-bottom: 6px; margin-top: 14px; }
.did-chart { width: 100%; height: 260px; }
.did-nochart {
  height: 120px; display: flex; align-items: center; justify-content: center;
  font-size: 12.5px; color: var(--text-faint);
  border: 1px dashed var(--border); border-radius: 9px;
}
</style>
