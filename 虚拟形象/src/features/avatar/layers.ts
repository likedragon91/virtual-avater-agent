import { Container, Graphics } from 'pixi.js'
import { getEmotionVisual, normalizeEmotion, type Emotion } from './emotionMap'

/**
 * 槽位层级（与设计文档一致）
 * 渲染时按 zIndex 排序；美术资源就绪后可把对应 Graphics 替换为 Sprite。
 */
export const SLOT_Z_INDEX = {
  background: 0,
  hairBack: 10,
  body: 20,
  eyes: 25,
  bottom: 30,
  dress: 35,
  top: 40,
  shoes: 45,
  hairFront: 50,
  effect: 70
} as const

const SKIN = 0xf2c9a0
const SKIN_SHADOW = 0xd9a87c
const HAIR = 0x6b4b3e
const HAIR_LIGHT = 0x8a6352
const SHIRT = 0x4f86c6
const SHIRT_DARK = 0x3a6aa3
const PANTS = 0x37415f
const SHOES = 0x2b2b33
const INK = 0x2b2b3a
const BLUSH = 0xf2a6b8

function makeLayer(zIndex: number) {
  const layer = new Container()
  layer.zIndex = zIndex
  return layer
}

/**
 * 无美术资源时，用像素块程序化绘制一个占位角色。
 * 结构已经按槽位拆好，后续只需替换各 Container 的子节点。
 */
export function createAvatarLayers() {
  const root = new Container()
  root.sortableChildren = true

  const background = makeLayer(SLOT_Z_INDEX.background)
  const hairBack = makeLayer(SLOT_Z_INDEX.hairBack)
  const body = makeLayer(SLOT_Z_INDEX.body)
  const eyes = makeLayer(SLOT_Z_INDEX.eyes)
  const bottom = makeLayer(SLOT_Z_INDEX.bottom)
  const top = makeLayer(SLOT_Z_INDEX.top)
  const shoes = makeLayer(SLOT_Z_INDEX.shoes)
  const hairFront = makeLayer(SLOT_Z_INDEX.hairFront)
  const effect = makeLayer(SLOT_Z_INDEX.effect)

  // 角色整体容器：动画统一位移，不影响背景和特效
  const character = new Container()
  character.sortableChildren = true
  character.zIndex = 10
  character.addChild(hairBack, body, eyes, bottom, top, shoes, hairFront)

  root.addChild(background, character, effect)

  // ---- 背景 ----
  const bg = new Graphics()
  bg.rect(0, 0, 256, 256).fill(0x101827)
  bg.rect(24, 28, 92, 70).fill(0x1a2740)
  bg.rect(28, 32, 84, 62).fill(0x24365a)
  bg.circle(70, 52, 10).fill(0xf4e6b8)
  bg.rect(92, 40, 4, 4).fill(0x8fa6c9)
  bg.rect(40, 76, 4, 4).fill(0x8fa6c9)
  bg.rect(60, 68, 4, 4).fill(0x8fa6c9)
  bg.rect(0, 196, 256, 60).fill(0x1a2338)
  bg.rect(0, 196, 256, 2).fill(0x2c3a55)
  bg.ellipse(128, 232, 86, 22).fill(0x243052)
  background.addChild(bg)

  // ---- 后发 ----
  const hairBackG = new Graphics()
  hairBackG.rect(92, 60, 72, 58).fill(HAIR)
  hairBackG.rect(88, 96, 12, 54).fill(HAIR)
  hairBackG.rect(156, 96, 12, 54).fill(HAIR)
  hairBack.addChild(hairBackG)

  // ---- 身体 ----
  const bodyG = new Graphics()
  bodyG.rect(120, 126, 16, 12).fill(SKIN_SHADOW)
  bodyG.rect(88, 140, 14, 48).fill(SKIN)
  bodyG.rect(154, 140, 14, 48).fill(SKIN)
  bodyG.rect(104, 136, 48, 44).fill(SKIN)
  bodyG.rect(108, 176, 16, 42).fill(SKIN)
  bodyG.rect(132, 176, 16, 42).fill(SKIN)
  bodyG.rect(96, 72, 64, 58).fill(SKIN)
  bodyG.rect(90, 96, 8, 18).fill(SKIN)
  bodyG.rect(158, 96, 8, 18).fill(SKIN)
  body.addChild(bodyG)

  // ---- 眼睛/表情（动态重绘） ----
  const faceG = new Graphics()
  eyes.addChild(faceG)

  // ---- 下装 ----
  const bottomG = new Graphics()
  bottomG.rect(104, 174, 48, 28).fill(PANTS)
  bottomG.rect(108, 202, 16, 18).fill(PANTS)
  bottomG.rect(132, 202, 16, 18).fill(PANTS)
  bottom.addChild(bottomG)

  // ---- 上装 ----
  const topG = new Graphics()
  topG.rect(102, 134, 52, 44).fill(SHIRT)
  topG.rect(88, 138, 16, 44).fill(SHIRT)
  topG.rect(152, 138, 16, 44).fill(SHIRT)
  topG.rect(120, 134, 16, 10).fill(SHIRT_DARK)
  top.addChild(topG)

  // ---- 鞋子 ----
  const shoesG = new Graphics()
  shoesG.rect(104, 218, 22, 10).fill(SHOES)
  shoesG.rect(130, 218, 22, 10).fill(SHOES)
  shoes.addChild(shoesG)

  // ---- 前发 ----
  const hairFrontG = new Graphics()
  hairFrontG.rect(94, 64, 68, 22).fill(HAIR)
  hairFrontG.rect(94, 82, 10, 10).fill(HAIR)
  hairFrontG.rect(152, 82, 10, 10).fill(HAIR)
  hairFrontG.rect(104, 64, 14, 6).fill(HAIR_LIGHT)
  hairFront.addChild(hairFrontG)

  // ---- 特效 ----
  const effectG = new Graphics()
  effect.addChild(effectG)

  const EYE_LEFT_X = 112
  const EYE_RIGHT_X = 136
  const EYE_BASE_Y = 100
  const MOUTH_CENTER_X = 128

  function drawEyes(emotion: Emotion, blinking: boolean) {
    const visual = getEmotionVisual(emotion)

    if (blinking) {
      faceG.rect(EYE_LEFT_X, EYE_BASE_Y + 3, 8, 2).fill(INK)
      faceG.rect(EYE_RIGHT_X, EYE_BASE_Y + 3, 8, 2).fill(INK)
      return
    }

    const eyeY = EYE_BASE_Y + Math.round((8 - visual.eyeHeight) / 2)
    for (const x of [EYE_LEFT_X, EYE_RIGHT_X]) {
      faceG.rect(x, eyeY, visual.eyeWidth, visual.eyeHeight).fill(INK)
      // 高光：保持 2×2 像素块
      faceG.rect(x + 2, eyeY + 1, 2, 2).fill(0xffffff)
    }

    if (emotion === 'anger') {
      faceG.rect(EYE_LEFT_X - 2, EYE_BASE_Y - 6, 12, 2).fill(HAIR)
      faceG.rect(EYE_RIGHT_X - 2, EYE_BASE_Y - 6, 12, 2).fill(HAIR)
    }
  }

  function drawMouth(emotion: Emotion) {
    const shape = getEmotionVisual(emotion).mouth

    switch (shape) {
      case 'smile':
        faceG.rect(MOUTH_CENTER_X - 10, 119, 4, 2).fill(INK)
        faceG.rect(MOUTH_CENTER_X - 6, 121, 12, 2).fill(INK)
        faceG.rect(MOUTH_CENTER_X + 6, 119, 4, 2).fill(INK)
        break
      case 'frown':
        faceG.rect(MOUTH_CENTER_X - 10, 123, 4, 2).fill(INK)
        faceG.rect(MOUTH_CENTER_X - 6, 121, 12, 2).fill(INK)
        faceG.rect(MOUTH_CENTER_X + 6, 123, 4, 2).fill(INK)
        break
      case 'open':
        faceG.rect(MOUTH_CENTER_X - 4, 118, 8, 8).fill(0x8b4a4a)
        break
      case 'grit':
        faceG.rect(MOUTH_CENTER_X - 8, 120, 16, 4).fill(INK)
        faceG.rect(MOUTH_CENTER_X - 6, 121, 12, 2).fill(0xffffff)
        break
      default:
        faceG.rect(MOUTH_CENTER_X - 6, 120, 12, 2).fill(INK)
    }
  }

  function drawBlush(emotion: Emotion) {
    if (!getEmotionVisual(emotion).blush) return
    faceG.rect(102, 116, 10, 4).fill(BLUSH)
    faceG.rect(144, 116, 10, 4).fill(BLUSH)
  }

  function drawEffect(emotion: Emotion) {
    effectG.clear()
    const kind = getEmotionVisual(emotion).effect

    if (kind === 'hearts') {
      // 左上、右上两个像素爱心
      for (const [x, y] of [
        [88, 52],
        [158, 60]
      ]) {
        effectG.rect(x, y + 4, 4, 4).fill(0xf06b8a)
        effectG.rect(x + 4, y + 4, 4, 4).fill(0xf06b8a)
        effectG.rect(x + 2, y, 4, 6).fill(0xf06b8a)
      }
    } else if (kind === 'tear') {
      faceG.rect(EYE_RIGHT_X + 2, EYE_BASE_Y + 10, 3, 8).fill(0x6fb7ff)
    } else if (kind === 'anger') {
      effectG.rect(168, 52, 12, 4).fill(0xe05252)
      effectG.rect(172, 48, 4, 12).fill(0xe05252)
    } else if (kind === 'spark') {
      effectG.rect(84, 48, 4, 12).fill(0xf4e6b8)
      effectG.rect(80, 52, 12, 4).fill(0xf4e6b8)
      effectG.rect(164, 56, 4, 12).fill(0xf4e6b8)
      effectG.rect(160, 60, 12, 4).fill(0xf4e6b8)
    }
  }

  function setFace(value: string, blinking = false) {
    const emotion = normalizeEmotion(value)
    faceG.clear()
    drawEyes(emotion, blinking)
    drawMouth(emotion)
    drawBlush(emotion)
    drawEffect(emotion)
    // tear 属于表情的一部分，重绘时已经画在 faceG 上
  }

  setFace('neutral')

  return {
    root,
    character,
    setFace
  }
}

export type AvatarLayers = ReturnType<typeof createAvatarLayers>
