/**
 * 情绪 -> 表情映射
 *
 * 表情由后端返回的 emotion 字段驱动（joy/sadness/anger/surprise/neutral），
 * 前端只做映射，不根据聊天文本猜测情绪。
 */
export type Emotion = 'joy' | 'sadness' | 'anger' | 'surprise' | 'neutral'

export type MouthShape = 'flat' | 'smile' | 'frown' | 'open' | 'grit'

export type EffectKind = 'none' | 'hearts' | 'tear' | 'anger' | 'spark'

export interface EmotionVisual {
  eyeWidth: number
  eyeHeight: number
  mouth: MouthShape
  blush: boolean
  effect: EffectKind
}

export const EMOTION_VISUALS: Record<Emotion, EmotionVisual> = {
  neutral: { eyeWidth: 8, eyeHeight: 8, mouth: 'flat', blush: false, effect: 'none' },
  joy: { eyeWidth: 8, eyeHeight: 5, mouth: 'smile', blush: true, effect: 'hearts' },
  sadness: { eyeWidth: 8, eyeHeight: 8, mouth: 'frown', blush: false, effect: 'tear' },
  anger: { eyeWidth: 8, eyeHeight: 6, mouth: 'grit', blush: false, effect: 'anger' },
  surprise: { eyeWidth: 9, eyeHeight: 10, mouth: 'open', blush: false, effect: 'spark' }
}

export function normalizeEmotion(value: unknown): Emotion {
  return typeof value === 'string' && value in EMOTION_VISUALS
    ? (value as Emotion)
    : 'neutral'
}

export function getEmotionVisual(value: unknown): EmotionVisual {
  return EMOTION_VISUALS[normalizeEmotion(value)]
}
