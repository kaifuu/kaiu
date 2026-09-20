<template>
  <div class="login-page">
    <!-- CSS 亮蓝渐变底:Three 画布透明叠加其上,WebGL 不可用时即最终视觉 -->
    <div class="bg-grad"></div>
    <div class="glow glow-a"></div>
    <div class="glow glow-b"></div>
    <div class="glow glow-c"></div>
    <div ref="threeWrap" class="three-wrap"></div>
    <div class="vignette"></div>

    <!-- ===== 顶部栏 ===== -->
    <header class="top-bar">
      <div class="top-left">
        <span class="top-emblem">
          <svg viewBox="0 0 120 96" width="34" height="27">
            <circle cx="60" cy="58" r="20" fill="none" stroke="#ffffff" stroke-width="2.4" />
            <circle cx="60" cy="58" r="4.2" fill="#38bdf8" />
            <path d="M49 47 L56 54 M71 47 L64 54 M49 69 L56 62 M71 69 L64 62"
                  stroke="#38bdf8" stroke-width="2.2" stroke-linecap="round" />
            <circle cx="48" cy="46" r="2.8" fill="#38bdf8" /><circle cx="72" cy="46" r="2.8" fill="#38bdf8" />
            <circle cx="48" cy="70" r="2.8" fill="#38bdf8" /><circle cx="72" cy="70" r="2.8" fill="#38bdf8" />
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
    </header>

    <!-- ===== 任务遥测 HUD(左下,随 3D 场景实时刷新) ===== -->
    <div class="mission fade-in">
      <div class="m-head">
        <span class="m-live"><i></i>LIVE</span>
        <span class="m-title">机场自动巡检 · 任务模拟</span>
      </div>
      <div class="m-phase"><i class="m-dot" :class="'p-' + tele.phase"></i>{{ tele.text }}</div>
      <div class="m-grid">
        <div class="m-item"><span>高度</span><b>{{ tele.alt }}<i>m</i></b></div>
        <div class="m-item"><span>速度</span><b>{{ tele.spd }}<i>m/s</i></b></div>
        <div class="m-item"><span>电量</span><b>{{ tele.bat }}<i>%</i></b></div>
        <div class="m-item"><span>航点</span><b class="mono-w">{{ tele.wp }}</b></div>
      </div>
      <div class="m-bar"><i :style="{ width: tele.progress + '%' }"></i></div>
    </div>

    <!-- ===== 主体 ===== -->
    <div class="stage">
      <div class="brand">
        <div class="brand-inner fade-in">
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
        <div class="login-card fade-in-up">
          <i class="corner tl"></i><i class="corner br"></i>
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
        <p class="copy">© 2026 应急巡检平台 · 应急管理无人机巡检系统</p>
      </div>
    </div>
  </div>
</template>

<script setup>
import { reactive, ref, computed, onMounted, onUnmounted, nextTick } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { User, Lock, Key, Loading } from '@element-plus/icons-vue'
import http from '../api'
import { createLoginScene } from './loginScene'

const router = useRouter()
const loading = ref(false)
const captchaSvg = ref('')
const captchaLoading = ref(false)
const form = reactive({ username: 'admin', password: '', captcha: '', cid: '' })

const svgUrl = computed(() =>
  captchaSvg.value ? 'data:image/svg+xml;utf8,' + encodeURIComponent(captchaSvg.value) : ''
)

/* 顶部时钟 */
const clock = ref('')
let clockTimer = null
function tick() {
  const d = new Date()
  const p = (n) => String(n).padStart(2, '0')
  clock.value = `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())} 星期${'日一二三四五六'[d.getDay()]} ${p(d.getHours())}:${p(d.getMinutes())}:${p(d.getSeconds())}`
}

/* ---------------- Three.js 巡检场景 ---------------- */
const threeWrap = ref(null)
let scene = null

const PHASE_TEXT = {
  IDLE: '待命 · 机库停靠',
  OPENING: '机库舱盖开启',
  TAKEOFF: '无人机起飞',
  PATROL: '航线巡检中',
  DESCEND: '返航 · 降落对位',
  CLOSING: '入库 · 舱盖关闭'
}
const tele = reactive({
  phase: 'IDLE', text: PHASE_TEXT.IDLE,
  alt: '0.0', spd: '0.0', bat: 98, wp: '0/10', progress: 0
})

onMounted(() => {
  loadCaptcha()
  tick()
  clockTimer = setInterval(tick, 1000)
  nextTick(() => {
    try {
      scene = createLoginScene(threeWrap.value, {
        onTelemetry(d) {
          tele.phase = d.phase
          tele.text = PHASE_TEXT[d.phase] || d.phase
          tele.alt = d.alt
          tele.spd = d.spd
          tele.bat = d.bat
          tele.wp = d.wp
          tele.progress = d.progress
        }
      })
    } catch (e) {
      // WebGL 不可用:保留 CSS 亮蓝渐变视觉
      threeWrap.value.style.display = 'none'
    }
  })
})
onUnmounted(() => {
  clearInterval(clockTimer)
  scene?.dispose()
  scene = null
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
.login-page {
  position: relative; width: 100%; height: 100vh; min-height: 640px;
  overflow: hidden; background: #04173a;
}

/* ---------------- 背景层 ---------------- */
.bg-grad {
  position: absolute; inset: 0; z-index: 0;
  background:
    radial-gradient(1200px 620px at 18% 12%, rgba(56, 189, 248, 0.32), transparent 60%),
    radial-gradient(1000px 560px at 86% 78%, rgba(24, 119, 230, 0.38), transparent 62%),
    linear-gradient(158deg, #0b47b8 0%, #0a2f8f 44%, #071c4f 100%);
}
.glow { position: absolute; border-radius: 50%; filter: blur(90px); z-index: 0; pointer-events: none; }
.glow-a { width: 480px; height: 480px; left: -120px; top: -140px; background: rgba(56, 189, 248, 0.25); }
.glow-b { width: 560px; height: 560px; right: -160px; bottom: -200px; background: rgba(14, 90, 218, 0.32); }
.glow-c { width: 360px; height: 360px; left: 42%; top: 56%; background: rgba(45, 212, 191, 0.1); }

.three-wrap { position: absolute; inset: 0; z-index: 1; }
.three-wrap :deep(canvas) { display: block; }

.vignette {
  position: absolute; inset: 0; z-index: 2; pointer-events: none;
  background:
    radial-gradient(120% 90% at 50% 42%, transparent 55%, rgba(3, 14, 40, 0.5) 100%);
}

/* ---------------- 顶部栏 ---------------- */
.top-bar {
  position: absolute; top: 0; left: 0; right: 0; z-index: 6;
  display: flex; align-items: center; justify-content: space-between;
  padding: 16px 34px;
  background: linear-gradient(180deg, rgba(4, 20, 54, 0.55), transparent);
}
.top-left, .top-right { display: flex; align-items: center; gap: 12px; }
.top-emblem { display: inline-flex; filter: drop-shadow(0 0 8px rgba(56, 189, 248, 0.6)); }
.top-name { font-size: 17px; font-weight: 700; color: #fff; letter-spacing: 3px; }
.top-sep { width: 1px; height: 14px; background: rgba(160, 205, 255, 0.35); }
.top-sub { font-size: 12.5px; color: rgba(178, 212, 255, 0.85); letter-spacing: 2px; }
.top-right { font-size: 12.5px; color: rgba(178, 212, 255, 0.85); }
.top-clock { font-variant-numeric: tabular-nums; letter-spacing: 1px; }
.top-live { display: inline-flex; align-items: center; gap: 6px; color: #7ef0c0; }
.top-live i {
  width: 7px; height: 7px; border-radius: 50%; background: #2effa0;
  box-shadow: 0 0 8px #2effa0; animation: blink 1.6s infinite;
}
@keyframes blink { 0%, 100% { opacity: 1; } 50% { opacity: 0.25; } }

/* ---------------- 任务遥测 HUD ---------------- */
.mission {
  position: absolute; left: 34px; bottom: 34px; z-index: 5;
  width: 262px; padding: 13px 16px 14px;
  border-radius: 12px;
  background: rgba(6, 28, 70, 0.5);
  border: 1px solid rgba(103, 183, 255, 0.28);
  backdrop-filter: blur(10px);
  box-shadow: 0 10px 34px -12px rgba(2, 12, 38, 0.7);
}
.m-head { display: flex; align-items: center; gap: 8px; padding-bottom: 9px; margin-bottom: 9px;
  border-bottom: 1px solid rgba(103, 183, 255, 0.18); }
.m-live {
  display: inline-flex; align-items: center; gap: 5px;
  font-size: 10px; font-weight: 700; letter-spacing: 1.5px; color: #ff6b81;
  padding: 2px 7px; border-radius: 4px; background: rgba(255, 107, 129, 0.12);
  border: 1px solid rgba(255, 107, 129, 0.3);
}
.m-live i { width: 5px; height: 5px; border-radius: 50%; background: #ff6b81; animation: blink 1.2s infinite; }
.m-title { font-size: 12px; color: #a8ccf5; letter-spacing: 1px; }
.m-phase { display: flex; align-items: center; gap: 8px; font-size: 14.5px; font-weight: 600; color: #eaf4ff; letter-spacing: 1px; }
.m-dot { width: 9px; height: 9px; border-radius: 50%; background: #8fa8c9; flex: 0 0 auto; }
.m-dot.p-OPENING { background: #f7b955; box-shadow: 0 0 8px #f7b955; }
.m-dot.p-TAKEOFF { background: #38bdf8; box-shadow: 0 0 8px #38bdf8; animation: blink 0.8s infinite; }
.m-dot.p-PATROL { background: #2effa0; box-shadow: 0 0 8px #2effa0; }
.m-dot.p-DESCEND { background: #86d5ff; box-shadow: 0 0 8px #86d5ff; animation: blink 1s infinite; }
.m-dot.p-CLOSING { background: #ff9a76; box-shadow: 0 0 8px #ff9a76; }
.m-grid { display: grid; grid-template-columns: repeat(4, 1fr); gap: 6px; margin: 11px 0 10px; }
.m-item span { display: block; font-size: 10.5px; color: #7d9cc7; letter-spacing: 1px; }
.m-item b { font-size: 15px; font-weight: 700; color: #fff; font-variant-numeric: tabular-nums; }
.m-item b i { font-style: normal; font-size: 10px; font-weight: 400; color: #7d9cc7; margin-left: 1px; }
.m-item b.mono-w { font-size: 13.5px; letter-spacing: 0.5px; }
.m-bar { height: 3px; border-radius: 2px; background: rgba(103, 183, 255, 0.16); overflow: hidden; }
.m-bar i { display: block; height: 100%; border-radius: 2px; transition: width 0.3s linear;
  background: linear-gradient(90deg, #38bdf8, #2effa0); }

/* ---------------- 主体布局 ---------------- */
.stage {
  position: relative; z-index: 4;
  min-height: 100vh; display: flex; align-items: center; justify-content: space-between;
  gap: 40px; padding: 100px 7vw 60px;
}
.brand { flex: 1 1 0; max-width: 620px; }
.title {
  margin: 0; font-size: 46px; font-weight: 800; color: #fff; letter-spacing: 6px;
  text-shadow: 0 0 28px rgba(56, 189, 248, 0.55);
}
.title-en { margin: 10px 0 0; font-size: 13px; letter-spacing: 6px; color: rgba(150, 200, 255, 0.75); }
.rule { margin: 20px 0 18px; }
.rule i {
  display: block; width: 210px; height: 3px; border-radius: 2px;
  background: linear-gradient(90deg, #38bdf8, rgba(56, 189, 248, 0));
  box-shadow: 0 0 12px rgba(56, 189, 248, 0.7);
}
.sub { margin: 0; font-size: 15px; line-height: 1.9; color: rgba(205, 228, 255, 0.88); letter-spacing: 1px; }
.chips { display: flex; gap: 12px; margin-top: 26px; flex-wrap: wrap; }
.chip {
  display: inline-flex; align-items: center; gap: 7px;
  padding: 7px 14px; border-radius: 999px; font-size: 12.5px; letter-spacing: 1px;
  color: #d8ecff; background: rgba(10, 42, 100, 0.45);
  border: 1px solid rgba(103, 183, 255, 0.3); backdrop-filter: blur(6px);
}
.chip-dot { width: 7px; height: 7px; border-radius: 50%; background: #38bdf8; box-shadow: 0 0 7px #38bdf8; }
.chip-dot.cyan { background: #2dd4bf; box-shadow: 0 0 7px #2dd4bf; }
.chip-dot.amber { background: #fbbf24; box-shadow: 0 0 7px #fbbf24; }

/* ---------------- 登录卡:简洁白底 + 科技点缀 ---------------- */
.login-wrap { display: flex; flex-direction: column; align-items: center; flex: 0 0 auto; }
.login-card {
  position: relative; width: 400px; max-width: calc(100vw - 40px);
  padding: 34px 36px 24px; border-radius: 18px; background: #fff;
  box-shadow:
    0 30px 80px -20px rgba(3, 22, 60, 0.62),
    0 6px 20px rgba(3, 22, 60, 0.2),
    0 0 0 1px rgba(24, 119, 230, 0.06);
  overflow: hidden;
}
.login-card::before {
  content: ''; position: absolute; top: 0; left: 0; right: 0; height: 3px;
  background: linear-gradient(90deg, #0f5bd0, #38bdf8 45%, #0f5bd0 90%);
}
.corner { position: absolute; width: 20px; height: 20px; pointer-events: none; }
.corner.tl { top: 11px; left: 11px; border-top: 2px solid rgba(24, 119, 230, 0.4); border-left: 2px solid rgba(24, 119, 230, 0.4); border-radius: 8px 0 0 0; }
.corner.br { bottom: 11px; right: 11px; border-bottom: 2px solid rgba(24, 119, 230, 0.4); border-right: 2px solid rgba(24, 119, 230, 0.4); border-radius: 0 0 8px 0; }

.card-head { text-align: center; margin-bottom: 24px; }
.card-badge {
  display: inline-flex; align-items: center; gap: 10px;
  font-size: 22px; font-weight: 800; color: #0b2447; letter-spacing: 3px;
}
.card-badge::before, .card-badge::after {
  content: ''; width: 5px; height: 20px; border-radius: 3px;
  background: linear-gradient(180deg, #38bdf8, #1877e6);
}
.card-sub { margin: 10px 0 0; font-size: 12.5px; color: #8ba3c7; letter-spacing: 1px; }

.card-body :deep(.el-form-item) { margin-bottom: 18px; }
.card-body :deep(.el-input__wrapper) {
  border-radius: 10px; padding: 5px 15px;
  background: #f5f9ff;
  box-shadow: 0 0 0 1px #dbe7f6 inset;
  transition: box-shadow 0.25s, background 0.25s;
}
.card-body :deep(.el-input__wrapper.is-focus) {
  background: #fff;
  box-shadow: 0 0 0 1.5px #1877e6 inset, 0 0 0 4px rgba(24, 119, 230, 0.14);
}
.card-body :deep(.el-input__inner) { color: #17325c; font-weight: 500; }
.card-body :deep(.el-input__inner::placeholder) { color: #a8bcd9; font-weight: 400; }

.captcha-row { display: flex; gap: 10px; width: 100%; }
.captcha-box {
  flex: 0 0 118px; height: 44px; border-radius: 10px; overflow: hidden; cursor: pointer;
  display: flex; align-items: center; justify-content: center;
  background: #eef4fd; border: 1px solid #dbe7f6;
  transition: border-color 0.2s, box-shadow 0.2s;
}
.captcha-box:hover { border-color: #1877e6; box-shadow: 0 0 0 3px rgba(24, 119, 230, 0.12); }
.captcha-box img { width: 100%; height: 100%; object-fit: cover; }
.captcha-loading { color: #1877e6; font-size: 18px; }
.captcha-retry { font-size: 12px; color: #8ba3c7; }

.submit-btn {
  width: 100%; margin-top: 4px; height: 46px;
  font-size: 16.5px; font-weight: 700; letter-spacing: 10px; text-indent: 10px;
  color: #fff; border: none; border-radius: 10px;
  background: linear-gradient(135deg, #2b8bff 0%, #0f5bd0 100%);
  box-shadow: 0 12px 26px -8px rgba(23, 100, 230, 0.65);
  transition: transform 0.2s, box-shadow 0.2s, filter 0.2s;
}
.submit-btn:hover:not(.is-disabled) {
  transform: translateY(-1px); filter: brightness(1.08);
  box-shadow: 0 16px 32px -8px rgba(23, 100, 230, 0.75);
}
.submit-btn:active:not(.is-disabled) { transform: translateY(0); }

.engine-line {
  display: flex; align-items: center; justify-content: center; gap: 8px;
  margin-top: 18px; padding-top: 16px;
  border-top: 1px dashed #e3edfa;
  font-size: 12px; color: #5b7ba6; letter-spacing: 1px;
}
.link-pulse {
  width: 7px; height: 7px; border-radius: 50%; background: #2effa0;
  box-shadow: 0 0 8px #2effa0; animation: blink 1.6s infinite;
}
.engine-line .ver { font-size: 10px; letter-spacing: 2px; color: #a8bcd9; }
.demo-line { margin-top: 10px; text-align: center; font-size: 12.5px; color: #9fb2cf; letter-spacing: 0.5px; }

.copy { margin: 18px 0 0; font-size: 12px; color: rgba(178, 212, 255, 0.55); letter-spacing: 1px; }

/* ---------------- 入场动画 ---------------- */
.fade-in { animation: fadeIn 0.9s ease both; }
.fade-in-up { animation: fadeInUp 0.8s cubic-bezier(0.2, 0.7, 0.3, 1) 0.15s both; }
@keyframes fadeIn { from { opacity: 0; } to { opacity: 1; } }
@keyframes fadeInUp { from { opacity: 0; transform: translateY(26px); } to { opacity: 1; transform: translateY(0); } }

/* ---------------- 响应式 ---------------- */
@media (max-width: 1180px) {
  .title { font-size: 38px; }
  .stage { padding: 96px 5vw 56px; }
}
@media (max-width: 960px) {
  .brand { display: none; }
  .stage { justify-content: center; }
  .mission { display: none; }
}
@media (max-height: 720px) {
  .mission { display: none; }
}
</style>
