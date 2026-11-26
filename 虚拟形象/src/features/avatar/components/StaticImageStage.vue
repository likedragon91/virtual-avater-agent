<script setup>
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { ASSET_BASE_URL } from '@/shared/api/config'
import { useAvatarStore } from '@/shared/stores/avatar'
import { removeImageBackground } from '../removeImageBackground'

const props = defineProps({
  src: {
    type: String,
    default: ''
  },
  fill: {
    type: Boolean,
    default: false
  },
  /** 兼容 StageHost 透传；静态图现在始终自动去除纯色背景 */
  transparent: {
    type: Boolean,
    default: false
  },
  /** 大头模式：裁到上半身 */
  head: {
    type: Boolean,
    default: false
  }
})

const emit = defineEmits(['ready'])

const avatarStore = useAvatarStore()
const speaking = ref(false)
const displaySrc = ref('')
const processing = ref(false)
let processToken = 0

const resolvedSrc = computed(() => {
  const value = props.src || ''
  if (!value) return ''
  return /^https?:\/\//.test(value) ? value : `${ASSET_BASE_URL}${value}`
})

watch(
  resolvedSrc,
  async (value) => {
    const token = ++processToken
    displaySrc.value = ''
    if (!value) {
      processing.value = false
      return
    }
    processing.value = true
    // 纯色背景（黑/白/任意单色）自动去背；透明图或复杂背景回退原图
    const processed = await removeImageBackground(value)
    processing.value = false
    if (token !== processToken) return
    displaySrc.value = processed || value
  },
  { immediate: true }
)

let runtime = null

function noop() {}

const staticRuntime = {
  setEmotion: noop,
  playEmotion: noop,
  playMotion: () => false,
  setSpeaking(value) {
    speaking.value = Boolean(value)
  },
  setMouth: noop,
  triggerTouch: noop,
  blink: noop,
  setPointer: noop,
  clearPointer: noop,
  setReducedMotion: noop,
  setPalette: noop,
  inputAudio: noop,
  destroy: noop
}

onMounted(() => {
  runtime = staticRuntime
  avatarStore.registerRuntime(runtime)
  emit('ready')
})

onBeforeUnmount(() => {
  processToken++
  if (runtime) {
    avatarStore.unregisterRuntime(runtime)
    runtime = null
  }
})
</script>

<template>
  <div class="static-stage" :class="{ 'is-fill': fill, 'is-speaking': speaking, 'is-head': head }">
    <img
      v-if="displaySrc"
      :src="displaySrc"
      class="static-image"
      alt="AI 形象"
      draggable="false"
    />
    <div v-else-if="processing" class="static-loading" aria-hidden="true" />
    <div v-else class="static-empty">未选择静态立绘</div>
  </div>
</template>

<style scoped>
.static-stage {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 100%;
  height: 100%;
  min-height: 260px;
  overflow: hidden;
  background: transparent;
}

.static-stage.is-fill {
  min-height: 0;
}

/* 大头模式：填满圆形窗口并保留图片顶部（头部/上半身） */
.static-stage.is-head {
  align-items: flex-start;
}

.static-stage.is-head .static-image {
  width: 100%;
  height: 100%;
  max-width: none;
  max-height: none;
  object-fit: cover;
  object-position: 50% 0%;
}

.static-image {
  display: block;
  max-width: 100%;
  max-height: 100%;
  object-fit: contain;
  user-select: none;
  animation: standee-breath 6s ease-in-out infinite;
  transform-origin: 50% 100%;
  transition: filter 0.2s ease;
}

.static-stage.is-speaking .static-image {
  animation-duration: 4s;
  filter: drop-shadow(0 0 12px rgba(120, 170, 255, 0.28));
}

.static-loading {
  width: 100%;
  height: 100%;
}

.static-empty {
  font-size: 13px;
  color: var(--el-text-color-secondary);
}

@keyframes standee-breath {
  0%,
  100% {
    transform: translateY(0) scale(1);
  }
  50% {
    transform: translateY(-4px) scale(1.006);
  }
}

@media (prefers-reduced-motion: reduce) {
  .static-image {
    animation: none;
  }
}
</style>
