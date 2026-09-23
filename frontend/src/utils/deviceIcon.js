/**
 * 设备图标工具(参考 10_WRJ 的 utils/map.js 图标体系,按本项目设备模型裁剪)。
 *
 * 设备的 icon 字段三种取值:
 *   ''           按设备类型用默认 SVG
 *   'preset:xxx' 预设图标(xxx 为 DEVICE_META 的键)
 *   dataURL      用户上传的自定义图片
 *
 * 图标当前消费方:机场/无人机列表行、大屏设备遥测列表行。
 * resolveDeviceIcon 返回可直接用于 <img src> 的地址,后续做监控地图时可直接复用。
 */

/** 设备类型元数据:标签 / 主色 / 图形 */
const DEVICE_META = {
  DOCK: { label: '机场', color: '#155eef', glyph: 'dock' },
  DRONE: { label: '无人机', color: '#0e9384', glyph: 'drone' }
}

/** 预设图标项(表单里的可选网格) */
export const ICON_PRESETS = Object.entries(DEVICE_META)
  .map(([key, meta]) => ({ key, label: meta.label }))

/** 图形按 44×44 画布、以中心为原点绘制 */
const GLYPHS = {
  dock: (c) => `
    <rect x="-9" y="-3.5" width="18" height="8.5" rx="2" fill="none" stroke="${c}" stroke-width="1.8"/>
    <path d="M-5.5 -3.5 L-5.5 -6.5 M5.5 -3.5 L5.5 -6.5" stroke="${c}" stroke-width="1.6"/>
    <path d="M0 -6.5 L0 -11.5 M-3 -9.2 L0 -12 L3 -9.2" fill="none" stroke="${c}" stroke-width="1.6"
          stroke-linecap="round" stroke-linejoin="round"/>`,
  drone: (c) => `
    <rect x="-3.6" y="-3.6" width="7.2" height="7.2" rx="1.6" fill="none" stroke="${c}" stroke-width="1.8"/>
    <path d="M-3.6 -3.6 L-7 -7 M3.6 -3.6 L7 -7 M-3.6 3.6 L-7 7 M3.6 3.6 L7 7"
          stroke="${c}" stroke-width="1.5"/>
    <circle cx="-7.8" cy="-7.8" r="2.3" fill="none" stroke="${c}" stroke-width="1.5"/>
    <circle cx="7.8" cy="-7.8" r="2.3" fill="none" stroke="${c}" stroke-width="1.5"/>
    <circle cx="-7.8" cy="7.8" r="2.3" fill="none" stroke="${c}" stroke-width="1.5"/>
    <circle cx="7.8" cy="7.8" r="2.3" fill="none" stroke="${c}" stroke-width="1.5"/>`
}

const svgUrl = (svg) => 'data:image/svg+xml;charset=utf-8,' + encodeURIComponent(svg)

/**
 * 生成预设图标的 SVG 地址。
 *
 * @param {string} type  设备类型(DOCK / DRONE)
 * @param {object} opts  online 在线脉冲光圈;color 主色;bg 底板色(大屏暗色下传 'transparent')
 */
export function deviceSvg(type = 'DRONE', { online = true, color, bg = '#ffffff' } = {}) {
  const meta = DEVICE_META[type] || DEVICE_META.DRONE
  const c = color || meta.color
  const glyph = (GLYPHS[meta.glyph] || GLYPHS.drone)(c)
  const pulse = online
    ? `<circle r="16" fill="none" stroke="${c}" stroke-width="1.2" opacity="0.5">
         <animate attributeName="r" values="14;21;14" dur="2.4s" repeatCount="indefinite"/>
         <animate attributeName="opacity" values="0.5;0;0.5" dur="2.4s" repeatCount="indefinite"/>
       </circle>`
    : ''
  // 底板单独一层:暗色场景传 transparent 时只留描边与半透明底色
  const plate = bg === 'transparent'
    ? `<rect x="-13" y="-13" width="26" height="26" rx="8" fill="none" stroke="${c}" stroke-width="1.8"/>`
    : `<rect x="-13" y="-13" width="26" height="26" rx="8" fill="${bg}" stroke="${c}" stroke-width="2.2"/>
       <rect x="-13" y="-13" width="26" height="26" rx="8" fill="${c}1f"/>`
  const svg = `<svg xmlns="http://www.w3.org/2000/svg" width="44" height="44" viewBox="0 0 44 44">
    <g transform="translate(22,22)">
      ${pulse}
      ${plate}
      ${glyph}
    </g>
  </svg>`
  return svgUrl(svg)
}

/** 解析设备图标为可直接使用的地址(列表 <img src> / 后续地图 marker) */
export function resolveDeviceIcon(d, { online = true, color, bg } = {}) {
  const icon = d && d.icon
  // 用户上传的图片原样返回(不套预设底板)
  if (icon && !icon.startsWith('preset:')) return icon
  const type = icon ? icon.slice(7) : (d?.deviceType || 'DRONE')
  return deviceSvg(type, { online, color, bg })
}

/** 仅返回"用户上传"的自定义图标;预设与默认都返回空(便于加边框样式区分) */
export function customDeviceIcon(d) {
  const icon = d && d.icon
  return icon && !icon.startsWith('preset:') ? icon : ''
}

/**
 * 显示态派生。
 *
 * 数据库只维护 ONLINE/OFFLINE 两态(状态机由 MQTT 连接驱动),「飞行中/待命」
 * 是展示层的派生结果 —— 依据最新遥测的 modeCode(≥2 为飞行中)。
 * 这样既能让列表更直观,又不必改动状态机与大屏、预警等既有读取逻辑。
 */
const STATUS_LABEL = { FLYING: '飞行中', IDLE: '待命', OFFLINE: '离线' }

export function deriveStatus(d) {
  if (!d || d.status !== 'ONLINE') return 'OFFLINE'
  return (d.modeCode ?? 0) >= 2 ? 'FLYING' : 'IDLE'
}

export const statusLabel = (key) => STATUS_LABEL[key] || key
