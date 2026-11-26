import { clamp01, smoothstep } from '../../pose'

const RELEASE_MS = 200
const HANDOFF_MS = 700

/**
 * 口型插件。
 *
 * - 说话中：写入 mouthOpen / viseme，优先级高于表情；
 * - 说完 200ms 内：从最后口型 smoothstep 回中性；
 * - 700ms 后：强制闭嘴，再交还给表情，避免 Idle 嘴角残留。
 */
export function createLipSyncPlugin() {
  let releaseStart = 0
  let lastOpen = 0
  let lastViseme = 'closed'

  return {
    name: 'lipSync',
    apply(ctx) {
      const state = ctx.state
      if (state.speaking) {
        releaseStart = 0
        const open = clamp01(state.mouthTarget)
        ctx.pose.mouthOpen = Math.max(ctx.pose.mouthOpen, open)
        ctx.pose.viseme = state.viseme || 'closed'
        lastOpen = mathMax(ctx.pose.mouthOpen, open)
        lastViseme = ctx.pose.viseme
        return
      }

      if (!releaseStart && lastOpen > 0.01) {
        releaseStart = ctx.now
      }
      if (!releaseStart) {
        ctx.pose.viseme = 'closed'
        return
      }

      const elapsed = ctx.now - releaseStart
      if (elapsed < RELEASE_MS) {
        const factor = 1 - smoothstep(elapsed / RELEASE_MS)
        ctx.pose.mouthOpen = Math.max(ctx.pose.mouthOpen, lastOpen * factor)
        ctx.pose.viseme = lastViseme
      } else if (elapsed < HANDOFF_MS) {
        ctx.pose.mouthOpen = 0
        ctx.pose.viseme = 'closed'
      } else {
        releaseStart = 0
        lastOpen = 0
        lastViseme = 'closed'
        ctx.pose.mouthOpen = 0
        ctx.pose.viseme = 'closed'
      }
    }
  }
}

function mathMax(a, b) {
  return a > b ? a : b
}
