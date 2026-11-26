/**
 * 把图片边缘的纯色背景（黑、白或任意单一颜色）转成透明，只保留角色。
 *
 * 做法：
 * 1. 先看边缘是否已经以透明为主（透明 PNG 原样保留，交给显示层处理）；
 * 2. 对不透明边缘像素做粗量化，找出主色；占比不足视为复杂背景，放弃；
 * 3. 从四边向内 flood fill，只删除与边缘连通且接近主色的像素，
 *    保留角色内部的同色细节（眼睛、高光、白色衣物）。
 *
 * 处理失败或无法可靠判断时返回空字符串，调用方回退原图。
 */

const MAX_SIZE = 1024
const MAX_CACHE = 8
const BORDER_ALPHA = 24
const TRANSPARENT_SKIP_SHARE = 0.2
const DOMINANT_MIN_SHARE = 0.3
const COLOR_TOLERANCE = 40
const REMOVED_MIN_RATIO = 0.01
const REMOVED_MAX_RATIO = 0.9

/** 统计四条边上的透明像素比例 */
export function scanBorder(data, width, height) {
  let total = 0
  let transparent = 0
  const visit = (x, y) => {
    total += 1
    if (data[(y * width + x) * 4 + 3] < BORDER_ALPHA) transparent += 1
  }
  for (let x = 0; x < width; x += 1) {
    visit(x, 0)
    visit(x, height - 1)
  }
  for (let y = 1; y < height - 1; y += 1) {
    visit(0, y)
    visit(width - 1, y)
  }
  return { total, transparent, transparentShare: total ? transparent / total : 0 }
}

/**
 * 找出边缘的主色（5 bit/通道量化后取最大桶，桶内求均值）。
 * 主色占比不足时返回 null。
 */
export function detectBorderColor(data, width, height) {
  const counts = new Map()
  const sums = new Map()

  const visit = (x, y) => {
    const index = (y * width + x) * 4
    if (data[index + 3] < BORDER_ALPHA) return
    const r = data[index]
    const g = data[index + 1]
    const b = data[index + 2]
    const key = ((r >> 3) << 10) | ((g >> 3) << 5) | (b >> 3)
    counts.set(key, (counts.get(key) || 0) + 1)
    const sum = sums.get(key)
    if (sum) {
      sum[0] += r
      sum[1] += g
      sum[2] += b
    } else {
      sums.set(key, [r, g, b])
    }
  }

  for (let x = 0; x < width; x += 1) {
    visit(x, 0)
    visit(x, height - 1)
  }
  for (let y = 1; y < height - 1; y += 1) {
    visit(0, y)
    visit(width - 1, y)
  }

  let opaque = 0
  let bestKey = -1
  let bestCount = 0
  for (const [key, count] of counts) {
    opaque += count
    if (count > bestCount) {
      bestCount = count
      bestKey = key
    }
  }
  if (!opaque || bestKey < 0 || bestCount / opaque < DOMINANT_MIN_SHARE) return null

  const sum = sums.get(bestKey)
  return {
    r: sum[0] / bestCount,
    g: sum[1] / bestCount,
    b: sum[2] / bestCount,
    share: bestCount / opaque
  }
}

/** 从四边向内删除与主色接近、且与边缘连通的像素（只写 removed 掩码） */
export function floodFillBackground(
  data,
  width,
  height,
  color,
  removed,
  tolerance = COLOR_TOLERANCE
) {
  const red = color.r
  const green = color.g
  const blue = color.b
  const toleranceSquared = tolerance * tolerance
  const queue = new Int32Array(width * height)
  let head = 0
  let tail = 0
  let count = 0

  const isClose = (index) => {
    const dr = data[index] - red
    const dg = data[index + 1] - green
    const db = data[index + 2] - blue
    return dr * dr + dg * dg + db * db <= toleranceSquared
  }

  const seed = (x, y) => {
    const point = y * width + x
    if (removed[point]) return
    const index = point * 4
    // 已经透明的像素也视为背景，允许从它继续向内扩散
    if (data[index + 3] >= BORDER_ALPHA && !isClose(index)) return
    removed[point] = 1
    queue[tail++] = point
  }

  for (let x = 0; x < width; x += 1) {
    seed(x, 0)
    seed(x, height - 1)
  }
  for (let y = 1; y < height - 1; y += 1) {
    seed(0, y)
    seed(width - 1, y)
  }

  while (head < tail) {
    const point = queue[head++]
    count += 1
    const x = point % width
    const y = (point / width) | 0
    if (x > 0) seed(x - 1, y)
    if (x + 1 < width) seed(x + 1, y)
    if (y > 0) seed(x, y - 1)
    if (y + 1 < height) seed(x, y + 1)
  }

  return count
}

/** 紧邻删除区的 1px 环按颜色距离降低 alpha，减少锯齿/底色残留 */
export function softenEdge(data, width, height, color, removed, tolerance = COLOR_TOLERANCE) {
  const red = color.r
  const green = color.g
  const blue = color.b
  const limit = tolerance * 1.8
  const limitSquared = limit * limit
  const toleranceSquared = tolerance * tolerance

  for (let y = 0; y < height; y += 1) {
    for (let x = 0; x < width; x += 1) {
      const point = y * width + x
      if (removed[point]) continue
      const adjacent =
        (x > 0 && removed[point - 1]) ||
        (x + 1 < width && removed[point + 1]) ||
        (y > 0 && removed[point - width]) ||
        (y + 1 < height && removed[point + width])
      if (!adjacent) continue

      const index = point * 4
      if (data[index + 3] < BORDER_ALPHA) continue
      const dr = data[index] - red
      const dg = data[index + 1] - green
      const db = data[index + 2] - blue
      const distanceSquared = dr * dr + dg * dg + db * db
      if (distanceSquared > limitSquared) continue

      const factor = Math.max(
        0,
        Math.min(1, (Math.sqrt(distanceSquared) - tolerance) / (limit - tolerance))
      )
      data[index + 3] = Math.min(data[index + 3], Math.round(factor * 255))
    }
  }
}

/**
 * 在像素数据上执行去背景。
 * @returns {{ changed: boolean, removedRatio: number, color: Object|null }}
 */
export function removeBackgroundPixels(data, width, height) {
  const total = width * height
  if (!total) return { changed: false, removedRatio: 0, color: null }

  const border = scanBorder(data, width, height)
  if (border.transparentShare >= TRANSPARENT_SKIP_SHARE) {
    return { changed: false, removedRatio: 0, color: null }
  }

  const color = detectBorderColor(data, width, height)
  if (!color) return { changed: false, removedRatio: 0, color: null }

  const removed = new Uint8Array(total)
  const count = floodFillBackground(data, width, height, color, removed)
  const removedRatio = count / total
  if (removedRatio < REMOVED_MIN_RATIO || removedRatio > REMOVED_MAX_RATIO) {
    return { changed: false, removedRatio, color }
  }

  softenEdge(data, width, height, color, removed)
  for (let point = 0; point < total; point += 1) {
    if (removed[point]) data[point * 4 + 3] = 0
  }
  return { changed: true, removedRatio, color }
}

const cache = new Map()

/** 测试/调试用：清空按 URL 的结果缓存 */
export function clearBackgroundCache() {
  cache.clear()
}

/**
 * @param {string} src
 * @returns {Promise<string>} 透明 PNG 的 data URL；无需处理或失败时返回 ''
 */
export function removeImageBackground(src) {
  if (!src) return Promise.resolve('')
  if (cache.has(src)) return cache.get(src)

  const task = process(src).catch(() => '')
  cache.set(src, task)
  while (cache.size > MAX_CACHE) {
    cache.delete(cache.keys().next().value)
  }
  return task
}

async function process(src) {
  const response = await fetch(src, { mode: 'cors', credentials: 'omit' })
  if (!response.ok) return ''

  const blob = await response.blob()
  const bitmap = await createImageBitmap(blob)
  const ratio = Math.min(1, MAX_SIZE / Math.max(bitmap.width, bitmap.height))
  const width = Math.max(1, Math.round(bitmap.width * ratio))
  const height = Math.max(1, Math.round(bitmap.height * ratio))

  const canvas = document.createElement('canvas')
  canvas.width = width
  canvas.height = height
  const context = canvas.getContext('2d', { willReadFrequently: true })
  if (!context) return ''
  context.drawImage(bitmap, 0, 0, width, height)
  bitmap.close?.()

  const image = context.getImageData(0, 0, width, height)
  if (!removeBackgroundPixels(image.data, width, height).changed) return ''
  context.putImageData(image, 0, 0)
  return canvas.toDataURL('image/png')
}
