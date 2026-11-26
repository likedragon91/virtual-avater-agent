import { Container, Graphics } from 'pixi.js'
import { getEmotionVisual, normalizeEmotion, type Emotion } from './emotionMap'

/**
 * 槽位层级（与设计文档一致）
 * 渲染时按 zIndex 排序；美术资源就绪后把对应 Graphics 替换为 Sprite。
 */
export const SLOT_Z_INDEX = {
  background: 0,
  hairBack: 10,
  body: 20,
  head: 22,
  eyes: 25,
  bottom: 30,
  dress: 35,
  top: 40,
  shoes: 45,
  hairFront: 50,
  effect: 70
} as const

export const DEFAULT_PALETTE = {
  hair: '#6b4b3e',
  top: '#4f86c6',
  bottom: '#37415f',
  shoes: '#2b2b33',
  skin: '#f2c9a0',
  background: '#101827'
}

const FACE_INK = 0x2b2b3a
const FACE_BLUSH = 0xf2a6b8
const MOON = 0xf4e6b8
const STAR = 0x8fa6c9
const COLOR_PATTERN = /^#[0-9a-fA-F]{6}$/

const EYE_LEFT_X = 112
const EYE_RIGHT_X = 136
const EYE_BASE_Y = 100
const MOUTH_CENTER_X = 128
const MOUTH_BASE_Y = 121

function makeLayer(zIndex: number) {
  const layer = new Container()
  layer.zIndex = zIndex
  return layer
}

function clamp(value: number, min: number, max: number) {
  if (Number.isNaN(value)) return min
  return Math.max(min, Math.min(max, value))
}

function clamp01(value: number) {
  return clamp(Number(value) || 0, 0, 1)
}

function quantize(value: number) {
  return Math.round(clamp01(value) * 6)
}

function clampMouthForm(value: number) {
  return clamp(Number(value) || 0, -1, 1)
}

function parseColor(value: string | undefined, fallback: number) {
  if (typeof value !== 'string' || !COLOR_PATTERN.test(value)) return fallback
  return parseInt(value.slice(1), 16)
}

function shade(color: number, factor: number) {
  const r = Math.max(0, Math.min(255, Math.round(((color >> 16) & 0xff) * factor)))
  const g = Math.max(0, Math.min(255, Math.round(((color >> 8) & 0xff) * factor)))
  const b = Math.max(0, Math.min(255, Math.round((color & 0xff) * factor)))
  return (r << 16) | (g << 8) | b
}

/**
 * 无美术资源时，用像素块程序化绘制一个占位角色。
 * 结构已经按槽位拆好，后续只需替换各 Container 的子节点。
 * 配色通过重绘实现，不依赖额外素材。
 */
export function createAvatarLayers() {
  const root = new Container()
  root.sortableChildren = true

  const background = makeLayer(SLOT_Z_INDEX.background)
  const hairBack = makeLayer(SLOT_Z_INDEX.hairBack)
  const body = makeLayer(SLOT_Z_INDEX.body)
  const head = makeLayer(SLOT_Z_INDEX.head)
  const eyes = makeLayer(SLOT_Z_INDEX.eyes)
  const bottom = makeLayer(SLOT_Z_INDEX.bottom)
  const dress = makeLayer(SLOT_Z_INDEX.dress)
  const top = makeLayer(SLOT_Z_INDEX.top)
  const shoes = makeLayer(SLOT_Z_INDEX.shoes)
  const hairFront = makeLayer(SLOT_Z_INDEX.hairFront)
  const effect = makeLayer(SLOT_Z_INDEX.effect)

  // 角色整体容器：身体位移统一作用于角色，不影响背景和特效
  const character = new Container()
  character.sortableChildren = true
  character.zIndex = 10
  character.addChild(hairBack, body, head, eyes, bottom, dress, top, shoes, hairFront)

  root.addChild(background, character, effect)

  const bg = new Graphics()
  background.addChild(bg)
  const hairBackG = new Graphics()
  hairBack.addChild(hairBackG)
  const bodyG = new Graphics()
  body.addChild(bodyG)
  const headG = new Graphics()
  head.addChild(headG)
  const faceG = new Graphics()
  eyes.addChild(faceG)
  const bottomG = new Graphics()
  bottom.addChild(bottomG)
  const topG = new Graphics()
  top.addChild(topG)
  const shoesG = new Graphics()
  shoes.addChild(shoesG)
  const hairFrontG = new Graphics()
  hairFront.addChild(hairFrontG)
  const effectG = new Graphics()
  effect.addChild(effectG)

  const palette: Record<string, string> = { ...DEFAULT_PALETTE }

  function drawBackground() {
    const base = parseColor(palette.background, 0x101827)
    bg.clear()
    bg.rect(0, 0, 256, 256).fill(base)
    bg.rect(24, 28, 92, 70).fill(shade(base, 1.7))
    bg.rect(28, 32, 84, 62).fill(shade(base, 2.3))
    bg.circle(70, 52, 10).fill(MOON)
    bg.rect(92, 40, 4, 4).fill(STAR)
    bg.rect(40, 76, 4, 4).fill(STAR)
    bg.rect(60, 68, 4, 4).fill(STAR)
    bg.rect(0, 196, 256, 60).fill(shade(base, 1.45))
    bg.rect(0, 196, 256, 2).fill(shade(base, 2.6))
    bg.ellipse(128, 232, 86, 22).fill(shade(base, 2.1))
  }

  function drawHairBack() {
    const hair = parseColor(palette.hair, 0x6b4b3e)
    hairBackG.clear()
    hairBackG.rect(92, 60, 72, 58).fill(hair)
    hairBackG.rect(88, 96, 12, 54).fill(hair)
    hairBackG.rect(156, 96, 12, 54).fill(hair)
  }

  function drawBody() {
    const skin = parseColor(palette.skin, 0xf2c9a0)
    bodyG.clear()
    bodyG.rect(120, 126, 16, 12).fill(shade(skin, 0.88))
    bodyG.rect(88, 140, 14, 48).fill(skin)
    bodyG.rect(154, 140, 14, 48).fill(skin)
    bodyG.rect(104, 136, 48, 44).fill(skin)
    bodyG.rect(108, 176, 16, 42).fill(skin)
    bodyG.rect(132, 176, 16, 42).fill(skin)
  }

  function drawHead() {
    const skin = parseColor(palette.skin, 0xf2c9a0)
    headG.clear()
    headG.rect(96, 72, 64, 58).fill(skin)
    headG.rect(90, 96, 8, 18).fill(skin)
    headG.rect(158, 96, 8, 18).fill(skin)
  }

  function drawBottom() {
    const color = parseColor(palette.bottom, 0x37415f)
    bottomG.clear()
    bottomG.rect(104, 174, 48, 28).fill(color)
    bottomG.rect(108, 202, 16, 18).fill(color)
    bottomG.rect(132, 202, 16, 18).fill(color)
  }

  function drawTop() {
    const color = parseColor(palette.top, 0x4f86c6)
    topG.clear()
    topG.rect(102, 134, 52, 44).fill(color)
    topG.rect(88, 138, 16, 44).fill(color)
    topG.rect(152, 138, 16, 44).fill(color)
    topG.rect(120, 134, 16, 10).fill(shade(color, 0.78))
  }

  function drawShoes() {
    const color = parseColor(palette.shoes, 0x2b2b33)
    shoesG.clear()
    shoesG.rect(104, 218, 22, 10).fill(color)
    shoesG.rect(130, 218, 22, 10).fill(color)
  }

  function drawHairFront() {
    const hair = parseColor(palette.hair, 0x6b4b3e)
    hairFrontG.clear()
    hairFrontG.rect(94, 64, 68, 22).fill(hair)
    hairFrontG.rect(94, 82, 10, 10).fill(hair)
    hairFrontG.rect(152, 82, 10, 10).fill(hair)
    hairFrontG.rect(104, 64, 14, 6).fill(shade(hair, 1.25))
  }

  function applyPalette(next: Record<string, string> = {}) {
    for (const key of Object.keys(DEFAULT_PALETTE)) {
      const value = next[key]
      if (typeof value === 'string' && COLOR_PATTERN.test(value)) {
        palette[key] = value.toLowerCase()
      }
    }
    drawBackground()
    drawHairBack()
    drawBody()
    drawHead()
    drawBottom()
    drawTop()
    drawShoes()
    drawHairFront()
    // 眉毛颜色跟随发色，换色后强制下一帧重绘五官
    lastFaceKey = ''
  }

  function drawEyes(
    emotion: Emotion,
    eyeOpen: number,
    squint: number,
    offsetX: number,
    offsetY: number
  ) {
    const visual = getEmotionVisual(emotion)
    const baseHeight = visual.eyeHeight
    const height = Math.max(1, Math.round(baseHeight * clamp(eyeOpen, 0, 1.2)))

    if (height <= 1) {
      faceG.rect(EYE_LEFT_X + offsetX, EYE_BASE_Y + 3 + offsetY, 8, 2).fill(FACE_INK)
      faceG.rect(EYE_RIGHT_X + offsetX, EYE_BASE_Y + 3 + offsetY, 8, 2).fill(FACE_INK)
      drawBrows(emotion)
      return
    }

    if (squint > 0.55) {
      for (const x of [EYE_LEFT_X, EYE_RIGHT_X]) {
        faceG.rect(x + offsetX, EYE_BASE_Y + 3 + offsetY, 8, 2).fill(FACE_INK)
        faceG.rect(x + 2 + offsetX, EYE_BASE_Y + 1 + offsetY, 4, 2).fill(FACE_INK)
      }
      drawBrows(emotion)
      return
    }

    const eyeY = EYE_BASE_Y + Math.round((baseHeight - height) / 2) + offsetY
    for (const x of [EYE_LEFT_X, EYE_RIGHT_X]) {
      faceG.rect(x + offsetX, eyeY, visual.eyeWidth, height).fill(FACE_INK)
      if (height >= 3) {
        faceG.rect(x + 2 + offsetX, eyeY + 1, 2, 2).fill(0xffffff)
      }
    }
    drawBrows(emotion)
  }

  function drawBrows(emotion: Emotion) {
    const hair = parseColor(palette.hair, 0x6b4b3e)
    if (emotion === 'anger') {
      faceG.rect(EYE_LEFT_X - 2, EYE_BASE_Y - 6, 12, 2).fill(hair)
      faceG.rect(EYE_RIGHT_X - 2, EYE_BASE_Y - 6, 12, 2).fill(hair)
    } else if (emotion === 'sadness') {
      faceG.rect(EYE_LEFT_X - 2, EYE_BASE_Y - 6, 8, 2).fill(hair)
      faceG.rect(EYE_RIGHT_X + 2, EYE_BASE_Y - 4, 8, 2).fill(hair)
    }
  }

  function drawMouth(
    emotion: Emotion,
    mouthOpen: number,
    mouthForm: number,
    viseme: string
  ) {
    if (mouthOpen > 0.06) {
      const shapes: Record<string, { width: number; height: number }> = {
        wide: { width: 16, height: 2 + 6 * mouthOpen },
        round: { width: 6, height: 4 + 6 * mouthOpen },
        open: { width: 10, height: 4 + 8 * mouthOpen },
        closed: { width: 12, height: 2 + 7 * mouthOpen }
      }
      const shape = shapes[viseme] || shapes.closed
      const width = Math.max(4, Math.round(shape.width))
      const height = Math.max(2, Math.round(shape.height))
      const x = MOUTH_CENTER_X - Math.floor(width / 2)
      const y = MOUTH_BASE_Y - Math.floor(height / 2) - 1
      faceG.rect(x, y, width, height).fill(0x8b4a4a)
      if (height >= 5) {
        faceG.rect(x + 1, y + 1, width - 2, 1).fill(0xf4e6e6)
      }
      return
    }

    if (mouthForm > 0.2 || getEmotionVisual(emotion).mouth === 'smile') {
      faceG.rect(MOUTH_CENTER_X - 10, 119, 4, 2).fill(FACE_INK)
      faceG.rect(MOUTH_CENTER_X - 6, 121, 12, 2).fill(FACE_INK)
      faceG.rect(MOUTH_CENTER_X + 6, 119, 4, 2).fill(FACE_INK)
      return
    }
    if (mouthForm < -0.2 || getEmotionVisual(emotion).mouth === 'frown') {
      faceG.rect(MOUTH_CENTER_X - 10, 123, 4, 2).fill(FACE_INK)
      faceG.rect(MOUTH_CENTER_X - 6, 121, 12, 2).fill(FACE_INK)
      faceG.rect(MOUTH_CENTER_X + 6, 123, 4, 2).fill(FACE_INK)
      return
    }
    if (getEmotionVisual(emotion).mouth === 'grit') {
      faceG.rect(MOUTH_CENTER_X - 8, 120, 16, 4).fill(FACE_INK)
      faceG.rect(MOUTH_CENTER_X - 6, 121, 12, 2).fill(0xffffff)
      return
    }
    if (getEmotionVisual(emotion).mouth === 'open') {
      faceG.rect(MOUTH_CENTER_X - 4, 118, 8, 8).fill(0x8b4a4a)
      return
    }
    faceG.rect(MOUTH_CENTER_X - 6, 120, 12, 2).fill(FACE_INK)
  }

  function drawBlush(blush: number) {
    if (blush <= 0.25) return
    faceG.rect(102, 116, 10, 4).fill(FACE_BLUSH)
    faceG.rect(144, 116, 10, 4).fill(FACE_BLUSH)
  }

  function drawEffect(heart: number, sweat: number, sparkle: number, fallback: string) {
    effectG.clear()

    if (heart > 0.3 || fallback === 'hearts') {
      for (const [x, y] of [
        [88, 52],
        [158, 60]
      ]) {
        effectG.rect(x, y + 4, 4, 4).fill(0xf06b8a)
        effectG.rect(x + 4, y + 4, 4, 4).fill(0xf06b8a)
        effectG.rect(x + 2, y, 4, 6).fill(0xf06b8a)
      }
    } else if (sweat > 0.3 || fallback === 'tear') {
      faceG.rect(EYE_RIGHT_X + 2, EYE_BASE_Y + 10, 3, 8).fill(0x6fb7ff)
    } else if (fallback === 'anger') {
      effectG.rect(168, 52, 12, 4).fill(0xe05252)
      effectG.rect(172, 48, 4, 12).fill(0xe05252)
    } else if (sparkle > 0.3 || fallback === 'spark') {
      effectG.rect(84, 48, 4, 12).fill(0xf4e6b8)
      effectG.rect(80, 52, 12, 4).fill(0xf4e6b8)
      effectG.rect(164, 56, 4, 12).fill(0xf4e6b8)
      effectG.rect(160, 60, 12, 4).fill(0xf4e6b8)
    }
  }

  let lastFaceKey = ''

  function setFace(face: {
    emotion?: string
    blinking?: boolean
    eyeOpen?: number
    eyeSquint?: number
    eyeX?: number
    eyeY?: number
    brow?: number
    mouthOpen?: number
    mouthForm?: number
    viseme?: string
    blush?: number
    heart?: number
    sweat?: number
    sparkle?: number
  } = {}) {
    const emotion = normalizeEmotion(face.emotion)
    const eyeOpen = face.blinking ? 0 : clamp(face.eyeOpen ?? 1, 0, 1.2)
    const eyeSquint = clamp(face.eyeSquint ?? 0, -1, 1)
    const eyeX = Math.round(face.eyeX ?? 0)
    const eyeY = Math.round(face.eyeY ?? 0)
    const brow = clamp(face.brow ?? 0, -1, 1)
    const mouthOpen = clamp01(face.mouthOpen ?? 0)
    const mouthForm = clampMouthForm(face.mouthForm ?? 0)
    const viseme = face.viseme || 'closed'
    const blush = clamp01(face.blush ?? 0)
    const heart = clamp01(face.heart ?? 0)
    const sweat = clamp01(face.sweat ?? 0)
    const sparkle = clamp01(face.sparkle ?? 0)

    const key = [
      emotion,
      quantize(eyeOpen),
      Math.round(eyeSquint * 3),
      eyeX,
      eyeY,
      Math.round(brow * 3),
      quantize(mouthOpen),
      Math.round(mouthForm * 3),
      viseme,
      quantize(blush),
      quantize(heart),
      quantize(sweat),
      quantize(sparkle)
    ].join('|')
    if (key === lastFaceKey) return
    lastFaceKey = key

    faceG.clear()
    drawEyes(emotion, eyeOpen, eyeSquint, eyeX, eyeY)
    drawMouth(emotion, mouthOpen, mouthForm, viseme)
    drawBlush(blush)
    drawEffect(heart, sweat, sparkle, getEmotionVisual(emotion).effect)
  }

  applyPalette(DEFAULT_PALETTE)
  setFace({ emotion: 'neutral' })

  return {
    root,
    background,
    character,
    head,
    eyes,
    hairBack,
    hairFront,
    dress,
    setFace,
    applyPalette
  }
}

export type AvatarLayers = ReturnType<typeof createAvatarLayers>
