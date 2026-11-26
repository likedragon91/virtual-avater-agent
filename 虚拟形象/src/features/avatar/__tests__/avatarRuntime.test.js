import { describe, expect, it, vi } from 'vitest'
import { createPose } from '../pose'
import { sampleTrack } from '../motionPresets'
import { createPluginPipeline } from '../runtime/pluginPipeline'
import { createEmotionPlugin } from '../runtime/plugins/emotionPlugin'
import { createLipSyncPlugin } from '../runtime/plugins/lipSyncPlugin'
import { createAvatarRuntime } from '../runtime/createAvatarRuntime'

function fakeLayers() {
  const part = () => ({ pivot: { set: vi.fn() }, x: 0, y: 0, rotation: 0 })
  return {
    character: part(),
    head: part(),
    eyes: part(),
    hairBack: part(),
    hairFront: part(),
    setFace: vi.fn()
  }
}

describe('motion presets', () => {
  it('interpolates keyframes and clamps progress', () => {
    const keys = [[0, 0], [0.5, 10], [1, 0]]
    expect(sampleTrack(keys, 0)).toBe(0)
    expect(sampleTrack(keys, 0.25)).toBe(5)
    expect(sampleTrack(keys, 0.75)).toBe(5)
    expect(sampleTrack(keys, 2)).toBe(0)
  })
})

describe('plugin pipeline', () => {
  it('runs plugins in pre/post/final order', () => {
    const calls = []
    const pipeline = createPluginPipeline()
    pipeline.register({ apply: () => calls.push('final') }, 'final')
    pipeline.register({ apply: () => calls.push('pre') }, 'pre')
    pipeline.register({ apply: () => calls.push('post') }, 'post')

    pipeline.run({})
    expect(calls).toEqual(['pre', 'post', 'final'])
  })
})

describe('emotion plugin', () => {
  it('applies joy offsets', () => {
    const pose = createPose()
    const state = { emotion: 'joy', speaking: false, oneShot: null }
    createEmotionPlugin().apply({ pose, state, now: 0, dt: 0.016 })

    expect(pose.mouthForm).toBeGreaterThan(0)
    expect(pose.blush).toBeGreaterThan(0)
    expect(pose.eyeOpen).toBeLessThan(1)
    expect(state.effectiveEmotion).toBe('joy')
  })

  it('does not force an open mouth while speaking', () => {
    const pose = createPose()
    const state = { emotion: 'surprise', speaking: true, oneShot: null }
    createEmotionPlugin().apply({ pose, state, now: 0, dt: 0.016 })
    expect(pose.mouthOpen).toBe(0)
  })
})

describe('lip sync plugin', () => {
  it('opens while speaking and closes after release', () => {
    const plugin = createLipSyncPlugin()
    const pose = createPose()
    const state = { speaking: true, mouthTarget: 0.8, viseme: 'open' }

    plugin.apply({ pose, state, now: 1000, dt: 0.016 })
    expect(pose.mouthOpen).toBeCloseTo(0.8)
    expect(pose.viseme).toBe('open')

    state.speaking = false
    pose.mouthOpen = 0
    plugin.apply({ pose, state, now: 1100, dt: 0.016 })
    expect(pose.mouthOpen).toBeGreaterThan(0)

    pose.mouthOpen = 0
    plugin.apply({ pose, state, now: 1300, dt: 0.016 })
    expect(pose.mouthOpen).toBe(0)

    pose.mouthOpen = 0
    plugin.apply({ pose, state, now: 1800, dt: 0.016 })
    expect(pose.mouthOpen).toBe(0)
    expect(pose.viseme).toBe('closed')
  })
})

describe('avatar runtime', () => {
  it('renders initial state and exposes controls', () => {
    const layers = fakeLayers()
    const ticker = { add: vi.fn(), remove: vi.fn() }
    const runtime = createAvatarRuntime({ app: { ticker }, layers })

    expect(ticker.add).toHaveBeenCalledTimes(1)
    expect(layers.setFace).toHaveBeenCalled()
    expect(layers.setFace.mock.calls[0][0].emotion).toBe('neutral')

    runtime.setEmotion('joy')
    expect(runtime.getState().emotion).toBe('joy')

    expect(runtime.playMotion('nod', 0.7)).toBe(true)
    expect(runtime.playMotion('backflip')).toBe(false)
    expect(runtime.getState().act.name).toBe('nod')

    runtime.setSpeaking(true)
    runtime.setMouth(0.6, 'wide')
    expect(runtime.getState().speaking).toBe(true)
    expect(runtime.getState().mouthTarget).toBeCloseTo(0.6)
    expect(runtime.getState().viseme).toBe('wide')

    runtime.triggerTouch(0.5, 0.4)
    expect(runtime.getState().touch).not.toBeNull()

    runtime.destroy()
    expect(ticker.remove).toHaveBeenCalledTimes(1)
  })
})
