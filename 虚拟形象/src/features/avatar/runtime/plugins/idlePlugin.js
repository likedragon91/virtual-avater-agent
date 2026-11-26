/**
 * 待机：呼吸与轻微摇摆。
 * 只用整数像素位移，避免最近邻缩放后画面发虚。
 */
export function createIdlePlugin() {
  return {
    name: 'idle',
    apply(ctx) {
      if (ctx.state.reducedMotion) return
      const t = ctx.now / 1000
      ctx.pose.bodyY += Math.sin(t * 1.05) * 1.4
      ctx.pose.bodyX += Math.sin(t * 0.62) * 0.7
      ctx.pose.bodyRot += Math.sin(t * 0.48) * 0.008
      ctx.pose.headY += Math.sin(t * 1.6) * 0.5
    }
  }
}
