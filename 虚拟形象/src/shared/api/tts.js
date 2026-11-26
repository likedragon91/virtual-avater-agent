import { API_BASE_URL } from './config'
import { request } from './request'
import { authorizedFetch } from './authFetch'

const TTS_BASE = `${API_BASE_URL}/tts`

export class TtsError extends Error {
  constructor(code, message) {
    super(message || '语音合成失败')
    this.name = 'TtsError'
    this.code = code || 'TTS_ERROR'
  }
}

/** 语音配置与可用音色；不包含密钥或上游地址 */
export async function getTtsConfig() {
  const res = await request(`${TTS_BASE}/config`, { method: 'GET' })
  if (!res || String(res.code) !== '200') {
    throw new TtsError(res?.code, res?.msg || '加载语音配置失败')
  }
  return res.data
}

/**
 * 合成一句语音。
 *
 * @param {{ text: string, speaker?: string, speed?: number, signal?: AbortSignal }} options
 * @returns {Promise<{ arrayBuffer: ArrayBuffer, contentType: string, cacheHit: boolean, sampleRate: number }>}
 */
export async function synthesizeSpeech({ text, speaker, speed, signal }) {
  const response = await authorizedFetch(
    `${TTS_BASE}/synthesize`,
    {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        Accept: 'audio/mpeg, audio/L16, audio/wav, audio/ogg, application/json'
      },
      body: JSON.stringify({ text, speaker, speed })
    },
    signal
  )

  const contentType = response.headers.get('content-type') || ''
  if (!response.ok || contentType.includes('application/json')) {
    let payload = null
    try {
      payload = await response.json()
    } catch {
      payload = null
    }
    throw new TtsError(
      payload?.code || `HTTP_${response.status}`,
      payload?.msg || '语音合成失败，请稍后重试'
    )
  }

  return {
    arrayBuffer: await response.arrayBuffer(),
    contentType,
    cacheHit: response.headers.get('X-TTS-Cache') === 'HIT',
    sampleRate: Number(response.headers.get('X-Audio-Sample-Rate')) || 24000
  }
}
