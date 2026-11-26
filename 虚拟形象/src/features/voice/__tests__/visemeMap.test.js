import { describe, expect, it } from 'vitest'
import { VISEME, finalToViseme, syllableToViseme, visemeUnits } from '../visemeMap'

describe('viseme map', () => {
  it('maps common pinyin finals to pixel mouth shapes', () => {
    expect(finalToViseme('a')).toBe(VISEME.OPEN)
    expect(finalToViseme('ao')).toBe(VISEME.OPEN)
    expect(finalToViseme('i')).toBe(VISEME.WIDE)
    expect(finalToViseme('ie')).toBe(VISEME.WIDE)
    expect(finalToViseme('u')).toBe(VISEME.ROUND)
    expect(finalToViseme('ue')).toBe(VISEME.ROUND)
    expect(finalToViseme('ong')).toBe(VISEME.ROUND)
    expect(finalToViseme('')).toBe(VISEME.CLOSED)
  })

  it('strips initials from syllables', () => {
    expect(syllableToViseme('ni')).toBe(VISEME.WIDE)
    expect(syllableToViseme('hao')).toBe(VISEME.OPEN)
    expect(syllableToViseme('ku')).toBe(VISEME.ROUND)
    expect(syllableToViseme('le')).toBe(VISEME.WIDE)
    expect(syllableToViseme('er')).toBe(VISEME.OPEN)
    expect(syllableToViseme('xue')).toBe(VISEME.ROUND)
    expect(syllableToViseme('yuan')).toBe(VISEME.OPEN)
    expect(syllableToViseme('zhong')).toBe(VISEME.ROUND)
  })

  it('treats unknown latin words as english', () => {
    expect(syllableToViseme('hello')).toBeNull()
    expect(syllableToViseme('ok')).toBeNull()
  })

  it('builds units for chinese, english, digits and punctuation', () => {
    const chinese = visemeUnits('你好')
    expect(chinese.map((unit) => unit.viseme)).toEqual([VISEME.WIDE, VISEME.OPEN])

    const english = visemeUnits('hello')
    expect(english).toHaveLength(5)
    expect(english.some((unit) => unit.viseme === VISEME.ROUND)).toBe(true)

    const digits = visemeUnits('123')
    expect(digits.every((unit) => unit.viseme === VISEME.OPEN)).toBe(true)

    const punctuation = visemeUnits('你好，世界！')
    expect(punctuation.some((unit) => unit.viseme === VISEME.CLOSED)).toBe(true)
  })

  it('returns no units for blank text', () => {
    expect(visemeUnits('')).toEqual([])
    expect(visemeUnits('   ')).toEqual([])
  })
})
