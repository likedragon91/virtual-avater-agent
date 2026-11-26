<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Delete, Download, Upload } from '@element-plus/icons-vue'
import {
  deleteAppVersion,
  listAppVersions,
  publishAppVersion
} from '@/shared/api/appVersion'
import { APP_VERSION, openInstaller, resolveDownloadUrl } from '@/shared/utils/appVersion'

const MAX_INSTALLER_BYTES = 300 * 1024 * 1024
const VERSION_PATTERN = /^[0-9]+(\.[0-9]+){1,3}$/

const form = reactive({
  version: '',
  notes: '',
  notify: true
})
const fileList = ref([])
const selectedFile = ref(null)
const publishing = ref(false)
const loading = ref(false)
const rows = ref([])

function formatSize(size) {
  const value = Number(size) || 0
  if (value >= 1024 * 1024) return `${(value / 1024 / 1024).toFixed(1)} MB`
  if (value >= 1024) return `${Math.round(value / 1024)} KB`
  return `${value} B`
}

function resetFile() {
  fileList.value = []
  selectedFile.value = null
}

function handleFileChange(uploadFile, uploadFiles) {
  const raw = uploadFile?.raw
  if (!raw) return
  if (!/\.exe$/i.test(raw.name)) {
    ElMessage.warning('请选择 Windows .exe 安装包')
    resetFile()
    return
  }
  if (raw.size > MAX_INSTALLER_BYTES) {
    ElMessage.warning('安装包不能超过 300MB')
    resetFile()
    return
  }
  selectedFile.value = raw
  fileList.value = uploadFiles.slice(-1)
}

function handleFileRemove() {
  resetFile()
}

function handleExceed() {
  ElMessage.warning('一次只能上传一个安装包')
}

async function load() {
  loading.value = true
  try {
    rows.value = (await listAppVersions()) || []
  } catch (error) {
    ElMessage.error(error?.message || '加载版本列表失败')
  } finally {
    loading.value = false
  }
}

async function handlePublish() {
  const version = form.version.trim()
  if (!VERSION_PATTERN.test(version)) {
    ElMessage.warning('版本号格式应为 0.1.1')
    return
  }
  if (!selectedFile.value) {
    ElMessage.warning('请选择安装包')
    return
  }
  publishing.value = true
  try {
    await publishAppVersion({
      version,
      notes: form.notes.trim(),
      file: selectedFile.value,
      notify: form.notify
    })
    ElMessage.success(form.notify ? '版本已发布，并已通知所有用户' : '版本已发布')
    form.version = ''
    form.notes = ''
    form.notify = true
    resetFile()
    await load()
  } catch (error) {
    ElMessage.error(error?.message || '发布版本失败')
  } finally {
    publishing.value = false
  }
}

async function handleDelete(row) {
  try {
    await ElMessageBox.confirm(
      `删除版本 v${row.version}？安装包文件会一并删除，已发出的通知将无法下载。`,
      '删除版本',
      { type: 'warning', confirmButtonText: '删除', cancelButtonText: '取消' }
    )
  } catch {
    return
  }
  try {
    await deleteAppVersion(row.id)
    ElMessage.success('版本已删除')
    await load()
  } catch (error) {
    ElMessage.error(error?.message || '删除版本失败')
  }
}

async function handleDownload(row) {
  const opened = await openInstaller(resolveDownloadUrl(row.downloadUrl))
  if (!opened) {
    ElMessage.warning('无法打开下载地址')
  }
}

onMounted(load)
</script>

<template>
  <div class="app-update-view">
    <el-card shadow="never">
      <template #header>
        <div class="card-header">
          <span class="card-title">发布桌面端版本</span>
          <span class="card-sub">
            当前前端版本 {{ APP_VERSION }}；发布前请先在 package.json 提升版本并重新打包
          </span>
        </div>
      </template>

      <el-form label-width="88px" class="publish-form">
        <el-form-item label="版本号" required>
          <el-input
            v-model="form.version"
            maxlength="32"
            placeholder="例如 0.1.1"
            clearable
          />
        </el-form-item>

        <el-form-item label="更新说明">
          <el-input
            v-model="form.notes"
            type="textarea"
            :autosize="{ minRows: 3, maxRows: 8 }"
            maxlength="4000"
            show-word-limit
            placeholder="本次更新内容，会显示在检查更新页和通知正文中"
          />
        </el-form-item>

        <el-form-item label="安装包" required>
          <el-upload
            v-model:file-list="fileList"
            :auto-upload="false"
            :limit="1"
            accept=".exe"
            :on-change="handleFileChange"
            :on-remove="handleFileRemove"
            :on-exceed="handleExceed"
          >
            <el-button :icon="Upload">选择安装包</el-button>
            <template #tip>
              <div class="el-upload__tip">
                Windows NSIS 安装包（.exe），单文件不超过 300MB；上传后即发布
              </div>
            </template>
          </el-upload>
        </el-form-item>

        <el-form-item label="发布通知">
          <el-switch v-model="form.notify" />
          <span class="form-hint">
            开启后立即向所有启用用户推送 APP_UPDATE 通知（不遵循免打扰）
          </span>
        </el-form-item>

        <el-form-item>
          <el-button
            type="primary"
            :icon="Upload"
            :loading="publishing"
            @click="handlePublish"
          >
            上传并发布
          </el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card shadow="never">
      <template #header>
        <div class="card-header">
          <span class="card-title">已发布版本</span>
          <span class="card-sub">最新一条会展示在用户的「检查更新」页</span>
        </div>
      </template>

      <el-table v-loading="loading" :data="rows" stripe>
        <el-table-column prop="version" label="版本" width="110">
          <template #default="{ row }">v{{ row.version }}</template>
        </el-table-column>
        <el-table-column prop="fileName" label="安装包" min-width="220" show-overflow-tooltip />
        <el-table-column label="大小" width="110">
          <template #default="{ row }">{{ formatSize(row.fileSize) }}</template>
        </el-table-column>
        <el-table-column prop="createdAt" label="发布时间" width="180" />
        <el-table-column label="操作" width="190" fixed="right">
          <template #default="{ row }">
            <el-button text size="small" :icon="Download" @click="handleDownload(row)">
              下载
            </el-button>
            <el-button text size="small" type="danger" :icon="Delete" @click="handleDelete(row)">
              删除
            </el-button>
          </template>
        </el-table-column>
      </el-table>

      <el-empty v-if="!loading && !rows.length" description="还没有发布过桌面端版本" :image-size="64" />
    </el-card>
  </div>
</template>

<style scoped>
.app-update-view {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.card-header {
  display: flex;
  flex-wrap: wrap;
  align-items: baseline;
  gap: 8px;
}

.card-title {
  font-weight: 600;
}

.card-sub {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.publish-form {
  max-width: 760px;
}

.form-hint {
  margin-left: 10px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}
</style>
