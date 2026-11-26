import { describe, expect, it } from 'vitest'
import {
  detectBorderColor,
  removeBackgroundPixels,
  scanBorder,
  softenEdge
} from '../removeImageBackground'

function createImage(width, height, fill) {
  const data = new Uint8ClampedArray(width * height * 4)
  for (let y = 0; y < height; y += 1) {
    for (let x = 0; x < width; x += 1) {
      const color = fill(x, y)
      const index = (y * width + x) * 4
      data[index] = color[0]
      data[index + 1] = color[1]
      data[index + 2] = color[2]
      data[index + 3] = color[3] ?? 255
    }
  }
  return data
}

function alphaAt(data, width, x, y) {
  return data[(y * width + x) * 4 + 3]
}

function setPixel(data, width, x, y, color) {
  const index = (y * width + x) * 4
  data[index] = color[0]
  data[index + 1] = color[1]
  data[index + 2] = color[2]
  data[index + 3] = color[3] ?? 255
}

describe('removeImageBackground', () => {
  it('边缘已经透明时原样保留', () => {
    const width = 20
    const data = createImage(width, 20, (x, y) =>
      x < 2 || x >= 18 || y < 2 || y >= 18 ? [0, 0, 0, 0] : [220, 40, 40, 255]
    )
    const border = scanBorder(data, width, 20)
    expect(border.transparentShare).toBeGreaterThan(0.2)

    const result = removeBackgroundPixels(data, width, 20)
    expect(result.changed).toBe(false)
    expect(alphaAt(data, width, 0, 0)).toBe(0)
    expect(alphaAt(data, width, 10, 10)).toBe(255)
  })

  it('去除白色背景，保留角色与角色内部白色细节', () => {
    const width = 24
    const data = createImage(width, 24, (x, y) => {
      const inside = x >= 6 && x <= 17 && y >= 6 && y <= 17
      return inside ? [220, 40, 40, 255] : [255, 255, 255, 255]
    })
    // 角色内部的白色高光不应该被删除
    setPixel(data, width, 11, 11, [255, 255, 255, 255])

    const result = removeBackgroundPixels(data, width, 24)
    expect(result.changed).toBe(true)
    expect(result.removedRatio).toBeGreaterThan(0.5)
    expect(result.removedRatio).toBeLessThan(0.9)
    expect(alphaAt(data, width, 0, 0)).toBe(0)
    expect(alphaAt(data, width, 11, 11)).toBe(255)
    expect(alphaAt(data, width, 7, 7)).toBe(255)
  })

  it('识别并去除深色底板（#101827）', () => {
    const width = 20
    const data = createImage(width, 20, (x, y) => {
      const inside = x >= 5 && x <= 14 && y >= 5 && y <= 14
      return inside ? [235, 190, 120, 255] : [16, 24, 39, 255]
    })

    const color = detectBorderColor(data, width, 20)
    expect(color).not.toBeNull()
    expect(color.r).toBeCloseTo(16, 0)
    expect(color.g).toBeCloseTo(24, 0)
    expect(color.b).toBeCloseTo(39, 0)

    const result = removeBackgroundPixels(data, width, 20)
    expect(result.changed).toBe(true)
    expect(alphaAt(data, width, 0, 0)).toBe(0)
    expect(alphaAt(data, width, 10, 10)).toBe(255)
  })

  it('复杂多色边缘不做处理', () => {
    const palette = [
      [255, 0, 0, 255],
      [0, 255, 0, 255],
      [0, 0, 255, 255],
      [255, 255, 0, 255],
      [255, 0, 255, 255]
    ]
    const width = 20
    const data = createImage(width, 20, (x, y) => {
      const edge = x === 0 || y === 0 || x === width - 1 || y === 19
      return edge ? palette[(x + y) % palette.length] : [120, 120, 120, 255]
    })

    expect(detectBorderColor(data, width, 20)).toBeNull()
    const result = removeBackgroundPixels(data, width, 20)
    expect(result.changed).toBe(false)
    expect(alphaAt(data, width, 0, 0)).toBe(255)
  })

  it('整张图都是同色时不误删', () => {
    const width = 16
    const data = createImage(width, 16, () => [10, 10, 10, 255])
    const result = removeBackgroundPixels(data, width, 16)
    expect(result.changed).toBe(false)
    expect(result.removedRatio).toBeGreaterThan(0.9)
    expect(alphaAt(data, width, 8, 8)).toBe(255)
  })

  it('软边只影响紧邻删除区的相近像素', () => {
    const width = 5
    const data = createImage(width, 5, () => [0, 0, 0, 255])
    setPixel(data, width, 2, 2, [40, 40, 40, 255])
    const removed = new Uint8Array(width * 5)
    removed[2 * width + 1] = 1

    softenEdge(data, width, 5, { r: 0, g: 0, b: 0 }, removed, 40)

    const softened = alphaAt(data, width, 2, 2)
    expect(softened).toBeLessThan(255)
    expect(softened).toBeGreaterThan(0)
    expect(alphaAt(data, width, 3, 2)).toBe(255)
    expect(alphaAt(data, width, 4, 2)).toBe(255)
  })
})
