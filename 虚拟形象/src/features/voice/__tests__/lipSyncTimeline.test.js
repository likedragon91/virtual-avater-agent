import { describe, expect, it } from 'vitest'
import { buildVisemeTimeline } from '../lipSyncTimeline'
import { VISEME } from '../visemeMap'

describe('lip sync timeline', () => {
  it('covers the audio duration after the leading silence', () => {
    const timeline = buildVisemeTimeline('你好世界', 1000, { leadingSilenceMs: 80 })

    expect(timeline.segments.length).toBeGreaterThan(0)
    expect(timeline.segments[0].start).toBeCloseTo(80)
    expect(timeline.segments[timeline.segments.length - 1].end).toBeCloseTo(1000)
  })

  it('returns closed during leading silence and after the end', () => {
    const timeline = buildVisemeTimeline('你好世界', 1000, { leadingSilenceMs: 80 })

    expect(timeline.at(0)).toBe(VISEME.CLOSED)
    expect(timeline.at(50)).toBe(VISEME.CLOSED)
    expect(timeline.at(1100)).toBe(VISEME.CLOSED)
  })

  it('follows text units through the timeline', () => {
    const timeline = buildVisemeTimeline('你好', 1000, { leadingSilenceMs: 0 })

    expect(timeline.at(10)).toBe(VISEME.WIDE)
    expect(timeline.at(900)).toBe(VISEME.OPEN)
  })

  it('closes for punctuation', () => {
    const timeline = buildVisemeTimeline('你好，世界', 1000, { leadingSilenceMs: 0 })
    const closed = timeline.segments.find((segment) => segment.viseme === VISEME.CLOSED)
    expect(closed).toBeTruthy()
  })

  it('handles empty text and zero duration', () => {
    expect(buildVisemeTimeline('', 1000).at(100)).toBe(VISEME.CLOSED)
    expect(buildVisemeTimeline('你好', 0).at(0)).toBe(VISEME.CLOSED)
  })

  it('never leaves the mouth open when duration is shorter than leading silence', () => {
    const timeline = buildVisemeTimeline('你好', 40, { leadingSilenceMs: 80 })
    expect(timeline.at(10)).toBe(VISEME.CLOSED)
    expect(timeline.at(30)).toBe(VISEME.CLOSED)
  })
})
