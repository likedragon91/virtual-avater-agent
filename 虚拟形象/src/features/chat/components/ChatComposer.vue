<script setup>
import { computed, nextTick, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Promotion } from '@element-plus/icons-vue'
import { primeAudio } from '@/features/voice/audioEngine'
import { useChatStore } from '@/shared/stores/chat'
import { useVoiceStore } from '@/shared/stores/voice'
import { useChatBootstrap } from '../composables/useChatBootstrap'

const props = defineProps({
  /** 左侧面板：单行输入 + 并排按钮，适配 420×140 窗口 */
  panel: {
    type: Boolean,
    default: false
  },
  autofocus: {
    type: Boolean,
    default: false
  }
})

const emit = defineEmits(['sent'])

const chat = useChatStore()
const voiceStore = useVoiceStore()
const { ensure } = useChatBootstrap()
const inputRef = ref(null)

const canSend = computed(() => Boolean(chat.draft.trim()) && !chat.streaming)

function focus() {
  nextTick(() => inputRef.value?.focus())
}

defineExpose({ focus })

async function send() {
  if (chat.streaming) return
  const content = chat.draft.trim()
  if (!content) return
  if (content.length > 8000) {
    ElMessage.warning('单条消息不能超过 8000 字')
    return
  }
  // 发送按钮是用户手势，顺带解锁移动端音频
  if (voiceStore.enabled) {
    await primeAudio()
  }
  chat.setDraft('')
  const started = await chat.sendMessage(content)
  if (!started) chat.setDraft(content)
  emit('sent')
}

function onKeydown(event) {
  if (event.key !== 'Enter') return
  // 输入法组合期间的回车不发送
  if (event.shiftKey || event.isComposing || event.keyCode === 229) return
  event.preventDefault()
  send()
}

onMounted(async () => {
  if (props.panel) {
    // 完整聊天页由 ChatView 自己初始化，避免并发重复加载
    await ensure().catch(() => {})
  }
  if (props.autofocus || props.panel) focus()
})
</script>

<template>
  <div class="composer" :class="{ 'is-panel': panel }">
    <el-input
      ref="inputRef"
      v-model="chat.draft"
      type="textarea"
      resize="none"
      :autosize="panel ? { minRows: 1, maxRows: 2 } : { minRows: 1, maxRows: 5 }"
      maxlength="8000"
      :show-word-limit="!panel"
      :disabled="chat.messagesLoading"
      :placeholder="panel ? '说点什么，Enter 发送' : '说点什么… Enter 发送，Shift + Enter 换行'"
      @keydown="onKeydown"
    />
    <div class="composer-actions">
      <el-button v-if="chat.streaming" type="danger" plain @click="chat.stopStreaming()">
        停止生成
      </el-button>
      <el-button v-else type="primary" :icon="Promotion" :disabled="!canSend" @click="send">
        发送
      </el-button>
    </div>
  </div>
</template>

<style scoped>
.composer {
  padding: 12px 16px 14px;
  border-top: 1px solid var(--el-border-color-lighter);
  background: #fff;
}

.composer-actions {
  display: flex;
  justify-content: flex-end;
  margin-top: 8px;
}

/* 左侧面板：高度只有 140px，输入框与按钮必须并排 */
.composer.is-panel {
  display: flex;
  flex: 1;
  align-items: flex-end;
  gap: 8px;
  box-sizing: border-box;
  width: 100%;
  height: 100%;
  padding: 6px 8px;
  border-top: none;
}

.composer.is-panel :deep(.el-textarea) {
  flex: 1;
  min-width: 0;
}

.composer.is-panel .composer-actions {
  flex-shrink: 0;
  margin: 0;
}

@media (max-width: 900px) {
  .composer:not(.is-panel) {
    padding: 10px 12px calc(12px + env(safe-area-inset-bottom, 0px));
  }

  .composer:not(.is-panel) .composer-actions .el-button {
    min-height: 44px;
  }
}
</style>
