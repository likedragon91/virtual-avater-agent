/**
 * 像素角色的动作预设。
 *
 * 没有骨骼绑定，所以动作只做头部/身体变换与表情叠加；
 * tracks 的 keys 是 [归一化时间 0..1, 数值]，线性插值后乘以 intensity。
 */
export const MOTION_PRESETS = Object.freeze({
  nod: {
    duration: 700,
    tracks: [
      { field: 'headY', keys: [[0, 0], [0.22, 4], [0.45, 0], [0.68, 2.4], [1, 0]] }
    ]
  },
  shake: {
    duration: 820,
    tracks: [
      { field: 'headX', keys: [[0, 0], [0.18, -4], [0.4, 4], [0.62, -3], [0.82, 2], [1, 0]] }
    ]
  },
  tilt: {
    duration: 1000,
    tracks: [
      { field: 'headRot', keys: [[0, 0], [0.28, -0.1], [0.72, -0.1], [1, 0]] },
      { field: 'headX', keys: [[0, 0], [0.3, 2], [0.7, 2], [1, 0]] }
    ]
  },
  bounce: {
    duration: 760,
    tracks: [
      { field: 'bodyY', keys: [[0, 0], [0.24, -6], [0.5, 0], [0.74, -3], [1, 0]] }
    ]
  },
  hop: {
    duration: 900,
    tracks: [
      { field: 'bodyY', keys: [[0, 0], [0.2, -10], [0.46, 0], [0.62, -4], [0.8, 0], [1, 0]] },
      { field: 'headY', keys: [[0, 0], [0.2, -3], [0.46, 0], [0.62, -1.5], [1, 0]] }
    ]
  },
  look_away: {
    duration: 1200,
    tracks: [
      { field: 'eyeX', keys: [[0, 0], [0.2, -4], [0.75, -4], [1, 0]] },
      { field: 'headRot', keys: [[0, 0], [0.25, 0.06], [0.75, 0.06], [1, 0]] }
    ]
  }
})

export function hasMotion(name) {
  return Boolean(name && MOTION_PRESETS[name])
}

export function sampleTrack(keys, progress) {
  if (!Array.isArray(keys) || keys.length === 0) return 0
  const t = Math.max(0, Math.min(1, progress))
  for (let i = 1; i < keys.length; i++) {
    const [endT, endValue] = keys[i]
    if (t <= endT) {
      const [startT, startValue] = keys[i - 1]
      const span = endT - startT || 1
      return startValue + (endValue - startValue) * ((t - startT) / span)
    }
  }
  return keys[keys.length - 1][1]
}
