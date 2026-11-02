<script setup>
import { onBeforeUnmount, onMounted, ref } from 'vue'
import { Rectangle } from 'pixi.js'
import { createStage, fitStage, STAGE_SIZE } from '../stage'
import { createAvatarLayers } from '../layers'
import { createAnimator } from '../animation'

const wrapRef = ref(null)
const hostRef = ref(null)
const scale = ref(1)

let app = null
let layers = null
let animator = null
let disposed = false

function resize() {
  if (!app || !wrapRef.value || !hostRef.value) return
  scale.value = fitStage(wrapRef.value, app.canvas)
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
    app.stage.addChild(layers.root)

    animator = createAnimator(app, layers)

    // 点击热区：眨眼 + 开心表情，验证动画状态机
    layers.root.eventMode = 'static'
    layers.root.cursor = 'pointer'
    layers.root.hitArea = new Rectangle(0, 0, STAGE_SIZE, STAGE_SIZE)
    layers.root.on('pointertap', () => {
      animator.blink()
      animator.play('joy', 1600)
    })

    resize()
    window.addEventListener('resize', resize)
  } catch (error) {
    console.error('像素舞台初始化失败', error)
  }
})

onBeforeUnmount(() => {
  disposed = true
  window.removeEventListener('resize', resize)

  if (animator) {
    animator.destroy()
    animator = null
  }

  if (app) {
    app.destroy({ removeView: true }, { children: true, texture: true })
    app = null
  }

  layers = null
})
</script>

<template>
  <div class="pixel-stage">
    <div ref="wrapRef" class="stage-wrap">
      <div ref="hostRef" class="stage-host" />
      <p class="stage-hint">点击角色试试眨眼 · 当前缩放 {{ scale }}×</p>
    </div>
  </div>
</template>

<style scoped>
.pixel-stage {
  display: flex;
  justify-content: center;
  overflow-x: auto;
}

.stage-wrap {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 12px;
  width: 100%;
}

.stage-host {
  line-height: 0;
  background: #101827;
  border: 4px solid #2c3a55;
  box-shadow: 0 0 0 4px #101827;
}

.stage-hint {
  margin: 0;
  font-size: 13px;
  color: var(--el-text-color-secondary);
}
</style>
