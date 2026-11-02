import type { Application } from 'pixi.js'
import { normalizeEmotion, type Emotion } from './emotionMap'
import type { AvatarLayers } from './layers'

/**
 * 动画状态机（简化版）
 *
 * IDLE ──用户输入──> BLINK / HAPPY
 * IDLE ──LLM 流式──> TALK ──结束──> IDLE
 * IDLE ──情绪事件──> HAPPY / SAD / ANGRY ──超时──> IDLE
 *
 * 当前后端尚未提供聊天/情绪接口，动画只由本地交互驱动；
 * 后续把接口返回的 emotion 传给 setEmotion/play 即可，无需改舞台。
 */
export type AvatarState = 'IDLE' | 'LISTEN' | 'TALK' | 'HAPPY' | 'SAD' | 'ANGRY' | 'SURPRISE' | 'SLEEP'

const BLINK_DURATION = 140
const BLINK_MIN_DELAY = 2000
const BLINK_MAX_DELAY = 5000
const ONE_SHOT_DURATION = 1800

const STATE_EMOTION: Partial<Record<AvatarState, Emotion>> = {
  IDLE: 'neutral',
  LISTEN: 'neutral',
  TALK: 'neutral',
  HAPPY: 'joy',
  SAD: 'sadness',
  ANGRY: 'anger',
  SURPRISE: 'surprise'
}

function randomBlinkDelay() {
  return BLINK_MIN_DELAY + Math.random() * (BLINK_MAX_DELAY - BLINK_MIN_DELAY)
}

export function createAnimator(app: Application, layers: AvatarLayers) {
  let state: AvatarState = 'IDLE'
  let baseEmotion: Emotion = 'neutral'
  let oneShotEmotion: Emotion | null = null
  let oneShotUntil = 0
  let blinking = false
  let blinkUntil = 0
  let nextBlinkAt = performance.now() + randomBlinkDelay()

  function activeEmotion(): Emotion {
    return oneShotEmotion && performance.now() < oneShotUntil ? oneShotEmotion : baseEmotion
  }

  function renderFace() {
    layers.setFace(activeEmotion(), blinking)
  }

  function tick() {
    const now = performance.now()

    // idle 呼吸：只做整数像素位移，避免画面发虚
    layers.character.y = Math.round(Math.sin(now / 480) * 2)

    if (now >= nextBlinkAt) {
      blinking = true
      blinkUntil = now + BLINK_DURATION
      nextBlinkAt = now + randomBlinkDelay()
      renderFace()
    } else if (blinking && now >= blinkUntil) {
      blinking = false
      renderFace()
    }

    if (oneShotEmotion && now >= oneShotUntil) {
      oneShotEmotion = null
      state = 'IDLE'
      renderFace()
    }
  }

  app.ticker.add(tick)

  return {
    get state() {
      return state
    },

    /** 基线情绪：由后续接口的 emotion 字段驱动 */
    setEmotion(emotion: string) {
      baseEmotion = normalizeEmotion(emotion)
      renderFace()
    },

    /** 切换到动画状态；HAPPY/SAD/ANGRY 等会同步表情 */
    setState(next: AvatarState) {
      state = next
      const emotion = STATE_EMOTION[next]
      if (emotion) {
        baseEmotion = emotion
        renderFace()
      }
    },

    /** 一次性表情（如点击、收到消息），超时后回到基线情绪 */
    play(emotion: string, duration = ONE_SHOT_DURATION) {
      oneShotEmotion = normalizeEmotion(emotion)
      oneShotUntil = performance.now() + duration
      renderFace()
    },

    blink() {
      blinking = true
      blinkUntil = performance.now() + BLINK_DURATION
      renderFace()
    },

    destroy() {
      app.ticker.remove(tick)
    }
  }
}

export type AvatarAnimator = ReturnType<typeof createAnimator>
