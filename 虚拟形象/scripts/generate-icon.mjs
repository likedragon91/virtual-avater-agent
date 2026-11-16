// 生成桌面图标与 PWA 图标，无需额外依赖。
// 用法：node scripts/generate-icon.mjs
import fs from 'node:fs'
import path from 'node:path'
import zlib from 'node:zlib'

const NAVY = [20, 28, 43, 255]
const SKIN = [242, 201, 160, 255]
const HAIR = [107, 75, 62, 255]
const EYE = [43, 43, 58, 255]
const MOUTH = [201, 106, 106, 255]
const BLUE = [79, 134, 198, 255]
const PINK = [242, 166, 184, 255]
const YELLOW = [242, 193, 78, 255]

function renderIcon(size, { maskable = false } = {}) {
  const pixels = Buffer.alloc(size * size * 4)
  const unit = (size / 256) * (maskable ? 0.8 : 1)
  const offset = maskable ? size * 0.1 : 0

  const X = (value) => Math.round(offset + value * unit)
  const Y = (value) => Math.round(offset + value * unit)
  const R = (value) => Math.max(1, Math.round(value * unit))

  const setPixel = (x, y, color) => {
    if (x < 0 || y < 0 || x >= size || y >= size) return
    const index = (y * size + x) * 4
    pixels[index] = color[0]
    pixels[index + 1] = color[1]
    pixels[index + 2] = color[2]
    pixels[index + 3] = color[3]
  }

  const fillRect = (x0, y0, x1, y1, color) => {
    for (let y = Y(y0); y < Y(y1); y += 1) {
      for (let x = X(x0); x < X(x1); x += 1) {
        setPixel(x, y, color)
      }
    }
  }

  const fillCircle = (cx, cy, radius, color) => {
    const centerX = X(cx)
    const centerY = Y(cy)
    const r = R(radius)
    const r2 = r * r
    for (let y = centerY - r; y <= centerY + r; y += 1) {
      for (let x = centerX - r; x <= centerX + r; x += 1) {
        const dx = x - centerX
        const dy = y - centerY
        if (dx * dx + dy * dy <= r2) setPixel(x, y, color)
      }
    }
  }

  if (maskable) {
    // maskable 需要铺满画布，避免被系统圆形/圆角裁切
    fillRect(0, 0, 256 / 0.8 + 25, 256 / 0.8 + 25, NAVY)
  } else {
    fillCircle(128, 128, 120, NAVY)
  }

  fillRect(72, 58, 184, 130, HAIR)
  fillRect(64, 90, 84, 150, HAIR)
  fillRect(172, 90, 192, 150, HAIR)
  fillRect(84, 96, 172, 208, SKIN)
  fillRect(104, 136, 120, 154, EYE)
  fillRect(136, 136, 152, 154, EYE)
  fillRect(112, 172, 144, 180, MOUTH)
  fillRect(196, 196, 212, 212, BLUE)
  fillRect(212, 212, 228, 228, PINK)
  fillRect(180, 212, 196, 228, YELLOW)

  return pixels
}

function crc32(buffer) {
  let crc = 0xffffffff
  for (let i = 0; i < buffer.length; i += 1) {
    crc ^= buffer[i]
    for (let bit = 0; bit < 8; bit += 1) {
      crc = crc & 1 ? (crc >>> 1) ^ 0xedb88320 : crc >>> 1
    }
  }
  return (crc ^ 0xffffffff) >>> 0
}

function chunk(type, data) {
  const typeBuffer = Buffer.from(type, 'ascii')
  const body = Buffer.concat([typeBuffer, data])
  const result = Buffer.alloc(8 + data.length + 4)
  result.writeUInt32BE(data.length, 0)
  typeBuffer.copy(result, 4)
  data.copy(result, 8)
  result.writeUInt32BE(crc32(body), 8 + data.length)
  return result
}

function encodePng(size, pixels) {
  const raw = Buffer.alloc((size * 4 + 1) * size)
  for (let y = 0; y < size; y += 1) {
    raw[y * (size * 4 + 1)] = 0
    pixels.copy(raw, y * (size * 4 + 1) + 1, y * size * 4, (y + 1) * size * 4)
  }

  const ihdr = Buffer.alloc(13)
  ihdr.writeUInt32BE(size, 0)
  ihdr.writeUInt32BE(size, 4)
  ihdr[8] = 8
  ihdr[9] = 6
  ihdr[10] = 0
  ihdr[11] = 0
  ihdr[12] = 0

  return Buffer.concat([
    Buffer.from([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a]),
    chunk('IHDR', ihdr),
    chunk('IDAT', zlib.deflateSync(raw, { level: 9 })),
    chunk('IEND', Buffer.alloc(0))
  ])
}

function encodeIco(png) {
  const header = Buffer.alloc(6)
  header.writeUInt16LE(0, 0)
  header.writeUInt16LE(1, 2)
  header.writeUInt16LE(1, 4)

  const entry = Buffer.alloc(16)
  entry[0] = 0 // 256
  entry[1] = 0 // 256
  entry.writeUInt16LE(1, 4)
  entry.writeUInt16LE(32, 6)
  entry.writeUInt32LE(png.length, 8)
  entry.writeUInt32LE(22, 12)

  return Buffer.concat([header, entry, png])
}

const electronDir = path.join(process.cwd(), 'electron', 'assets')
const pwaDir = path.join(process.cwd(), 'public', 'icons')
fs.mkdirSync(electronDir, { recursive: true })
fs.mkdirSync(pwaDir, { recursive: true })

const desktopPng = encodePng(256, renderIcon(256))
fs.writeFileSync(path.join(electronDir, 'icon.png'), desktopPng)
fs.writeFileSync(path.join(electronDir, 'icon.ico'), encodeIco(desktopPng))

fs.writeFileSync(path.join(pwaDir, 'icon-192.png'), encodePng(192, renderIcon(192)))
fs.writeFileSync(path.join(pwaDir, 'icon-512.png'), encodePng(512, renderIcon(512)))
fs.writeFileSync(
  path.join(pwaDir, 'icon-512-maskable.png'),
  encodePng(512, renderIcon(512, { maskable: true }))
)

console.log('icons generated:', electronDir, pwaDir)
