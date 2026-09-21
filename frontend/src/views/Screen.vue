<template>
  <div class="screen">
    <!-- ===== 顶栏 ===== -->
    <header class="scr-head">
      <div class="head-side">
        <span class="weather"><i>☀</i>晴 24℃ · 东南风 2 级</span>
      </div>
      <h1 class="head-title">应急巡检服务大屏</h1>
      <div class="head-side right">
        <span class="live"><i></i>数据实时刷新</span>
        <span class="clock">{{ clock }}</span>
      </div>
    </header>

    <!-- ===== 子屏导航:8 个胶囊 tab ===== -->
    <nav class="scr-tabs">
      <button v-for="t in TABS" :key="t.key" class="tab" :class="{ on: t.key === tab }" @click="go(t.key)">
        <i />{{ t.label }}
      </button>
    </nav>

    <!-- ===== 子屏(不 keep-alive:各自带轮询与 3D 清理) ===== -->
    <main class="scr-main">
      <component :is="active.comp" />
    </main>

    <div class="scr-foot">
      <span>数据每 30 秒自动刷新 · 最近更新 {{ clock.slice(11) }}</span>
      <span class="foot-right" @click="$router.push('/dashboard')">进入管理后台 →</span>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
// 公共深色面板样式必须全局注册(子屏模板直接用这些类名,scoped @import 会被打上哈希用不了)
import './screen/screen-common.css'
import ScreenHome from './screen/ScreenHome.vue'
import ScreenEmergency from './screen/ScreenEmergency.vue'
import ScreenEcology from './screen/ScreenEcology.vue'
import ScreenOrders from './screen/ScreenOrders.vue'
import ScreenPilots from './screen/ScreenPilots.vue'
import ScreenDevices from './screen/ScreenDevices.vue'
import ScreenWaylines from './screen/ScreenWaylines.vue'
import ScreenFlights from './screen/ScreenFlights.vue'

const TABS = [
  { key: 'home', label: '首页', comp: ScreenHome },
  { key: 'emergency', label: '应急专题', comp: ScreenEmergency },
  { key: 'ecology', label: '生态专题', comp: ScreenEcology },
  { key: 'orders', label: '工单管理', comp: ScreenOrders },
  { key: 'pilots', label: '飞手管理', comp: ScreenPilots },
  { key: 'devices', label: '设备监控', comp: ScreenDevices },
  { key: 'waylines', label: '航线管理', comp: ScreenWaylines },
  { key: 'flights', label: '飞行记录', comp: ScreenFlights }
]

const route = useRoute()
const router = useRouter()

const tab = computed(() => {
  const q = route.query.tab
  return TABS.some((t) => t.key === q) ? q : 'home'
})
const active = computed(() => TABS.find((t) => t.key === tab.value))

function go(key) {
  if (key !== tab.value) {
    // replace:大屏切页不入历史,回退仍回到管理后台
    router.replace({ query: { ...route.query, tab: key } })
  }
}

/* ===== 时钟 ===== */
const clock = ref('')
let clockTimer = null
function tick() {
  const d = new Date()
  const p = (n) => String(n).padStart(2, '0')
  clock.value = `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())} ${p(d.getHours())}:${p(d.getMinutes())}:${p(d.getSeconds())}`
}

onMounted(() => {
  tick()
  clockTimer = setInterval(tick, 1000)
})
onUnmounted(() => clearInterval(clockTimer))
</script>

<style scoped>
/* ===== 大屏:深色指挥中心配色,自成一套主题(不继承后台的亮色变量) ===== */
.screen {
  --scr-bg: #04102a;
  --scr-panel: rgba(9, 30, 62, .72);
  --scr-border: rgba(56, 189, 248, .22);
  --scr-text: #cfe6ff;
  --scr-dim: #7fa8d6;
  --scr-cyan: #38bdf8;

  height: 100vh;
  overflow: hidden;
  display: flex;
  flex-direction: column;
  color: var(--scr-text);
  background:
    radial-gradient(1200px 700px at 50% -10%, rgba(37, 99, 235, .38) 0%, transparent 62%),
    radial-gradient(900px 600px at 4% 100%, rgba(34, 211, 238, .14) 0%, transparent 58%),
    radial-gradient(900px 600px at 96% 100%, rgba(200, 16, 46, .12) 0%, transparent 58%),
    linear-gradient(170deg, #04102a 0%, #061a38 46%, #072248 100%);
}

/* ===== 顶栏 ===== */
.scr-head {
  position: relative; flex-shrink: 0; height: 60px;
  display: flex; align-items: center; justify-content: space-between;
  padding: 0 26px;
  background: linear-gradient(180deg, rgba(10, 40, 84, .9), rgba(6, 24, 52, .5));
  border-bottom: 1px solid var(--scr-border);
}
.scr-head::after {
  content: ''; position: absolute; left: 0; right: 0; bottom: -1px; height: 1px;
  background: linear-gradient(90deg, transparent, var(--scr-cyan) 50%, transparent);
  opacity: .7;
}
.head-title {
  font-size: 25px; font-weight: 800; letter-spacing: 8px; text-indent: 8px;
  background: linear-gradient(180deg, #ffffff 30%, #7dd3fc);
  -webkit-background-clip: text; background-clip: text; -webkit-text-fill-color: transparent;
  text-shadow: 0 0 30px rgba(56, 189, 248, .5);
}
.head-side { width: 320px; display: flex; align-items: center; gap: 12px; font-size: 13px; color: var(--scr-dim); }
.head-side.right { justify-content: flex-end; }
.weather i { color: #fbbf24; font-style: normal; margin-right: 5px; }
.live { display: inline-flex; align-items: center; gap: 6px; }
.live i { width: 7px; height: 7px; border-radius: 50%; background: #34d399; box-shadow: 0 0 8px #34d399; animation: blink 2s infinite; }
@keyframes blink { 0%, 100% { opacity: 1; } 50% { opacity: .3; } }
.clock { color: #7dd3fc; font-variant-numeric: tabular-nums; }

/* ===== 子屏导航 ===== */
.scr-tabs {
  flex-shrink: 0;
  display: flex; align-items: center; justify-content: center; gap: 10px;
  padding: 8px 20px;
  background: linear-gradient(180deg, rgba(8, 32, 68, .55), rgba(5, 20, 44, .3));
  border-bottom: 1px solid rgba(56, 189, 248, .12);
}
.tab {
  position: relative;
  display: inline-flex; align-items: center; gap: 7px;
  padding: 6px 18px;
  border-radius: 999px;
  font-size: 13px; letter-spacing: 1.5px;
  color: #9ec9ee;
  background: rgba(56, 189, 248, .07);
  border: 1px solid rgba(56, 189, 248, .22);
  cursor: pointer;
  transition: all .2s;
}
.tab i {
  width: 6px; height: 6px; border-radius: 50%;
  background: rgba(126, 211, 252, .5);
  transition: all .2s;
}
.tab:hover { color: #e0f2fe; border-color: rgba(56, 189, 248, .5); }
.tab.on {
  color: #04102a; font-weight: 700;
  background: linear-gradient(180deg, #7dd3fc, #38bdf8);
  border-color: #7dd3fc;
  box-shadow: 0 0 18px rgba(56, 189, 248, .55);
}
.tab.on i { background: #04102a; }

/* ===== 子屏挂载区 ===== */
.scr-main { flex: 1; min-height: 0; display: flex; padding: 12px; }
.scr-main > :deep(*) { flex: 1; min-width: 0; }

/* ===== 底栏 ===== */
.scr-foot {
  flex-shrink: 0; height: 30px;
  display: flex; align-items: center; justify-content: space-between;
  padding: 0 26px; font-size: 11.5px; color: var(--scr-dim);
  border-top: 1px solid rgba(56, 189, 248, .14);
}
.foot-right { color: #7dd3fc; cursor: pointer; }
.foot-right:hover { text-decoration: underline; }

@media (max-width: 1500px) {
  .head-title { font-size: 21px; letter-spacing: 5px; }
  .scr-tabs { gap: 7px; padding: 7px 12px; }
  .tab { padding: 5px 13px; font-size: 12px; letter-spacing: 1px; }
}
</style>
