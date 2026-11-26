<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useAuthStore } from '@/shared/stores/auth'
import { useVoiceStore } from '@/shared/stores/voice'
import { updateProfile, uploadAvatar } from '@/shared/api/user'
import { ASSET_BASE_URL } from '@/shared/api/config'
import { APP_VERSION, resolveCurrentVersion } from '@/shared/utils/appVersion'
import { getCurrentPersona, getPersonaOptions, updatePersona } from '@/shared/api/persona'
import {
  getNotificationSettings,
  updateNotificationSettings,
  getPushPublicKey,
  subscribePush,
  unsubscribePush,
  sendTestPush
} from '@/shared/api/notification'
import {
  isPushSupported,
  subscribeBrowserPush,
  unsubscribeBrowserPush,
  getExistingPushSubscription
} from '@/shared/utils/push'
import { useMediaQuery } from '@/shared/composables/useMediaQuery'

const router = useRouter()
const authStore = useAuthStore()
const voiceStore = useVoiceStore()
const appVersion = ref(APP_VERSION)
const { matches: isMobile } = useMediaQuery('(max-width: 900px)')

const voiceEnabled = computed({
  get: () => voiceStore.enabled,
  set: (value) => {
    if (value) {
      void voiceStore.enableFromGesture()
    } else {
      voiceStore.setEnabled(false)
    }
  }
})
const voiceSpeaker = computed({
  get: () => voiceStore.speaker,
  set: (value) => voiceStore.setSpeaker(value)
})
const voiceVolume = computed({
  get: () => Math.round(voiceStore.volume * 100),
  set: (value) => voiceStore.setVolume(Number(value) / 100)
})
const voicePreviewing = ref(false)

async function handleVoicePreview() {
  voicePreviewing.value = true
  try {
    await voiceStore.preview()
    ElMessage.success('试听已播放')
  } catch (error) {
    ElMessage.warning(error?.message || '试听失败')
  } finally {
    voicePreviewing.value = false
  }
}

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

const personaOptions = ref([])
const personaSaving = ref(false)
const personaLoadError = ref('')
const personaForm = reactive({
  personaCode: 'persona_default',
  traits: {
    warmth: 50,
    humor: 30,
    directness: 50,
    initiative: 50,
    verbosity: 50
  }
})

const traitDefinitions = [
  { key: 'warmth', label: '温柔度', hint: '越高越会先接住情绪' },
  { key: 'humor', label: '幽默感', hint: '越高越容易开玩笑' },
  { key: 'directness', label: '直接度', hint: '越高越先给结论' },
  { key: 'initiative', label: '主动度', hint: '越高越会主动追问' },
  { key: 'verbosity', label: '话量', hint: '越高回答越展开' }
]

function applyPersona(current) {
  if (!current) return
  personaForm.personaCode = current.code || 'persona_default'
  const traits = current.traits || {}
  personaForm.traits = {
    warmth: Math.round((traits.warmth ?? 0.5) * 100),
    humor: Math.round((traits.humor ?? 0.3) * 100),
    directness: Math.round((traits.directness ?? 0.5) * 100),
    initiative: Math.round((traits.initiative ?? 0.5) * 100),
    verbosity: Math.round((traits.verbosity ?? 0.5) * 100)
  }
}

function selectPersona(option) {
  if (!option || personaForm.personaCode === option.code) return
  personaForm.personaCode = option.code
  const defaults = option.defaultTraits || {}
  personaForm.traits = {
    warmth: Math.round((defaults.warmth ?? 0.5) * 100),
    humor: Math.round((defaults.humor ?? 0.3) * 100),
    directness: Math.round((defaults.directness ?? 0.5) * 100),
    initiative: Math.round((defaults.initiative ?? 0.5) * 100),
    verbosity: Math.round((defaults.verbosity ?? 0.5) * 100)
  }
}

async function loadPersona() {
  personaLoadError.value = ''
  try {
    const [options, current] = await Promise.all([
      getPersonaOptions(),
      getCurrentPersona()
    ])
    personaOptions.value = options || []
    applyPersona(current)
  } catch {
    // 后端还未加载人格接口时，不弹阻塞式错误，卡片内提示并允许重试
    personaLoadError.value = '人格设置暂不可用，请重启后端后重试。'
  }
}

async function savePersona() {
  personaSaving.value = true
  try {
    const traits = {}
    for (const item of traitDefinitions) {
      traits[item.key] = personaForm.traits[item.key] / 100
    }
    const current = await updatePersona({
      personaCode: personaForm.personaCode,
      traits
    })
    applyPersona(current)
    ElMessage.success('人格设置已保存')
  } catch (error) {
    ElMessage.error(error.message || '保存人格失败')
  } finally {
    personaSaving.value = false
  }
}

const notificationSettings = reactive({
  dndEnabled: true,
  dndStart: '23:00',
  dndEnd: '08:00',
  proactiveEnabled: true
})
const notificationSaving = ref(false)
const pushSupported = ref(false)
const pushServerEnabled = ref(false)
const pushEnabled = ref(false)
const pushBusy = ref(false)
const pushStatusText = ref('')

async function loadNotificationSettings() {
  try {
    const data = await getNotificationSettings()
    if (data) {
      notificationSettings.dndEnabled = Boolean(data.dndEnabled)
      notificationSettings.dndStart = data.dndStart || '23:00'
      notificationSettings.dndEnd = data.dndEnd || '08:00'
      notificationSettings.proactiveEnabled = data.proactiveEnabled !== false
    }
  } catch {
    // 后端未加载通知设置时静默
  }
  await loadPushState()
}

async function loadPushState() {
  pushSupported.value = isPushSupported()
  if (!pushSupported.value) {
    pushStatusText.value = '当前浏览器不支持通知'
    return
  }
  try {
    const info = await getPushPublicKey()
    pushServerEnabled.value = Boolean(info?.enabled)
    const subscription = await getExistingPushSubscription()
    pushEnabled.value = Boolean(subscription)
    if (!pushServerEnabled.value) {
      pushStatusText.value = '服务端未启用 Web Push'
    } else {
      pushStatusText.value = pushEnabled.value ? '已启用浏览器通知' : '未启用'
    }
  } catch {
    pushStatusText.value = '通知服务暂不可用'
  }
}

async function saveNotificationSettings() {
  if (
    notificationSettings.dndEnabled &&
    notificationSettings.dndStart === notificationSettings.dndEnd
  ) {
    ElMessage.warning('免打扰开始和结束时间不能相同')
    return
  }
  notificationSaving.value = true
  try {
    const data = await updateNotificationSettings({
      dndEnabled: notificationSettings.dndEnabled,
      dndStart: notificationSettings.dndEnabled ? notificationSettings.dndStart : '00:00',
      dndEnd: notificationSettings.dndEnabled ? notificationSettings.dndEnd : '00:00',
      proactiveEnabled: notificationSettings.proactiveEnabled
    })
    if (data) {
      notificationSettings.dndEnabled = Boolean(data.dndEnabled)
      notificationSettings.dndStart = data.dndStart || '00:00'
      notificationSettings.dndEnd = data.dndEnd || '00:00'
      notificationSettings.proactiveEnabled = data.proactiveEnabled !== false
    }
    ElMessage.success('通知设置已保存')
  } catch (error) {
    ElMessage.error(error.message || '保存通知设置失败')
  } finally {
    notificationSaving.value = false
  }
}

async function togglePush() {
  pushBusy.value = true
  try {
    if (pushEnabled.value) {
      const endpoint = await unsubscribeBrowserPush()
      if (endpoint) await unsubscribePush(endpoint)
      pushEnabled.value = false
      pushStatusText.value = '已关闭浏览器通知'
      ElMessage.success('已关闭浏览器通知')
      return
    }

    if (!pushServerEnabled.value) {
      ElMessage.warning('服务端未启用 Web Push')
      return
    }
    const info = await getPushPublicKey()
    const subscription = await subscribeBrowserPush(info.publicKey)
    const json = subscription.toJSON()
    await subscribePush({
      endpoint: json.endpoint,
      keys: json.keys,
      userAgent: navigator.userAgent
    })
    pushEnabled.value = true
    pushStatusText.value = '已启用浏览器通知'
    ElMessage.success('浏览器通知已启用')
  } catch (error) {
    ElMessage.error(error.message || '操作失败')
  } finally {
    pushBusy.value = false
  }
}

async function handleTestPush() {
  pushBusy.value = true
  try {
    const count = await sendTestPush()
    ElMessage.success(count > 0 ? `已发送到 ${count} 个订阅` : '当前浏览器还没有订阅')
  } catch (error) {
    ElMessage.error(error.message || '发送测试通知失败')
  } finally {
    pushBusy.value = false
  }
}

const aiAvatarInput = ref(null)
const userAvatarInput = ref(null)
const avatarUploading = ref('')

function assetUrl(path) {
  if (!path) return ''
  return /^https?:\/\//.test(path) ? path : `${ASSET_BASE_URL}${path}`
}

const aiAvatarUrl = computed(() => assetUrl(authStore.userInfo.aiAvatar))
const userAvatarUrl = computed(() => assetUrl(authStore.userInfo.avatar))

async function handleAvatarFile(event, target) {
  const file = event.target?.files?.[0]
  if (event.target) event.target.value = ''
  if (!file) return
  if (!/^image\//.test(file.type)) {
    ElMessage.warning('请选择图片文件')
    return
  }
  if (file.size > 20 * 1024 * 1024) {
    ElMessage.warning('头像不能超过 20MB')
    return
  }

  avatarUploading.value = target
  try {
    const res = await uploadAvatar(file, target)
    if (!res || String(res.code) !== '200') {
      throw new Error((res && res.msg) || '上传头像失败')
    }
    authStore.updateUserInfo(res.data || {})
    if (target === 'user' && res.data?.avatar) {
      profileForm.avatar = res.data.avatar
    }
    ElMessage.success(target === 'ai' ? 'AI 头像已更新' : '用户头像已更新')
  } catch (error) {
    ElMessage.error(error?.message || '上传头像失败')
  } finally {
    avatarUploading.value = ''
  }
}

const profileDialogVisible = ref(false)
const profileSaving = ref(false)
const profileFormRef = ref(null)
const profileForm = reactive({
  nickname: '',
  avatar: '',
  phone: '',
  gender: 0,
  birthday: ''
})

const profileRules = {
  nickname: [{ max: 50, message: '昵称不能超过 50 个字符', trigger: 'blur' }],
  avatar: [{ max: 255, message: '头像地址不能超过 255 个字符', trigger: 'blur' }],
  phone: [{ pattern: /^$|^1[3456789]\d{9}$/, message: '手机号格式错误', trigger: 'blur' }]
}

function openProfileDialog() {
  const info = authStore.userInfo || {}
  profileForm.nickname = info.nickname || ''
  profileForm.avatar = info.avatar || ''
  profileForm.phone = info.phone || ''
  profileForm.gender = Number(info.gender ?? 0)
  profileForm.birthday = info.birthday || ''
  profileDialogVisible.value = true
}

async function saveProfile() {
  if (!profileFormRef.value) return
  try {
    await profileFormRef.value.validate()
  } catch {
    return
  }

  profileSaving.value = true
  try {
    const res = await updateProfile({
      nickname: profileForm.nickname.trim(),
      avatar: profileForm.avatar.trim(),
      phone: profileForm.phone.trim(),
      gender: profileForm.gender,
      birthday: profileForm.birthday || ''
    })
    if (res && res.code === '200') {
      authStore.updateUserInfo(res.data || {})
      profileDialogVisible.value = false
      ElMessage.success('个人资料已更新')
    } else {
      ElMessage.error((res && res.msg) || '保存失败')
    }
  } catch (e) {
    if (e && e.isAuthError) return
    ElMessage.error('保存失败，请稍后重试')
  } finally {
    profileSaving.value = false
  }
}

onMounted(async () => {
  await Promise.all([loadPersona(), loadNotificationSettings(), voiceStore.loadConfig(true)])
  appVersion.value = await resolveCurrentVersion()
})

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

      <el-descriptions :column="isMobile ? 1 : 2" border>
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
        <div class="card-header account-header">
          <span class="card-title">账号信息</span>
          <el-button size="small" type="primary" plain @click="openProfileDialog">
            编辑资料
          </el-button>
        </div>
      </template>

      <el-descriptions :column="isMobile ? 1 : 2" border>
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
        <div class="card-header">
          <span class="card-title">头像</span>
          <span class="card-sub">PNG / JPEG / WebP / GIF，单文件不超过 20MB</span>
        </div>
      </template>

      <div class="avatar-row">
        <div class="avatar-field">
          <el-avatar :size="56" :src="aiAvatarUrl || undefined">
            <span v-if="!aiAvatarUrl">角</span>
          </el-avatar>
          <div class="avatar-field-main">
            <span class="avatar-label">AI 头像</span>
            <span class="avatar-hint">聊天消息里角色使用的头像</span>
            <el-button
              size="small"
              :loading="avatarUploading === 'ai'"
              @click="aiAvatarInput?.click()"
            >
              上传 AI 头像
            </el-button>
            <input
              ref="aiAvatarInput"
              type="file"
              accept="image/png,image/jpeg,image/webp,image/gif"
              hidden
              @change="handleAvatarFile($event, 'ai')"
            />
          </div>
        </div>

        <div class="avatar-field">
          <el-avatar :size="56" :src="userAvatarUrl || undefined">
            <span v-if="!userAvatarUrl">{{ authStore.displayName.slice(0, 1) }}</span>
          </el-avatar>
          <div class="avatar-field-main">
            <span class="avatar-label">用户头像</span>
            <span class="avatar-hint">聊天消息与顶栏使用的头像</span>
            <el-button
              size="small"
              :loading="avatarUploading === 'user'"
              @click="userAvatarInput?.click()"
            >
              上传用户头像
            </el-button>
            <input
              ref="userAvatarInput"
              type="file"
              accept="image/png,image/jpeg,image/webp,image/gif"
              hidden
              @change="handleAvatarFile($event, 'user')"
            />
          </div>
        </div>
      </div>
    </el-card>

    <el-card shadow="never">
      <template #header>
        <div class="card-header">
          <span class="card-title">人格</span>
          <span class="card-sub">人格决定“怎么说”，关系阶段决定“能说多深”</span>
        </div>
      </template>

      <el-alert
        v-if="personaLoadError"
        :title="personaLoadError"
        type="warning"
        :closable="false"
        show-icon
        class="persona-alert"
      />

      <template v-else>
        <div class="persona-grid">
          <div
            v-for="option in personaOptions"
            :key="option.code"
            class="persona-card"
            :class="{ active: personaForm.personaCode === option.code }"
            @click="selectPersona(option)"
          >
            <div class="persona-name">{{ option.name }}</div>
            <div class="persona-desc">{{ option.description }}</div>
            <div class="persona-sample">“{{ option.sample }}”</div>
          </div>
        </div>

        <div class="trait-list">
          <div v-for="trait in traitDefinitions" :key="trait.key" class="trait-row">
            <div class="trait-label">{{ trait.label }}</div>
            <el-slider
              v-model="personaForm.traits[trait.key]"
              :min="0"
              :max="100"
              :step="5"
            />
            <div class="trait-value">{{ personaForm.traits[trait.key] }}</div>
            <div class="trait-hint">{{ trait.hint }}</div>
          </div>
        </div>
      </template>

      <div class="persona-footer">
        <span class="persona-tip">
          保存后作为你的默认人格；新建会话时可以单独选择。
        </span>
        <el-button v-if="personaLoadError" @click="loadPersona">重试</el-button>
        <el-button v-else type="primary" :loading="personaSaving" @click="savePersona">
          保存人格
        </el-button>
      </div>
    </el-card>

    <el-card shadow="never">
      <template #header>
        <div class="card-header">
          <span class="card-title">通知与免打扰</span>
          <span class="card-sub">免打扰时段只保留站内信，不弹窗、不推送</span>
        </div>
      </template>

      <div class="notification-row">
        <div class="notification-label">免打扰</div>
        <el-switch v-model="notificationSettings.dndEnabled" />
        <el-time-picker
          v-model="notificationSettings.dndStart"
          format="HH:mm"
          value-format="HH:mm"
          :disabled="!notificationSettings.dndEnabled"
          placeholder="开始"
          class="time-picker"
        />
        <span class="time-sep">至</span>
        <el-time-picker
          v-model="notificationSettings.dndEnd"
          format="HH:mm"
          value-format="HH:mm"
          :disabled="!notificationSettings.dndEnabled"
          placeholder="结束"
          class="time-picker"
        />
      </div>

      <div class="notification-row">
        <div class="notification-label">允许主动消息</div>
        <el-switch v-model="notificationSettings.proactiveEnabled" />
        <span class="notification-hint">角色主动发消息的总开关；任务到点提醒不受影响</span>
      </div>

      <div class="notification-actions">
        <el-button type="primary" :loading="notificationSaving" @click="saveNotificationSettings">
          保存通知设置
        </el-button>
      </div>

      <el-divider />

      <div class="push-row">
        <div>
          <div class="push-title">浏览器通知（Web Push）</div>
          <div class="push-status">{{ pushStatusText }}</div>
          <div class="push-hint">
            需要 HTTPS 或 localhost；关闭页面后仍可能收到通知，取决于浏览器。
          </div>
        </div>
        <div class="push-actions">
          <el-button
            :loading="pushBusy"
            :disabled="!pushSupported || !pushServerEnabled"
            @click="togglePush"
          >
            {{ pushEnabled ? '关闭浏览器通知' : '启用浏览器通知' }}
          </el-button>
          <el-button :loading="pushBusy" :disabled="!pushEnabled" @click="handleTestPush">
            发送测试通知
          </el-button>
        </div>
      </div>
    </el-card>

    <el-card shadow="never">
      <template #header>
        <div class="card-header">
          <span class="card-title">语音</span>
          <span class="card-sub">角色朗读回复；音量调到 0 时口型仍会动</span>
        </div>
      </template>

      <el-form label-width="88px" class="voice-form">
        <el-form-item label="语音朗读">
          <el-switch v-model="voiceEnabled" :disabled="!voiceStore.available" />
          <span v-if="!voiceStore.available" class="voice-hint">
            {{ voiceStore.loadError || '服务器未开放语音合成' }}
          </span>
          <span v-else-if="!voiceStore.configured" class="voice-hint">
            未配置密钥：入口已开放，试听和朗读会返回 TTS_NOT_CONFIGURED
          </span>
        </el-form-item>

        <el-form-item label="音色">
          <el-select
            v-model="voiceSpeaker"
            :disabled="!voiceStore.available"
            placeholder="默认音色"
            style="width: 100%"
          >
            <el-option
              v-for="voice in voiceStore.voices"
              :key="voice.id"
              :value="voice.id"
              :label="voice.name || voice.id"
            />
          </el-select>
        </el-form-item>

        <el-form-item label="音量">
          <el-slider
            v-model="voiceVolume"
            :min="0"
            :max="100"
            :disabled="!voiceStore.available"
            style="width: 100%"
          />
        </el-form-item>

        <el-form-item>
          <div class="voice-actions">
            <el-button
              :loading="voicePreviewing"
              :disabled="!voiceStore.available"
              @click="handleVoicePreview"
            >
              试听
            </el-button>
            <span v-if="voiceStore.quotaText" class="voice-hint">
              {{ voiceStore.quotaText }}
            </span>
          </div>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card shadow="never">
      <template #header>
        <div class="card-header">
          <span class="card-title">应用更新</span>
          <span class="card-sub">当前版本 {{ appVersion }}</span>
        </div>
      </template>
      <p class="profile-note">
        桌面端可检查并下载最新安装包；网页端会随服务器自动更新。
      </p>
      <el-button type="primary" plain @click="router.push('/update')">
        检查更新
      </el-button>
    </el-card>

    <el-card shadow="never">
      <template #header>
        <span class="card-title">登录状态</span>
      </template>
      <p class="logout-tip">退出后会同时通知后端撤销当前 token，并清理本地登录态。</p>
      <el-button type="danger" plain @click="handleLogout">退出登录</el-button>
    </el-card>

    <el-dialog
      v-model="profileDialogVisible"
      title="编辑个人资料"
      width="min(480px, 92vw)"
      destroy-on-close
    >
      <el-form
        ref="profileFormRef"
        :model="profileForm"
        :rules="profileRules"
        :label-position="isMobile ? 'top' : 'right'"
        :label-width="isMobile ? 'auto' : '80px'"
      >
        <el-form-item label="昵称" prop="nickname">
          <el-input v-model="profileForm.nickname" maxlength="50" show-word-limit />
        </el-form-item>
        <el-form-item label="头像" prop="avatar">
          <el-input
            v-model="profileForm.avatar"
            placeholder="图片 URL 或 /files/... 路径"
            maxlength="255"
          />
        </el-form-item>
        <el-form-item label="手机号" prop="phone">
          <el-input v-model="profileForm.phone" maxlength="11" placeholder="选填" />
        </el-form-item>
        <el-form-item label="性别" prop="gender">
          <el-radio-group v-model="profileForm.gender">
            <el-radio :value="0">未知</el-radio>
            <el-radio :value="1">男</el-radio>
            <el-radio :value="2">女</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="生日" prop="birthday">
          <el-date-picker
            v-model="profileForm.birthday"
            type="date"
            value-format="YYYY-MM-DD"
            placeholder="选择生日"
            style="width: 100%"
          />
        </el-form-item>
        <el-alert
          type="info"
          :closable="false"
          show-icon
          title="账号、邮箱、用户类型和状态不可自行修改"
        />
      </el-form>
      <template #footer>
        <el-button @click="profileDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="profileSaving" @click="saveProfile">
          保存
        </el-button>
      </template>
    </el-dialog>
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

.voice-form {
  max-width: 640px;
}

.avatar-row {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(240px, 1fr));
  gap: 16px;
}

.avatar-field {
  display: flex;
  align-items: flex-start;
  gap: 12px;
  padding: 12px;
  border: 1px solid var(--el-border-color-light);
  border-radius: 8px;
}

.avatar-field-main {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 4px;
}

.avatar-label {
  font-weight: 600;
}

.avatar-hint {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.voice-hint {
  margin-left: 10px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.voice-actions {
  display: flex;
  align-items: center;
  gap: 8px;
}

.profile-note {
  margin: 16px 0 0;
  font-size: 13px;
  line-height: 1.8;
  color: var(--el-text-color-secondary);
}

.card-header {
  display: flex;
  align-items: baseline;
  gap: 10px;
}

.account-header {
  align-items: center;
  justify-content: space-between;
}

.card-sub {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.persona-alert {
  margin-bottom: 4px;
}

.persona-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(180px, 1fr));
  gap: 12px;
}

.persona-card {
  padding: 14px;
  border: 1px solid var(--el-border-color);
  border-radius: 10px;
  background: #fff;
  cursor: pointer;
  transition: border-color 0.2s ease, box-shadow 0.2s ease;
}

.persona-card:hover {
  border-color: var(--el-color-primary-light-5);
}

.persona-card.active {
  border-color: var(--el-color-primary);
  box-shadow: 0 0 0 2px var(--el-color-primary-light-8);
}

.persona-name {
  font-weight: 600;
}

.persona-desc {
  margin-top: 6px;
  font-size: 12px;
  color: var(--el-text-color-regular);
}

.persona-sample {
  margin-top: 10px;
  font-size: 12px;
  line-height: 1.6;
  color: var(--el-text-color-secondary);
}

.trait-list {
  display: flex;
  flex-direction: column;
  gap: 6px;
  margin-top: 18px;
}

.trait-row {
  display: grid;
  grid-template-columns: 80px minmax(0, 1fr) 48px;
  align-items: center;
  gap: 12px;
}

.trait-label {
  font-size: 13px;
}

.trait-value {
  text-align: right;
  font-size: 13px;
  font-weight: 600;
}

.trait-hint {
  grid-column: 2 / 4;
  margin-top: -4px;
  font-size: 11px;
  color: var(--el-text-color-placeholder);
}

.persona-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-top: 18px;
}

.persona-tip {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.notification-row {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 12px;
}

.notification-label {
  width: 100px;
  font-size: 13px;
}

.time-picker {
  width: 120px;
}

.time-sep {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.notification-hint {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.notification-actions {
  display: flex;
  justify-content: flex-end;
  margin-top: 8px;
}

.push-row {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
}

.push-title {
  font-size: 14px;
  font-weight: 600;
}

.push-status {
  margin-top: 6px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.push-hint {
  margin-top: 4px;
  font-size: 11px;
  color: var(--el-text-color-placeholder);
}

.push-actions {
  display: flex;
  flex-shrink: 0;
  gap: 8px;
}

@media (max-width: 900px) {
  .card-header {
    flex-wrap: wrap;
  }

  .persona-grid {
    grid-template-columns: 1fr;
  }

  .trait-row {
    grid-template-columns: 64px minmax(0, 1fr) 40px;
    gap: 8px;
  }

  .persona-footer {
    flex-wrap: wrap;
    justify-content: flex-start;
  }

  .notification-row {
    flex-wrap: wrap;
  }

  .notification-label {
    width: 100%;
  }

  .time-picker {
    flex: 1;
    min-width: 110px;
  }

  .notification-actions {
    justify-content: stretch;
  }

  .notification-actions .el-button {
    width: 100%;
  }

  .push-row {
    flex-direction: column;
  }

  .push-actions {
    flex-wrap: wrap;
    width: 100%;
  }

  .push-actions .el-button {
    flex: 1;
    min-height: 44px;
    margin-left: 0;
  }
}

.logout-tip {
  margin: 0 0 16px;
  font-size: 14px;
  color: var(--el-text-color-secondary);
}
</style>
