<script setup>
import { onBeforeUnmount, onMounted, ref } from 'vue'
// 直接用源码入口：发布包的 CJS 产物会把 jszip 的动态 require 变成不可用桩
import { Live2DCubismModel } from 'live2d-renderer/renderer/Live2DCubismModel'
import { fetchLive2dModelFile } from '@/shared/api/avatar'
import { useAvatarStore } from '@/shared/stores/avatar'
import { ensureAudioContext } from '@/features/voice/audioEngine'

const props = defineProps({
  modelId: {
    type: [Number, String],
    required: true
  },
  fill: {
    type: Boolean,
    default: false
  },
  /** 悬浮窗：鉴权失败时不跳登录页 */
  silentAuth: {
    type: Boolean,
    default: false
  }
})

const emit = defineEmits(['ready', 'error'])

const avatarStore = useAvatarStore()
const wrapRef = ref(null)
const canvasRef = ref(null)
const loading = ref(true)
const errorText = ref('')

const CUBISM_CORE_PATH = `${import.meta.env.BASE_URL}live2d/live2dcubismcore.min.js`

const EXPRESSION_CANDIDATES = {
  neutral: ['neutral', 'Normal', 'normal', 'Idle', 'idle'],
  joy: ['happy', 'joy', 'smile', 'Fun', 'Happy', 'Joy', 'Smile', '笑'],
  sadness: ['sad', 'sadness', 'Sad', 'Sadness', 'cry', '哭'],
  anger: ['angry', 'anger', 'Angry', 'Anger', 'mad'],
  surprise: ['surprised', 'surprise', 'Surprised', 'Surprise', 'shock']
}

const MOTION_CANDIDATES = {
  nod: ['Nod', 'nod', 'Nodding', 'tap_head', 'TapHead'],
  shake: ['Shake', 'shake', 'Shaking', 'tap_body', 'TapBody'],
  tilt: ['Tilt', 'tilt', 'TiltHead', 'tap_head', 'TapHead'],
  bounce: ['Bounce', 'bounce', 'TapBody', 'tap_body', 'Happy'],
  hop: ['Jump', 'jump', 'Hop', 'hop', 'TapBody', 'tap_body'],
  look_away: ['LookAway', 'look_away', 'Idle', 'idle']
}

let model = null
let runtime = null
let disposed = false

function collectionHas(collection, name) {
  if (!collection) return false
  if (typeof collection.has === 'function') return collection.has(name)
  return Object.prototype.hasOwnProperty.call(collection, name)
}

function findName(collection, candidates) {
  for (const name of candidates) {
    if (collectionHas(collection, name)) return name
  }
  return null
}

function applyExpression(value) {
  if (!model) return
  const candidates = EXPRESSION_CANDIDATES[value] || EXPRESSION_CANDIDATES.neutral
  const name = findName(model.expressions, candidates)
  if (!name) return
  try {
    model.setExpression(name)
  } catch {
    // 模型可能没有该表情的播放数据
  }
}

function applyMotion(name, intensity = 0.8) {
  if (!model || !name) return false
  const candidates = MOTION_CANDIDATES[name] || [name]
  const group = findName(model.motions, candidates)
  if (!group) return false
  try {
    // MotionPriority.Normal = 2
    model.startRandomMotion(group, 2)
    return true
  } catch {
    return false
  }
}

function createRuntime() {
  return {
    setEmotion: applyExpression,
    playEmotion: (value) => applyExpression(value),
    playMotion: applyMotion,
    inputAudio: (arrayBuffer) => {
      try {
        // 只驱动口型，不重复播放声音
        model?.inputAudio?.(arrayBuffer, false)
      } catch {
        // 音频格式不支持时忽略
      }
    },
    setSpeaking() {},
    setMouth() {},
    triggerTouch() {
      try {
        model?.setRandomExpression?.()
      } catch {
        // 无表情时忽略
      }
    },
    setPointer() {},
    clearPointer() {},
    setPalette() {},
    setReducedMotion() {},
    blink() {},
    destroy() {
      try {
        // 释放全局 Cubism Framework，下一份模型可以干净地重新初始化
        model?.destroy?.(true)
      } catch {
        // 已销毁
      }
      model = null
    }
  }
}

function readableError(error) {
  const message = String(error?.message || error || '')
  if (/jszip|magic-bytes/i.test(message)) {
    return 'Live2D 依赖未就绪，请重新安装前端依赖'
  }
  if (/live2dcubismcore|script/i.test(message)) {
    return '缺少 Live2D Cubism Core，请确认 public/live2d/live2dcubismcore.min.js 存在'
  }
  return message || 'Live2D 模型加载失败'
}

async function load() {
  loading.value = true
  errorText.value = ''
  try {
    const canvas = canvasRef.value
    if (!canvas) return
    canvas.width = 512
    canvas.height = 512

    const arrayBuffer = await fetchLive2dModelFile(props.modelId, undefined, {
      skipAuthHandler: props.silentAuth
    })
    if (disposed) return

    // 与 TTS 播放共用 AudioContext，用户点击语音开关后即可驱动 Live2D 口型
    const audioContext = await ensureAudioContext().catch(() => undefined)

    model = new Live2DCubismModel(canvas, {
      autoAnimate: true,
      autoInteraction: true,
      tapInteraction: true,
      randomMotion: true,
      keepAspect: true,
      cubismCorePath: CUBISM_CORE_PATH,
      scale: 1,
      lipsyncSmoothing: 0.12,
      audioContext
    })
    await model.load(arrayBuffer)

    if (disposed) {
      model.destroy?.(true)
      model = null
      return
    }

    runtime = createRuntime()
    avatarStore.registerRuntime(runtime)
    loading.value = false
    emit('ready')
  } catch (error) {
    console.error('Live2D 模型加载失败', error)
    errorText.value = readableError(error)
    loading.value = false
    emit('error', errorText.value)
  }
}

onMounted(load)

onBeforeUnmount(() => {
  disposed = true
  if (runtime) {
    avatarStore.unregisterRuntime(runtime)
    runtime.destroy()
    runtime = null
  } else if (model) {
    model.destroy?.(true)
    model = null
  }
})
</script>

<template>
  <div ref="wrapRef" class="live2d-stage" :class="{ 'is-fill': fill }">
    <canvas ref="canvasRef" class="live2d-canvas" />
    <div v-if="loading" class="live2d-status">正在加载 Live2D 模型…</div>
    <div v-else-if="errorText" class="live2d-status is-error">{{ errorText }}</div>
  </div>
</template>

<style scoped>
.live2d-stage {
  position: relative;
  display: flex;
  align-items: center;
  justify-content: center;
  width: 100%;
  height: 100%;
  min-height: 260px;
  overflow: hidden;
  background: transparent;
}

.live2d-stage.is-fill {
  min-height: 0;
}

.live2d-canvas {
  display: block;
  width: 100%;
  height: 100%;
  max-width: 100%;
  max-height: 100%;
  object-fit: contain;
  background: transparent;
}

.live2d-status {
  position: absolute;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 24px;
  text-align: center;
  font-size: 13px;
  color: var(--el-text-color-secondary);
  background: rgba(16, 24, 39, 0.55);
  pointer-events: none;
}

.live2d-status.is-error {
  color: #ffd7d7;
}
</style>
