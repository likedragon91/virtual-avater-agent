<script setup>
import { computed } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useAuthStore } from '@/shared/stores/auth'

const router = useRouter()
const authStore = useAuthStore()

const userTypeText = computed(() => {
  const map = { 1: '普通用户', 2: '管理员', 3: 'VIP' }
  return map[Number(authStore.userInfo.userType)] || '-'
})

const genderText = computed(() => {
  const map = { 0: '未知', 1: '男', 2: '女' }
  return map[Number(authStore.userInfo.gender)] || '未知'
})

// 形象档案：目前来自登录用户信息，后续由形象接口补充
const avatarProfile = computed(() => [
  { label: '形象名称', value: authStore.displayName },
  { label: '生日', value: authStore.userInfo.birthday || '未设置' },
  { label: '性格标签', value: authStore.userInfo.personality || '未设置' },
  { label: '关系阶段', value: authStore.userInfo.relationship || '初识' }
])

async function handleLogout() {
  await authStore.logout()
  ElMessage.success('已退出登录')
  router.replace('/login')
}
</script>

<template>
  <div class="settings-view">
    <el-card shadow="never">
      <template #header>
        <span class="card-title">形象档案</span>
      </template>

      <el-descriptions :column="2" border>
        <el-descriptions-item
          v-for="item in avatarProfile"
          :key="item.label"
          :label="item.label"
        >
          {{ item.value }}
        </el-descriptions-item>
      </el-descriptions>

      <p class="profile-note">
        发型、肤色、瞳色与体型等外观设置将在形象接口就绪后接入。
      </p>
    </el-card>

    <el-card shadow="never">
      <template #header>
        <span class="card-title">账号信息</span>
      </template>

      <el-descriptions :column="2" border>
        <el-descriptions-item label="账号">
          {{ authStore.userInfo.username || '-' }}
        </el-descriptions-item>
        <el-descriptions-item label="昵称">
          {{ authStore.userInfo.nickname || '-' }}
        </el-descriptions-item>
        <el-descriptions-item label="邮箱">
          {{ authStore.userInfo.email || '-' }}
        </el-descriptions-item>
        <el-descriptions-item label="手机号">
          {{ authStore.userInfo.phone || '-' }}
        </el-descriptions-item>
        <el-descriptions-item label="用户类型">
          {{ userTypeText }}
        </el-descriptions-item>
        <el-descriptions-item label="性别">
          {{ genderText }}
        </el-descriptions-item>
      </el-descriptions>
    </el-card>

    <el-card shadow="never">
      <template #header>
        <span class="card-title">更多设置</span>
      </template>
      <el-empty description="人设切换、音色、模型、隐私与数据管理将在后端接口就绪后接入" />
    </el-card>

    <el-card shadow="never">
      <template #header>
        <span class="card-title">登录状态</span>
      </template>
      <p class="logout-tip">退出后会同时通知后端撤销当前 token，并清理本地登录态。</p>
      <el-button type="danger" plain @click="handleLogout">退出登录</el-button>
    </el-card>
  </div>
</template>

<style scoped>
.settings-view {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.card-title {
  font-weight: 600;
}

.profile-note {
  margin: 16px 0 0;
  font-size: 13px;
  line-height: 1.8;
  color: var(--el-text-color-secondary);
}

.logout-tip {
  margin: 0 0 16px;
  font-size: 14px;
  color: var(--el-text-color-secondary);
}
</style>
