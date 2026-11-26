import { beforeEach, describe, expect, it, vi } from 'vitest'

const mocks = vi.hoisted(() => {
  const avatar = {
    setSpeaking: vi.fn(),
    setMouth: vi.fn(),
    playEmotion: vi.fn(),
    playMotion: vi.fn(),
    inputAudio: vi.fn()
  }
  return {
    synthesizeSpeech: vi.fn(),
    decodeAudioBuffer: vi.fn(),
    decodePcm16: vi.fn(),
    playDecodedBuffer: vi.fn(),
    stopPlayback: vi.fn(),
    avatar
  }
})

vi.mock('@/shared/api/tts', () => ({
  synthesizeSpeech: mocks.synthesizeSpeech
}))

vi.mock('@/features/voice/audioEngine', () => ({
  decodeAudioBuffer: mocks.decodeAudioBuffer,
  decodePcm16: mocks.decodePcm16,
  playDecodedBuffer: mocks.playDecodedBuffer,
  stopPlayback: mocks.stopPlayback
}))

vi.mock('@/shared/stores/avatar', () => ({
  useAvatarStore: () => mocks.avatar
}))

import {
  enqueueSentence,
  getSpeechQueueSize,
  stopSpeech
} from '../speechQueue'

function tick() {
  return new Promise((resolve) => setTimeout(resolve, 30))
}

describe('speech queue', () => {
  beforeEach(() => {
    stopSpeech()
    mocks.synthesizeSpeech.mockReset().mockResolvedValue({
      arrayBuffer: new ArrayBuffer(8),
      contentType: 'audio/mpeg',
      sampleRate: 24000
    })
    mocks.decodeAudioBuffer.mockReset().mockResolvedValue({ duration: 1 })
    mocks.decodePcm16.mockReset()
    mocks.playDecodedBuffer.mockReset().mockResolvedValue()
    mocks.stopPlayback.mockReset()
    mocks.avatar.setSpeaking.mockReset()
    mocks.avatar.setMouth.mockReset()
    mocks.avatar.playEmotion.mockReset()
    mocks.avatar.playMotion.mockReset()
    mocks.avatar.inputAudio.mockReset()
  })

  it('synthesizes and plays every queued sentence', async () => {
    for (let i = 0; i < 6; i++) {
      enqueueSentence({ index: i, text: `第${i}句`, acts: [] })
    }

    await tick()

    expect(mocks.synthesizeSpeech).toHaveBeenCalledTimes(6)
    expect(mocks.playDecodedBuffer).toHaveBeenCalledTimes(6)
    expect(getSpeechQueueSize()).toBe(0)
  })

  it('applies acts when the sentence starts playing', async () => {
    enqueueSentence({
      index: 0,
      text: '你好。',
      acts: [{ emotion: 'joy', motion: 'nod', intensity: 0.8, sentenceIndex: 0 }]
    })

    await tick()

    expect(mocks.avatar.playEmotion).toHaveBeenCalledWith('joy', 2400)
    expect(mocks.avatar.playMotion).toHaveBeenCalledWith('nod', 0.8)
    expect(mocks.avatar.setSpeaking).toHaveBeenCalledWith(true)
  })

  it('clears the queue and stops playback', async () => {
    mocks.playDecodedBuffer.mockReturnValue(new Promise(() => {}))
    enqueueSentence({ index: 0, text: '第一句。', acts: [] })
    await tick()

    stopSpeech()

    expect(getSpeechQueueSize()).toBe(0)
    expect(mocks.stopPlayback).toHaveBeenCalled()
    expect(mocks.avatar.setSpeaking).toHaveBeenCalledWith(false)
  })
})
