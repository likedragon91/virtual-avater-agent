<script setup>
import { ref } from 'vue'
import PixelStage from '@/features/avatar/components/PixelStage.vue'

// 聊天接口尚未接入，输入框先保持禁用，避免提交到不存在的接口
const draft = ref('')
</script>

<template>
  <div class="chat-view">
    <el-card shadow="never" class="chat-card">
      <template #header>
        <div class="card-header">
          <span class="card-title">聊天</span>
          <el-tag size="small" type="info">接口待接入</el-tag>
        </div>
      </template>

      <div class="message-area">
        <el-empty description="聊天记录将在后端接口就绪后显示">
          <div class="chat-features">
            <el-tag v-for="item in ['流式打字', '中断重答', '引用记忆', '表情气泡', '快捷回复']" :key="item" size="small" type="info">
              {{ item }}
            </el-tag>
          </div>
        </el-empty>
      </div>

      <div class="composer">
        <el-input
          v-model="draft"
          disabled
          placeholder="聊天接口就绪后可在这里发送消息"
        />
        <el-button type="primary" disabled>发送</el-button>
      </div>
    </el-card>

    <el-card shadow="never" class="stage-card">
      <template #header>
        <div class="card-header">
          <span class="card-title">虚拟形象</span>
          <span class="card-tip">256 × 256 · 整数倍缩放</span>
        </div>
      </template>
      <PixelStage />
    </el-card>
  </div>
</template>

<style scoped>
.chat-view {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 380px;
  gap: 16px;
  height: calc(100vh - 100px);
  min-height: 520px;
}

.chat-card,
.stage-card {
  display: flex;
  flex-direction: column;
}

.chat-card :deep(.el-card__body),
.stage-card :deep(.el-card__body) {
  flex: 1;
  display: flex;
  flex-direction: column;
  min-height: 0;
}

.card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.card-title {
  font-weight: 600;
}

.card-tip {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.message-area {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 0;
  padding: 16px;
  background: #fafbfc;
  border: 1px dashed var(--el-border-color);
  border-radius: 6px;
}

.chat-features {
  display: flex;
  flex-wrap: wrap;
  justify-content: center;
  gap: 8px;
}

.composer {
  display: flex;
  gap: 8px;
  margin-top: 12px;
}

@media (max-width: 1100px) {
  .chat-view {
    grid-template-columns: minmax(0, 1fr);
    height: auto;
  }

  .message-area {
    min-height: 360px;
  }
}
</style>
