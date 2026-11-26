const CLOSE_MS = 75
const OPEN_MS = 170
const MIN_DELAY = 3000
const MAX_DELAY = 8000

function randomDelay() {
  return MIN_DELAY + Math.random() * (MAX_DELAY - MIN_DELAY)
}

/**
 * 自动眨眼状态机。
 * 用乘法叠加到 eyeOpen，所以表情闭眼（joy 眯眼）不会被眨眼顶开。
 */
export function createBlinkPlugin() {
  let phase = 'idle'
  let phaseStart = 0
  let nextBlinkAt = 0

  return {
    name: 'blink',
    apply(ctx) {
      const now = ctx.now
      if (!nextBlinkAt) nextBlinkAt = now + randomDelay()

      if (ctx.state.blinkAt) {
        phase = 'closing'
        phaseStart = now
        nextBlinkAt = now + randomDelay()
        ctx.state.blinkAt = 0
      } else if (phase === 'idle' && now >= nextBlinkAt) {
        phase = 'closing'
        phaseStart = now
      }

      if (phase === 'closing' && now - phaseStart >= CLOSE_MS) {
        phase = 'opening'
        phaseStart = now
      } else if (phase === 'opening' && now - phaseStart >= OPEN_MS) {
        phase = 'idle'
        nextBlinkAt = now + randomDelay()
      }

      let factor = 1
      if (phase === 'closing') {
        factor = 1 - 0.94 * Math.min(1, (now - phaseStart) / CLOSE_MS)
      } else if (phase === 'opening') {
        factor = 0.06 + 0.94 * Math.min(1, (now - phaseStart) / OPEN_MS)
      }
      ctx.pose.eyeOpen *= factor
    }
  }
}
