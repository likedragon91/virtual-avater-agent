/**
 * 视线：鼠标/触摸偏置 + 无输入时的轻微扫视。
 */
export function createGazePlugin() {
  let currentX = 0
  let currentY = 0

  return {
    name: 'gaze',
    apply(ctx) {
      const pointer = ctx.state.pointer
      let targetX = Math.sin(ctx.now / 1700) * 1.6
      let targetY = Math.sin(ctx.now / 2400) * 0.8
      if (pointer && pointer.active) {
        targetX = (pointer.x - 0.5) * 6
        targetY = (pointer.y - 0.5) * 4
      }

      const alpha = Math.min(1, ctx.dt * 8)
      currentX += (targetX - currentX) * alpha
      currentY += (targetY - currentY) * alpha

      ctx.pose.eyeX += currentX
      ctx.pose.eyeY += currentY
      // 视线带动一点点头部，像素角色更有「看过去」的感觉
      ctx.pose.headX += currentX * 0.35
      ctx.pose.headY += currentY * 0.2
    }
  }
}
