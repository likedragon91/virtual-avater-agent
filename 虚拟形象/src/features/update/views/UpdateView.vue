<script setup>
import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Download, Refresh } from '@element-plus/icons-vue'
import { getLatestAppVersion } from '@/shared/api/appVersion'
import {
  APP_VERSION,
  compareVersions,
  openInstaller,
  resolveCurrentVersion,
  resolveDownloadUrl
} from '@/shared/utils/appVersion'

const isDesktop = Boolean(
  typeof window !== 'undefined' && window.desktopBridge?.isElectron
)

const currentVersion = ref(APP_VERSION)
const latest = ref(null)
const loading = ref(false)
const error = ref('')

const hasUpdate = computed(
  () => Boolean(latest.value)
    && compareVersions(latest.value.version, currentVersion.value) > 0
)
const downloadUrl = computed(() => resolveDownloadUrl(latest.value?.downloadUrl))
const latestVersion = computed(() => latest.value?.version || '未发布')
const releaseNotes = computed(() => latest.value?.notes || '暂无更新说明')

function formatSize(size) {
  const value = Number(size) || 0
  if (value >= 1024 * 1024) return `${(value / 1024 / 1024).toFixed(1)} MB`
  if (value >= 1024) return `${Math.round(value / 1024)} KB`
  return `${value} B`
}

async function check() {
  loading.value = true
  error.value = ''
  try {
    currentVersion.value = await resolveCurrentVersion()
    latest.value = await getLatestAppVersion()
  } catch (e) {
    error.value = e?.message || '检查更新失败，请稍后重试'
    latest.value = null
  } finally {
    loading.value = false
  }
}

async function handleDownload() {
  if (!downloadUrl.value) {
    ElMessage.warning('暂无可下载的安装包')
    return
  }
  const opened = await openInstaller(downloadUrl.value)
  if (opened) {
    ElMessage.success(isDesktop ? '已在系统浏览器打开下载' : '已打开安装包下载地址')
  }
}

onMounted(check)
</script>

<template>
  <div v-loading="loading" class="update-view">
    <el-card shadow="never">
      <template #header>
        <div class="card-header">
          <span class="card-title">应用更新</span>
          <el-tag size="small" effect="plain" :type="isDesktop ? 'primary' : 'info'">
            {{ isDesktop ? 'Windows 桌面端' : '网页端' }}
          </el-tag>
        </div>
      </template>

      <el-alert
        v-if="error"
        :title="error"
        type="error"
        show-icon
        :closable="false"
        class="update-alert"
      />

      <el-descriptions :column="1" border>
        <el-descriptions-item label="当前版本">
          {{ currentVersion }}
        </el-descriptions-item>
        <el-descriptions-item label="最新版本">
          {{ latestVersion }}
        </el-descriptions-item>
        <el-descriptions-item label="发布时间">
          {{ latest?.createdAt || '-' }}
        </el-descriptions-item>
        <el-descriptions-item label="安装包">
          <span v-if="latest?.fileName">
            {{ latest.fileName }}
            <span v-if="latest.fileSize">（{{ formatSize(latest.fileSize) }}）</span>
          </span>
          <span v-else>-</span>
        </el-descriptions-item>
      </el-descriptions>

      <el-alert
        v-if="hasUpdate"
        type="success"
        show-icon
        :closable="false"
        :title="`发现新版本 v${latest.version}`"
        class="update-alert"
      >
        <template #default>
          <div class="release-notes">{{ releaseNotes }}</div>
        </template>
      </el-alert>
      <el-alert
        v-else-if="latest"
        type="info"
        show-icon
        :closable="false"
        title="当前已是最新版本"
        class="update-alert"
      />
      <el-empty
        v-else-if="!error"
        description="服务器暂未发布桌面端版本"
        :image-size="64"
      />

      <div class="actions">
        <el-button :icon="Refresh" :loading="loading" @click="check">
          检查更新
        </el-button>
        <el-button
          v-if="hasUpdate"
          type="primary"
          :icon="Download"
          @click="handleDownload"
        >
          下载安装包
        </el-button>
      </div>

      <p class="update-hint">
        网页端会随服务器自动更新；Windows 桌面端需要下载并安装新版本。
        安装包由管理员发布，版本号与安装包文件名以管理员填写为准。
      </p>
    </el-card>
  </div>
</template>

<style scoped>
.update-view {
  max-width: 760px;
}

.card-header {
  display: flex;
  align-items: center;
  gap: 8px;
}

.card-title {
  font-weight: 600;
}

.update-alert {
  margin-top: 16px;
}

.release-notes {
  margin-top: 6px;
  font-size: 13px;
  line-height: 1.7;
  white-space: pre-wrap;
}

.actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 20px;
}

.update-hint {
  margin: 16px 0 0;
  font-size: 12px;
  line-height: 1.7;
  color: var(--el-text-color-secondary);
}
</style>
