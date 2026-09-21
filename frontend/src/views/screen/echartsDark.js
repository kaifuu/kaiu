/**
 * 大屏子屏共享的 echarts 暗色基建:按需注册 + 统一轴/提示框/动画配置。
 * echarts.use 幂等,多个子屏重复 import 无副作用。
 */
import * as echarts from 'echarts/core'
import { BarChart, LineChart, PieChart } from 'echarts/charts'
import { TooltipComponent, LegendComponent, GridComponent, TitleComponent } from 'echarts/components'
import { CanvasRenderer } from 'echarts/renderers'

echarts.use([BarChart, LineChart, PieChart, TooltipComponent, LegendComponent,
  GridComponent, TitleComponent, CanvasRenderer])

/** 轴标签/图例文字 */
export const AXIS = { color: '#7fa8d6', fontSize: 11 }

/** 分割线(淡青) */
export const SPLIT = { lineStyle: { color: 'rgba(56,189,248,.12)' } }

/** 提示框:深蓝毛玻璃 */
export const TIP = {
  backgroundColor: 'rgba(6,24,52,.94)',
  borderColor: 'rgba(56,189,248,.35)',
  textStyle: { color: '#cfe6ff', fontSize: 12 }
}

/** 与工作台一致的过渡动画参数,数据刷新时形态平滑变化 */
export const ANIM = {
  animationDuration: 800,
  animationEasing: 'cubicOut',
  animationDurationUpdate: 700,
  animationEasingUpdate: 'cubicInOut'
}

/** 大屏语义色(与 el-tag/3D 光柱同色系) */
export const C = {
  cyan: '#38bdf8', blue: '#2563eb', sky: '#7dd3fc', teal: '#22d3ee',
  green: '#34d399', yellow: '#fbbf24', orange: '#fb923c', red: '#f87171',
  purple: '#818cf8', grey: '#64748b', pink: '#f472b6'
}

/** 暗色纵向渐变柱 */
export function barGradient(hex, top = 1, bottom = 0.4) {
  const r = parseInt(hex.slice(1, 3), 16)
  const g = parseInt(hex.slice(3, 5), 16)
  const b = parseInt(hex.slice(5, 7), 16)
  return new echarts.graphic.LinearGradient(0, 0, 0, 1, [
    { offset: 0, color: hex },
    { offset: 1, color: `rgba(${r},${g},${b},${bottom})` }
  ])
}

export { echarts }
