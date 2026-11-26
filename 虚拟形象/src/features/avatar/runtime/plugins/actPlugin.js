import { MOTION_PRESETS, sampleTrack } from '../../motionPresets'
import { clamp01, smoothstep } from '../../pose'

/**
 * 动作预设播放：线性插值关键帧，按 intensity 缩放，再叠加到基础 Pose。
 * 动作结束后自动清空，不会影响下一帧。
 */
export function createActPlugin() {
  return {
    name: 'act',
    apply(ctx) {
      const act = ctx.state.act
      if (!act) return
      const preset = MOTION_PRESETS[act.name]
      if (!preset) {
        ctx.state.act = null
        return
      }

      const progress = (ctx.now - act.startedAt) / preset.duration
      if (progress >= 1) {
        ctx.state.act = null
        return
      }

      const eased = smoothstep(clamp01(progress))
      for (const track of preset.tracks) {
        const value = sampleTrack(track.keys, eased) * act.intensity
        ctx.pose[track.field] = (ctx.pose[track.field] || 0) + value
      }
    }
  }
}
