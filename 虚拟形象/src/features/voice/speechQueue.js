import { ElMessage } from 'element-plus'
import { synthesizeSpeech } from '@/shared/api/tts'
import { useAvatarStore } from '@/shared/stores/avatar'
import {
  decodeAudioBuffer,
  decodePcm16,
  playDecodedBuffer,
  stopPlayback
} from './audioEngine'
import { buildVisemeTimeline } from './lipSyncTimeline'

const MAX_CONCURRENT_FETCHES = 2
const MAX_BUFFERED = 3
const ERROR_NOTICE_INTERVAL = 8000

let queue = []
let activeFetches = 0
let playing = false
let generation = 0
let lastErrorNotice = 0

function notifyError(error) {
  const now = Date.now()
  if (now - lastErrorNotice < ERROR_NOTICE_INTERVAL) return
  lastErrorNotice = now
  if (error?.name === 'AbortError') return
  ElMessage.warning(error?.message || '语音播放失败')
}

function applyActs(entry) {
  const avatar = useAvatarStore()
  for (const act of entry.acts || []) {
    if (act.emotion) avatar.playEmotion(act.emotion, 2400)
    if (act.motion) avatar.playMotion(act.motion, act.intensity ?? 0.8)
  }
}

function bufferedCount() {
  return queue.filter((entry) => entry.promise && !entry.played && !entry.cancelled).length
}

function startFetch(entry) {
  activeFetches++
  entry.controller = new AbortController()
  entry.promise = synthesizeSpeech({
    text: entry.text,
    speaker: entry.speaker,
    signal: entry.controller.signal
  })
    .then((result) => {
      entry.arrayBuffer = result.arrayBuffer
      entry.contentType = result.contentType
      entry.sampleRate = result.sampleRate
    })
    .catch((error) => {
      entry.error = error
    })
    .finally(() => {
      activeFetches = Math.max(0, activeFetches - 1)
      pumpFetches()
    })
}

function pumpFetches() {
  if (bufferedCount() >= MAX_BUFFERED) return
  for (const entry of queue) {
    if (entry.promise || entry.cancelled || entry.played) continue
    if (activeFetches >= MAX_CONCURRENT_FETCHES) break
    startFetch(entry)
  }
}

function removeEntry(entry) {
  const index = queue.indexOf(entry)
  if (index >= 0) queue.splice(index, 1)
  // 腾出缓冲位后，继续为后面的句子预取
  pumpFetches()
}

/**
 * 把一句语音加入播放队列。句子按入队顺序播放，最多 2 路并发合成、3 句缓冲。
 * acts 在该句音频真正开始时执行，保证动作与声音对齐。
 */
export function enqueueSentence({ index = 0, text, emotion, acts, speaker }) {
  if (!text) return
  queue.push({
    index,
    text,
    emotion,
    acts: acts || [],
    speaker,
    promise: null,
    controller: null,
    arrayBuffer: null,
    contentType: '',
    sampleRate: 24000,
    error: null,
    played: false,
    cancelled: false
  })
  pumpFetches()
  void processQueue()
}

async function processQueue() {
  if (playing) return
  pumpFetches()
  const gen = generation
  const entry = queue.find((item) => !item.played && !item.cancelled)
  if (!entry) return
  if (!entry.promise) {
    startFetch(entry)
  }

  playing = true
  try {
    await entry.promise
    if (gen !== generation || entry.cancelled) return

    if (entry.error || !entry.arrayBuffer) {
      notifyError(entry.error)
      applyActs(entry)
      entry.played = true
      removeEntry(entry)
      return
    }

    const isPcm = /L16|pcm/i.test(entry.contentType || '')
    const decoded = isPcm
      ? await decodePcm16(entry.arrayBuffer, entry.sampleRate, 1)
      : await decodeAudioBuffer(entry.arrayBuffer)
    if (gen !== generation || entry.cancelled) return

    applyActs(entry)
    const avatar = useAvatarStore()
    const timeline = buildVisemeTimeline(entry.text, decoded.duration * 1000)
    // Live2D 用同一段音频驱动内部口型；像素驱动会忽略这个方法
    avatar.inputAudio?.(entry.arrayBuffer)
    avatar.setSpeaking(true)

    await playDecodedBuffer(decoded, {
      onLevel: (level, elapsedMs) => {
        // 静音时闭嘴；有声音时让文本口型决定嘴形，振幅决定张口大小
        const viseme = level < 0.04 ? 'closed' : timeline.at(elapsedMs)
        avatar.setMouth(level, viseme)
      }
    })

    if (gen === generation) {
      avatar.setSpeaking(false)
    }
    entry.played = true
    removeEntry(entry)
  } catch (error) {
    if (gen === generation) {
      useAvatarStore().setSpeaking(false)
      notifyError(error)
      applyActs(entry)
    }
    entry.played = true
    removeEntry(entry)
  } finally {
    playing = false
    if (gen === generation) {
      void processQueue()
    }
  }
}

/** 停止并清空队列（切换会话、停止生成、关闭语音） */
export function stopSpeech() {
  generation++
  for (const entry of queue) {
    entry.cancelled = true
    entry.controller?.abort()
  }
  queue = []
  playing = false
  stopPlayback()
  useAvatarStore().setSpeaking(false)
}

export function getSpeechQueueSize() {
  return queue.length
}

export function isSpeaking() {
  return playing
}
