<script setup>
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { ElMessage } from 'element-plus'
import { Refresh } from '@element-plus/icons-vue'
import { getOnlineUsers } from '@/shared/api/user'
import { useAuthStore } from '@/shared/stores/auth'

const authStore = useAuthStore()

const onlineData = ref([])
const onlineLoading = ref(false)
let onlineTimer = null

const isAdmin = computed(() => authStore.isAdmin)

// 获取在线用户（仅管理员）
async function loadOnlineUsers() {
  if (!isAdmin.value) {
    onlineData.value = []
    return
  }

  onlineLoading.value = true
  try {
    const res = await getOnlineUsers()
    if (res && res.code === '200') {
      onlineData.value = Array.isArray(res.data) ? res.data : []
    } else if (res && res.code === '403') {
      onlineData.value = []
      ElMessage.error(res.msg || '没有访问权限，仅管理员可查看在线用户')
    } else {
      onlineData.value = []
      ElMessage.error((res && res.msg) || '获取在线用户失败')
    }
  } catch (e) {
    onlineData.value = []
    if (e && e.isAuthError) return
    ElMessage.error('获取在线用户失败，请检查网络或接口地址')
  } finally {
    onlineLoading.value = false
  }
}

function startOnlineTimer() {
  stopOnlineTimer()
  onlineTimer = setInterval(loadOnlineUsers, 30000)
}

function stopOnlineTimer() {
  if (onlineTimer) {
    clearInterval(onlineTimer)
    onlineTimer = null
  }
}

// 在线时长：从登录时间到现在
function onlineDuration(row) {
  if (!row.loginTime) return '-'
  const start = new Date(row.loginTime.replace(/-/g, '/')).getTime()
  if (Number.isNaN(start)) return '-'
  const minutes = Math.max(0, Math.floor((Date.now() - start) / 60000))
  if (minutes < 1) return '不到 1 分钟'
  if (minutes < 60) return `${minutes} 分钟`
  const hours = Math.floor(minutes / 60)
  return `${hours} 小时 ${minutes % 60} 分钟`
}

onMounted(() => {
  loadOnlineUsers()
  startOnlineTimer()
})

onUnmounted(stopOnlineTimer)
</script>

<template>
  <el-card shadow="never">
    <template #header>
      <div class="card-header">
        <span class="card-title">在线用户（{{ onlineData.length }}）</span>
        <div class="card-actions">
          <span class="refresh-tip">每 30 秒自动刷新</span>
          <el-button
            type="primary"
            :icon="Refresh"
            :loading="onlineLoading"
            @click="loadOnlineUsers"
          >
            刷新
          </el-button>
        </div>
      </div>
    </template>

    <div class="table-scroll">
      <el-table
        v-loading="onlineLoading"
        :data="onlineData"
        empty-text="当前暂无在线用户"
        style="width: 100%"
        height="400"
      >
      <el-table-column prop="username" label="账号" min-width="150" />
      <el-table-column label="昵称" min-width="150">
        <template #default="{ row }">
          {{ row.nickname || '-' }}
        </template>
      </el-table-column>
      <el-table-column label="用户类型" width="120">
        <template #default="{ row }">
          {{ row.userTypeDisplayName || '-' }}
        </template>
      </el-table-column>
      <el-table-column prop="loginTime" label="登录时间" width="180" />
      <el-table-column prop="lastActiveTime" label="最后活跃" width="180" />
      <el-table-column label="在线时长" width="150">
        <template #default="{ row }">
          {{ onlineDuration(row) }}
        </template>
      </el-table-column>
      </el-table>
    </div>
  </el-card>
</template>

<style scoped>
.card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.card-title {
  font-weight: 600;
}

.card-actions {
  display: flex;
  align-items: center;
  gap: 8px;
}

.refresh-tip {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

@media (max-width: 900px) {
  .card-header {
    flex-wrap: wrap;
    gap: 8px;
  }

  .card-actions {
    flex-wrap: wrap;
  }
}
</style>
