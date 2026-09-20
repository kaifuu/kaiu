/**
 * 枚举字典:与 doc/API.md 逐条对齐(后端一律传枚举名字符串)。
 * 每个枚举值 → { label: 中文文案, tagType: el-tag 的 type }。
 * 颜色约定全站统一:极高/特别重大 → danger,高/重大 → warning,中/较大 → primary,低/一般 → info。
 */

/* ---------------- 通用状态 ---------------- */
export const ENABLE_STATUS = {
  ENABLED: { label: '启用', tagType: 'success' },
  DISABLED: { label: '停用', tagType: 'info' }
}

/* ---------------- 巡检点位(生态环境巡检对象) ---------------- */
export const POINT_CATEGORY = {
  OUTFALL: { label: '入河排污口', tagType: 'danger' },
  RIVER: { label: '河道断面', tagType: 'primary' },
  AIR: { label: '空气自动站', tagType: 'success' },
  WATER_SOURCE: { label: '饮用水源地', tagType: 'warning' },
  SOLID_WASTE: { label: '固废堆场', tagType: 'warning' },
  FOREST: { label: '林地', tagType: 'success' },
  OTHER: { label: '其他', tagType: 'info' }
}

export const RISK_LEVEL = {
  LOW: { label: '低', tagType: 'info' },
  MEDIUM: { label: '中', tagType: 'primary' },
  HIGH: { label: '高', tagType: 'warning' },
  EXTREME: { label: '极高', tagType: 'danger' }
}

/* ---------------- 巡检计划 ---------------- */
export const PLAN_CATEGORY = {
  DAILY: { label: '日常', tagType: 'primary' },
  SPECIAL: { label: '专项', tagType: 'warning' },
  EMERGENCY: { label: '应急', tagType: 'danger' }
}

export const CYCLE_TYPE = {
  DAY: { label: '天', tagType: 'primary' },
  WEEK: { label: '周', tagType: 'primary' },
  MONTH: { label: '月', tagType: 'primary' },
  ONCE: { label: '一次性', tagType: 'info' }
}

export const PLAN_STATUS = {
  DRAFT: { label: '草稿', tagType: 'info' },
  ENABLED: { label: '已启用', tagType: 'success' },
  DISABLED: { label: '已停用', tagType: 'info' }
}

/* ---------------- 巡检任务 ---------------- */
export const TASK_STATUS = {
  PENDING: { label: '待执行', tagType: 'info' },
  RUNNING: { label: '执行中', tagType: 'primary' },
  DONE: { label: '已完成', tagType: 'success' },
  OVERDUE: { label: '已逾期', tagType: 'danger' },
  CANCELED: { label: '已取消', tagType: 'info' }
}

export const TASK_RESULT = {
  NORMAL: { label: '正常', tagType: 'success' },
  ABNORMAL: { label: '异常', tagType: 'danger' }
}

/* ---------------- 隐患上报 ---------------- */
export const HAZARD_LEVEL = {
  GENERAL: { label: '一般', tagType: 'info' },
  MAJOR: { label: '较大', tagType: 'primary' },
  SEVERE: { label: '重大', tagType: 'warning' },
  CRITICAL: { label: '特别重大', tagType: 'danger' }
}

export const HAZARD_STATUS = {
  PENDING: { label: '待处理', tagType: 'warning' },
  PROCESSING: { label: '处理中', tagType: 'primary' },
  RECTIFIED: { label: '已整改', tagType: 'success' },
  CLOSED: { label: '已关闭', tagType: 'info' }
}

/* ---------------- 环境应急事件 ---------------- */
export const EVENT_CATEGORY = {
  WATER_POLLUTION: { label: '水污染', tagType: 'primary' },
  AIR_POLLUTION: { label: '大气污染', tagType: 'info' },
  SOIL_POLLUTION: { label: '土壤污染', tagType: 'warning' },
  CHEMICAL: { label: '危化品泄漏', tagType: 'danger' },
  ECOLOGY: { label: '生态破坏', tagType: 'success' },
  OTHER: { label: '其他', tagType: 'info' }
}

export const EVENT_LEVEL = {
  I: { label: 'Ⅰ级 特别重大', tagType: 'danger' },
  II: { label: 'Ⅱ级 重大', tagType: 'warning' },
  III: { label: 'Ⅲ级 较大', tagType: 'primary' },
  IV: { label: 'Ⅳ级 一般', tagType: 'info' }
}

export const EVENT_STATUS = {
  PENDING: { label: '待响应', tagType: 'warning' },
  RESPONDING: { label: '响应中', tagType: 'danger' },
  HANDLED: { label: '已处置', tagType: 'success' },
  ARCHIVED: { label: '已归档', tagType: 'info' }
}

/* ---------------- 设备接入(大疆上云 API) ---------------- */
export const DEVICE_TYPE = {
  DOCK: { label: '机场', tagType: 'primary' },
  DRONE: { label: '无人机', tagType: 'success' }
}

export const DEVICE_STATUS = {
  ONLINE: { label: '在线', tagType: 'success' },
  OFFLINE: { label: '离线', tagType: 'info' }
}

export const COMMAND_STATUS = {
  SENT: { label: '已下发', tagType: 'primary' },
  OK: { label: '成功', tagType: 'success' },
  FAILED: { label: '失败', tagType: 'danger' },
  TIMEOUT: { label: '超时', tagType: 'warning' }
}

/** 机场工作状态(mode_code),见上云 API 机场属性 */
export const DOCK_MODE = {
  0: { label: '空闲中', tagType: 'info' },
  1: { label: '现场调试', tagType: 'warning' },
  2: { label: '远程调试', tagType: 'warning' },
  3: { label: '固件升级中', tagType: 'primary' },
  4: { label: '作业中', tagType: 'success' },
  5: { label: '待标定', tagType: 'warning' }
}

/** 飞行器状态(mode_code),见上云 API 飞行器属性 */
export const AIRCRAFT_MODE = {
  0: { label: '待机', tagType: 'info' },
  1: { label: '起飞准备', tagType: 'warning' },
  2: { label: '起飞准备完毕', tagType: 'warning' },
  3: { label: '手动飞行', tagType: 'primary' },
  4: { label: '自动起飞', tagType: 'primary' },
  5: { label: '航线飞行', tagType: 'success' },
  6: { label: '返航中', tagType: 'warning' },
  7: { label: '降落中', tagType: 'warning' },
  8: { label: '降落完成', tagType: 'success' },
  9: { label: '上电中', tagType: 'info' },
  10: { label: '已开机', tagType: 'success' },
  11: { label: '已关机', tagType: 'info' }
}

/** 舱盖 / 推杆状态 */
export const COVER_STATE = {
  0: { label: '关闭', tagType: 'info' },
  1: { label: '打开', tagType: 'success' },
  2: { label: '半开', tagType: 'warning' },
  3: { label: '状态异常', tagType: 'danger' }
}

export const DEVICE_EVENT_TYPE = {
  ONLINE: { label: '上线', tagType: 'success' },
  OFFLINE: { label: '离线', tagType: 'warning' },
  HMS: { label: '健康告警', tagType: 'danger' },
  FLIGHTTASK: { label: '任务进度', tagType: 'primary' },
  FILE_UPLOAD: { label: '媒体上传', tagType: 'info' },
  OTHER: { label: '其他', tagType: 'info' }
}

/** mode_code 按设备类型取不同字典 */
export function modeLabel(deviceType, code) {
  if (code === null || code === undefined) return '-'
  const map = deviceType === 'DOCK' ? DOCK_MODE : AIRCRAFT_MODE
  return map[code]?.label ?? String(code)
}

export function modeTag(deviceType, code) {
  const map = deviceType === 'DOCK' ? DOCK_MODE : AIRCRAFT_MODE
  return map[code]?.tagType || 'info'
}

/* ---------------- 机场作业(航线 / 固件 / 日志 / AI 识别,对齐上云 API) ---------------- */
export const WAYLINE_TEMPLATE = {
  WAYPOINT: { label: '航点模板', tagType: 'primary' },
  POI: { label: '兴趣点', tagType: 'success' },
  INSPECT: { label: '巡查拍照', tagType: 'warning' },
  STRIP: { label: '航带作业', tagType: 'primary' },
  SOLID: { label: '立体作业', tagType: 'info' }
}

export const WAYLINE_JOB_TYPE = {
  IMMEDIATE: { label: '立即任务', tagType: 'primary' },
  TIMED: { label: '定时任务', tagType: 'warning' }
}

export const WAYLINE_JOB_STATUS = {
  SENT: { label: '已下发', tagType: 'primary' },
  READY: { label: '机场就绪', tagType: 'warning' },
  QUEUED: { label: '已入队', tagType: 'primary' },
  RUNNING: { label: '执行中', tagType: 'success' },
  SUCCESS: { label: '已完成', tagType: 'success' },
  FAILED: { label: '失败', tagType: 'danger' },
  CANCELED: { label: '已取消', tagType: 'info' }
}

export const FIRMWARE_TASK_STATUS = {
  SENT: { label: '已下发', tagType: 'primary' },
  DOWNLOADING: { label: '下载中', tagType: 'primary' },
  UPGRADING: { label: '升级中', tagType: 'warning' },
  SUCCESS: { label: '升级成功', tagType: 'success' },
  FAILED: { label: '升级失败', tagType: 'danger' }
}

export const DEVICE_LOG_STATUS = {
  FOUND: { label: '待上传', tagType: 'info' },
  UPLOADING: { label: '上传中', tagType: 'primary' },
  UPLOADED: { label: '已上传', tagType: 'success' },
  FAILED: { label: '失败', tagType: 'danger' }
}

export const AI_TARGET_TYPE = {
  PERSON: { label: '人员', tagType: 'danger' },
  CAR: { label: '车辆', tagType: 'warning' },
  BOAT: { label: '船只', tagType: 'primary' }
}

export const AI_CONFIDENCE_MODE = {
  COUNT: { label: '计数模式', tagType: 'primary' },
  RESCUE: { label: '搜救模式', tagType: 'warning' },
  CUSTOM: { label: '自定义', tagType: 'success' }
}

/* ---------------- 巡检服务(问题 / 工单 / 需求 / 飞手 / 视频) ---------------- */
export const ISSUE_TYPE = {
  ILLEGAL_BUILD: { label: '疑似违建', tagType: 'danger' },
  GARBAGE: { label: '垃圾堆放', tagType: 'warning' },
  FLOATING: { label: '水面漂浮物', tagType: 'primary' },
  ILLEGAL_NET: { label: '非法围网', tagType: 'danger' },
  OUTFALL: { label: '疑似排污口', tagType: 'danger' },
  SLUDGE: { label: '渣土车', tagType: 'warning' },
  CRACK: { label: '路面裂纹', tagType: 'info' },
  WATER_PLANT: { label: '水面植物', tagType: 'success' },
  CONSTRUCTION: { label: '施工堆料', tagType: 'warning' },
  OTHER: { label: '其他', tagType: 'info' }
}

export const ISSUE_STATUS = {
  PENDING: { label: '待处理', tagType: 'warning' },
  DISPATCHED: { label: '已推送', tagType: 'primary' },
  WORK_ORDER: { label: '已生成工单', tagType: 'success' },
  CLOSED: { label: '已结案', tagType: 'info' }
}

export const ISSUE_SOURCE = {
  AI: { label: '算法识别', tagType: 'primary' },
  MANUAL: { label: '人工上报', tagType: 'info' }
}

export const WORK_ORDER_STATUS = {
  PENDING: { label: '待派发', tagType: 'warning' },
  PROCESSING: { label: '处理中', tagType: 'primary' },
  HANDLED: { label: '已处理', tagType: 'success' },
  CLOSED: { label: '已结案', tagType: 'info' }
}

export const WORK_ORDER_PRIORITY = {
  LOW: { label: '低', tagType: 'info' },
  NORMAL: { label: '一般', tagType: 'primary' },
  HIGH: { label: '高', tagType: 'warning' },
  URGENT: { label: '紧急', tagType: 'danger' }
}

export const DEMAND_STATUS = {
  PENDING: { label: '待执行', tagType: 'warning' },
  EXECUTED: { label: '已执行', tagType: 'success' },
  OVERDUE: { label: '已超时', tagType: 'danger' },
  CANCELED: { label: '已取消', tagType: 'info' }
}

export const PILOT_STATUS = {
  AVAILABLE: { label: '可调度', tagType: 'success' },
  ON_TASK: { label: '执行中', tagType: 'primary' },
  LEAVE: { label: '休假', tagType: 'warning' },
  DISABLED: { label: '停用', tagType: 'info' }
}

export const PILOT_CERT = {
  CAAC: { label: 'CAAC 民航局', tagType: 'success' },
  UTC: { label: 'UTC 大疆慧飞', tagType: 'primary' },
  AOPA: { label: 'AOPA', tagType: 'warning' },
  NONE: { label: '无证', tagType: 'danger' }
}

export const VIDEO_STATUS = {
  ONLINE: { label: '在线', tagType: 'success' },
  OFFLINE: { label: '离线', tagType: 'info' }
}

export const VIDEO_PROTOCOL = {
  RTMP: { label: 'RTMP', tagType: 'primary' },
  FLV: { label: 'HTTP-FLV', tagType: 'success' },
  HLS: { label: 'HLS', tagType: 'primary' },
  GB28181: { label: 'GB28181', tagType: 'warning' },
  WEBRTC: { label: 'WebRTC', tagType: 'success' }
}

export const VIDEO_TYPE = {
  LIVE: { label: '直播', tagType: 'success' },
  PLAYBACK: { label: '回放', tagType: 'info' }
}

/** 巡检类别(需求模块复用计划的分类口径) */
export const DEMAND_CATEGORY = {
  DAILY: { label: '日常巡检', tagType: 'primary' },
  SPECIAL: { label: '专项巡检', tagType: 'warning' },
  EMERGENCY: { label: '应急巡检', tagType: 'danger' }
}

/* ---------------- 系统日志 ---------------- */
export const LOG_TYPE = {
  OPERATE: { label: '操作', tagType: 'primary' },
  LOGIN: { label: '登录', tagType: 'success' },
  DEVICE: { label: '设备', tagType: 'warning' }
}

/* ---------------- 取用辅助 ---------------- */
/** 枚举名 → 中文文案;未命中回退原值 */
export function dictLabel(map, value) {
  if (value === null || value === undefined || value === '') return '-'
  return map[value]?.label ?? value
}

/** 枚举名 → el-tag type;未命中回退 info */
export function dictTag(map, value) {
  return map[value]?.tagType || 'info'
}

/** 下拉选项:枚举字典 → [{ label, value }],按对象声明顺序 */
export function dictOptions(map) {
  return Object.entries(map).map(([value, v]) => ({ value, label: v.label }))
}

/** 计划周期展示:「每 2 周」/「一次性」 */
export function cycleText(cycleType, cycleValue) {
  if (cycleType === 'ONCE') return '一次性'
  const unit = CYCLE_TYPE[cycleType]?.label || ''
  return `每 ${cycleValue ?? 1} ${unit}`
}

/** 解析后端存的 JSON 数组字符串(menuIdsJson / pointIds),脏数据按空数组处理 */
export function parseIdList(json) {
  try {
    const arr = JSON.parse(json || '[]')
    return Array.isArray(arr) ? arr.filter((v) => v !== null && v !== undefined) : []
  } catch (e) {
    return []
  }
}
