# 应急巡检平台 · 前端

Vue 3 + Vite 5 + Element Plus 2.9 单页应用,对接后端 `http://localhost:8181`(见 `doc/API.md`)。

## 快速开始

```bash
npm install
npm run dev     # http://localhost:5175,/api 代理到 8181
npm run build   # 产物输出到 dist/
```

演示账号:`admin / admin123`(超管)、`operator / operator123`(业务操作员)、`inspector / inspector123`(巡检员)。

## 目录结构

```
src/
  api/index.js          axios 封装:baseURL /api、Bearer 令牌、解包 res.data.data、401 跳登录
  router/index.js       hash 路由,path 与后端 sys_menu.path 一一对应
  styles/index.css      全局主题(科技蓝,品牌中性)
  utils/dict.js         全部枚举的 { label, tagType } 字典
  views/
    Login.vue           登录页(动态 SVG 场景 + 图形验证码)
    Layout.vue          侧边栏 + 顶栏 + 个人信息抽屉
    Dashboard.vue       工作台(指标卡 + ECharts + 待办清单)
    biz/                巡检点位 / 计划 / 任务 / 隐患 / 应急事件
    sys/                人员 / 角色 / 菜单 / 组织 / 租户 / 日志
```

## 约定

- 所有时间字段后端已格式化为 `yyyy-MM-dd HH:mm:ss`,前端直接展示。
- 列表页统一「筛选栏 + 服务端分页表格 + 抽屉表单」;表头 `sortable="custom"` 的 `prop`
  必须落在后端 `PageUtil.allowedCamel(...)` 白名单内,否则排序会被静默回退。
- 侧边栏由 `GET /api/menus/mine` 驱动,按 `group`(BIZ/SYS)分组,折叠状态记在 localStorage。
- 枚举一律传字符串名称,颜色语义集中在 `src/utils/dict.js`。
