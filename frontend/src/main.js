import { createApp } from 'vue'
import ElementPlus, { ElMessage } from 'element-plus'
import 'element-plus/dist/index.css'
import zhCn from 'element-plus/es/locale/lang/zh-cn'
import * as ElementPlusIconsVue from '@element-plus/icons-vue'
import App from './App.vue'
import router from './router'
import './styles/index.css'

// 规避 element-plus ElMessage 已知 TDZ bug:message 的 onClose 闭包引用了
// 声明在后的 instance,若消息在对话框关闭过渡的同一 tick 内创建并被同步关闭,
// 会抛 "Cannot access 'instance' before initialization"。延迟一个宏任务创建即可避开。
for (const k of ['success', 'warning', 'error', 'info']) {
  const raw = ElMessage[k]
  if (typeof raw === 'function') {
    ElMessage[k] = (...args) => setTimeout(() => raw(...args), 0)
  }
}

const app = createApp(App)

// 图标全局注册:侧边栏/表格里用 <component :is="图标名" /> 渲染后端下发的 icon 字符串
for (const [key, component] of Object.entries(ElementPlusIconsVue)) {
  app.component(key, component)
}

app.use(ElementPlus, { locale: zhCn })
app.use(router)
app.mount('#app')
