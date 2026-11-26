import { normalizeEmotion } from '../emotionMap'
import { clamp01, createPose, resetPose } from '../pose'
import { hasMotion } from '../motionPresets'
import { createPixelDriver } from '../drivers/pixelDriver'
import { createPluginPipeline } from './pluginPipeline'
import { createIdlePlugin } from './plugins/idlePlugin'
import { createGazePlugin } from './plugins/gazePlugin'
import { createEmotionPlugin } from './plugins/emotionPlugin'
import { createActPlugin } from './plugins/actPlugin'
import { createBlinkPlugin } from './plugins/blinkPlugin'
import { createLipSyncPlugin } from './plugins/lipSyncPlugin'
import { createTouchPlugin } from './plugins/touchPlugin'

function prefersReducedMotion() {
  return (
    typeof window !== 'undefined' &&
    typeof window.matchMedia === 'function' &&
    window.matchMedia('(prefers-reduced-motion: reduce)').matches
  )
}

/**
 * 像素形象运行时。
 *
 * 由 PixelStage 创建并注册到 avatarStore；聊天、语音、设置页只通过 store 调用，
 * 不再直接持有 Pixi 对象。
 */
export function createAvatarRuntime({ app, layers }) {
  const pose = createPose()
  const state = {
    emotion: 'neutral',
    effectiveEmotion: 'neutral',
    oneShot: null,
    speaking: false,
    mouthTarget: 0,
    viseme: 'closed',
    act: null,
    touch: null,
    blinkAt: 0,
    pointer: { x: 0.5, y: 0.5, active: false },
    reducedMotion: prefersReducedMotion()
  }

  const pipeline = createPluginPipeline()
  pipeline.register(createIdlePlugin())
  pipeline.register(createGazePlugin())
  pipeline.register(createEmotionPlugin())
  pipeline.register(createActPlugin())
  pipeline.register(createBlinkPlugin())
  pipeline.register(createLipSyncPlugin())
  pipeline.register(createTouchPlugin())

  const driver = createPixelDriver(layers)
  let lastTick = performance.now()
  let destroyed = false

  function tick() {
    if (destroyed) return
    const now = performance.now()
    const dt = Math.min(Math.max((now - lastTick) / 1000, 0), 0.1)
    lastTick = now

    resetPose(pose)
    pipeline.run({ pose, state, now, dt })
    driver.render(pose, state)
  }

  app.ticker.add(tick)
  driver.render(pose, state)

  const runtime = {
    setEmotion(value) {
      state.emotion = normalizeEmotion(value)
      state.oneShot = null
    },

    playEmotion(value, duration = 1600) {
      state.oneShot = {
        emotion: normalizeEmotion(value),
        until: performance.now() + Math.max(duration, 200)
      }
    },

    blink() {
      state.blinkAt = performance.now()
    },

    playMotion(name, intensity = 0.8) {
      if (!hasMotion(name)) return false
      state.act = {
        name,
        startedAt: performance.now(),
        intensity: clamp01(intensity)
      }
      return true
    },

    setSpeaking(value) {
      state.speaking = Boolean(value)
      if (!state.speaking) {
        state.mouthTarget = 0
      }
    },

    /** 播放层每帧调用：开口 0..1 + 口型大类 closed/wide/round/open */
    setMouth(open, viseme = 'closed') {
      state.mouthTarget = clamp01(open)
      state.viseme = viseme || 'closed'
    },

    triggerTouch(x, y) {
      state.touch = {
        x: clamp01(x),
        y: clamp01(y),
        intensity: 0.85,
        at: performance.now()
      }
      state.blinkAt = performance.now()
    },

    setPointer(x, y, active = true) {
      state.pointer.x = clamp01(x)
      state.pointer.y = clamp01(y)
      state.pointer.active = Boolean(active)
    },

    clearPointer() {
      state.pointer.x = 0.5
      state.pointer.y = 0.5
      state.pointer.active = false
    },

    setReducedMotion(value) {
      state.reducedMotion = Boolean(value)
    },

    /** 衣橱/形象配置：换色或换预设 */
    setPalette(palette) {
      layers.applyPalette?.(palette || {})
    },

    getState() {
      return state
    },

    getPose() {
      return pose
    },

    destroy() {
      if (destroyed) return
      destroyed = true
      app.ticker.remove(tick)
      pipeline.clear()
    }
  }

  return runtime
}
