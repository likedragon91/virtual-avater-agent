import { computed, ref } from 'vue'
import { defineStore } from 'pinia'
import { getTtsConfig, synthesizeSpeech } from '@/shared/api/tts'
import { ensureAudioContext, playBuffer, setVolume as applyVolume } from '@/features/voice/audioEngine'
import { stopSpeech } from '@/features/voice/speechQueue'

const ENABLED_KEY = 'ui:voice:enabled'
const VOLUME_KEY = 'ui:voice:volume'
const SPEAKER_KEY = 'ui:voice:speaker'
const PREVIEW_TEXT = '你好呀，今天也辛苦了。'

function readStorage(key, fallback) {
  try {
    return localStorage.getItem(key) ?? fallback
  } catch {
    return fallback
  }
}

function writeStorage(key, value) {
  try {
    localStorage.setItem(key, String(value))
  } catch {
    // 隐私模式下可能不可写，忽略
  }
}

/**
 * 语音偏好与服务端语音配置。
 *
 * 是否真正播放由 chat store 判断：voice.enabled && voice.available。
 * 关闭语音只影响播放，形象表情/动作仍由 act 事件驱动。
 */
export const useVoiceStore = defineStore('voice', () => {
  const config = ref(null)
  const loading = ref(false)
  const loadError = ref('')
  const enabled = ref(readStorage(ENABLED_KEY, '0') === '1')
  const volume = ref(Math.max(0, Math.min(1, Number(readStorage(VOLUME_KEY, '0.9')) || 0.9)))
  const speaker = ref(readStorage(SPEAKER_KEY, ''))

  const available = computed(() => Boolean(config.value?.enabled))
  const configured = computed(() => Boolean(config.value?.configured))
  const voices = computed(() => config.value?.voices || [])
  const defaultSpeaker = computed(() => config.value?.defaultSpeaker || '')
  const quotaText = computed(() => {
    const quota = Number(config.value?.dailyCharQuota) || 0
    if (quota <= 0) return ''
    const remaining = Number(config.value?.remainingChars)
    return Number.isFinite(remaining) && remaining >= 0
      ? `今日剩余 ${remaining} 字`
      : ''
  })

  async function loadConfig(force = false) {
    if (loading.value || (config.value && !force)) return
    loading.value = true
    try {
      const data = await getTtsConfig()
      config.value = data
      loadError.value = ''
      if (!speaker.value) {
        speaker.value = data?.defaultSpeaker || ''
      }
    } catch (error) {
      loadError.value = error?.message || '加载语音配置失败'
    } finally {
      loading.value = false
    }
  }

  /** 必须由用户手势触发（点击开关/发送），用于解锁移动端 AudioContext */
  async function enableFromGesture() {
    if (!available.value) return false
    await ensureAudioContext().catch(() => {})
    enabled.value = true
    writeStorage(ENABLED_KEY, '1')
    applyVolume(volume.value)
    return true
  }

  function setEnabled(value) {
    enabled.value = Boolean(value)
    writeStorage(ENABLED_KEY, enabled.value ? '1' : '0')
    if (!enabled.value) {
      stopSpeech()
    }
  }

  async function toggle() {
    if (!available.value) return false
    if (enabled.value) {
      setEnabled(false)
      return true
    }
    return enableFromGesture()
  }

  function setVolume(value) {
    volume.value = Math.max(0, Math.min(1, Number(value) || 0))
    writeStorage(VOLUME_KEY, volume.value)
    applyVolume(volume.value)
  }

  function setSpeaker(value) {
    speaker.value = value || ''
    writeStorage(SPEAKER_KEY, speaker.value)
  }

  async function preview() {
    if (!available.value) {
      throw new Error('语音合成入口未开放')
    }
    if (!configured.value) {
      throw new Error('语音合成未配置密钥，请先填写 VOLC_TTS_API_KEY')
    }
    await ensureAudioContext()
    const result = await synthesizeSpeech({
      text: PREVIEW_TEXT,
      speaker: speaker.value || undefined
    })
    await playBuffer(result.arrayBuffer, {})
  }

  return {
    config,
    loading,
    loadError,
    enabled,
    volume,
    speaker,
    available,
    configured,
    voices,
    defaultSpeaker,
    quotaText,
    loadConfig,
    enableFromGesture,
    setEnabled,
    toggle,
    setVolume,
    setSpeaker,
    preview
  }
})
