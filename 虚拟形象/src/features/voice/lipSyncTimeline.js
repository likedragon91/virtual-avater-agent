import { VISEME, visemeUnits } from './visemeMap'

const DEFAULT_LEADING_SILENCE_MS = 80

/**
 * 把文本 viseme 单元按权重铺到音频时长上。
 *
 * MP3 句首可能有 ≤100ms 静音，默认先闭嘴 80ms；播放层用
 * timeline.at(elapsedMs) 取当前口型，再用 Analyser 包络控制张口大小。
 */
export function buildVisemeTimeline(text, durationMs, options = {}) {
  const total = Math.max(0, Number(durationMs) || 0)
  const units = visemeUnits(text)
  const leading = Math.min(
    Math.max(0, options.leadingSilenceMs ?? DEFAULT_LEADING_SILENCE_MS),
    Math.max(0, total)
  )

  if (!units.length || total <= 0) {
    return {
      durationMs: total,
      segments: [],
      at: () => VISEME.CLOSED
    }
  }

  const weightSum = units.reduce((sum, unit) => sum + unit.weight, 0) || 1
  const available = Math.max(0, total - leading)

  const segments = []
  let cursor = leading
  for (const unit of units) {
    const span = (available * unit.weight) / weightSum
    segments.push({
      start: cursor,
      end: cursor + span,
      viseme: unit.viseme
    })
    cursor += span
  }

  return {
    durationMs: total,
    segments,
    at(elapsedMs) {
      const time = Number(elapsedMs) || 0
      if (time < leading || time >= total) return VISEME.CLOSED
      let low = 0
      let high = segments.length - 1
      while (low <= high) {
        const mid = (low + high) >> 1
        const segment = segments[mid]
        if (time < segment.start) {
          high = mid - 1
        } else if (time >= segment.end) {
          low = mid + 1
        } else {
          return segment.viseme
        }
      }
      return VISEME.CLOSED
    }
  }
}
