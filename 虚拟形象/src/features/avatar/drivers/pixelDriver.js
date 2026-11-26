import { clamp, snap } from '../pose'

const HEAD_PIVOT_X = 128
const HEAD_PIVOT_Y = 100
const BODY_PIVOT_X = 128
const BODY_PIVOT_Y = 200

/**
 * 像素驱动：把 Pose 映射到现有 Pixi 分层。
 *
 * 头、眼、前后发绕同一个头部枢轴旋转/位移；
 * 身体容器绕脚下枢轴轻微摆动；脸/嘴只在量化值变化时重绘。
 */
export function createPixelDriver(layers) {
  const headParts = [layers.head, layers.eyes, layers.hairFront, layers.hairBack]

  for (const part of headParts) {
    part.pivot.set(HEAD_PIVOT_X, HEAD_PIVOT_Y)
  }
  layers.character.pivot.set(BODY_PIVOT_X, BODY_PIVOT_Y)

  return {
    render(pose, state) {
      const headX = snap(pose.headX)
      const headY = snap(pose.headY)
      const headRot = clamp(pose.headRot, -0.28, 0.28)
      for (const part of headParts) {
        part.x = HEAD_PIVOT_X + headX
        part.y = HEAD_PIVOT_Y + headY
        part.rotation = headRot
      }

      layers.character.x = BODY_PIVOT_X + snap(pose.bodyX)
      layers.character.y = BODY_PIVOT_Y + snap(pose.bodyY)
      layers.character.rotation = clamp(pose.bodyRot, -0.12, 0.12)

      layers.setFace({
        emotion: state.effectiveEmotion || state.emotion,
        eyeOpen: pose.eyeOpen,
        eyeSquint: pose.eyeSquint,
        eyeX: pose.eyeX,
        eyeY: pose.eyeY,
        brow: pose.brow,
        mouthOpen: pose.mouthOpen,
        mouthForm: pose.mouthForm,
        viseme: pose.viseme,
        blush: pose.blush,
        heart: pose.heart,
        sweat: pose.sweat,
        sparkle: pose.sparkle
      })
    }
  }
}
