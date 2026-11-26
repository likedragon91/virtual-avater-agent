/**
 * 通用「身体控制面」。
 *
 * 参考 AIRI 的 Pose 抽象：动画文件负责大动作，插件每帧写这些小参数，
 * 最终由 StageDriver 映射到具体形象（当前是像素分层，未来可接 Live2D/VRM）。
 * 所有字段都可缺省，默认 0；eyeOpen 默认 1。
 */
export const POSE_DEFAULTS = Object.freeze({
  headX: 0,
  headY: 0,
  headRot: 0,
  bodyX: 0,
  bodyY: 0,
  bodyRot: 0,
  eyeX: 0,
  eyeY: 0,
  eyeOpen: 1,
  eyeSquint: 0,
  brow: 0,
  mouthOpen: 0,
  mouthForm: 0,
  viseme: 'closed',
  blush: 0,
  sweat: 0,
  sparkle: 0,
  heart: 0
})

export function createPose(overrides = {}) {
  return { ...POSE_DEFAULTS, ...overrides }
}

/** 每帧重置，插件在其上叠加，避免上一帧的残留 */
export function resetPose(pose) {
  Object.assign(pose, POSE_DEFAULTS)
}

export function clamp(value, min, max) {
  if (Number.isNaN(value)) return min
  return Math.max(min, Math.min(max, value))
}

export function clamp01(value) {
  return clamp(Number(value) || 0, 0, 1)
}

export function smoothstep(value) {
  const t = clamp01(value)
  return t * t * (3 - 2 * t)
}

export function lerp(from, to, alpha) {
  return from + (to - from) * clamp01(alpha)
}

/** 像素舞台用整像素位移，避免画面发虚 */
export function snap(value) {
  return Math.round(value)
}
