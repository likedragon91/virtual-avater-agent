<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useAuthStore } from '@/shared/stores/auth'
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

onMounted(async () => {
  await Promise.all([loadPersona(), loadNotificationSettings()])
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
        <span class="card-title">更多设置</span>
      </template>
      <el-empty description="音色、模型、隐私与数据管理将在后续接入" />
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

.card-header {
  display: flex;
  align-items: baseline;
  gap: 10px;
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

@media (max-width: 720px) {
  .notification-row {
    flex-wrap: wrap;
  }

  .push-row {
    flex-direction: column;
  }
}

.logout-tip {
  margin: 0 0 16px;
  font-size: 14px;
  color: var(--el-text-color-secondary);
}
</style>
