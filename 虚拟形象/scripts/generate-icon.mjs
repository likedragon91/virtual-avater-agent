// 生成 256×256 像素风应用图标（PNG + ICO），无需额外依赖。
// 用法：node scripts/generate-icon.mjs
import fs from 'node:fs'
import path from 'node:path'
import zlib from 'node:zlib'

const SIZE = 256
const pixels = Buffer.alloc(SIZE * SIZE * 4)

function setPixel(x, y, [r, g, b, a = 255]) {
  if (x < 0 || y < 0 || x >= SIZE || y >= SIZE) return
  const offset = (y * SIZE + x) * 4
  pixels[offset] = r
  pixels[offset + 1] = g
  pixels[offset + 2] = b
  pixels[offset + 3] = a
}

function fillRect(x0, y0, x1, y1, color) {
  for (let y = y0; y < y1; y += 1) {
    for (let x = x0; x < x1; x += 1) {
      setPixel(x, y, color)
    }
  }
}

function fillCircle(cx, cy, radius, color) {
  const r2 = radius * radius
  for (let y = cy - radius; y <= cy + radius; y += 1) {
    for (let x = cx - radius; x <= cx + radius; x += 1) {
      const dx = x - cx
      const dy = y - cy
      if (dx * dx + dy * dy <= r2) setPixel(x, y, color)
    }
  }
}

// 背景圆
fillCircle(128, 128, 120, [20, 28, 43, 255])
// 头发与脸
fillRect(72, 58, 184, 130, [107, 75, 62, 255])
fillRect(64, 90, 84, 150, [107, 75, 62, 255])
fillRect(172, 90, 192, 150, [107, 75, 62, 255])
fillRect(84, 96, 172, 208, [242, 201, 160, 255])
// 眼睛
fillRect(104, 136, 120, 154, [43, 43, 58, 255])
fillRect(136, 136, 152, 154, [43, 43, 58, 255])
// 嘴
fillRect(112, 172, 144, 180, [201, 106, 106, 255])
// 品牌三色点
fillRect(196, 196, 212, 212, [79, 134, 198, 255])
fillRect(212, 212, 228, 228, [242, 166, 184, 255])
fillRect(180, 212, 196, 228, [242, 193, 78, 255])

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

const raw = Buffer.alloc((SIZE * 4 + 1) * SIZE)
for (let y = 0; y < SIZE; y += 1) {
  raw[y * (SIZE * 4 + 1)] = 0
  pixels.copy(raw, y * (SIZE * 4 + 1) + 1, y * SIZE * 4, (y + 1) * SIZE * 4)
}

const ihdr = Buffer.alloc(13)
ihdr.writeUInt32BE(SIZE, 0)
ihdr.writeUInt32BE(SIZE, 4)
ihdr[8] = 8
ihdr[9] = 6
ihdr[10] = 0
ihdr[11] = 0
ihdr[12] = 0

const png = Buffer.concat([
  Buffer.from([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a]),
  chunk('IHDR', ihdr),
  chunk('IDAT', zlib.deflateSync(raw, { level: 9 })),
  chunk('IEND', Buffer.alloc(0))
])

const icoHeader = Buffer.alloc(6)
icoHeader.writeUInt16LE(0, 0)
icoHeader.writeUInt16LE(1, 2)
icoHeader.writeUInt16LE(1, 4)

const icoEntry = Buffer.alloc(16)
icoEntry[0] = 0 // 256
icoEntry[1] = 0 // 256
icoEntry.writeUInt16LE(1, 4)
icoEntry.writeUInt16LE(32, 6)
icoEntry.writeUInt32LE(png.length, 8)
icoEntry.writeUInt32LE(22, 12)

const outDir = path.join(process.cwd(), 'electron', 'assets')
fs.mkdirSync(outDir, { recursive: true })
fs.writeFileSync(path.join(outDir, 'icon.png'), png)
fs.writeFileSync(path.join(outDir, 'icon.ico'), Buffer.concat([icoHeader, icoEntry, png]))
console.log('icon generated:', path.join(outDir, 'icon.ico'))
