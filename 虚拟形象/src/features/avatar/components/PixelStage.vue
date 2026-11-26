<script setup>
import { onBeforeUnmount, onMounted, ref } from 'vue'
import { Rectangle } from 'pixi.js'
import { createStage, fitStage, STAGE_SIZE } from '../stage'
import { createAvatarLayers } from '../layers'
import { createAvatarRuntime } from '../runtime/createAvatarRuntime'
import { useAvatarStore } from '@/shared/stores/avatar'

const props = defineProps({
  /** 撑满父容器：宽高同时参与计算，允许小数倍缩放 */
  fill: {
    type: Boolean,
    default: false
  },
  /** 悬浮窗：去掉舞台背景与边框 */
  transparent: {
    type: Boolean,
    default: false
  },
  /** 小窗模式：允许缩放到 1× 以下 */
  compact: {
    type: Boolean,
    default: false
  }
})

const emit = defineEmits(['ready'])

const rootRef = ref(null)
const wrapRef = ref(null)
const hostRef = ref(null)
const scale = ref(1)

const avatarStore = useAvatarStore()

let app = null
let layers = null
let runtime = null
let disposed = false
let resizeObserver = null

function resize() {
  if (!app || !wrapRef.value || !hostRef.value) return
  // 舞台边框 4px × 2，fill 模式下从可用空间里扣除，避免被容器裁掉
  scale.value = fitStage(wrapRef.value, app.canvas, {
    fill: props.fill,
    gutter: props.fill && !props.transparent ? 8 : 0,
    minScale: props.compact ? 0.1 : 1
  })
  hostRef.value.style.height = `${STAGE_SIZE * scale.value}px`
}

onMounted(async () => {
  if (!hostRef.value) return

  try {
    const created = await createStage(hostRef.value)

    // init 是异步的，等待期间组件可能已经卸载
    if (disposed) {
      created.destroy({ removeView: true }, { children: true, texture: true })
      return
    }

    app = created
    layers = createAvatarLayers()
    // 悬浮窗模式只保留角色本身，隐藏调色板绘制的场景背景
    if (props.transparent) {
      layers.background.visible = false
    }
    app.stage.addChild(layers.root)

    runtime = createAvatarRuntime({ app, layers })
    avatarStore.registerRuntime(runtime)
    emit('ready')

    // 点击热区：触摸反应 + 开心表情 + 弹跳
    layers.root.eventMode = 'static'
    layers.root.cursor = 'pointer'
    layers.root.hitArea = new Rectangle(0, 0, STAGE_SIZE, STAGE_SIZE)
    layers.root.on('pointertap', (event) => {
      const point = event.global
      const x = point ? point.x / STAGE_SIZE : 0.5
      const y = point ? point.y / STAGE_SIZE : 0.5
      avatarStore.triggerTouch(x, y)
      avatarStore.playEmotion('joy', 1600)
      avatarStore.playMotion('bounce', 0.6)
    })
    layers.root.on('pointermove', (event) => {
      const point = event.global
      if (!point) return
      avatarStore.setPointer(point.x / STAGE_SIZE, point.y / STAGE_SIZE)
    })
    layers.root.on('pointerleave', () => avatarStore.clearPointer())

    resize()
    // 侧栏折叠、窗口缩放都会改变舞台尺寸，统一用 ResizeObserver 跟踪
    if (typeof ResizeObserver !== 'undefined' && rootRef.value) {
      resizeObserver = new ResizeObserver(() => resize())
      resizeObserver.observe(rootRef.value)
    } else {
      window.addEventListener('resize', resize)
    }
  } catch (error) {
    console.error('像素舞台初始化失败', error)
  }
})

onBeforeUnmount(() => {
  disposed = true
  if (resizeObserver) {
    resizeObserver.disconnect()
    resizeObserver = null
  }
  window.removeEventListener('resize', resize)

  if (runtime) {
    avatarStore.unregisterRuntime(runtime)
    runtime.destroy()
    runtime = null
  }

  if (app) {
    app.destroy({ removeView: true }, { children: true, texture: true })
    app = null
  }

  layers = null
})
</script>

<template>
  <div
    ref="rootRef"
    class="pixel-stage"
    :class="{ 'is-fill': fill, 'is-transparent': transparent }"
  >
    <div ref="wrapRef" class="stage-wrap">
      <div ref="hostRef" class="stage-host" />
      <p v-if="!fill" class="stage-hint">点击角色试试眨眼 · 当前缩放 {{ scale }}×</p>
    </div>
  </div>
</template>

<style scoped>
.pixel-stage {
  display: flex;
  justify-content: center;
  overflow-x: auto;
}

.pixel-stage.is-fill {
  align-items: center;
  min-height: 0;
  overflow: hidden;
}

.stage-wrap {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 12px;
  width: 100%;
  height: 100%;
}

.stage-host {
  line-height: 0;
  background: #101827;
  border: 4px solid #2c3a55;
  box-shadow: 0 0 0 4px #101827;
}

/* 悬浮窗：只保留角色本身 */
.pixel-stage.is-transparent .stage-host {
  background: transparent;
  border: none;
  box-shadow: none;
}

.stage-hint {
  margin: 0;
  font-size: 13px;
  color: var(--el-text-color-secondary);
}
</style>
