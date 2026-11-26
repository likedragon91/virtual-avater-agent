<script setup>
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import StageHost from '@/features/avatar/components/StageHost.vue'
import { useAvatarStore } from '@/shared/stores/avatar'
import { loadRefreshTokenFromStore } from '@/shared/utils/auth'

const avatarStore = useAvatarStore()
const ready = ref(false)
const forcePixel = ref(false)
const headMode = ref(false)
const lyricLines = ref([])
const lyricsVisible = ref(false)
const dragging = ref(false)
let drag = null
let unsubscribe = null
let unsubscribeLyric = null
let unsubscribeClear = null
let unsubscribeMode = null
let lyricTimer = null

const SILENT_AUTH = { skipAuthHandler: true }

const avatarDriver = computed(() => {
  if (forcePixel.value) return 'pixel'
  return avatarStore.avatarConfig?.driver || 'pixel'
})

const headClass = computed(() => {
  if (!headMode.value) return ''
  return `is-head is-head-${avatarDriver.value}`
})

async function refresh() {
  // 悬浮窗是独立渲染进程，需要自己把安全存储里的刷新凭证载入内存
  await loadRefreshTokenFromStore().catch(() => '')
  const config = await avatarStore.loadAvatarConfig(true, SILENT_AUTH)
  if (config) {
    forcePixel.value = false
  } else {
    // 未登录 / 401 / 加载失败：回退到默认像素形象
    avatarStore.avatarConfig = null
    forcePixel.value = true
  }
  ready.value = true
}

function expand() {
  window.desktopBridge?.expand?.()
}

// 自定义拖动：角色区域保持普通指针事件，右键菜单和点击展开都不会被拖拽区吞掉
function onAvatarPointerDown(event) {
  if (event.button !== 0 || !window.desktopBridge?.startBallDrag) return
  drag = {
    x: event.screenX,
    y: event.screenY,
    moved: false,
    pointerId: event.pointerId,
    target: event.currentTarget
  }
  dragging.value = true
  try {
    event.currentTarget.setPointerCapture?.(event.pointerId)
  } catch {
    // 指针捕获失败时仍可依赖普通 pointermove
  }
  window.desktopBridge.startBallDrag({ screenX: event.screenX, screenY: event.screenY })
}

function onAvatarPointerMove(event) {
  if (!drag) return
  if (Math.abs(event.screenX - drag.x) + Math.abs(event.screenY - drag.y) > 4) {
    drag.moved = true
  }
  window.desktopBridge?.moveBallDrag?.({ screenX: event.screenX, screenY: event.screenY })
}

function onAvatarPointerUp() {
  if (!drag) return
  const moved = drag.moved
  const target = drag.target
  const pointerId = drag.pointerId
  drag = null
  dragging.value = false
  window.desktopBridge?.endBallDrag?.()
  try {
    target?.releasePointerCapture?.(pointerId)
  } catch {
    // 忽略释放失败
  }
  if (!moved) expand()
}

function onStageError() {
  forcePixel.value = true
}

function pushLyric(payload) {
  const text = payload?.text ? String(payload.text).trim() : ''
  if (!text) return
  lyricLines.value = [...lyricLines.value, text].slice(-3)
  lyricsVisible.value = true
  if (lyricTimer) clearTimeout(lyricTimer)
  lyricTimer = setTimeout(() => {
    lyricsVisible.value = false
    lyricLines.value = []
  }, 8000)
}

function clearLyrics() {
  if (lyricTimer) clearTimeout(lyricTimer)
  lyricTimer = null
  lyricLines.value = []
  lyricsVisible.value = false
}

onMounted(async () => {
  let mode = 'full'
  if (window.desktopBridge?.getBallMode) {
    mode = await window.desktopBridge.getBallMode().catch(() => 'full')
  }
  headMode.value = mode === 'head'
  unsubscribeMode = window.desktopBridge?.onBallMode?.((value) => {
    headMode.value = value === 'head'
  })
  await refresh()
  unsubscribe = window.desktopBridge?.onAvatarUpdated?.(() => {
    refresh()
  })
  unsubscribeLyric = window.desktopBridge?.onBallLyric?.((payload) => pushLyric(payload))
  unsubscribeClear = window.desktopBridge?.onBallLyricClear?.(() => clearLyrics())
  window.addEventListener('focus', refresh)
})

onBeforeUnmount(() => {
  unsubscribe?.()
  unsubscribeLyric?.()
  unsubscribeClear?.()
  unsubscribeMode?.()
  if (lyricTimer) clearTimeout(lyricTimer)
  window.removeEventListener('focus', refresh)
})
</script>

<template>
  <div class="ball-shell">
    <span class="ball-grip" aria-hidden="true"></span>
    <div
      v-if="ready"
      class="ball-avatar"
      :class="[headClass, { 'is-dragging': dragging }]"
      @pointerdown="onAvatarPointerDown"
      @pointermove="onAvatarPointerMove"
      @pointerup="onAvatarPointerUp"
      @pointercancel="onAvatarPointerUp"
    >
      <StageHost
        fill
        transparent
        compact
        :head="headMode"
        silent-auth
        :driver="forcePixel ? 'pixel' : ''"
        @error="onStageError"
      />
      <button
        class="ball-expand"
        type="button"
        title="展开主窗口"
        @pointerdown.stop
        @click.stop="expand"
      >
        ⌃
      </button>
    </div>
    <transition name="lyric-fade">
      <div v-if="!headMode && lyricsVisible && lyricLines.length" class="ball-lyrics">
        <p
          v-for="(line, index) in lyricLines"
          :key="`${index}-${line}`"
          :class="{ current: index === lyricLines.length - 1 }"
        >
          {{ line }}
        </p>
      </div>
    </transition>
  </div>
</template>

<style scoped>
.ball-shell {
  position: relative;
  box-sizing: border-box;
  width: 100vw;
  height: 100vh;
  padding: 14px;
  background: transparent;
  user-select: none;
}

/* 顶部握把：提示这里可以拖动窗口 */
.ball-grip {
  position: absolute;
  top: 4px;
  left: 50%;
  z-index: 2;
  width: 44px;
  height: 8px;
  margin-left: -22px;
  border-radius: 3px;
  background: rgba(255, 255, 255, 0.28);
  /* 留给右键菜单的 no-drag 区域；拖动交给整个角色区 */
  -webkit-app-region: no-drag;
}

.ball-avatar {
  position: relative;
  display: flex;
  width: 100%;
  height: 100%;
  background: transparent;
  cursor: grab;
  touch-action: none;
}

.ball-avatar.is-dragging {
  cursor: grabbing;
}

/* 大头模式：圆形窗口，只保留头部/上半身 */
.ball-avatar.is-head {
  overflow: hidden;
  border-radius: 50%;
}

.ball-avatar.is-head::before {
  content: '';
  position: absolute;
  inset: 0;
  border-radius: 50%;
  background: rgba(20, 28, 43, 0.92);
  border: 2px solid rgba(255, 255, 255, 0.18);
  box-shadow: 0 8px 24px rgba(0, 0, 0, 0.3);
}

/* 静态立绘本身可以带透明背景，深色圆盘会变成黑框 */
.ball-avatar.is-head-image::before {
  display: none;
}

.ball-avatar.is-head-pixel :deep(.stage-host-root) {
  transform: scale(2.4);
  transform-origin: 50% 41%;
}

.ball-avatar.is-head-live2d :deep(.stage-host-root) {
  transform: scale(1.9);
  transform-origin: 50% 20%;
}

.ball-expand {
  position: absolute;
  right: 10px;
  bottom: 10px;
  width: 30px;
  height: 30px;
  padding: 0;
  border: none;
  border-radius: 50%;
  background: rgba(15, 23, 42, 0.42);
  color: #fff;
  font-size: 16px;
  line-height: 28px;
  cursor: pointer;
  opacity: 0.55;
  transition: opacity 0.2s ease;
  -webkit-app-region: no-drag;
}

.ball-expand:hover {
  opacity: 1;
}

.ball-avatar :deep(.stage-host-root) {
  background: transparent;
}

.ball-avatar :deep(.pixel-stage),
.ball-avatar :deep(.live2d-stage),
.ball-avatar :deep(.static-stage) {
  flex: 1;
  min-width: 0;
  min-height: 0;
  background: transparent;
}

/* 歌词式台词：只显示最近三句，最后一句高亮 */
.ball-lyrics {
  position: absolute;
  left: 14px;
  right: 14px;
  bottom: 16px;
  padding: 12px 10px 10px;
  text-align: center;
  pointer-events: none;
  background: linear-gradient(180deg, rgba(0, 0, 0, 0), rgba(0, 0, 0, 0.62));
  border-radius: 12px;
  font-size: 12px;
  line-height: 1.55;
  color: rgba(255, 255, 255, 0.55);
  text-shadow:
    0 1px 3px rgba(0, 0, 0, 0.9),
    0 0 8px rgba(0, 0, 0, 0.65);
}

.ball-lyrics p {
  margin: 0;
  transition:
    color 0.25s ease,
    opacity 0.25s ease,
    transform 0.25s ease;
}

.ball-lyrics p:not(.current) {
  opacity: 0.65;
}

.ball-lyrics p.current {
  color: #fff;
  font-size: 13px;
  transform: translateY(-1px);
}

.lyric-fade-enter-active,
.lyric-fade-leave-active {
  transition:
    opacity 0.3s ease,
    transform 0.3s ease;
}

.lyric-fade-enter-from,
.lyric-fade-leave-to {
  opacity: 0;
  transform: translateY(6px);
}
</style>
