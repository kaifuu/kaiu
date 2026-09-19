<template>
  <div class="login-page">
    <!-- ===== 背景:亮蓝渐变 + 光晕 + 网格 ===== -->
    <div class="bg-grad"></div>
    <div class="glow glow-1"></div>
    <div class="glow glow-2"></div>
    <div class="glow glow-3"></div>
    <div class="grid-bg"></div>
    <div class="vignette"></div>

    <!-- ===== 场景层:3D 城市 / 山地 / 河川,自动轮播 ===== -->
    <div class="scene-layer">
      <!-- ① 3D 等距城市 -->
      <svg class="scene" :class="{ active: scene === 0 }" :viewBox="CITY_VIEWBOX"
           preserveAspectRatio="xMidYMax slice" fill="none">
        <g fill="rgba(255,255,255,.26)"><path :d="CITY_FACES.right" /></g>
        <g fill="rgba(255,255,255,.46)"><path :d="CITY_FACES.left" /></g>
        <g fill="rgba(255,255,255,.88)"><path :d="CITY_FACES.top" /></g>
      </svg>

      <!-- ② 山地 -->
      <svg class="scene" :class="{ active: scene === 1 }" viewBox="0 0 940 620"
           preserveAspectRatio="xMidYMax slice" fill="none">
        <path d="M0 392 Q 118 296, 222 356 Q 332 246, 462 336 Q 562 266, 682 346 Q 802 276, 940 356 L940 620 L0 620 Z"
              fill="rgba(255,255,255,.15)"/>
        <path d="M0 452 Q 140 372, 262 424 Q 402 332, 542 414 Q 662 352, 782 424 Q 872 384, 940 424 L940 620 L0 620 Z"
              fill="rgba(255,255,255,.27)"/>
        <path d="M0 518 Q 162 458, 302 500 Q 442 428, 602 490 Q 742 450, 862 500 Q 912 480, 940 496 L940 620 L0 620 Z"
              fill="rgba(255,255,255,.44)"/>
        <!-- 山脊林带 -->
        <g fill="rgba(255,255,255,.62)">
          <path d="M96 500 l-9 24 h18 Z"/><path d="M126 506 l-8 22 h16 Z"/>
          <path d="M336 486 l-9 24 h18 Z"/><path d="M366 492 l-8 22 h16 Z"/>
          <path d="M646 480 l-9 24 h18 Z"/><path d="M676 486 l-8 22 h16 Z"/>
          <path d="M836 490 l-8 22 h16 Z"/>
        </g>
      </svg>

      <!-- ③ 河川 -->
      <svg class="scene" :class="{ active: scene === 2 }" viewBox="0 0 940 620"
           preserveAspectRatio="xMidYMax slice" fill="none">
        <path d="M0 376 Q 200 344, 400 370 T 940 360 L940 620 L0 620 Z" fill="rgba(255,255,255,.14)"/>
        <path d="M0 436 Q 240 396, 480 426 T 940 416 L940 620 L0 620 Z" fill="rgba(255,255,255,.26)"/>
        <!-- 水纹:两组反向漂移,叠出流动感 -->
        <g stroke="rgba(255,255,255,.45)" stroke-width="2" fill="none" stroke-linecap="round">
          <animateTransform attributeName="transform" type="translate" values="0 0; 52 0; 0 0" dur="9s" repeatCount="indefinite"/>
          <path d="M60 476 q 32 -11, 64 0 t 64 0"/>
          <path d="M420 500 q 32 -11, 64 0 t 64 0"/>
          <path d="M760 470 q 32 -11, 64 0 t 64 0"/>
          <path d="M200 540 q 32 -11, 64 0 t 64 0"/>
          <path d="M640 556 q 32 -11, 64 0 t 64 0"/>
        </g>
        <g stroke="rgba(255,255,255,.28)" stroke-width="1.6" fill="none" stroke-linecap="round">
          <animateTransform attributeName="transform" type="translate" values="0 0; -44 0; 0 0" dur="12s" repeatCount="indefinite"/>
          <path d="M300 462 q 26 -9, 52 0 t 52 0"/>
          <path d="M620 486 q 26 -9, 52 0 t 52 0"/>
          <path d="M100 520 q 26 -9, 52 0 t 52 0"/>
          <path d="M500 534 q 26 -9, 52 0 t 52 0"/>
        </g>
        <path d="M0 548 Q 260 516, 520 542 T 940 530 L940 620 L0 620 Z" fill="rgba(255,255,255,.42)"/>
        <!-- 河岸林带 -->
        <g fill="rgba(255,255,255,.6)">
          <path d="M180 540 l-9 24 h18 Z"/><path d="M210 546 l-8 22 h16 Z"/>
          <path d="M760 522 l-9 24 h18 Z"/><path d="M790 528 l-8 22 h16 Z"/>
        </g>
      </svg>
    </div>

    <!-- 场景指示 -->
    <div class="scene-dots">
      <span v-for="(s, idx) in SCENE_NAMES" :key="s" class="dot"
            :class="{ on: scene === idx }" @click="scene = idx">{{ s }}</span>
    </div>

    <!-- ===== 飞行中的巡检无人机 ===== -->
    <div class="drone">
      <div class="drone-bob">
        <svg viewBox="0 0 240 200" fill="none">
          <defs>
            <linearGradient id="dBody" x1="0" y1="0" x2=".6" y2="1">
              <stop offset="0" stop-color="#ffffff"/><stop offset="1" stop-color="#cfe6ff"/>
            </linearGradient>
            <radialGradient id="dRotor" cx=".5" cy=".5" r=".5">
              <stop offset="0" stop-color="rgba(255,255,255,.55)"/>
              <stop offset="1" stop-color="rgba(255,255,255,0)"/>
            </radialGradient>
          </defs>

          <!-- 旋翼气流盘 -->
          <g opacity=".75">
            <circle cx="62" cy="52" r="40" fill="url(#dRotor)"/>
            <circle cx="178" cy="52" r="40" fill="url(#dRotor)"/>
            <circle cx="62" cy="148" r="40" fill="url(#dRotor)"/>
            <circle cx="178" cy="148" r="40" fill="url(#dRotor)"/>
          </g>
          <!-- 机臂 -->
          <g stroke="#ffffff" stroke-width="7" stroke-linecap="round" opacity=".9">
            <path d="M96 86 L76 66"/><path d="M144 86 L164 66"/>
            <path d="M96 114 L76 134"/><path d="M144 114 L164 134"/>
          </g>
          <!-- 旋翼 -->
          <g>
            <circle cx="62" cy="52" r="28" fill="none" stroke="rgba(255,255,255,.35)" stroke-width="8"/>
            <circle cx="62" cy="52" r="28" fill="none" stroke="#ffffff" stroke-width="2.4" stroke-dasharray="16 9">
              <animateTransform attributeName="transform" type="rotate" from="0 62 52" to="360 62 52" dur=".5s" repeatCount="indefinite"/>
            </circle>
            <circle cx="178" cy="52" r="28" fill="none" stroke="rgba(255,255,255,.35)" stroke-width="8"/>
            <circle cx="178" cy="52" r="28" fill="none" stroke="#ffffff" stroke-width="2.4" stroke-dasharray="16 9">
              <animateTransform attributeName="transform" type="rotate" from="0 178 52" to="360 178 52" dur=".46s" repeatCount="indefinite"/>
            </circle>
            <circle cx="62" cy="148" r="28" fill="none" stroke="rgba(255,255,255,.35)" stroke-width="8"/>
            <circle cx="62" cy="148" r="28" fill="none" stroke="#ffffff" stroke-width="2.4" stroke-dasharray="16 9">
              <animateTransform attributeName="transform" type="rotate" from="0 62 148" to="360 62 148" dur=".54s" repeatCount="indefinite"/>
            </circle>
            <circle cx="178" cy="148" r="28" fill="none" stroke="rgba(255,255,255,.35)" stroke-width="8"/>
            <circle cx="178" cy="148" r="28" fill="none" stroke="#ffffff" stroke-width="2.4" stroke-dasharray="16 9">
              <animateTransform attributeName="transform" type="rotate" from="0 178 148" to="360 178 148" dur=".42s" repeatCount="indefinite"/>
            </circle>
          </g>
          <!-- 机身 -->
          <rect x="88" y="78" width="64" height="64" rx="18" fill="url(#dBody)" stroke="#ffffff" stroke-width="2"/>
          <circle cx="120" cy="110" r="15" fill="#0b3f8f" stroke="#7dd3fc" stroke-width="2.2"/>
          <circle cx="120" cy="110" r="6.5" fill="#38bdf8"/>
          <circle cx="120" cy="110" r="2.6" fill="#ffffff"/>
          <!-- 云台相机 -->
          <rect x="104" y="140" width="32" height="14" rx="7" fill="#e2eeff" stroke="#ffffff" stroke-width="1.4"/>
          <circle cx="120" cy="147" r="5" fill="#0b3f8f" stroke="#7dd3fc" stroke-width="1.4"/>
          <!-- 状态灯 -->
          <circle cx="120" cy="90" r="3" fill="#4ade80">
            <animate attributeName="opacity" values="1;.15;1" dur="2s" repeatCount="indefinite"/>
          </circle>
        </svg>
        <!-- 扫描光锥 -->
        <div class="drone-beam"></div>
      </div>
    </div>

    <!-- ===== 顶部栏 ===== -->
    <header class="top-bar">
      <div class="top-inner">
        <div class="top-left">
          <span class="top-emblem">
            <svg viewBox="0 0 120 120" fill="none">
              <circle cx="60" cy="60" r="54" fill="none" stroke="rgba(255,255,255,.6)" stroke-width="2" stroke-dasharray="7 5"/>
              <path d="M60 22 L92 35 V64 C92 84 78 98 60 104 C42 98 28 84 28 64 V35 Z"
                    fill="#ffffff" stroke="#ffffff" stroke-width="2.4"/>
              <circle cx="60" cy="58" r="4.2" fill="#1877e6"/>
              <path d="M49 47 L56 54 M71 47 L64 54 M49 69 L56 62 M71 69 L64 62"
                    stroke="#1877e6" stroke-width="2.2" stroke-linecap="round"/>
              <circle cx="48" cy="46" r="2.8" fill="#1877e6"/><circle cx="72" cy="46" r="2.8" fill="#1877e6"/>
              <circle cx="48" cy="70" r="2.8" fill="#1877e6"/><circle cx="72" cy="70" r="2.8" fill="#1877e6"/>
            </svg>
          </span>
          <span class="top-name">应急巡检平台</span>
          <i class="top-sep"></i>
          <span class="top-sub">应急管理 · 无人机巡检</span>
        </div>
        <div class="top-right">
          <span class="top-live"><i></i>系统运行正常</span>
          <i class="top-sep"></i>
          <span class="top-clock">{{ clock }}</span>
        </div>
      </div>
    </header>

    <!-- ===== 主体 ===== -->
    <div class="stage">
      <div class="brand">
        <div class="brand-inner">
          <h1 class="title">应急巡检平台</h1>
          <p class="title-en">EMERGENCY INSPECTION PLATFORM</p>
          <div class="rule"><i></i></div>
          <p class="sub">无人机空中巡查 · 隐患智能识别 · 应急事件联动<br />构建「感知—巡检—处置」一体化的应急管理体系</p>
          <div class="chips">
            <span class="chip"><i class="chip-dot"></i>无人机自动巡检</span>
            <span class="chip"><i class="chip-dot cyan"></i>AI 隐患识别</span>
            <span class="chip"><i class="chip-dot amber"></i>应急事件联动</span>
          </div>
        </div>
      </div>

      <div class="login-wrap">
        <div class="card-dock fade-in-up">
          <div class="login-card">
            <div class="card-head">
              <span class="card-badge">用户登录</span>
              <p class="card-sub">请使用应急管理实名账号登录</p>
            </div>
            <div class="card-body">
              <el-form :model="form" size="large" @keyup.enter="doLogin">
                <el-form-item>
                  <el-input v-model="form.username" placeholder="请输入账号" :prefix-icon="User" />
                </el-form-item>
                <el-form-item>
                  <el-input v-model="form.password" type="password" placeholder="请输入密码" :prefix-icon="Lock" show-password />
                </el-form-item>
                <el-form-item>
                  <div class="captcha-row">
                    <el-input v-model="form.captcha" placeholder="请输入验证码" :prefix-icon="Key" maxlength="4"
                              style="flex: 1" @input="form.captcha = form.captcha.toUpperCase()" />
                    <div class="captcha-box" @click="loadCaptcha" title="点击刷新">
                      <div v-if="captchaLoading" class="captcha-loading"><el-icon class="is-loading"><Loading /></el-icon></div>
                      <img v-else-if="captchaSvg" :src="svgUrl" alt="验证码" />
                      <span v-else class="captcha-retry">点击获取</span>
                    </div>
                  </div>
                </el-form-item>
                <el-button class="submit-btn" :loading="loading" @click="doLogin">登 录</el-button>
              </el-form>
              <div class="engine-line">
                <span class="link-pulse"></span>巡检链路在线<span class="ver">UAV LINK ONLINE</span>
              </div>
              <div class="demo-line">演示账号:admin / admin123</div>
            </div>
          </div>
        </div>
        <p class="copy">© 2026 应急巡检平台 · 应急管理无人机巡检系统</p>
      </div>
    </div>
  </div>
</template>

<script setup>
import { reactive, ref, computed, onMounted, onUnmounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { User, Lock, Key, Loading } from '@element-plus/icons-vue'
import http from '../api'
import { CITY_FACES, CITY_VIEWBOX } from './login/cityScene'

const router = useRouter()
const loading = ref(false)
const captchaSvg = ref('')
const captchaLoading = ref(false)
const form = reactive({ username: 'admin', password: '', captcha: '', cid: '' })

const svgUrl = computed(() =>
  captchaSvg.value ? 'data:image/svg+xml;utf8,' + encodeURIComponent(captchaSvg.value) : ''
)

/* 场景轮播:城市 → 山地 → 河川,8 秒一切 */
const SCENE_NAMES = ['城市', '山地', '河川']
const SCENE_INTERVAL = 8000
const scene = ref(0)
let sceneTimer = null

/* 顶部时钟 */
const clock = ref('')
let clockTimer = null
function tick() {
  const d = new Date()
  const p = (n) => String(n).padStart(2, '0')
  clock.value = `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())} 星期${'日一二三四五六'[d.getDay()]} ${p(d.getHours())}:${p(d.getMinutes())}:${p(d.getSeconds())}`
}

onMounted(() => {
  loadCaptcha()
  tick()
  clockTimer = setInterval(tick, 1000)
  sceneTimer = setInterval(() => { scene.value = (scene.value + 1) % SCENE_NAMES.length }, SCENE_INTERVAL)
})
onUnmounted(() => {
  clearInterval(clockTimer)
  clearInterval(sceneTimer)
})

async function loadCaptcha() {
  captchaLoading.value = true
  try {
    const data = await http.get('/auth/captcha')
    form.cid = data.cid
    captchaSvg.value = data.svg
    form.captcha = ''
  } finally {
    captchaLoading.value = false
  }
}

async function doLogin() {
  if (!form.username || !form.password) return ElMessage.warning('请输入账号和密码')
  if (!form.captcha) return ElMessage.warning('请输入验证码')
  loading.value = true
  try {
    const data = await http.post('/auth/login', form)
    localStorage.setItem('token', data.token)
    localStorage.setItem('nickname', data.nickname || data.username)
    localStorage.setItem('roleCode', data.roleCode || '')
    if (data.menus) localStorage.setItem('menus', JSON.stringify(data.menus))
    ElMessage.success('登录成功')
    router.push('/')
  } catch (e) {
    loadCaptcha()
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
/* ===== 亮蓝科技风:亮蓝底 + 白色 3D 场景 + 飞行无人机 ===== */
.login-page {
  position: relative; height: 100%; overflow: hidden;
  display: flex; flex-direction: column;
  background: #0b5ed7;
  color: #ffffff;
}

.bg-grad {
  position: absolute; inset: 0;
  background: linear-gradient(165deg, #0847b8 0%, #0f63d8 26%, #1877e6 50%, #2b9df5 74%, #6cc6fb 100%);
}
.glow { position: absolute; border-radius: 50%; filter: blur(150px); }
.glow-1 { width: 700px; height: 700px; background: #38bdf8; top: -280px; right: -60px; opacity: .5; }
.glow-2 { width: 560px; height: 560px; background: #7dd3fc; bottom: -260px; left: -180px; opacity: .38; }
.glow-3 { width: 520px; height: 520px; background: #00e5ff; top: 34%; left: 46%; opacity: .22; }

.grid-bg {
  position: absolute; inset: 0;
  background-image:
    linear-gradient(rgba(255, 255, 255, .07) 1px, transparent 1px),
    linear-gradient(90deg, rgba(255, 255, 255, .07) 1px, transparent 1px);
  background-size: 58px 58px;
  mask-image: radial-gradient(ellipse at 50% 36%, #000 30%, transparent 80%);
}
.vignette {
  position: absolute; inset: 0; pointer-events: none;
  background: radial-gradient(ellipse 84% 74% at 50% 42%, transparent 46%, rgba(3, 26, 66, .42) 100%);
}

/* ===== 场景层 ===== */
.scene-layer { position: absolute; inset: 0; pointer-events: none; }
.scene {
  position: absolute; left: 0; right: 0; bottom: 0;
  width: 100%; height: 68%;
  opacity: 0;
  transform: translateY(14px);
  transition: opacity 1.4s ease, transform 1.4s ease;
}
.scene.active { opacity: 1; transform: translateY(0); }

/* 场景切换指示 */
.scene-dots {
  position: absolute; left: 50%; bottom: 22px; transform: translateX(-50%);
  display: flex; gap: 10px; z-index: 4;
}
.dot {
  padding: 4px 14px; border-radius: 999px; cursor: pointer;
  font-size: 12px; letter-spacing: 1px; color: rgba(255, 255, 255, .7);
  background: rgba(255, 255, 255, .10);
  border: 1px solid rgba(255, 255, 255, .22);
  transition: all .25s;
}
.dot:hover { color: #fff; background: rgba(255, 255, 255, .18); }
.dot.on {
  color: #0b3f8f; font-weight: 700;
  background: rgba(255, 255, 255, .92);
  border-color: #fff;
}

/* ===== 飞行中的无人机 ===== */
.drone {
  position: absolute; z-index: 3; pointer-events: none;
  width: 190px; height: 158px;
  /* 沿一条横跨画面的弧线飞行;轨迹压在标题上方,避免掠过文案 */
  offset-path: path('M -160 250 C 180 100, 620 92, 1000 176 C 1260 236, 1500 214, 1760 120');
  offset-rotate: auto;
  animation: drone-fly 26s linear infinite;
  filter: drop-shadow(0 18px 34px rgba(3, 30, 76, .5));
}
@keyframes drone-fly {
  from { offset-distance: 0%; }
  to { offset-distance: 100%; }
}
.drone-bob { position: relative; width: 100%; height: 100%; animation: drone-bob 3.4s ease-in-out infinite; }
@keyframes drone-bob {
  0%, 100% { transform: translateY(0); }
  50% { transform: translateY(-10px); }
}
.drone-bob svg { width: 100%; height: 100%; }

/* 下视扫描光锥 */
.drone-beam {
  position: absolute; left: 50%; top: 74%;
  width: 92px; height: 92px;
  transform: translateX(-50%);
  clip-path: polygon(42% 0, 58% 0, 100% 100%, 0 100%);
  background: linear-gradient(180deg, rgba(255, 255, 255, .5), rgba(255, 255, 255, 0));
  animation: beam-pulse 2.6s ease-in-out infinite;
}
@keyframes beam-pulse { 0%, 100% { opacity: .5; } 50% { opacity: .95; } }

/* ===== 顶部栏 ===== */
.top-bar {
  position: relative; z-index: 5; flex-shrink: 0; height: 60px;
  background: rgba(6, 40, 96, .28);
  backdrop-filter: blur(12px);
  border-bottom: 1px solid rgba(255, 255, 255, .16);
}
.top-inner {
  height: 100%; max-width: 1520px; margin: 0 auto; padding: 0 28px;
  display: flex; align-items: center; justify-content: space-between;
}
.top-left { display: flex; align-items: center; gap: 11px; }
.top-emblem { width: 32px; height: 32px; display: flex; flex-shrink: 0; }
.top-emblem svg { width: 100%; height: 100%; }
.top-name { font-size: 16px; font-weight: 700; letter-spacing: 2.5px; }
.top-sep { width: 1px; height: 16px; background: rgba(255, 255, 255, .35); margin: 0 3px; }
.top-sub { font-size: 12.5px; color: rgba(255, 255, 255, .8); letter-spacing: 1.5px; }
.top-right { display: flex; align-items: center; gap: 10px; }
.top-live { display: inline-flex; align-items: center; gap: 7px; font-size: 12.5px; color: rgba(255, 255, 255, .88); }
.top-live i {
  width: 6px; height: 6px; border-radius: 50%; background: #4ade80;
  box-shadow: 0 0 8px #4ade80; animation: live-blink 2s infinite;
}
@keyframes live-blink { 0%, 100% { opacity: 1; } 50% { opacity: .3; } }
.top-clock { font-size: 12.5px; color: rgba(255, 255, 255, .82); font-variant-numeric: tabular-nums; }

/* ===== 主体 ===== */
.stage { position: relative; z-index: 2; flex: 1; min-height: 0; display: flex; align-items: center; }

.brand { flex: 1; min-width: 0; display: flex; align-items: center; }
.brand-inner { padding: 0 0 0 7%; max-width: 640px; }

.title {
  font-size: 48px; font-weight: 800; letter-spacing: 10px; text-indent: 10px;
  color: #ffffff;
  text-shadow: 0 6px 26px rgba(3, 30, 76, .45);
  line-height: 1.18;
}
.title-en { margin-top: 10px; font-size: 11.5px; color: rgba(255, 255, 255, .72); letter-spacing: 3.6px; }

.rule { display: flex; align-items: center; width: 100%; max-width: 440px; margin: 20px 0 18px; }
.rule::before, .rule::after {
  content: ''; height: 1px; flex: 1;
  background: linear-gradient(90deg, rgba(255, 255, 255, .7), rgba(255, 255, 255, .06));
}
.rule::after { background: linear-gradient(90deg, rgba(255, 255, 255, .06), rgba(255, 255, 255, .7)); }
.rule i { width: 7px; height: 7px; margin: 0 11px; background: #ffffff; transform: rotate(45deg); }

.sub { font-size: 14px; color: rgba(255, 255, 255, .86); line-height: 2; letter-spacing: 1.2px; }

.chips { display: flex; flex-wrap: wrap; gap: 11px; margin-top: 28px; }
.chip {
  display: inline-flex; align-items: center; gap: 8px;
  padding: 8px 15px; border-radius: 4px;
  background: rgba(255, 255, 255, .12);
  border: 1px solid rgba(255, 255, 255, .26);
  font-size: 12.5px; font-weight: 600; letter-spacing: 1.2px;
}
.chip-dot { width: 6px; height: 6px; border-radius: 50%; background: #ffffff; box-shadow: 0 0 8px #fff; }
.chip-dot.cyan { background: #7dd3fc; box-shadow: 0 0 8px #7dd3fc; }
.chip-dot.amber { background: #fdba74; box-shadow: 0 0 8px #fdba74; }

/* ===== 登录卡 ===== */
.login-wrap {
  width: 500px; flex-shrink: 0; margin-left: auto;
  display: flex; flex-direction: column; align-items: center; justify-content: center;
  position: relative; z-index: 4;
}
.card-dock { position: relative; }
.card-dock::before {
  content: ''; position: absolute; inset: -40px; border-radius: 40px;
  background: radial-gradient(closest-side, rgba(255, 255, 255, .26), transparent 76%);
  filter: blur(18px); z-index: -1;
}

.login-card {
  position: relative; width: 400px;
  border-radius: 14px; overflow: hidden;
  background: rgba(255, 255, 255, .96);
  backdrop-filter: blur(20px);
  border: 1px solid rgba(255, 255, 255, .8);
  box-shadow: 0 30px 70px -24px rgba(3, 30, 76, .55), 0 4px 14px rgba(3, 30, 76, .12);
  color: #0f2444;
}
.login-card::before {
  content: ''; position: absolute; top: 0; left: 0; right: 0; height: 3px;
  background: linear-gradient(90deg, #0847b8, #1877e6 34%, #38bdf8 66%, #7dd3fc);
  z-index: 1;
}

.card-head {
  position: relative; padding: 24px 32px 18px; text-align: center;
  background: linear-gradient(180deg, rgba(24, 119, 230, .08) 0%, transparent 100%);
  border-bottom: 1px solid rgba(24, 119, 230, .1);
}
.card-head::before {
  content: ''; position: absolute; inset: 0;
  background-image:
    linear-gradient(rgba(24, 119, 230, .05) 1px, transparent 1px),
    linear-gradient(90deg, rgba(24, 119, 230, .05) 1px, transparent 1px);
  background-size: 22px 22px;
  mask-image: linear-gradient(180deg, #000 0%, transparent 74%);
  pointer-events: none;
}
.card-badge {
  position: relative; display: inline-block; padding: 6px 22px;
  font-size: 15px; font-weight: 700; color: #ffffff; letter-spacing: 4px; text-indent: 4px;
  border-radius: 3px;
  background: linear-gradient(90deg, #0f63d8, #1877e6 50%, #38bdf8);
  box-shadow: 0 6px 16px -6px rgba(24, 119, 230, .8);
}
.card-sub { position: relative; margin-top: 12px; font-size: 12px; color: #7b9cc4; letter-spacing: 1px; }

.card-body { padding: 22px 34px 24px; }
.el-form-item { margin-bottom: 18px; }

:deep(.el-input__wrapper) {
  border-radius: 6px; background: #f4f8ff;
  box-shadow: inset 0 0 0 1px #dbe7f8;
}
:deep(.el-input__wrapper:hover) { box-shadow: inset 0 0 0 1px #a9c8ee; }
:deep(.el-input__wrapper.is-focus) {
  background: #ffffff;
  box-shadow: inset 0 0 0 1.5px #1877e6, 0 0 0 4px rgba(24, 119, 230, .13) !important;
}
:deep(.el-input__inner) { color: #0f2444; }
:deep(.el-input__prefix-inner .el-icon),
:deep(.el-input__suffix-inner .el-icon) { color: #7b9cc4; }

.captcha-row { display: flex; gap: 10px; width: 100%; }
.captcha-box {
  width: 112px; height: 40px; flex-shrink: 0;
  border-radius: 6px; overflow: hidden; cursor: pointer;
  border: 1px solid #dbe7f8; background: #f4f8ff;
  display: flex; align-items: center; justify-content: center;
  transition: border-color .2s;
}
.captcha-box:hover { border-color: #1877e6; }
.captcha-box img { width: 100%; height: 100%; display: block; }
.captcha-loading { color: #7b9cc4; display: flex; }
.captcha-retry { font-size: 12px; color: #7b9cc4; }

.submit-btn {
  width: 100%; height: 46px; margin-top: 4px;
  font-size: 15px; font-weight: 700; letter-spacing: 10px; text-indent: 10px;
  border-radius: 6px;
  background: linear-gradient(90deg, #0847b8, #1877e6 30%, #38bdf8 60%, #7dd3fc 80%, #0847b8);
  background-size: 220% 100%; background-position: 0% 0;
  border: none; color: #ffffff;
  box-shadow: 0 10px 26px -8px rgba(24, 119, 230, .7);
  transition: background-position .6s ease, transform .25s, box-shadow .25s;
}
.submit-btn:hover {
  transform: translateY(-1px); background-position: 95% 0;
  box-shadow: 0 14px 32px -8px rgba(24, 119, 230, .85);
}

.engine-line {
  margin-top: 20px; padding-top: 14px;
  border-top: 1px dashed #dbe7f8;
  display: flex; align-items: center; justify-content: center; gap: 8px;
  font-size: 12px; font-weight: 600; color: #46618a; letter-spacing: 1px;
}
.engine-line .ver { font-size: 10px; font-weight: 700; color: #1877e6; letter-spacing: 1.2px; }
.link-pulse { position: relative; width: 7px; height: 7px; border-radius: 50%; background: #1877e6; flex-shrink: 0; }
.link-pulse::after {
  content: ''; position: absolute; inset: 0; border-radius: 50%;
  border: 1.5px solid #1877e6; animation: link-ring 1.8s ease-out infinite;
}
@keyframes link-ring { 0% { transform: scale(1); opacity: .9; } 100% { transform: scale(2.6); opacity: 0; } }
.demo-line { margin-top: 9px; text-align: center; font-size: 12px; color: #9ab3d4; }

.copy {
  margin-top: 20px; text-align: center;
  font-size: 12px; color: rgba(255, 255, 255, .7); letter-spacing: 1px;
  text-shadow: 0 1px 8px rgba(3, 30, 76, .4);
}

/* ===== 响应式 ===== */
@media (max-width: 1500px) {
  .title { font-size: 42px; letter-spacing: 8px; text-indent: 8px; }
  .drone { width: 165px; height: 138px; }
}
@media (max-width: 1280px) {
  .brand-inner { max-width: 430px; }
  .login-wrap { width: 450px; }
  .login-card { width: 370px; }
  .title { font-size: 36px; letter-spacing: 6px; text-indent: 6px; }
  .drone { width: 140px; height: 118px; }
}
@media (max-width: 1100px) {
  .top-sub { display: none; }
  .drone { width: 118px; height: 100px; }
}
@media (max-width: 900px) {
  .brand { display: none; }
  .login-wrap { width: 100%; }
  .login-card { width: calc(100% - 48px); max-width: 400px; }
  .top-right { display: none; }
  .drone { display: none; }
}

/* 矮屏压缩 */
@media (max-height: 860px) {
  .title { font-size: 38px; }
  .rule { margin: 15px 0 14px; }
  .chips { margin-top: 20px; }
  .card-head { padding: 20px 32px 15px; }
  .card-body { padding: 18px 34px 20px; }
  .scene { height: 62%; }
}
@media (max-height: 720px) {
  .sub { display: none; }
  .rule { margin: 12px 0; }
  .scene { height: 56%; }
  .scene-dots { bottom: 12px; }
}
</style>
