<template>
  <el-container class="layout">
    <!-- 侧边栏 -->
    <el-aside :width="collapsed ? '64px' : '216px'" class="aside">
      <div class="logo" @click="$router.push('/dashboard')">
        <div class="logo-icon">
          <!-- 巡检无人机标识:四旋翼 X 形机臂 + 中心机身 -->
          <svg viewBox="0 0 24 24" width="20" height="20" fill="none" stroke="currentColor" stroke-width="1.6">
            <circle cx="5" cy="5" r="2.6"/><circle cx="19" cy="5" r="2.6"/>
            <circle cx="5" cy="19" r="2.6"/><circle cx="19" cy="19" r="2.6"/>
            <path d="M7 6.5 L17 6.5 M6.5 7 L17.5 17.5 M6.5 17 L17.5 6.5 M7 17.5 L17 17.5"/>
          </svg>
        </div>
        <transition name="fade">
          <span v-if="!collapsed" class="logo-text">应急巡检平台</span>
        </transition>
      </div>

      <el-menu :default-active="$route.path" :collapse="collapsed" router>
        <template v-for="grp in menuGroups">
          <li v-if="!collapsed && grp.count" :key="grp.key" class="menu-group-title"
              :title="groupClosed[grp.key] ? '展开' : '收起'" @click="toggleGroup(grp.key)">
            <span class="mgt-text">{{ grp.title }}</span>
            <el-icon class="mgt-caret" :class="{ closed: groupClosed[grp.key] }"><ArrowDown /></el-icon>
          </li>
          <el-menu-item v-for="m in grp.items" :key="m.path" :index="m.path">
            <el-icon><component :is="m.icon" /></el-icon>
            <template #title>{{ m.label }}</template>
          </el-menu-item>
        </template>
      </el-menu>
    </el-aside>

    <!-- 主区 -->
    <el-container>
      <el-header class="header">
        <div class="header-left">
          <button class="collapse-btn" @click="collapsed = !collapsed">
            <el-icon :size="18"><Fold v-if="!collapsed" /><Expand v-else /></el-icon>
          </button>
          <span class="crumb">{{ $route.meta.title }}</span>
        </div>
        <div class="header-right">
          <span class="clock">{{ clock }}</span>
          <el-dropdown @command="onCommand">
            <span class="user">
              <el-avatar :size="30" style="background: linear-gradient(135deg,#155eef,#0ea5e9)">{{ initial }}</el-avatar>
              <span class="nick">{{ nickname }}</span>
              <el-icon color="#98a2b3"><ArrowDown /></el-icon>
            </span>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item command="profile">个人信息</el-dropdown-item>
                <el-dropdown-item command="logout" divided>退出登录</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </el-header>

      <el-main class="main">
        <router-view />
      </el-main>
    </el-container>

    <!-- 个人信息抽屉(右到左):基础资料 + 修改密码 -->
    <el-drawer v-model="me.visible" title="个人信息" direction="rtl" size="440px">
      <div class="me-head">
        <el-avatar :size="52" style="background: linear-gradient(135deg,#155eef,#0ea5e9)">{{ initial }}</el-avatar>
        <div class="me-head-info">
          <div class="me-nick">{{ me.form.nickname || me.form.username }}</div>
          <div class="me-sub">{{ me.form.username }} · {{ me.form.roleName || '-' }}</div>
        </div>
      </div>

      <el-divider content-position="left">基础资料</el-divider>
      <el-form label-width="80px" v-loading="me.loading">
        <el-form-item label="账号"><el-input :model-value="me.form.username" disabled /></el-form-item>
        <el-form-item label="角色"><el-input :model-value="me.form.roleName" disabled /></el-form-item>
        <el-form-item label="昵称" required><el-input v-model="me.form.nickname" maxlength="32" placeholder="显示在顶栏与操作日志" /></el-form-item>
        <el-form-item label="手机号"><el-input v-model="me.form.phone" maxlength="20" placeholder="用于应急联动触达" /></el-form-item>
        <el-form-item label="最近登录"><span class="me-plain">{{ me.form.lastLoginAt || '-' }}</span></el-form-item>
        <el-button type="primary" :loading="me.saving" @click="saveMe">保存资料</el-button>
      </el-form>

      <el-divider content-position="left">修改密码</el-divider>
      <el-form label-width="80px">
        <el-form-item label="原密码" required>
          <el-input v-model="me.pwd.oldPassword" type="password" show-password placeholder="当前登录密码" />
        </el-form-item>
        <el-form-item label="新密码" required>
          <el-input v-model="me.pwd.newPassword" type="password" show-password placeholder="至少 6 位" />
        </el-form-item>
        <el-form-item label="确认密码" required>
          <el-input v-model="me.pwd.confirm" type="password" show-password placeholder="再次输入新密码" />
        </el-form-item>
        <el-button type="primary" plain :loading="me.pwdSaving" @click="changePwd">修改密码</el-button>
      </el-form>
    </el-drawer>
  </el-container>
</template>

<script setup>
import { ref, reactive, computed, onMounted, onUnmounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Fold, Expand, ArrowDown } from '@element-plus/icons-vue'
import http from '../api'

const router = useRouter()
const collapsed = ref(false)
const nickname = ref(localStorage.getItem('nickname') || 'admin')
const initial = computed(() => (nickname.value || 'A').charAt(0))

/* ---------- 个人信息抽屉 ---------- */
const me = reactive({
  visible: false, loading: false, saving: false, pwdSaving: false,
  form: { username: '', nickname: '', phone: '', roleName: '', lastLoginAt: '' },
  pwd: { oldPassword: '', newPassword: '', confirm: '' }
})

async function openProfile() {
  me.visible = true
  me.loading = true
  try {
    const data = await http.get('/auth/profile')
    me.form.username = data.username
    me.form.nickname = data.nickname || ''
    me.form.phone = data.phone || ''
    me.form.roleName = data.roleName || ''
    me.form.lastLoginAt = data.lastLoginAt || ''
  } finally { me.loading = false }
}

async function saveMe() {
  if (!me.form.nickname?.trim()) return ElMessage.warning('昵称不能为空')
  me.saving = true
  try {
    await http.put('/auth/profile', { nickname: me.form.nickname, phone: me.form.phone })
    nickname.value = me.form.nickname.trim()
    localStorage.setItem('nickname', nickname.value)
    ElMessage.success('资料已保存')
  } finally { me.saving = false }
}

async function changePwd() {
  const { oldPassword, newPassword, confirm } = me.pwd
  if (!oldPassword || !newPassword) return ElMessage.warning('请填写原密码与新密码')
  if (newPassword.length < 6) return ElMessage.warning('新密码长度至少 6 位')
  // 确认密码仅前端校验,后端只收 oldPassword/newPassword
  if (newPassword !== confirm) return ElMessage.warning('两次输入的新密码不一致')
  me.pwdSaving = true
  try {
    await http.post('/auth/profile/password', { oldPassword, newPassword })
    ElMessage.success('密码已修改,下次登录请使用新密码')
    me.pwd = { oldPassword: '', newPassword: '', confirm: '' }
  } finally { me.pwdSaving = false }
}

// 动态菜单:后端 /menus/mine 按角色下发(图标已全局注册,字符串名渲染)
const menus = ref([])
// 分组折叠:点击分组标题收起/展开,状态记忆到 localStorage
const groupClosed = reactive(Object.assign(
  { BIZ: false, SVC: false, SYS: false },
  (() => { try { return JSON.parse(localStorage.getItem('inspection.menuGroupClosed') || '{}') } catch (e) { return {} } })()
))
function toggleGroup(key) {
  groupClosed[key] = !groupClosed[key]
  localStorage.setItem('inspection.menuGroupClosed', JSON.stringify(groupClosed))
}
/** 三个分组:巡检业务 / 巡检服务 / 系统管理,与后端 SysMenu.Group 一一对应 */
const GROUP_TITLES = { BIZ: '巡检业务', SVC: '巡检服务', SYS: '系统管理' }
const menuGroups = computed(() => Object.entries(GROUP_TITLES).map(([key, title]) => {
  const items = menus.value.filter(m => m.group === key)
  return { key, title, count: items.length, items: groupClosed[key] ? [] : items }
}))

const clock = ref('')
let timer = null
onMounted(async () => {
  clock.value = new Date().toLocaleString('zh-CN', { hour12: false })
  timer = setInterval(() => {
    clock.value = new Date().toLocaleString('zh-CN', { hour12: false })
  }, 1000)
  try {
    // 刷新页面后重新拉取菜单(登录响应里已存过一份,失败则回退)
    menus.value = (await http.get('/menus/mine')).map(m => ({
      path: m.path, label: m.name, icon: m.icon, group: m.group
    }))
  } catch (e) {
    const cached = JSON.parse(localStorage.getItem('menus') || '[]')
    menus.value = cached.map(m => ({ path: m.path, label: m.name, icon: m.icon, group: m.group }))
  }
})
onUnmounted(() => clearInterval(timer))

async function onCommand(cmd) {
  if (cmd === 'profile') {
    openProfile()
    return
  }
  if (cmd === 'logout') {
    try {
      await http.post('/auth/logout')
    } catch (e) { /* token 失效也继续退出 */ }
    localStorage.removeItem('token')
    localStorage.removeItem('nickname')
    localStorage.removeItem('roleCode')
    localStorage.removeItem('menus')
    ElMessage.success('已退出登录')
    router.push('/login')
  }
}
</script>

<style scoped>
.layout { height: 100%; }

.aside {
  display: flex; flex-direction: column;
  background: #fff;
  border-right: 1px solid var(--border);
  transition: width 0.25s;
  overflow: hidden;
}

.logo {
  display: flex; align-items: center; gap: 10px;
  padding: 16px 16px; cursor: pointer; white-space: nowrap;
  border-bottom: 1px solid var(--border);
}
.logo-icon {
  width: 34px; height: 34px; flex-shrink: 0;
  display: flex; align-items: center; justify-content: center;
  color: #fff;
  background: linear-gradient(135deg, #155eef, #0ea5e9);
  border-radius: 10px;
  box-shadow: 0 4px 10px -2px rgba(21, 94, 239, 0.4);
}
.logo-text {
  font-size: 15px; font-weight: 700; letter-spacing: 1px; color: #101828;
}

.el-menu { border-right: none; flex: 1; padding: 10px 10px; overflow-y: auto; }
.menu-group-title {
  list-style: none;
  display: flex; align-items: center; justify-content: space-between;
  padding: 14px 12px 6px;
  font-size: 11px; font-weight: 700; letter-spacing: 2px;
  color: #98a2b3;
  cursor: pointer; user-select: none;
}
.menu-group-title:hover .mgt-text { color: #155eef; }
.menu-group-title .mgt-caret { font-size: 12px; transition: transform 0.2s; }
.menu-group-title .mgt-caret.closed { transform: rotate(-90deg); }
.menu-group-title:not(:first-child) { border-top: 1px dashed var(--border); margin-top: 8px; }
:deep(.el-menu-item) {
  border-radius: 9px; margin: 3px 0; height: 42px; line-height: 42px;
  color: #475467; font-weight: 500;
}
:deep(.el-menu-item .el-icon) { color: #667085; }
:deep(.el-menu-item:hover) { background: #f2f6fd; color: #155eef; }
:deep(.el-menu-item:hover .el-icon) { color: #155eef; }
:deep(.el-menu-item.is-active) {
  background: linear-gradient(90deg, #eaf1ff, #f0f9ff);
  color: #155eef; font-weight: 600;
  box-shadow: inset 0 0 0 1px #d6e4ff;
}
:deep(.el-menu-item.is-active .el-icon) { color: #155eef; }

.header {
  height: 56px;
  display: flex; align-items: center; justify-content: space-between;
  background: #fff;
  border-bottom: 1px solid var(--border);
}

.header-left { display: flex; align-items: center; gap: 12px; }
.collapse-btn {
  width: 32px; height: 32px; border-radius: 8px;
  border: 1px solid var(--border); background: #fff;
  display: flex; align-items: center; justify-content: center;
  color: #475467; cursor: pointer; transition: all .2s;
}
.collapse-btn:hover { border-color: #155eef; color: #155eef; background: #f5f8ff; }
.crumb { font-size: 15px; font-weight: 700; color: #101828; }

.header-right { display: flex; align-items: center; gap: 18px; }
.clock { color: #667085; font-size: 13px; font-variant-numeric: tabular-nums; }
.user { display: flex; align-items: center; gap: 8px; cursor: pointer; }
.nick { color: #344054; font-weight: 500; font-size: 13px; }

/* 个人信息抽屉 */
.me-head { display: flex; align-items: center; gap: 14px; padding: 4px 2px 2px; }
.me-head-info { min-width: 0; }
.me-nick { font-size: 16px; font-weight: 700; color: #101828; }
.me-sub { font-size: 12.5px; color: #98a2b3; margin-top: 3px; }
.me-plain { font-size: 13px; color: #475467; }

.main { padding: 0; overflow: hidden; background: var(--bg); }

.fade-enter-active, .fade-leave-active { transition: opacity 0.2s; }
.fade-enter-from, .fade-leave-to { opacity: 0; }
</style>
