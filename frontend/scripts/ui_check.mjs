/**
 * 前端 UI 冒烟:真实浏览器登录 + 遍历所有页面 + 截图。
 *
 * 验证码是图片,脚本改为旁路读取 /api/auth/captcha 的响应体——
 * 验证码字符本身就以 <text> 写在返回的 SVG 里,读回来填进表单即可,
 * 走的仍是与人工完全一致的登录链路。
 *
 * 用法:node doc/ui_check.mjs [frontend_url] [out_dir]
 * 依赖:playwright-core(用系统已安装的 Chrome/Edge,不下载浏览器)
 */
import { chromium } from 'playwright-core'
import { mkdirSync, writeFileSync } from 'node:fs'

const BASE = process.argv[2] || 'http://localhost:5175'
const OUT = process.argv[3] || 'doc/screenshots'
mkdirSync(OUT, { recursive: true })

const PAGES = [
  ['/dashboard', '工作台'],
  ['/points', '巡检点位'],
  ['/plans', '巡检计划'],
  ['/tasks', '巡检任务'],
  ['/hazards', '隐患上报'],
  ['/events', '应急事件'],
  ['/docks', '机场管理'],
  ['/drones', '无人机管理'],
  ['/issues', '问题清单'],
  ['/work-orders', '工单管理'],
  ['/demands', '需求管理'],
  ['/videos', '视频管理'],
  ['/pilots', '飞手管理'],
  ['/algorithms', '算法管理'],
  ['/sys/users', '人员管理'],
  ['/sys/roles', '角色管理'],
  ['/sys/menus', '菜单管理'],
  ['/sys/orgs', '组织管理'],
  ['/sys/tenants', '租户管理'],
  ['/sys/logs', '日志管理']
]

/** 侧边栏应下发的全部菜单(含不在 PAGES 里的服务大屏) */
const MENUS = [...PAGES.map((p) => p[1]), '服务大屏']

const problems = []
const ok = (label) => console.log('  PASS  ' + label)
const bad = (label, detail) => {
  problems.push(label + ' :: ' + detail)
  console.log('  FAIL  ' + label + '   ' + detail)
}

const browser = await chromium.launch({ channel: 'chrome' })
const ctx = await browser.newContext({ viewport: { width: 1600, height: 950 } })
const page = await ctx.newPage()

// 控制台报错与失败请求都算问题(排除 favicon 这类无关噪音)
page.on('console', (m) => {
  if (m.type() === 'error' && !/favicon|Failed to load resource/i.test(m.text())) {
    problems.push('console error: ' + m.text().slice(0, 200))
  }
})
page.on('pageerror', (e) => problems.push('page error: ' + String(e).slice(0, 200)))
page.on('response', (r) => {
  if (r.url().includes('/api/') && r.status() >= 500) {
    problems.push(`API ${r.status()} ${r.url()}`)
  }
})

console.log('='.repeat(74))
console.log('应急巡检平台 · 前端 UI 冒烟   ' + BASE)
console.log('='.repeat(74))

// ---------------------------------------------------------------- 登录
console.log('\n[1] 登录页')
let captchaCode = null
page.on('response', async (r) => {
  if (r.url().includes('/api/auth/captcha')) {
    try {
      const body = await r.json()
      captchaCode = [...body.data.svg.matchAll(/>([A-Z0-9])<\/text>/g)].map((m) => m[1]).join('')
    } catch { /* 忽略 */ }
  }
})

await page.goto(BASE + '/#/login', { waitUntil: 'networkidle' })
await page.waitForTimeout(1200)
await page.screenshot({ path: `${OUT}/00-login.png` })

const hasCaptcha = await page.locator('.captcha-box img').count()
hasCaptcha ? ok('登录页渲染 + 验证码图片已加载') : bad('登录页渲染', '未找到验证码图片')

// 触发一次验证码请求并读回字符
await page.locator('.captcha-box').click()
await page.waitForTimeout(1000)
captchaCode ? ok('从验证码响应中取回字符 ' + captchaCode) : bad('读取验证码', '响应中未解析出字符')

await page.locator('input[placeholder="请输入账号"]').fill('admin')
await page.locator('input[placeholder="请输入密码"]').fill('admin123')
await page.locator('input[placeholder="请输入验证码"]').fill(captchaCode || '')
await page.locator('.submit-btn').click()
// 等待路由真正离开登录页:dev server 首次编译该页时可能要几秒,固定 sleep 会假失败
let loggedIn = true
try {
  await page.waitForURL((u) => !u.hash.includes('/login'), { timeout: 20000 })
} catch {
  loggedIn = false
}
loggedIn ? ok('登录成功并跳转到工作台') : bad('登录跳转', '仍停留在 ' + page.url())
await page.waitForTimeout(1200)
await page.screenshot({ path: `${OUT}/01-dashboard.png`, fullPage: true })

// ---------------------------------------------------------------- 侧边栏
console.log('\n[2] 侧边栏菜单下发')
await page.goto(BASE + '/#/dashboard', { waitUntil: 'networkidle' })
await page.waitForTimeout(1200)
const menuTexts = await page.locator('.el-menu-item').allInnerTexts()
const expected = MENUS
const MENU_COUNT = MENUS.length
const missing = expected.filter((t) => !menuTexts.some((m) => m.includes(t)))
missing.length === 0 && menuTexts.length === MENU_COUNT
  ? ok(`${MENU_COUNT} 个菜单全部下发(${menuTexts.length} 项)`)
  : bad('菜单下发', `期望 ${MENU_COUNT} 项,实得 ${menuTexts.length} 项;缺少 ` + missing.join('/'))
await page.screenshot({ path: `${OUT}/02-sidebar.png` })

// ---------------------------------------------------------------- 遍历页面
console.log('\n[3] 逐页渲染')
let i = 3
for (const [path, title] of PAGES) {
  await page.goto(BASE + '/#' + path, { waitUntil: 'networkidle' })
  await page.waitForTimeout(1400)
  const file = `${OUT}/${String(i).padStart(2, '0')}-${path.replace(/\//g, '_').replace(/^_/, '')}.png`
  await page.screenshot({ path: file, fullPage: true })

  const titleOk = (await page.locator('.page-title').first().innerText().catch(() => '')).includes(title)
  const rows = await page.locator('.el-table__body tr').count()
  const emptyText = await page.locator('.el-table__empty-text').count()

  // 表宽超出容器时,右侧固定操作列会盖住尾部数据列(状态/结论被挤没)。
  // 这类问题截图上看不出来,必须量 scrollWidth——注意 Element Plus 2.x 真正的滚动容器是
  // 表头/表体内部的 el-scrollbar__wrap,量 .el-table__body-wrapper 永远是 0(会假通过)。
  const overflow = await page.evaluate(() => {
    const el = document.querySelector('.el-table__header-wrapper')
    return el ? el.scrollWidth - el.clientWidth : 0
  })
  if (overflow > 60) {
    problems.push(`${title} 表格横向溢出 ${overflow}px,右侧固定列会遮挡尾部数据列`)
  }

  if (!titleOk) {
    bad(`${title} 页标题`, `期望「${title}」,实际「${await page.locator('.page-title').first().innerText().catch(() => '无')}」`)
  } else if (path === '/dashboard') {
    const charts = await page.locator('canvas').count()
    charts > 0 ? ok(`${title}(图表 ${charts} 个)`) : bad(`${title} 图表`, '未渲染出 canvas')
  } else if (rows === 0 && emptyText === 0) {
    bad(`${title} 表格`, '既无数据行也无空状态')
  } else {
    ok(`${title}(标题正确,数据行 ${rows}${emptyText ? ',空态' : ''})`)
  }
  i++
}

// ---------------------------------------------------------------- 交互
console.log('\n[4] 交互:打开新增抽屉')
await page.goto(BASE + '/#/points', { waitUntil: 'networkidle' })
await page.waitForTimeout(1200)
await page.locator('button:has-text("新增点位")').first().click()
await page.waitForTimeout(900)
const drawerOpen = await page.locator('.el-drawer__body').isVisible().catch(() => false)
drawerOpen ? ok('新增点位抽屉可打开') : bad('抽屉', '未打开')
await page.screenshot({ path: `${OUT}/${String(i).padStart(2, '0')}-drawer.png`, fullPage: true })

// 机场控制页:四个 TAB 逐一校验(基本信息 / 指令单元 / 指令记录 / 设备事件)
console.log('\n[5] 交互:机场控制页')
await page.goto(BASE + '/#/docks', { waitUntil: 'networkidle' })
await page.waitForTimeout(1500)
const dockRow = page.locator('.el-table__body tr').first()
if (await dockRow.count()) {
  await dockRow.locator('button:has-text("控制")').click()
  await page.waitForTimeout(2200)

  // TAB 基本信息:剖面示意 + 指标
  let viz = await page.locator('.dock-viz').count()
  let metrics = await page.locator('.metric').count()
  viz > 0 && metrics >= 5
    ? ok(`TAB 基本信息:剖面示意 + ${metrics} 项指标`)
    : bad('TAB 基本信息', `示意=${viz} 指标=${metrics}`)
  await page.screenshot({ path: `${OUT}/${String(i).padStart(2, '0')}-dock-info.png` })

  // TAB 指令单元:机场图 + 操作台导轨(拨杆开关 + RTH 大钮 + 维护动作)
  await page.locator('.el-tabs__item', { hasText: '指令单元' }).click()
  await page.waitForTimeout(1200)
  let svgParts = await page.locator('.dock-svg .part').count()
  let switches = await page.locator('.sw-row').count()
  let rth = await page.locator('.rth-btn').count()
  let actBtns = await page.locator('.act-flow button').count()
  switches >= 5 && svgParts >= 6 && rth === 1 && actBtns > 0
    ? ok(`TAB 指令单元:机场图 ${svgParts} 个部件 + 操作台 ${switches} 拨杆 / RTH / ${actBtns} 维护动作`)
    : bad('TAB 指令单元', `部件=${svgParts} 拨杆=${switches} RTH=${rth} 维护=${actBtns}`)
  await page.screenshot({ path: `${OUT}/${String(i).padStart(2, '0')}-dock-commands.png` })

  // TAB 指令记录:筛选 + 分页
  await page.locator('.el-tabs__item', { hasText: '指令记录' }).click()
  await page.waitForTimeout(1200)
  let cmdRows = await page.locator('.el-tab-pane:visible .el-table__body tr').count()
  let cmdPager = await page.locator('.el-tab-pane:visible .el-pagination').count()
  cmdPager > 0
    ? ok(`TAB 指令记录:${cmdRows} 行,含筛选与分页`)
    : bad('TAB 指令记录', `无分页器(行=${cmdRows})`)
  await page.screenshot({ path: `${OUT}/${String(i).padStart(2, '0')}-dock-history.png` })

  // TAB 设备事件:筛选 + 分页
  await page.locator('.el-tabs__item', { hasText: '设备事件' }).click()
  await page.waitForTimeout(1200)
  let evtRows = await page.locator('.el-tab-pane:visible .el-table__body tr').count()
  let evtPager = await page.locator('.el-tab-pane:visible .el-pagination').count()
  evtPager > 0
    ? ok(`TAB 设备事件:${evtRows} 行,含筛选与分页`)
    : bad('TAB 设备事件', `无分页器(行=${evtRows})`)
  await page.screenshot({ path: `${OUT}/${String(i).padStart(2, '0')}-dock-events.png` })

  // TAB 航线任务:左航线库 + 右任务表(Dock 3 扩展:返航 / 空中下发)
  await page.locator('.el-tabs__item', { hasText: '航线任务' }).click()
  await page.waitForTimeout(1500)
  let wlRows = await page.locator('.el-tab-pane:visible .el-table__body tr').count()
  let wlJobBtn = await page.locator('.el-tab-pane:visible button:has-text("下发任务")').count()
  let wlRth = await page.locator('.el-tab-pane:visible button:has-text("一键返航")').count()
  let wlInFlight = await page.locator('.el-tab-pane:visible button:has-text("空中下发")').count()
  wlRows > 0 && wlJobBtn === 1 && wlRth === 1 && wlInFlight === 1
    ? ok(`TAB 航线任务:航线/任务表 ${wlRows} 行,含下发/返航/空中下发`)
    : bad('TAB 航线任务', `行=${wlRows} 下发钮=${wlJobBtn} 返航钮=${wlRth} 空中下发=${wlInFlight}`)
  await page.screenshot({ path: `${OUT}/${String(i).padStart(2, '0')}-dock-wayline.png` })

  // TAB 远程调试:指令目录 + 选中后出现参数执行区
  await page.locator('.el-tabs__item', { hasText: '远程调试' }).click()
  await page.waitForTimeout(1500)
  let dbgItems = await page.locator('.el-tab-pane:visible .cat-item').count()
  await page.locator('.el-tab-pane:visible .cat-item').first().click()
  await page.waitForTimeout(800)
  let dbgExec = await page.locator('.el-tab-pane:visible button:has-text("执行指令")').count()
  dbgItems > 0 && dbgExec === 1
    ? ok(`TAB 远程调试:目录 ${dbgItems} 项,选中出执行区`)
    : bad('TAB 远程调试', `目录=${dbgItems} 执行钮=${dbgExec}`)
  await page.screenshot({ path: `${OUT}/${String(i).padStart(2, '0')}-dock-debug.png` })

  // TAB 固件升级:固件库 + 升级任务
  await page.locator('.el-tabs__item', { hasText: '固件升级' }).click()
  await page.waitForTimeout(1500)
  let fwRows = await page.locator('.el-tab-pane:visible .el-table__body tr').count()
  let fwDeploy = await page.locator('.el-tab-pane:visible button:has-text("下发升级")').count()
  fwRows > 0 && fwDeploy > 0
    ? ok(`TAB 固件升级:固件/任务表 ${fwRows} 行,含下发升级`)
    : bad('TAB 固件升级', `行=${fwRows} 下发钮=${fwDeploy}`)
  await page.screenshot({ path: `${OUT}/${String(i).padStart(2, '0')}-dock-firmware.png` })

  // TAB 远程日志:拉取列表 + 上传所选
  await page.locator('.el-tabs__item', { hasText: '远程日志' }).click()
  await page.waitForTimeout(1500)
  let logSync = await page.locator('.el-tab-pane:visible button:has-text("拉取")').count()
  let logUpload = await page.locator('.el-tab-pane:visible button:has-text("上传所选")').count()
  logSync === 1 && logUpload === 1
    ? ok('TAB 远程日志:含拉取列表与上传所选')
    : bad('TAB 远程日志', `拉取=${logSync} 上传=${logUpload}`)
  await page.screenshot({ path: `${OUT}/${String(i).padStart(2, '0')}-dock-logs.png` })

  // TAB AI识别:配置卡 + 识别记录
  await page.locator('.el-tabs__item', { hasText: 'AI识别' }).click()
  await page.waitForTimeout(1500)
  let aiSwitch = await page.locator('.el-tab-pane:visible .el-switch').count()
  let aiSave = await page.locator('.el-tab-pane:visible button:has-text("保存")').count()
  aiSwitch >= 2 && aiSave === 1
    ? ok(`TAB AI识别:${aiSwitch} 个开关 + 保存下发`)
    : bad('TAB AI识别', `开关=${aiSwitch} 保存钮=${aiSave}`)
  await page.screenshot({ path: `${OUT}/${String(i).padStart(2, '0')}-dock-ai.png` })

  // TAB 直播管理:能力/开流表单 + 会话表(Dock 3 live)
  await page.locator('.el-tabs__item', { hasText: '直播管理' }).click()
  await page.waitForTimeout(1500)
  let liveStart = await page.locator('.el-tab-pane:visible button:has-text("开始直播")').count()
  let liveRows = await page.locator('.el-tab-pane:visible .el-table__body tr').count()
  liveStart === 1 && liveRows > 0
    ? ok(`TAB 直播管理:开流入口 + 会话 ${liveRows} 行`)
    : bad('TAB 直播管理', `开始钮=${liveStart} 会话行=${liveRows}`)
  await page.screenshot({ path: `${OUT}/${String(i).padStart(2, '0')}-dock-live.png` })

  // TAB 媒体管理:优先上传卡 + 媒体文件表(Dock 3 media)
  await page.locator('.el-tabs__item', { hasText: '媒体管理' }).click()
  await page.waitForTimeout(1500)
  let mediaPrio = await page.locator('.el-tab-pane:visible button:has-text("设为优先")').count()
  let mediaRows = await page.locator('.el-tab-pane:visible .el-table__body tr').count()
  let mediaEmpty = await page.locator('.el-tab-pane:visible .el-table__empty-text').count()
  mediaPrio === 1 && (mediaRows > 0 || mediaEmpty > 0)
    ? ok(`TAB 媒体管理:优先上传入口,文件 ${mediaRows} 行`)
    : bad('TAB 媒体管理', `优先钮=${mediaPrio} 行=${mediaRows} 空态=${mediaEmpty}`)
  await page.screenshot({ path: `${OUT}/${String(i).padStart(2, '0')}-dock-media.png` })

  // TAB 健康告警:HMS 分级列表
  await page.locator('.el-tabs__item', { hasText: '健康告警' }).click()
  await page.waitForTimeout(1500)
  let hmsRows = await page.locator('.el-tab-pane:visible .el-table__body tr').count()
  let hmsEmpty = await page.locator('.el-tab-pane:visible .el-table__empty-text').count()
  hmsRows > 0 || hmsEmpty > 0
    ? ok(`TAB 健康告警:${hmsRows} 条告警${hmsEmpty ? '(空态)' : ''}`)
    : bad('TAB 健康告警', '既无数据行也无空状态')
  await page.screenshot({ path: `${OUT}/${String(i).padStart(2, '0')}-dock-hms.png` })
} else {
  bad('机场控制页', '列表中没有机场(模拟器未运行?)')
}

// 无人机控制页:确认姿态罗盘、电池环与指令目录
console.log('\n[6] 交互:无人机控制页')
await page.goto(BASE + '/#/drones', { waitUntil: 'networkidle' })
await page.waitForTimeout(1500)
const droneRow = page.locator('.el-table__body tr').first()
if (await droneRow.count()) {
  await droneRow.locator('button:has-text("控制")').click()
  await page.waitForTimeout(2500)
  const compass = await page.locator('.compass').count()
  const ring = await page.locator('.batt-ring').count()
  const btns = await page.locator('.svc-btns button').count()
  compass > 0 && ring > 0
    ? ok(`无人机控制页:姿态罗盘 + 电池环 + ${btns} 条指令`)
    : bad('无人机控制页', `罗盘=${compass} 电池环=${ring}`)
  await page.screenshot({ path: `${OUT}/${String(i).padStart(2, '0')}-drone-control.png`, fullPage: true })
} else {
  bad('无人机控制页', '列表中没有无人机')
}

// 算法管理:算法卡配置 + 手动执行识别 + 臭气分布图与溯源
console.log('\n[7] 交互:算法管理')
await page.goto(BASE + '/#/algorithms', { waitUntil: 'networkidle' })
await page.waitForTimeout(1500)
{
  const cards = await page.locator('.algo-card').count()
  const switches = await page.locator('.algo-card .el-switch').count()
  const alarmRows = await page.locator('.table-panel .el-table__body tr').count()
  cards === 6 && switches === 6
    ? ok(`算法配置:6 张算法卡(含启停开关),告警记录 ${alarmRows} 行`)
    : bad('算法配置', `卡片=${cards} 开关=${switches} 告警行=${alarmRows}`)
  await page.screenshot({ path: `${OUT}/${String(i).padStart(2, '0')}-algo-cards.png`, fullPage: true })
  i++

  // 手动执行一次识别:命中后应弹成功消息并刷新告警表
  await page.locator('.algo-card button:has-text("执行识别")').first().click()
  await page.waitForTimeout(2000)
  const runMsg = await page.locator('.el-message:has-text("命中")').count()
  runMsg > 0
    ? ok('手动执行识别:命中并提示(含录像取证标记)')
    : bad('手动执行识别', '未出现命中消息')
  await page.screenshot({ path: `${OUT}/${String(i).padStart(2, '0')}-algo-run.png`, fullPage: true })
  i++

  // 臭气分布图与溯源:站点着色 + 烟羽轨迹 + 源点标记 + 改风重算
  await page.locator('button:has-text("臭气分布图与溯源")').first().click()
  await page.waitForTimeout(2500)
  const stations = await page.locator('.odor-map-wrap .station').count()
  const plume = await page.locator('.odor-map-wrap line').count()
  const windBadge = await page.locator('.odor-map-wrap .wind-badge').count()
  stations === 8 && windBadge === 1 && plume > 0
    ? ok(`臭气分布图:8 个监测站点 + 风向标 + 烟羽/源点绘制(${plume} 线元)`)
    : bad('臭气分布图', `站点=${stations} 风向标=${windBadge} 线元=${plume}`)
  await page.screenshot({ path: `${OUT}/${String(i).padStart(2, '0')}-algo-odor.png` })
  i++
  await page.locator('.el-dialog__headerbtn').first().click()
  await page.waitForTimeout(600)
}

// 服务大屏:独立全屏路由,不套 Layout,故单独校验
console.log('\n[8] 服务大屏')
await page.goto(BASE + '/#/screen', { waitUntil: 'networkidle' })
await page.waitForTimeout(3000)
const kpiRings = await page.locator('.kpi-ring').count()
const mapMarks = await page.locator('.map circle').count()
const panels = await page.locator('.panel').count()
kpiRings === 5 && mapMarks > 0
  ? ok(`大屏渲染:${panels} 个面板 / ${kpiRings} 个 KPI 环 / ${mapMarks} 个地图标记`)
  : bad('服务大屏', `面板=${panels} KPI环=${kpiRings} 地图标记=${mapMarks}`)
await page.screenshot({ path: `${OUT}/20-screen.png`, fullPage: false })

console.log('\n' + '='.repeat(74))
if (problems.length === 0) {
  console.log('UI 冒烟全部通过,截图已输出到 ' + OUT)
} else {
  console.log(`发现 ${problems.length} 个问题:`)
  for (const p of problems) console.log('  - ' + p)
}
console.log('='.repeat(74))

writeFileSync(`${OUT}/report.txt`, problems.join('\n') || 'all clear\n')
await browser.close()
process.exit(problems.length ? 1 : 0)
