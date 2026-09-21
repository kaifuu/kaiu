<template>
  <div class="scene3d-wrap">
    <div ref="wrapEl" class="scene3d-canvas"></div>
    <div v-if="failed" class="scene3d-fallback">
      <div class="fb-ring">3D</div>
      <p>当前环境不支持 WebGL,3D 视图不可用</p>
    </div>
    <slot />
  </div>
</template>

<script setup>
/**
 * scene3d 引擎的组件包装:负责创建/降级/页面隐藏暂停/卸载释放。
 * 子屏通过 @ready="api => ..." 拿到引擎句柄装配自己的场景。
 */
import { ref, onMounted, onUnmounted } from 'vue'
import { createScene3D } from './scene3d'

const props = defineProps({
  /** 透传给 createScene3D 的 opts(orbitSpeed/radius/height/fov) */
  opts: { type: Object, default: () => ({}) }
})
const emit = defineEmits(['ready'])

const wrapEl = ref(null)
const failed = ref(false)
let api = null

function onVisible() {
  if (!api) return
  document.hidden ? api.pause() : api.resume()
}

onMounted(() => {
  try {
    api = createScene3D(wrapEl.value, props.opts)
    emit('ready', api)
  } catch (e) {
    // WebGL 不可用(驱动/无头环境):占位降级,不影响面板其余部分
    console.warn('[Scene3D] 初始化失败,降级占位', e)
    failed.value = true
  }
  document.addEventListener('visibilitychange', onVisible)
})

onUnmounted(() => {
  document.removeEventListener('visibilitychange', onVisible)
  api?.dispose()
  api = null
})

defineExpose({ api: () => api })
</script>

<style scoped>
.scene3d-wrap { position: absolute; inset: 0; }
.scene3d-canvas { position: absolute; inset: 0; }
.scene3d-canvas :deep(canvas) { display: block; }
.scene3d-fallback {
  position: absolute; inset: 0;
  display: flex; flex-direction: column; align-items: center; justify-content: center; gap: 12px;
  background:
    radial-gradient(600px 300px at 50% 30%, rgba(37, 99, 235, .22), transparent 70%),
    repeating-linear-gradient(0deg, rgba(56, 189, 248, .05) 0 1px, transparent 1px 40px),
    repeating-linear-gradient(90deg, rgba(56, 189, 248, .05) 0 1px, transparent 1px 40px),
    #041225;
}
.fb-ring {
  width: 74px; height: 74px; border-radius: 50%;
  display: flex; align-items: center; justify-content: center;
  font-size: 18px; font-weight: 800; color: #7dd3fc;
  border: 1px solid rgba(56, 189, 248, .5);
  box-shadow: 0 0 24px rgba(56, 189, 248, .25), inset 0 0 18px rgba(56, 189, 248, .12);
}
.scene3d-fallback p { font-size: 12px; color: #7fa8d6; }
</style>
