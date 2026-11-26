<script setup>
import { computed, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import StageHost from '@/features/avatar/components/StageHost.vue'
import { useAvatarStore } from '@/shared/stores/avatar'
import { ASSET_BASE_URL } from '@/shared/api/config'

const avatarStore = useAvatarStore()

const MAX_MODEL_BYTES = 500 * 1024 * 1024
const MAX_IMAGE_BYTES = 20 * 1024 * 1024

const loading = ref(true)
const saving = ref(false)
const selectedPresetId = ref('')
const previewDriver = ref('pixel')
const previewModelId = ref(null)
const previewModelUrl = ref('')
const modelInputRef = ref(null)
const imageInputRef = ref(null)
const palette = reactive({
  hair: '#6b4b3e',
  top: '#4f86c6',
  bottom: '#37415f',
  shoes: '#2b2b33',
  skin: '#f2c9a0',
  background: '#101827'
})

const presets = computed(() => avatarStore.catalog?.presets || [])
const paletteFields = computed(() => avatarStore.catalog?.paletteFields || [])
const live2dModels = computed(() => avatarStore.live2dModels || [])
const imageModels = computed(() => avatarStore.imageModels || [])
const isLive2DSelected = computed(() => previewDriver.value === 'live2d')
const isImageSelected = computed(() => previewDriver.value === 'image')
const isPixelSelected = computed(() => previewDriver.value === 'pixel')

function applyPaletteToState(source) {
  if (!source) return
  for (const key of Object.keys(palette)) {
    if (typeof source[key] === 'string') {
      palette[key] = source[key]
    }
  }
}

function preview() {
  avatarStore.previewPalette({ ...palette })
}

function selectPreset(preset) {
  previewDriver.value = 'pixel'
  previewModelId.value = null
  previewModelUrl.value = ''
  selectedPresetId.value = preset.id
  applyPaletteToState(preset.palette)
  preview()
}

async function persistModelSelection() {
  if (previewModelId.value == null) return
  try {
    await avatarStore.saveAvatarConfig({
      driver: previewDriver.value,
      presetId: selectedPresetId.value,
      modelId: previewModelId.value,
      palette: { ...palette }
    })
    ElMessage.success('形象已保存，聊天页会使用该形象')
  } catch (error) {
    ElMessage.error(error?.message || '保存形象失败，请点击底部「保存形象」重试')
  }
}

async function selectModel(model) {
  previewDriver.value = 'live2d'
  previewModelId.value = model.id
  previewModelUrl.value = ''
  await persistModelSelection()
}

async function selectImage(model) {
  previewDriver.value = 'image'
  previewModelId.value = model.id
  previewModelUrl.value = model.url || ''
  await persistModelSelection()
}

function onColorChange() {
  // 改像素配色时自动切回像素预览
  previewDriver.value = 'pixel'
  previewModelId.value = null
  previewModelUrl.value = ''
  preview()
}

function assetUrl(path) {
  if (!path) return ''
  return /^https?:\/\//.test(path) ? path : `${ASSET_BASE_URL}${path}`
}

function formatSize(size) {
  const value = Number(size) || 0
  if (value >= 1024 * 1024) return `${(value / 1024 / 1024).toFixed(1)} MB`
  if (value >= 1024) return `${Math.round(value / 1024)} KB`
  return `${value} B`
}

async function load() {
  loading.value = true
  try {
    const [catalogData, current] = await Promise.all([
      avatarStore.loadCatalog(true),
      avatarStore.loadAvatarConfig(true),
      avatarStore.loadLive2dModels(true)
    ])
    selectedPresetId.value = current?.presetId || catalogData?.defaultPresetId || ''
    previewDriver.value = ['live2d', 'image'].includes(current?.driver)
      ? current.driver
      : 'pixel'
    previewModelId.value = current?.modelId || null
    previewModelUrl.value = current?.modelUrl || ''
    if (previewDriver.value === 'image' && !previewModelUrl.value) {
      const stored = avatarStore.avatarModels.find((model) => model.id === previewModelId.value)
      previewModelUrl.value = stored?.url || ''
    }
    applyPaletteToState(current?.palette || catalogData?.defaultPalette)
    preview()
  } catch (error) {
    ElMessage.error(error?.message || '加载形象配置失败')
  } finally {
    loading.value = false
  }
}

async function handleModelFile(event) {
  const file = event.target?.files?.[0]
  if (event.target) event.target.value = ''
  if (!file) return
  if (!/\.zip$/i.test(file.name)) {
    ElMessage.warning('请选择 Live2D 模型 zip')
    return
  }
  if (file.size > MAX_MODEL_BYTES) {
    ElMessage.warning('模型 zip 不能超过 500MB')
    return
  }
  try {
    const model = await avatarStore.uploadLive2dModel(file)
    await selectModel(model)
    ElMessage.success('Live2D 模型已导入')
  } catch (error) {
    ElMessage.error(error?.message || '导入 Live2D 模型失败')
  }
}

async function handleImageFile(event) {
  const file = event.target?.files?.[0]
  if (event.target) event.target.value = ''
  if (!file) return
  const looksLikeImage = /^image\//.test(file.type)
    || /\.(png|jpe?g|webp|gif)$/i.test(file.name)
  if (!looksLikeImage) {
    ElMessage.warning('请选择 PNG / JPEG / WebP / GIF 图片')
    return
  }
  if (file.size > MAX_IMAGE_BYTES) {
    ElMessage.warning('图片不能超过 20MB')
    return
  }
  try {
    const model = await avatarStore.uploadStaticImage(file)
    await selectImage(model)
    ElMessage.success('静态立绘已导入')
  } catch (error) {
    ElMessage.error(error?.message || '导入静态立绘失败')
  }
}

async function removeModel(model) {
  if (avatarStore.avatarConfig?.modelId === model.id) {
    ElMessage.warning('请先切换到其他形象并保存，再删除该模型')
    return
  }
  try {
    await ElMessageBox.confirm(`删除「${model.name}」？文件会一并删除。`, '删除模型', {
      type: 'warning',
      confirmButtonText: '删除',
      cancelButtonText: '取消'
    })
  } catch {
    return
  }
  try {
    await avatarStore.removeLive2dModel(model.id)
    if (previewModelId.value === model.id) {
      previewDriver.value = 'pixel'
      previewModelId.value = null
      previewModelUrl.value = ''
    }
    ElMessage.success('模型已删除')
  } catch (error) {
    ElMessage.error(error?.message || '删除模型失败')
  }
}

async function handleSave() {
  if (previewDriver.value === 'live2d' && previewModelId.value == null) {
    ElMessage.warning('请选择一个 Live2D 模型')
    return
  }
  if (previewDriver.value === 'image' && previewModelId.value == null) {
    ElMessage.warning('请选择一张静态立绘')
    return
  }
  saving.value = true
  try {
    await avatarStore.saveAvatarConfig({
      driver: previewDriver.value,
      presetId: selectedPresetId.value,
      modelId: isPixelSelected.value ? null : previewModelId.value,
      palette: { ...palette }
    })
    ElMessage.success('形象已保存')
  } catch (error) {
    ElMessage.error(error?.message || '保存形象失败')
    if (String(error?.message || '').includes('其他窗口')) {
      await load()
    }
  } finally {
    saving.value = false
  }
}

function handleReset() {
  const defaultId = avatarStore.catalog?.defaultPresetId
  const preset = presets.value.find((item) => item.id === defaultId)
  if (preset) {
    selectPreset(preset)
  } else {
    previewDriver.value = 'pixel'
    previewModelId.value = null
    previewModelUrl.value = ''
    applyPaletteToState(avatarStore.catalog?.defaultPalette)
    preview()
  }
}

onMounted(load)

onBeforeUnmount(() => {
  // 未保存的预览色不要带到聊天页
  avatarStore.restoreSavedPalette()
})
</script>

<template>
  <div v-loading="loading" class="wardrobe-view">
    <section class="preview-card">
      <header class="section-head">
        <span class="section-title">形象预览</span>
        <span class="section-sub">
          {{ isLive2DSelected ? 'Live2D 模型预览' : isImageSelected ? '静态立绘预览' : '像素形象预览' }}，保存后同步到聊天页
        </span>
      </header>
      <div class="preview-stage" :class="{ 'is-checker': !isPixelSelected }">
        <StageHost
          fill
          :driver="previewDriver"
          :model-id="previewModelId"
          :model-url="previewModelUrl"
        />
      </div>
    </section>

    <section class="config-panel">
      <el-card shadow="never">
        <template #header>
          <div class="card-header">
            <span class="card-title">Live2D 形象</span>
            <span class="card-sub">Cubism 3/4/5 zip，单文件不超过 500MB</span>
          </div>
        </template>

        <div class="live2d-actions">
          <el-button :loading="avatarStore.modelUploading" @click="modelInputRef?.click()">
            导入 Live2D zip
          </el-button>
          <input
            ref="modelInputRef"
            type="file"
            accept=".zip,application/zip"
            hidden
            @change="handleModelFile"
          />
        </div>

        <div v-if="live2dModels.length" class="model-grid">
          <div
            v-for="model in live2dModels"
            :key="model.id"
            class="model-item"
            :class="{ active: isLive2DSelected && previewModelId === model.id }"
          >
            <div class="model-main">
              <span class="model-name">{{ model.name }}</span>
              <span class="model-meta">{{ formatSize(model.size) }} · {{ model.createdAt }}</span>
            </div>
            <div class="model-actions">
              <el-button size="small" type="primary" plain @click="selectModel(model)">
                使用
              </el-button>
              <el-button size="small" text type="danger" @click="removeModel(model)">
                删除
              </el-button>
            </div>
          </div>
        </div>
        <el-empty v-else description="还没有导入 Live2D 模型" :image-size="56" />

        <p class="card-hint">
          模型保存在服务器 uploads 目录；未配置 Cubism Core 时请确认
          <code>public/live2d/live2dcubismcore.min.js</code> 存在。
        </p>
      </el-card>

      <el-card shadow="never">
        <template #header>
          <div class="card-header">
            <span class="card-title">静态立绘（单图）</span>
            <span class="card-sub">PNG / JPEG / WebP / GIF，≤20MB；没有 Live2D 骨骼与口型</span>
          </div>
        </template>

        <div class="live2d-actions">
          <el-button :loading="avatarStore.modelUploading" @click="imageInputRef?.click()">
            上传静态立绘
          </el-button>
          <input
            ref="imageInputRef"
            type="file"
            accept="image/png,image/jpeg,image/webp,image/gif"
            hidden
            @change="handleImageFile"
          />
        </div>

        <p class="card-hint">
          建议使用透明背景 PNG；纯色（白底、黑底等）背景会自动去除。
        </p>

        <div v-if="imageModels.length" class="model-grid">
          <div
            v-for="model in imageModels"
            :key="model.id"
            class="model-item"
            :class="{ active: isImageSelected && previewModelId === model.id }"
          >
            <img v-if="model.url" :src="assetUrl(model.url)" class="model-thumb" alt="" />
            <div class="model-main">
              <span class="model-name">{{ model.name }}</span>
              <span class="model-meta">{{ formatSize(model.size) }} · {{ model.createdAt }}</span>
            </div>
            <div class="model-actions">
              <el-button size="small" type="primary" plain @click="selectImage(model)">
                使用
              </el-button>
              <el-button size="small" text type="danger" @click="removeModel(model)">
                删除
              </el-button>
            </div>
          </div>
        </div>
        <el-empty v-else description="还没有上传静态立绘" :image-size="56" />
      </el-card>

      <el-card shadow="never">
        <template #header>
          <span class="card-title">像素预设</span>
        </template>
        <div class="preset-grid">
          <button
            v-for="preset in presets"
            :key="preset.id"
            type="button"
            class="preset-item"
            :class="{ active: isPixelSelected && selectedPresetId === preset.id }"
            @click="selectPreset(preset)"
          >
            <span class="preset-name">{{ preset.name }}</span>
            <span class="preset-desc">{{ preset.description }}</span>
            <span class="swatches">
              <i
                v-for="(color, key) in preset.palette"
                :key="key"
                :style="{ backgroundColor: color }"
              />
            </span>
          </button>
        </div>
      </el-card>

      <el-card shadow="never">
        <template #header>
          <div class="card-header">
            <span class="card-title">像素配色</span>
            <span class="card-sub">仅在像素形象下生效；改色会自动切回像素预览</span>
          </div>
        </template>
        <el-form label-width="72px" class="palette-form">
          <el-form-item
            v-for="field in paletteFields"
            :key="field.key"
            :label="field.label"
          >
            <el-color-picker
              v-model="palette[field.key]"
              @change="onColorChange"
              @active-change="onColorChange"
            />
          </el-form-item>
        </el-form>
      </el-card>

      <div class="actions">
        <el-button @click="handleReset">恢复默认</el-button>
        <el-button type="primary" :loading="saving" @click="handleSave">保存形象</el-button>
      </div>
    </section>
  </div>
</template>

<style scoped>
.wardrobe-view {
  display: grid;
  grid-template-columns: minmax(280px, 420px) minmax(0, 1fr);
  gap: 16px;
  align-items: start;
}

.preview-card {
  display: flex;
  flex-direction: column;
  gap: 12px;
  height: calc(100vh - 120px);
  min-height: 520px;
  padding: 16px;
  background: #fff;
  border: 1px solid var(--el-border-color-light);
  border-radius: 8px;
}

.section-head {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.section-title {
  font-weight: 600;
}

.section-sub {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.preview-stage {
  flex: 1;
  min-height: 0;
  background-color: #f6f7f9;
  border-radius: 6px;
  overflow: hidden;
}

/* 透明立绘用棋盘格，避免深色底板被看成黑色边框 */
.preview-stage.is-checker {
  background-color: #f8fafc;
  background-image:
    linear-gradient(45deg, #e9ebee 25%, transparent 25%),
    linear-gradient(-45deg, #e9ebee 25%, transparent 25%),
    linear-gradient(45deg, transparent 75%, #e9ebee 75%),
    linear-gradient(-45deg, transparent 75%, #e9ebee 75%);
  background-size: 16px 16px;
  background-position: 0 0, 0 8px, 8px -8px, -8px 0;
}

.config-panel {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.card-title {
  font-weight: 600;
}

.card-sub {
  margin-left: 8px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.card-hint {
  margin: 12px 0 0;
  font-size: 12px;
  line-height: 1.6;
  color: var(--el-text-color-secondary);
}

.card-hint code {
  padding: 1px 4px;
  border-radius: 4px;
  background: var(--el-fill-color-light);
}

.live2d-actions {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 12px;
}

.model-grid {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.model-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 10px 12px;
  border: 1px solid var(--el-border-color-light);
  border-radius: 8px;
}

.model-item.active {
  background: var(--el-color-primary-light-9);
  border-color: var(--el-color-primary);
}

.model-main {
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-width: 0;
}

.model-name {
  font-weight: 500;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.model-meta {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.model-actions {
  display: flex;
  flex-shrink: 0;
  gap: 4px;
}

.model-thumb {
  width: 44px;
  height: 44px;
  object-fit: contain;
  border-radius: 6px;
  background: var(--el-fill-color-light);
  pointer-events: none;
}

.preset-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(180px, 1fr));
  gap: 10px;
}

.preset-item {
  display: flex;
  flex-direction: column;
  gap: 6px;
  padding: 12px;
  text-align: left;
  background: #fff;
  border: 1px solid var(--el-border-color-light);
  border-radius: 8px;
  cursor: pointer;
  transition: border-color 0.15s ease, background-color 0.15s ease;
}

.preset-item:hover {
  border-color: var(--el-color-primary-light-5);
}

.preset-item.active {
  background: var(--el-color-primary-light-9);
  border-color: var(--el-color-primary);
}

.preset-name {
  font-weight: 600;
}

.preset-desc {
  font-size: 12px;
  line-height: 1.4;
  color: var(--el-text-color-secondary);
}

.swatches {
  display: flex;
  gap: 4px;
}

.swatches i {
  width: 16px;
  height: 16px;
  border: 1px solid rgba(0, 0, 0, 0.08);
  border-radius: 4px;
}

.palette-form {
  max-width: 420px;
}

.actions {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
}

@media (max-width: 900px) {
  .wardrobe-view {
    grid-template-columns: minmax(0, 1fr);
  }

  .preview-card {
    height: 380px;
    min-height: 0;
  }
}
</style>
