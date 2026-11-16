<script setup>
import { onBeforeUnmount, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { requestPasswordReset, resetPassword } from '@/shared/api/user'
import { sm3Encrypt } from '@/shared/utils/sm3'

const router = useRouter()
const formRef = ref(null)
const loading = ref(false)
const sending = ref(false)
const cooldown = ref(0)
let cooldownTimer = null

const form = reactive({
  email: '',
  code: '',
  newPassword: '',
  confirmPassword: ''
})

function validateConfirmPassword(rule, value, callback) {
  if (!value) {
    callback(new Error('请再次输入新密码'))
    return
  }
  if (value !== form.newPassword) {
    callback(new Error('两次输入的密码不一致'))
    return
  }
  callback()
}

const rules = {
  email: [
    { required: true, message: '请输入注册邮箱', trigger: 'blur' },
    { type: 'email', message: '邮箱格式不正确', trigger: 'blur' }
  ],
  code: [
    { required: true, message: '请输入验证码', trigger: 'blur' },
    { pattern: /^\d{6}$/, message: '验证码为 6 位数字', trigger: 'blur' }
  ],
  newPassword: [
    { required: true, message: '请输入新密码', trigger: 'blur' },
    { min: 6, max: 64, message: '密码长度需在 6 到 64 位之间', trigger: 'blur' }
  ],
  confirmPassword: [
    { required: true, validator: validateConfirmPassword, trigger: 'blur' }
  ]
}

function startCooldown(seconds = 60) {
  cooldown.value = seconds
  if (cooldownTimer) clearInterval(cooldownTimer)
  cooldownTimer = setInterval(() => {
    cooldown.value -= 1
    if (cooldown.value <= 0) {
      clearInterval(cooldownTimer)
      cooldownTimer = null
      cooldown.value = 0
    }
  }, 1000)
}

async function sendCode() {
  if (!formRef.value || cooldown.value > 0) return
  try {
    const valid = await formRef.value.validateField('email')
    if (valid === false) return
  } catch {
    return
  }

  sending.value = true
  try {
    const res = await requestPasswordReset(form.email.trim())
    if (res && res.code === '200') {
      ElMessage.success('如果该邮箱已注册，验证码已发送，请检查收件箱和垃圾箱')
      startCooldown(60)
    } else {
      ElMessage.error((res && res.msg) || '验证码发送失败，请稍后重试')
    }
  } catch {
    ElMessage.error('验证码发送失败，请稍后重试')
  } finally {
    sending.value = false
  }
}

async function handleReset() {
  if (!formRef.value) return
  try {
    await formRef.value.validate()
  } catch {
    return
  }

  loading.value = true
  try {
    const res = await resetPassword({
      email: form.email.trim(),
      code: form.code.trim(),
      newPassword: sm3Encrypt(form.newPassword.trim()),
      confirmPassword: sm3Encrypt(form.confirmPassword.trim())
    })
    if (res && res.code === '200') {
      ElMessage.success('密码已重置，请使用新密码登录')
      router.replace('/login')
    } else {
      ElMessage.error((res && res.msg) || '重置失败，请检查验证码')
    }
  } catch {
    ElMessage.error('重置失败，请稍后重试')
  } finally {
    loading.value = false
  }
}

function goLogin() {
  router.replace('/login')
}

onBeforeUnmount(() => {
  if (cooldownTimer) clearInterval(cooldownTimer)
})
</script>

<template>
  <div class="forgot-page">
    <div class="forgot-card">
      <h2 class="title">找回密码</h2>
      <p class="subtitle">通过注册邮箱验证身份，验证码 10 分钟内有效</p>

      <el-form ref="formRef" :model="form" :rules="rules" size="large">
        <el-form-item prop="email">
          <el-input v-model="form.email" placeholder="请输入注册邮箱" clearable />
        </el-form-item>

        <el-form-item prop="code">
          <div class="code-row">
            <el-input
              v-model="form.code"
              placeholder="6 位验证码"
              maxlength="6"
              clearable
            />
            <el-button :loading="sending" :disabled="cooldown > 0" @click="sendCode">
              {{ cooldown > 0 ? `${cooldown}s 后重发` : '发送验证码' }}
            </el-button>
          </div>
        </el-form-item>

        <el-form-item prop="newPassword">
          <el-input
            v-model="form.newPassword"
            type="password"
            placeholder="请输入新密码（至少 6 位）"
            show-password
          />
        </el-form-item>

        <el-form-item prop="confirmPassword">
          <el-input
            v-model="form.confirmPassword"
            type="password"
            placeholder="请再次输入新密码"
            show-password
            @keyup.enter="handleReset"
          />
        </el-form-item>

        <el-form-item>
          <el-button
            type="primary"
            class="submit-btn"
            :loading="loading"
            @click="handleReset"
          >
            重置密码
          </el-button>
        </el-form-item>
      </el-form>

      <div class="back-link">
        <el-link type="primary" @click="goLogin">返回登录</el-link>
      </div>
    </div>
  </div>
</template>

<style scoped>
.forgot-page {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background: #f5f7fa;
}

.forgot-card {
  width: 400px;
  padding: 40px 36px 28px;
  border-radius: 8px;
  background: #fff;
  box-shadow: 0 4px 20px rgba(0, 0, 0, 0.08);
}

.title {
  margin: 0 0 10px;
  text-align: center;
  font-size: 22px;
  color: #303133;
}

.subtitle {
  margin: 0 0 26px;
  text-align: center;
  font-size: 13px;
  line-height: 1.6;
  color: #909399;
}

.code-row {
  display: flex;
  gap: 12px;
  width: 100%;
}

.code-row .el-input {
  flex: 1;
}

.code-row .el-button {
  flex: 0 0 120px;
}

.submit-btn {
  width: 100%;
}

.back-link {
  margin-top: 4px;
  text-align: center;
  font-size: 14px;
}

@media (max-width: 900px) {
  .forgot-page {
    min-height: 100dvh;
    padding: 24px 16px;
  }

  .forgot-card {
    width: 100%;
    max-width: 400px;
    padding: 28px 20px 24px;
    box-shadow: none;
  }
}

@media (max-width: 480px) {
  .code-row {
    flex-direction: column;
    gap: 8px;
  }

  .code-row .el-button {
    flex: none;
    width: 100%;
    min-height: 44px;
  }
}
</style>
