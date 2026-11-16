<script setup>
import { ref, reactive, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { login, getCaptcha } from '@/shared/api/user'
import { sm3Encrypt } from '@/shared/utils/sm3'
import { useMediaQuery } from '@/shared/composables/useMediaQuery'
import { useAuthStore } from '@/shared/stores/auth'

const router = useRouter()
const authStore = useAuthStore()
const { matches: isMobile } = useMediaQuery('(max-width: 900px)')
const formRef = ref(null)
const loading = ref(false)

const form = reactive({
  username: '',
  password: '',
  captcha: ''
})

const rules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }],
  captcha: [{ required: true, message: '请输入验证码', trigger: 'blur' }]
}

// 后端验证码：captchaId 用于登录时回传，captchaImage 是 Base64 图片
const captchaId = ref('')
const captchaImage = ref('')
const captchaLoading = ref(false)
const captchaError = ref(false)

async function refreshCaptcha() {
  if (captchaLoading.value) return
  captchaLoading.value = true
  captchaError.value = false
  // 旧验证码已被后端一次性消费，刷新期间先清空，避免失败后拿旧 ID 去登录
  captchaId.value = ''
  captchaImage.value = ''
  try {
    const res = await getCaptcha()
    if (res && res.code === '200' && res.data) {
      captchaId.value = res.data.captchaId
      captchaImage.value = res.data.captchaImage
    } else {
      captchaError.value = true
      ElMessage.error((res && res.msg) || '验证码加载失败，请点击重试')
    }
  } catch {
    captchaError.value = true
    ElMessage.error('验证码加载失败，请检查网络或接口地址')
  } finally {
    captchaLoading.value = false
    // 每次刷新都清空已输入的验证码
    form.captcha = ''
  }
}
onMounted(refreshCaptcha)

function goRegister() {
  router.push('/register')
}

function goForgot() {
  router.push('/forgot-password')
}

async function handleLogin() {
  if (!formRef.value) return

  // 表单校验（校验失败会抛错，直接返回）
  try {
    await formRef.value.validate()
  } catch {
    return
  }

  // 验证码为空或图片未加载成功时，不发起请求
  if (!captchaId.value) {
    ElMessage.error('验证码未加载成功，请点击刷新')
    refreshCaptcha()
    return
  }

  loading.value = true
  try {
    // 验证码由后端校验：captchaId 原样回传，captchaCode 为用户输入
    // 后端统一返回 { code, msg, data }，code 为字符串 "200" 表示成功
    const res = await login({
      username: form.username,
      // 密码先做 SM3 摘要再传输，服务端用该摘要加盐再做一次 SM3 与库中哈希比对
      password: sm3Encrypt(form.password.trim()),
      captchaId: captchaId.value,
      captchaCode: form.captcha
    })

    if (res && res.code === '200') {
      const { token, userInfo, roleType } = res.data || {}
      // 后端 userInfo.userType 已是数字（1-普通 2-管理员 3-VIP），直接保留；仅在缺失时用 roleType 兜底
      const storedUserInfo = {
        ...(userInfo || {}),
        userType: userInfo?.userType ?? (roleType === '管理员' ? 2 : 1)
      }
      // 统一保存登录态（token 同时被后端写入 Redis 白名单）
      authStore.setSession(token, storedUserInfo)
      ElMessage.success('登录成功')

      // 支持被 401 拦截后登录回跳原页面
      const redirect = router.currentRoute.value.query.redirect
      const target =
        typeof redirect === 'string' && redirect.startsWith('/') && !redirect.startsWith('//')
          ? redirect
          : '/chat'
      router.replace(target)
    } else {
      ElMessage.error((res && res.msg) || '登录失败')
      // 后端验证码一次性使用，任何失败（验证码错误/过期、密码错误）后都要换一张
      refreshCaptcha()
    }
  } catch {
    ElMessage.error('登录请求失败，请检查网络或接口地址')
    // 请求失败一般也已消耗验证码，刷新避免重复提交同一个 ID
    refreshCaptcha()
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="login-page">
    <el-splitter class="login-splitter">
      <el-splitter-panel v-if="!isMobile" size="30%">
        <div class="left-panel">
          <div class="brand-block">
            <div class="pixel-avatar">
              <span class="hair" />
              <span class="face">
                <i class="eye left" />
                <i class="eye right" />
                <i class="mouth" />
              </span>
            </div>
            <h1 class="brand-title">虚拟形象</h1>
            <p class="brand-subtitle">你的像素陪伴助手</p>
          </div>
        </div>
      </el-splitter-panel>
      <el-splitter-panel :min="isMobile ? 0 : 320">
        <div class="right-panel">
          <div v-if="isMobile" class="mobile-brand">虚拟形象</div>
          <div class="login-card">
            <h2 class="title">用户登录</h2>
            <el-form
              ref="formRef"
              :model="form"
              :rules="rules"
              size="large"
              @keyup.enter="handleLogin"
            >
              <el-form-item prop="username">
                <el-input v-model="form.username" placeholder="请输入用户名" clearable />
              </el-form-item>

              <el-form-item prop="password">
                <el-input
                  v-model="form.password"
                  type="password"
                  placeholder="请输入密码"
                  show-password
                />
              </el-form-item>

              <el-form-item prop="captcha">
                <div class="captcha-row">
                  <el-input v-model="form.captcha" placeholder="请输入验证码" />
                  <div
                    class="captcha-code"
                    :class="{ 'is-error': captchaError }"
                    title="点击刷新验证码"
                    @click="refreshCaptcha"
                  >
                    <img
                      v-if="captchaImage && !captchaError"
                      :src="captchaImage"
                      alt="验证码"
                      @error="captchaError = true"
                    />
                    <span v-else>{{ captchaLoading ? '加载中...' : '点击重试' }}</span>
                  </div>
                </div>
              </el-form-item>

              <el-form-item>
                <el-button
                  type="primary"
                  class="login-btn"
                  :loading="loading"
                  @click="handleLogin"
                >
                  登 录
                </el-button>
              </el-form-item>
            </el-form>

            <div class="register-link">
              还没有账号？
              <el-link type="primary" @click="goRegister">立即注册</el-link>
              <span class="link-sep">|</span>
              <el-link type="info" @click="goForgot">忘记密码</el-link>
            </div>
          </div>
        </div>
      </el-splitter-panel>
    </el-splitter>
  </div>
</template>

<style scoped>
.login-page {
  height: 100vh;
  width: 100%;
}

.login-splitter {
  height: 100%;
}

.left-panel {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 100%;
  background-color: #141c2b;
  color: #fff;
  overflow: hidden;
}

.brand-block {
  text-align: center;
}

/* 纯 CSS 像素头像，占位到美术资源就绪 */
.pixel-avatar {
  position: relative;
  width: 96px;
  height: 96px;
  margin: 0 auto 28px;
  image-rendering: pixelated;
}

.pixel-avatar .hair {
  position: absolute;
  inset: 0 10px 38px;
  background: #6b4b3e;
  box-shadow: -10px 18px 0 #6b4b3e, 10px 18px 0 #6b4b3e;
}

.pixel-avatar .face {
  position: absolute;
  left: 16px;
  right: 16px;
  bottom: 0;
  height: 58px;
  background: #f2c9a0;
}

.pixel-avatar .eye {
  position: absolute;
  top: 18px;
  width: 10px;
  height: 10px;
  background: #2b2b3a;
}

.pixel-avatar .eye.left {
  left: 12px;
}

.pixel-avatar .eye.right {
  right: 12px;
}

.pixel-avatar .mouth {
  position: absolute;
  left: 50%;
  top: 38px;
  width: 16px;
  height: 4px;
  margin-left: -8px;
  background: #c96a6a;
}

.brand-title {
  margin: 0;
  font-size: 28px;
  letter-spacing: 6px;
}

.brand-subtitle {
  margin-top: 12px;
  font-size: 14px;
  color: #9fb0c9;
  letter-spacing: 2px;
}

.right-panel {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 100%;
  background-color: #fff;
}

.login-card {
  width: 360px;
  padding: 40px 36px;
  border-radius: 8px;
  box-shadow: 0 4px 20px rgba(0, 0, 0, 0.08);
}

.title {
  margin: 0 0 28px;
  text-align: center;
  font-size: 22px;
  color: #303133;
}

.captcha-row {
  display: flex;
  gap: 12px;
  width: 100%;
}

.captcha-code {
  flex: 0 0 120px;
  display: flex;
  align-items: center;
  justify-content: center;
  height: 40px;
  background: #f0f2f5;
  border-radius: 4px;
  font-size: 20px;
  font-weight: 600;
  letter-spacing: 4px;
  color: #409eff;
  cursor: pointer;
  user-select: none;
  overflow: hidden;
}

.captcha-code img {
  display: block;
  width: 100%;
  height: 100%;
  object-fit: cover;
  border-radius: 4px;
}

.captcha-code span {
  font-size: 13px;
  font-weight: 400;
  letter-spacing: 0;
}

.captcha-code.is-error {
  color: #f56c6c;
}

.login-btn {
  width: 100%;
}

.register-link {
  margin-top: 8px;
  text-align: center;
  font-size: 14px;
  color: #909399;
}

.link-sep {
  margin: 0 8px;
  color: #dcdfe6;
}

@media (max-width: 900px) {
  .login-page {
    height: 100dvh;
  }

  .right-panel {
    flex-direction: column;
    gap: 20px;
    padding: 24px 16px;
  }

  .mobile-brand {
    font-size: 20px;
    font-weight: 600;
    letter-spacing: 2px;
    color: #141c2b;
  }

  .login-card {
    width: 100%;
    max-width: 400px;
    padding: 28px 20px;
    box-shadow: none;
  }
}
</style>
