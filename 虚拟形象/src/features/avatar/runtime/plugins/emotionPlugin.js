import { normalizeEmotion } from '../../emotionMap'
import { clamp, clamp01 } from '../../pose'

/**
 * 情绪 -> Pose 的基础偏移。
 * 表情绘制仍由 layers 负责；这里负责眼睛开合、嘴角、眉毛与特效强度。
 */
export const EMOTION_POSE = Object.freeze({
  neutral: { eyeOpen: 1, eyeSquint: 0, brow: 0, mouthForm: 0, blush: 0, heart: 0, sweat: 0, sparkle: 0 },
  joy: { eyeOpen: 0.72, eyeSquint: 0.45, brow: 0.15, mouthForm: 0.85, blush: 0.65, heart: 1, sweat: 0, sparkle: 0 },
  sadness: { eyeOpen: 0.88, eyeSquint: 0, brow: -0.6, mouthForm: -0.75, blush: 0, heart: 0, sweat: 0.8, sparkle: 0 },
  anger: { eyeOpen: 0.8, eyeSquint: 0.2, brow: 0.75, mouthForm: -0.9, blush: 0, heart: 0, sweat: 0.4, sparkle: 0 },
  surprise: { eyeOpen: 1, eyeSquint: -0.1, brow: 0.45, mouthForm: 0.1, mouthOpen: 0.55, blush: 0, heart: 0, sweat: 0, sparkle: 1 }
})

export function createEmotionPlugin() {
  return {
    name: 'emotion',
    apply(ctx) {
      const state = ctx.state
      let emotion = normalizeEmotion(state.emotion)
      if (state.oneShot) {
        if (ctx.now < state.oneShot.until) {
          emotion = normalizeEmotion(state.oneShot.emotion)
        } else {
          state.oneShot = null
        }
      }
      state.effectiveEmotion = emotion

      const pose = EMOTION_POSE[emotion] || EMOTION_POSE.neutral
      ctx.pose.eyeOpen *= pose.eyeOpen ?? 1
      ctx.pose.eyeSquint += pose.eyeSquint ?? 0
      ctx.pose.brow += pose.brow ?? 0
      ctx.pose.mouthForm += pose.mouthForm ?? 0
      ctx.pose.blush = clamp01(ctx.pose.blush + (pose.blush ?? 0))
      ctx.pose.heart = clamp01(ctx.pose.heart + (pose.heart ?? 0))
      ctx.pose.sweat = clamp01(ctx.pose.sweat + (pose.sweat ?? 0))
      ctx.pose.sparkle = clamp01(ctx.pose.sparkle + (pose.sparkle ?? 0))

      if (pose.mouthOpen && !state.speaking) {
        ctx.pose.mouthOpen = Math.max(ctx.pose.mouthOpen, pose.mouthOpen)
      }
      ctx.pose.eyeOpen = clamp(ctx.pose.eyeOpen, 0, 1.4)
      ctx.pose.mouthForm = clamp(ctx.pose.mouthForm, -1, 1)
    }
  }
}
