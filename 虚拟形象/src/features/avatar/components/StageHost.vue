<script setup>
import { computed, defineAsyncComponent } from 'vue'
import PixelStage from './PixelStage.vue'
import StaticImageStage from './StaticImageStage.vue'
import { useAvatarStore } from '@/shared/stores/avatar'

// Live2D 渲染器和 Cubism 相关代码较大，仅在选中 Live2D 时加载
const Live2DStage = defineAsyncComponent(() => import('./Live2DStage.vue'))

const props = defineProps({
  fill: {
    type: Boolean,
    default: false
  },
  /** 衣橱预览时覆盖已保存配置：pixel / live2d */
  driver: {
    type: String,
    default: ''
  },
  /** 衣橱预览时覆盖已保存模型 */
  modelId: {
    type: [Number, String],
    default: null
  },
  /** driver=image 时的图片地址 */
  modelUrl: {
    type: String,
    default: ''
  },
  /** 悬浮窗：像素舞台去掉背景与边框 */
  transparent: {
    type: Boolean,
    default: false
  },
  /** 小窗模式：允许像素舞台缩放到 1× 以下 */
  compact: {
    type: Boolean,
    default: false
  },
  /** 静默鉴权：失败时不跳登录页（悬浮窗使用） */
  silentAuth: {
    type: Boolean,
    default: false
  },
  /** 大头模式：静态图裁到上半身 */
  head: {
    type: Boolean,
    default: false
  }
})

const emit = defineEmits(['ready', 'error'])
const avatarStore = useAvatarStore()

const resolvedDriver = computed(
  () => props.driver || avatarStore.avatarConfig?.driver || 'pixel'
)
const resolvedModelId = computed(() =>
  props.modelId != null ? props.modelId : avatarStore.avatarConfig?.modelId
)
const resolvedModelUrl = computed(
  () => props.modelUrl || avatarStore.avatarConfig?.modelUrl || ''
)
const useLive2D = computed(
  () => resolvedDriver.value === 'live2d' && resolvedModelId.value != null
)
const useStaticImage = computed(
  () => resolvedDriver.value === 'image' && Boolean(resolvedModelUrl.value)
)
</script>

<template>
  <div class="stage-host-root">
    <StaticImageStage
      v-if="useStaticImage"
      :key="`image-${resolvedModelId}-${resolvedModelUrl}`"
      :src="resolvedModelUrl"
      :fill="fill"
      :transparent="transparent"
      :head="head"
      @ready="emit('ready')"
    />
    <Live2DStage
      v-else-if="useLive2D"
      :key="`live2d-${resolvedModelId}`"
      :model-id="resolvedModelId"
      :fill="fill"
      :silent-auth="silentAuth"
      @ready="emit('ready')"
      @error="emit('error', $event)"
    />
    <PixelStage
      v-else
      :fill="fill"
      :transparent="transparent"
      :compact="compact"
      @ready="emit('ready')"
    />
  </div>
</template>

<style scoped>
.stage-host-root {
  display: flex;
  flex: 1;
  width: 100%;
  height: 100%;
  min-height: 0;
}
</style>
