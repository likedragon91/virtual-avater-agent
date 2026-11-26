/**
 * 语音播放的 Web Audio 图：
 *
 *   AudioBufferSource → Analyser → Gain(音量) → destination
 *
 * 口型取 Analyser（音量之前）的包络，所以音量调到 0 时嘴仍会动。
 * AudioContext 必须在用户手势中创建/恢复，调用方通过 primeAudio() 触发。
 */
let context = null
let analyser = null
let gainNode = null
let sourceNode = null
let rafId = 0
let currentVolume = 0.9
let stopHandler = null

export async function ensureAudioContext() {
  if (!context) {
    const AudioContextClass = window.AudioContext || window.webkitAudioContext
    if (!AudioContextClass) {
      throw new Error('当前浏览器不支持 Web Audio')
    }
    context = new AudioContextClass()
    analyser = context.createAnalyser()
    analyser.fftSize = 256
    gainNode = context.createGain()
    gainNode.gain.value = currentVolume
    analyser.connect(gainNode)
    gainNode.connect(context.destination)
  }
  if (context.state === 'suspended') {
    await context.resume()
  }
  return context
}

/** 在用户手势里调用，解锁移动端自动播放限制 */
export async function primeAudio() {
  try {
    await ensureAudioContext()
    return true
  } catch {
    return false
  }
}

export function setVolume(value) {
  currentVolume = Math.max(0, Math.min(1, Number(value) || 0))
  if (gainNode && context) {
    gainNode.gain.setTargetAtTime(currentVolume, context.currentTime, 0.01)
  }
}

export async function decodeAudioBuffer(arrayBuffer) {
  const ctx = await ensureAudioContext()
  // Safari 对已被消费的 ArrayBuffer 比较敏感，传入副本
  return ctx.decodeAudioData(arrayBuffer.slice(0))
}

/**
 * 原始 PCM 16-bit little-endian（火山格式 pcm）手写解码。
 * 用于 tts.format=pcm 的低延迟/无容器格式。
 */
export async function decodePcm16(arrayBuffer, sampleRate = 24000, channels = 1) {
  const ctx = await ensureAudioContext()
  const view = new DataView(arrayBuffer)
  const frames = Math.floor(Math.floor(arrayBuffer.byteLength / 2) / channels)
  const buffer = ctx.createBuffer(Math.max(1, channels), Math.max(1, frames), sampleRate || 24000)
  for (let channel = 0; channel < buffer.numberOfChannels; channel++) {
    const data = buffer.getChannelData(channel)
    for (let frame = 0; frame < frames; frame++) {
      const offset = (frame * channels + channel) * 2
      data[frame] = view.getInt16(offset, true) / 32768
    }
  }
  return buffer
}

export function getAnalyserLevel() {
  if (!analyser) return 0
  const data = new Uint8Array(analyser.fftSize)
  analyser.getByteTimeDomainData(data)
  let sum = 0
  for (let i = 0; i < data.length; i++) {
    const value = (data[i] - 128) / 128
    sum += value * value
  }
  const rms = Math.sqrt(sum / data.length)
  return Math.min(1, rms * 4)
}

/**
 * 播放已解码的 AudioBuffer，返回播放结束的 Promise。
 * onLevel(level, elapsedMs)：level 是 0..1 包络，elapsedMs 用于对齐文本口型时间线。
 */
export async function playDecodedBuffer(buffer, { onLevel, onEnded } = {}) {
  const ctx = await ensureAudioContext()
  stopPlayback({ silent: true })

  return new Promise((resolve) => {
    const source = ctx.createBufferSource()
    source.buffer = buffer
    source.connect(analyser)
    sourceNode = source
    const startedAt = ctx.currentTime

    let finished = false
    const finish = () => {
      if (finished) return
      finished = true
      if (rafId) {
        cancelAnimationFrame(rafId)
        rafId = 0
      }
      if (sourceNode === source) sourceNode = null
      stopHandler = null
      onEnded?.()
      resolve()
    }

    const loop = () => {
      if (finished) return
      const elapsedMs = (ctx.currentTime - startedAt) * 1000
      onLevel?.(getAnalyserLevel(), elapsedMs)
      rafId = requestAnimationFrame(loop)
    }

    stopHandler = () => {
      try {
        source.stop()
      } catch {
        // 已自然结束
      }
      finish()
    }

    source.onended = finish
    source.start()
    loop()
  })
}

/** 解码并播放；预览等简单场景用 */
export async function playBuffer(arrayBuffer, options = {}) {
  const buffer = await decodeAudioBuffer(arrayBuffer)
  return playDecodedBuffer(buffer, options)
}

export function stopPlayback({ silent = false } = {}) {
  if (rafId) {
    cancelAnimationFrame(rafId)
    rafId = 0
  }
  if (stopHandler) {
    const handler = stopHandler
    stopHandler = null
    handler()
    return
  }
  if (!silent && sourceNode) {
    try {
      sourceNode.stop()
    } catch {
      // 已结束
    }
    sourceNode = null
  }
}
