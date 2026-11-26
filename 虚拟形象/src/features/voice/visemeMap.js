import { pinyin } from 'pinyin-pro'

/**
 * 中文口型（viseme）近似映射。
 *
 * 不追求音素级精确：把拼音韵母归到像素嘴能表达的 4 类
 * open（a/ai/ao/an…）、wide（i/ie/in…）、round（o/u/ü/ou…）、closed。
 * 英文/数字按字母映射，标点作为闭嘴停顿。
 */
export const VISEME = Object.freeze({
  CLOSED: 'closed',
  OPEN: 'open',
  WIDE: 'wide',
  ROUND: 'round'
})

const INITIALS = ['zh', 'ch', 'sh', 'b', 'p', 'm', 'f', 'd', 't', 'n', 'l',
  'g', 'k', 'h', 'j', 'q', 'x', 'r', 'z', 'c', 's', 'y', 'w']

const OPEN_FINALS = new Set([
  'a', 'ai', 'ao', 'an', 'ang', 'ia', 'iao', 'ian', 'iang',
  'ua', 'uai', 'uan', 'uang', 'uan', 'er'
])
const WIDE_FINALS = new Set([
  'i', 'ie', 'in', 'ing', 'ei', 'en', 'eng', 'e', 'ueng'
])
const ROUND_FINALS = new Set([
  'o', 'uo', 'ou', 'iu', 'iou', 'ong', 'iong', 'u', 'v',
  'un', 'vn', 'ui', 'ue', 've', 'ün'
])

const ENGLISH_VOWELS = {
  a: VISEME.OPEN,
  o: VISEME.ROUND,
  e: VISEME.WIDE,
  i: VISEME.WIDE,
  u: VISEME.ROUND,
  y: VISEME.WIDE
}
const ENGLISH_CLOSED = new Set(['m', 'b', 'p'])

function stripInitial(syllable) {
  for (const initial of INITIALS) {
    if (syllable.startsWith(initial) && syllable.length > initial.length) {
      return syllable.slice(initial.length)
    }
  }
  return syllable
}

export function finalToViseme(final) {
  if (!final) return VISEME.CLOSED
  if (OPEN_FINALS.has(final)) return VISEME.OPEN
  if (ROUND_FINALS.has(final)) return VISEME.ROUND
  if (WIDE_FINALS.has(final)) return VISEME.WIDE

  const last = final[final.length - 1]
  if (last === 'a') return VISEME.OPEN
  if (last === 'o' || last === 'u' || last === 'v') return VISEME.ROUND
  return VISEME.WIDE
}

/** 单个拼音音节 -> viseme；无法识别为拼音时返回 null */
export function syllableToViseme(token) {
  const lower = String(token || '').toLowerCase().replace(/ü/g, 'v')
  if (!/^[a-z]+$/.test(lower)) return null
  const final = stripInitial(lower)
  if (!OPEN_FINALS.has(final) && !ROUND_FINALS.has(final) && !WIDE_FINALS.has(final)) {
    return null
  }
  return finalToViseme(final)
}

function englishUnits(chunk) {
  const units = []
  for (const char of chunk.toLowerCase()) {
    if (char >= 'a' && char <= 'z') {
      if (ENGLISH_CLOSED.has(char)) {
        units.push({ viseme: VISEME.CLOSED, weight: 0.5 })
      } else {
        units.push({ viseme: ENGLISH_VOWELS[char] || VISEME.OPEN, weight: 0.55 })
      }
    } else if (char >= '0' && char <= '9') {
      units.push({ viseme: VISEME.OPEN, weight: 0.7 })
    } else if (/\s/.test(char)) {
      units.push({ viseme: VISEME.CLOSED, weight: 0.8 })
    } else {
      units.push({ viseme: VISEME.CLOSED, weight: 1.4 })
    }
  }
  return units
}

/**
 * 把一段文本拆成按权重排布的 viseme 单元。
 */
export function visemeUnits(text) {
  const source = String(text || '').trim()
  if (!source) return []

  const tokens = pinyin(source, {
    type: 'array',
    toneType: 'none',
    nonZh: 'consecutive'
  })

  const units = []
  for (const token of tokens) {
    if (!token) continue
    const clean = token.trim()
    if (!clean) continue

    const viseme = syllableToViseme(clean)
    if (viseme) {
      units.push({ viseme, weight: 1 })
      continue
    }
    units.push(...englishUnits(clean))
  }
  return units
}
