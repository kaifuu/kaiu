<template>
  <!-- 悬浮球:可拖动,呼吸辉光 -->
  <div v-show="!panel" class="copilot-ball" :style="ballStyle"
       @pointerdown="onDragStart" @pointerup="onDragEnd" @click.stop="open">
    <svg viewBox="0 0 24 24" fill="none" stroke="#fff" stroke-width="1.7" stroke-linecap="round">
      <circle cx="5.5" cy="7.5" r="2.2" />
      <circle cx="18.5" cy="7.5" r="2.2" />
      <circle cx="5.5" cy="16.5" r="2.2" />
      <circle cx="18.5" cy="16.5" r="2.2" />
      <rect x="9.4" y="4.4" width="5.2" height="5.2" rx="1.4" />
      <path d="M 9.4 7 L 3 9.5 M 14.6 7 L 21 9.5 M 9.4 14 L 5.5 14.3 M 14.6 14 L 18.5 14.3 M 12 9.6 L 12 14 M 7 18.5 L 5.5 18.5 M 17 18.5 L 18.5 18.5" />
    </svg>
    <span class="ball-ring" />
  </div>

  <!-- 对话窗 -->
  <div v-if="panel" class="copilot-panel" :style="panelStyle">
    <div class="cp-head">
      <div class="cp-title">
        <span class="cp-dot" />
        AI 值班助手
        <span class="cp-sub">值守态势问答</span>
      </div>
      <el-icon class="cp-close" @click="panel = false"><Close /></el-icon>
    </div>

    <div class="cp-body" ref="bodyRef">
      <div v-for="(m, i) in messages" :key="i" class="cp-msg" :class="m.role">
        <div class="cp-bubble">{{ m.role === 'bot' ? m.shown : m.text }}</div>
      </div>
      <div v-if="thinking" class="cp-msg bot">
        <div class="cp-bubble cp-typing"><i /><i /><i /></div>
      </div>
    </div>

    <div class="cp-chips">
      <span v-for="s in suggests" :key="s" class="cp-chip" @click="ask(s)">{{ s }}</span>
    </div>

    <div class="cp-input">
      <el-input v-model="question" placeholder="问我平台态势,如:今天有多少任务?" size="default"
                @keyup.enter="ask()" clearable />
      <el-button type="primary" :loading="thinking" @click="ask()">发送</el-button>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, computed, nextTick, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { Close } from '@element-plus/icons-vue'
import http from '../api'

const route = useRoute()
const panel = ref(false)
const thinking = ref(false)
const question = ref('')
const suggests = ref([])
const bodyRef = ref(null)
/** { role: 'me'|'bot', text, shown }:bot 消息 shown 随打字机增长 */
const messages = reactive([])

/* 悬浮球位置:默认右下角,可拖动 */
const pos = reactive({ x: null, y: null })
const drag = reactive({ on: false, dx: 0, dy: 0, moved: false })

const ballStyle = computed(() => (pos.x === null ? {} : { right: 'auto', bottom: 'auto', left: pos.x + 'px', top: pos.y + 'px' }))
const panelStyle = computed(() => {
  if (pos.x === null) return {}
  // 面板跟随球,但不越出视口
  const w = 372
  return {
    right: 'auto', bottom: 'auto',
    left: Math.max(12, Math.min(pos.x - w + 56, window.innerWidth - w - 12)) + 'px',
    top: Math.max(12, pos.y - 500) + 'px'
  }
})

onMounted(async () => {
  try {
    suggests.value = await http.get('/copilot/suggest')
  } catch (e) { /* 建议加载失败不阻塞入口 */ }
})

function open() {
  if (drag.moved) { drag.moved = false; return } // 拖动结束不当作点击
  panel.value = true
  if (!messages.length) {
    pushBot(`您好,我是 AI 值班助手,可查询巡检任务、隐患、应急事件、设备与飞行安全预警态势。\n点击下方快捷问题或直接输入即可。`)
  }
}

/* 球体拖动:pointerdown 记偏移,move 跟随,up 判定位移 */
function onDragStart(e) {
  const rect = e.currentTarget.getBoundingClientRect()
  drag.on = true
  drag.moved = false
  drag.dx = e.clientX - rect.left
  drag.dy = e.clientY - rect.top
  pos.x = rect.left
  pos.y = rect.top
  window.addEventListener('pointermove', onDragMove)
  window.addEventListener('pointerup', onDragEnd, { once: true })
}
function onDragMove(e) {
  if (!drag.on) return
  drag.moved = true
  pos.x = Math.max(8, Math.min(e.clientX - drag.dx, window.innerWidth - 60))
  pos.y = Math.max(8, Math.min(e.clientY - drag.dy, window.innerHeight - 60))
}
function onDragEnd() {
  drag.on = false
  window.removeEventListener('pointermove', onDragMove)
}

async function ask(preset) {
  const q = (preset || question.value || '').trim()
  if (!q || thinking.value) return
  question.value = ''
  messages.push({ role: 'me', text: q })
  thinking.value = true
  scrollBottom()
  try {
    const res = await http.post('/copilot/ask', { q, route: route.path })
    thinking.value = false
    pushBot(res.answer || '暂时没有查询到相关数据。')
  } catch (e) {
    thinking.value = false
    pushBot('查询失败:' + (e.message || '网络异常'))
  }
}

/** 打字机逐字显示 */
function pushBot(text) {
  const m = reactive({ role: 'bot', text, shown: '' })
  messages.push(m)
  scrollBottom()
  let i = 0
  const step = Math.max(1, Math.round(text.length / 60))
  const timer = setInterval(() => {
    i = Math.min(text.length, i + step)
    m.shown = text.slice(0, i)
    scrollBottom()
    if (i >= text.length) clearInterval(timer)
  }, 28)
}

function scrollBottom() {
  nextTick(() => {
    if (bodyRef.value) bodyRef.value.scrollTop = bodyRef.value.scrollHeight
  })
}
</script>

<style scoped>
.copilot-ball {
  position: fixed;
  right: 28px;
  bottom: 96px;
  z-index: 2000;
  width: 56px;
  height: 56px;
  border-radius: 50%;
  background: linear-gradient(135deg, #155eef 0%, #0ea5e9 100%);
  box-shadow: 0 6px 20px rgba(21, 94, 239, .35);
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  user-select: none;
  touch-action: none;
  animation: breath 2.6s ease-in-out infinite;
}

.copilot-ball svg {
  width: 30px;
  height: 30px;
}

.ball-ring {
  position: absolute;
  inset: -6px;
  border-radius: 50%;
  border: 2px solid rgba(14, 165, 233, .45);
  animation: ripple 2.6s ease-out infinite;
}

@keyframes breath {
  0%, 100% { box-shadow: 0 6px 20px rgba(21, 94, 239, .35); }
  50% { box-shadow: 0 6px 30px rgba(14, 165, 233, .6); }
}

@keyframes ripple {
  0% { transform: scale(.9); opacity: .8; }
  100% { transform: scale(1.45); opacity: 0; }
}

.copilot-panel {
  position: fixed;
  right: 28px;
  bottom: 96px;
  z-index: 2000;
  width: 372px;
  height: 500px;
  background: #fff;
  border: 1px solid #dbeafe;
  border-radius: 16px;
  box-shadow: 0 12px 40px rgba(16, 24, 40, .14);
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.cp-head {
  position: relative;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 14px 16px;
  background: linear-gradient(135deg, #ffffff 0%, #eff6ff 100%);
  border-bottom: 1px solid #e8f1ff;
}
/* 底部亮蓝渐变收边,与工作台横幅同一语言 */
.cp-head::after {
  content: ''; position: absolute; left: 0; right: 0; bottom: -1px; height: 2px;
  background: linear-gradient(90deg, #155eef, #0ea5e9 55%, rgba(14, 165, 233, 0));
}

.cp-title {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 14.5px;
  font-weight: 700;
  color: #101828;
}

.cp-sub {
  font-size: 12px;
  font-weight: 400;
  color: #667085;
}

.cp-dot {
  width: 9px;
  height: 9px;
  border-radius: 50%;
  background: #12b76a;
  box-shadow: 0 0 8px #12b76a;
}

.cp-close { cursor: pointer; color: #667085; }
.cp-close:hover { color: #155eef; }

.cp-body {
  flex: 1;
  overflow-y: auto;
  padding: 14px 14px 6px;
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.cp-msg { display: flex; }
.cp-msg.me { justify-content: flex-end; }

.cp-bubble {
  max-width: 82%;
  padding: 9px 12px;
  border-radius: 12px;
  font-size: 13px;
  line-height: 1.65;
  white-space: pre-wrap;
  word-break: break-word;
}

.cp-msg.bot .cp-bubble {
  background: #f0f6ff;
  color: #182230;
  border-top-left-radius: 4px;
}

.cp-msg.me .cp-bubble {
  background: linear-gradient(135deg, #155eef, #0ea5e9);
  color: #fff;
  border-top-right-radius: 4px;
}

/* 思考中三点 */
.cp-typing { display: inline-flex; gap: 4px; align-items: center; padding: 12px 14px; }
.cp-typing i {
  width: 6px; height: 6px; border-radius: 50%; background: #0ea5e9;
  animation: blink 1.2s infinite ease-in-out;
}
.cp-typing i:nth-child(2) { animation-delay: .2s; }
.cp-typing i:nth-child(3) { animation-delay: .4s; }

@keyframes blink {
  0%, 80%, 100% { opacity: .25; transform: translateY(0); }
  40% { opacity: 1; transform: translateY(-3px); }
}

.cp-chips {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  padding: 8px 14px;
}

.cp-chip {
  font-size: 12px;
  color: #155eef;
  background: #eff6ff;
  border: 1px solid #dbeafe;
  border-radius: 999px;
  padding: 4px 10px;
  cursor: pointer;
  transition: all .15s;
}

.cp-chip:hover {
  background: #155eef;
  color: #fff;
  border-color: #155eef;
}

.cp-input {
  display: flex;
  gap: 8px;
  padding: 10px 14px 14px;
  border-top: 1px solid #eef2f7;
}
</style>
