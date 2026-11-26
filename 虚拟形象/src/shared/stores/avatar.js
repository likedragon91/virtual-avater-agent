import { defineStore } from 'pinia'
import { computed, ref } from 'vue'
import {
  deleteLive2dModel as deleteLive2dModelApi,
  getAvatarCatalog,
  getAvatarConfig,
  listLive2dModels as listLive2dModelsApi,
  updateAvatarConfig,
  uploadLive2dModel as uploadLive2dModelApi,
  uploadStaticImage as uploadStaticImageApi
} from '@/shared/api/avatar'

/**
 * 形象状态中枢。
 *
 * PixelStage / Live2DStage 挂载时把各自的运行时注册进来；聊天、语音、设置页只调用本 store，
 * 不直接依赖渲染层。形象组件卸载后调用仍然安全（空操作）。
 */
export const useAvatarStore = defineStore('avatar', () => {
  const emotion = ref('neutral')
  const speaking = ref(false)
  const ready = ref(false)
  const avatarConfig = ref(null)
  const catalog = ref(null)
  const configLoading = ref(false)
  const configError = ref('')
  const avatarModels = ref([])
  const live2dModels = computed(() =>
    avatarModels.value.filter((model) => model.driver === 'live2d')
  )
  const imageModels = computed(() =>
    avatarModels.value.filter((model) => model.driver === 'image')
  )
  const modelLoading = ref(false)
  const modelUploading = ref(false)

  // 运行时是 Pixi / Live2D 对象，不能深度响应式；用普通变量保存
  let runtime = null
  // 衣橱实时预览配色：切换像素/Live2D 后重新挂载时继续生效
  let previewPaletteValue = null

  function currentPalette() {
    return previewPaletteValue || avatarConfig.value?.palette || null
  }

  function registerRuntime(instance) {
    runtime = instance
    ready.value = true
    if (emotion.value !== 'neutral') {
      instance.setEmotion?.(emotion.value)
    }
    instance.setSpeaking?.(speaking.value)
    const palette = currentPalette()
    if (palette) {
      instance.setPalette?.(palette)
    }
  }

  function unregisterRuntime(instance) {
    if (runtime !== instance) return
    runtime = null
    ready.value = false
    speaking.value = false
  }

  /** 基线情绪；会立即切到该表情 */
  function setEmotion(value) {
    emotion.value = value || 'neutral'
    runtime?.setEmotion?.(emotion.value)
  }

  /** 一次性表情（如收到消息、点击），超时后回基线 */
  function playEmotion(value, duration = 1600) {
    runtime?.playEmotion?.(value, duration)
  }

  function setSpeaking(value) {
    speaking.value = Boolean(value)
    runtime?.setSpeaking?.(speaking.value)
  }

  /** 口型由语音播放层高频调用，不写入 Vue ref，避免无谓的响应式开销 */
  function setMouth(open, viseme = 'closed') {
    runtime?.setMouth?.(open, viseme)
  }

  function playMotion(name, intensity = 0.8) {
    return runtime?.playMotion?.(name, intensity) || false
  }

  function triggerTouch(x, y) {
    runtime?.triggerTouch?.(x, y)
  }

  function blink() {
    runtime?.blink?.()
  }

  function setPointer(x, y, active = true) {
    runtime?.setPointer?.(x, y, active)
  }

  function clearPointer() {
    runtime?.clearPointer?.()
  }

  function setReducedMotion(value) {
    runtime?.setReducedMotion?.(value)
  }

  /** Live2D 内部口型：把 TTS 音频交给模型做唇形，不重复播放声音 */
  function inputAudio(arrayBuffer) {
    runtime?.inputAudio?.(arrayBuffer)
  }

  async function loadCatalog(force = false) {
    if (catalog.value && !force) return catalog.value
    const data = await getAvatarCatalog()
    catalog.value = data
    return data
  }

  async function loadAvatarConfig(force = false, options = {}) {
    if (configLoading.value) return avatarConfig.value
    if (avatarConfig.value && !force) return avatarConfig.value
    configLoading.value = true
    try {
      const data = await getAvatarConfig(options)
      avatarConfig.value = data
      configError.value = ''
      previewPaletteValue = null
      if (data?.palette) runtime?.setPalette?.(data.palette)
      return data
    } catch (error) {
      configError.value = error?.message || '加载形象配置失败'
      return null
    } finally {
      configLoading.value = false
    }
  }

  async function saveAvatarConfig({ driver, presetId, modelId, palette }) {
    const saved = await updateAvatarConfig({
      driver,
      presetId,
      modelId,
      palette,
      revision: avatarConfig.value?.revision ?? 0
    })
    avatarConfig.value = saved
    previewPaletteValue = null
    if (saved?.palette) runtime?.setPalette?.(saved.palette)
    // 通知悬浮球立即刷新当前形象
    if (typeof window !== 'undefined') {
      window.desktopBridge?.notifyAvatarUpdated?.()
    }
    return saved
  }

  /** 衣橱里的实时预览，不落库；切换舞台组件后仍会重新应用 */
  function previewPalette(palette) {
    previewPaletteValue = palette ? { ...palette } : null
    runtime?.setPalette?.(previewPaletteValue || {})
  }

  function restoreSavedPalette() {
    previewPaletteValue = null
    if (avatarConfig.value?.palette) {
      runtime?.setPalette?.(avatarConfig.value.palette)
    }
  }

  async function loadLive2dModels(force = false) {
    if (modelLoading.value) return avatarModels.value
    if (avatarModels.value.length && !force) return avatarModels.value
    modelLoading.value = true
    try {
      avatarModels.value = (await listLive2dModelsApi()) || []
      return avatarModels.value
    } finally {
      modelLoading.value = false
    }
  }

  async function uploadLive2dModel(file) {
    modelUploading.value = true
    try {
      const model = await uploadLive2dModelApi(file)
      avatarModels.value = [model, ...avatarModels.value.filter((item) => item.id !== model.id)]
      return model
    } finally {
      modelUploading.value = false
    }
  }

  async function uploadStaticImage(file) {
    modelUploading.value = true
    try {
      const model = await uploadStaticImageApi(file)
      avatarModels.value = [model, ...avatarModels.value.filter((item) => item.id !== model.id)]
      return model
    } finally {
      modelUploading.value = false
    }
  }

  async function removeLive2dModel(id) {
    await deleteLive2dModelApi(id)
    avatarModels.value = avatarModels.value.filter((item) => item.id !== id)
  }

  function reset() {
    setEmotion('neutral')
    setSpeaking(false)
    setMouth(0, 'closed')
  }

  return {
    emotion,
    speaking,
    ready,
    avatarConfig,
    catalog,
    configLoading,
    configError,
    avatarModels,
    live2dModels,
    imageModels,
    modelLoading,
    modelUploading,
    loadCatalog,
    loadAvatarConfig,
    saveAvatarConfig,
    previewPalette,
    restoreSavedPalette,
    loadLive2dModels,
    uploadLive2dModel,
    uploadStaticImage,
    removeLive2dModel,
    registerRuntime,
    unregisterRuntime,
    setEmotion,
    playEmotion,
    setSpeaking,
    setMouth,
    playMotion,
    triggerTouch,
    blink,
    setPointer,
    clearPointer,
    setReducedMotion,
    inputAudio,
    reset
  }
})
