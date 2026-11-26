import { clamp01, smoothstep } from '../../pose'

const TOUCH_MS = 620

/**
 * 点击/触摸反应：短时间内头部偏移、微微后仰与眯眼。
 */
export function createTouchPlugin() {
  return {
    name: 'touch',
    apply(ctx) {
      const touch = ctx.state.touch
      if (!touch) return
      const elapsed = ctx.now - touch.at
      if (elapsed >= TOUCH_MS) {
        ctx.state.touch = null
        return
      }
      const progress = clamp01(elapsed / TOUCH_MS)
      const wave = Math.sin(progress * Math.PI)
      const strength = wave * touch.intensity

      ctx.pose.headX += (touch.x - 0.5) * 4 * strength
      ctx.pose.headY += -3 * strength
      ctx.pose.headRot += (touch.x - 0.5) * 0.05 * strength
      ctx.pose.eyeOpen *= 1 - 0.35 * strength
      ctx.pose.blush = clamp01(ctx.pose.blush + 0.4 * smoothstep(progress) * touch.intensity)
    }
  }
}
