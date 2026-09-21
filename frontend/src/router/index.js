import { createRouter, createWebHashHistory } from 'vue-router'
import Layout from '../views/Layout.vue'

/**
 * 子路由 path 必须与后端 sys_menu.path 种子数据一一对应:
 * 侧边栏由 GET /api/menus/mine 驱动,点击菜单即 router.push(m.path)。
 */
const routes = [
  { path: '/login', name: 'login', component: () => import('../views/Login.vue'), meta: { title: '登录' } },
  {
    path: '/',
    component: Layout,
    redirect: '/dashboard',
    children: [
      // ---- 业务菜单 ----
      { path: 'dashboard', name: 'dashboard', component: () => import('../views/Dashboard.vue'), meta: { title: '工作台' } },
      { path: 'points', name: 'points', component: () => import('../views/biz/PointList.vue'), meta: { title: '巡检点位' } },
      { path: 'plans', name: 'plans', component: () => import('../views/biz/PlanList.vue'), meta: { title: '巡检计划' } },
      { path: 'tasks', name: 'tasks', component: () => import('../views/biz/TaskList.vue'), meta: { title: '巡检任务' } },
      { path: 'hazards', name: 'hazards', component: () => import('../views/biz/HazardList.vue'), meta: { title: '隐患上报' } },
      { path: 'events', name: 'events', component: () => import('../views/biz/EventList.vue'), meta: { title: '应急事件' } },
      { path: 'fences', name: 'fences', component: () => import('../views/biz/FenceList.vue'), meta: { title: '电子围栏' } },
      { path: 'safe-alerts', name: 'safe-alerts', component: () => import('../views/biz/SafeAlertList.vue'), meta: { title: '安全预警' } },
      { path: 'docks', name: 'docks', component: () => import('../views/device/DockList.vue'), meta: { title: '机场管理' } },
      { path: 'docks/:id', name: 'dock-control', component: () => import('../views/device/DockControl.vue'), meta: { title: '机场控制' } },
      { path: 'drones', name: 'drones', component: () => import('../views/device/DroneList.vue'), meta: { title: '无人机管理' } },
      { path: 'drones/:id', name: 'drone-control', component: () => import('../views/device/DroneControl.vue'), meta: { title: '无人机控制' } },
      // ---- 巡检服务 ----
      { path: 'issues', name: 'issues', component: () => import('../views/svc/IssueList.vue'), meta: { title: '问题清单' } },
      { path: 'work-orders', name: 'work-orders', component: () => import('../views/svc/WorkOrderList.vue'), meta: { title: '工单管理' } },
      { path: 'demands', name: 'demands', component: () => import('../views/svc/DemandList.vue'), meta: { title: '需求管理' } },
      { path: 'videos', name: 'videos', component: () => import('../views/svc/VideoList.vue'), meta: { title: '视频管理' } },
      { path: 'pilots', name: 'pilots', component: () => import('../views/svc/PilotList.vue'), meta: { title: '飞手管理' } },
      { path: 'algorithms', name: 'algorithms', component: () => import('../views/algo/AlgorithmList.vue'), meta: { title: '算法管理' } },
      // ---- 系统管理 ----
      { path: 'sys/users', name: 'sys-users', component: () => import('../views/sys/SysUserList.vue'), meta: { title: '人员管理' } },
      { path: 'sys/roles', name: 'sys-roles', component: () => import('../views/sys/SysRoleList.vue'), meta: { title: '角色管理' } },
      { path: 'sys/menus', name: 'sys-menus', component: () => import('../views/sys/SysMenuList.vue'), meta: { title: '菜单管理' } },
      { path: 'sys/orgs', name: 'sys-orgs', component: () => import('../views/sys/SysOrgList.vue'), meta: { title: '组织管理' } },
      { path: 'sys/tenants', name: 'sys-tenants', component: () => import('../views/sys/SysTenantList.vue'), meta: { title: '租户管理' } },
      { path: 'sys/logs', name: 'sys-logs', component: () => import('../views/sys/SysLogList.vue'), meta: { title: '日志管理' } }
    ]
  },
  // 服务大屏独立于 Layout:大屏是全屏展示,不应带侧边栏与顶栏
  { path: '/screen', name: 'screen', component: () => import('../views/Screen.vue'), meta: { title: '服务大屏' } },
  { path: '/:pathMatch(.*)*', redirect: '/dashboard' }
]

const router = createRouter({
  history: createWebHashHistory(),
  routes
})

router.beforeEach((to, from, next) => {
  document.title = (to.meta.title ? to.meta.title + ' · ' : '') + '应急巡检平台'
  const token = localStorage.getItem('token')
  if (to.path !== '/login' && !token) {
    next('/login')
  } else {
    next()
  }
})

export default router
