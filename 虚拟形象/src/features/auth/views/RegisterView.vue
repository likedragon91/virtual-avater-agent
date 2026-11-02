<script setup>
import { ref, reactive } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { register } from '@/shared/api/user'
import { sm3Encrypt } from '@/shared/utils/sm3'

const router = useRouter()
const formRef = ref(null)
const loading = ref(false)

const form = reactive({
  username: '',
  email: '',
  nickname: '',
  phone: '',
  password: '',
  confirmPassword: '',
  gender: 0
})

// 校验规则与后端 UserRegisterCommandDTO 保持一致
const rules = {
  username: [
    { required: true, message: '请输入用户名', trigger: 'blur' },
    { min: 3, max: 50, message: '用户名长度必须在 3 到 50 之间', trigger: 'blur' },
    { pattern: /^[a-zA-Z0-9_]+$/, message: '用户名只能包含字母、数字和下划线', trigger: 'blur' }
  ],
  email: [
    { required: true, message: '请输入邮箱', trigger: 'blur' },
    { type: 'email', message: '邮箱格式错误', trigger: 'blur' },
    { max: 50, message: '邮箱长度不能超过 50', trigger: 'blur' }
  ],
  nickname: [
    { max: 50, message: '昵称长度不能超过 50', trigger: 'blur' }
  ],
  phone: [
    { pattern: /^1[3456789]\d{9}$/, message: '手机号格式错误', trigger: 'blur' }
  ],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    { min: 6, max: 50, message: '密码长度必须在 6 到 50 之间', trigger: 'blur' }
  ],
  confirmPassword: [
    { required: true, message: '请再次输入密码', trigger: 'blur' },
    {
      validator: (rule, value, callback) => {
        if (value !== form.password) {
          callback(new Error('两次输入的密码不一致'))
        } else {
          callback()
        }
      },
      trigger: 'blur'
    }
  ]
}

async function handleRegister() {
  if (!formRef.value) return

  // 表单校验（校验失败会抛错，直接返回）
  try {
    await formRef.value.validate()
  } catch {
    return
  }

  loading.value = true
  try {
    // 后端统一返回 { code, msg, data }，code 为字符串 "200" 表示成功
    const res = await register({
      username: form.username,
      email: form.email,
      nickname: form.nickname || null,
      phone: form.phone || null,
      // 密码先做 SM3 摘要再传输，服务端再用随机盐做一次 SM3 后入库
      password: sm3Encrypt(form.password.trim()),
      confirmPassword: sm3Encrypt(form.confirmPassword.trim()),
      gender: form.gender,
      userType: 1 // 公开注册一律为普通用户
    })

    if (res && res.code === '200') {
      ElMessage.success('注册成功，请登录')
      router.replace('/login')
    } else {
      ElMessage.error((res && res.msg) || '注册失败')
    }
  } catch {
    ElMessage.error('注册请求失败，请检查网络或接口地址')
  } finally {
    loading.value = false
  }
}

function goLogin() {
  router.push('/login')
}
</script>

<template>
  <div class="register-page">
    <el-splitter class="register-splitter">
      <el-splitter-panel size="30%">
        <div class="left-panel">
          <div class="brand-block">
            <h1 class="brand-title">虚拟形象</h1>
            <p class="brand-subtitle">创建账号，开始陪伴</p>
          </div>
        </div>
      </el-splitter-panel>
      <el-splitter-panel :min="360">
        <div class="right-panel">
          <div class="register-card">
            <h2 class="title">用户注册</h2>
            <el-form
              ref="formRef"
              :model="form"
              :rules="rules"
              size="large"
              @keyup.enter="handleRegister"
            >
              <el-form-item prop="username">
                <el-input v-model="form.username" placeholder="请输入用户名（3-50位字母、数字或下划线）" clearable />
              </el-form-item>

              <el-form-item prop="email">
                <el-input v-model="form.email" placeholder="请输入邮箱" clearable />
              </el-form-item>

              <el-form-item prop="nickname">
                <el-input v-model="form.nickname" placeholder="请输入昵称（选填）" clearable />
              </el-form-item>

              <el-form-item prop="phone">
                <el-input v-model="form.phone" placeholder="请输入手机号（选填）" clearable />
              </el-form-item>

              <el-form-item prop="password">
                <el-input
                  v-model="form.password"
                  type="password"
                  placeholder="请输入密码（6-50位）"
                  show-password
                />
              </el-form-item>

              <el-form-item prop="confirmPassword">
                <el-input
                  v-model="form.confirmPassword"
                  type="password"
                  placeholder="请再次输入密码"
                  show-password
                />
              </el-form-item>

              <el-form-item prop="gender">
                <el-radio-group v-model="form.gender">
                  <el-radio :value="0">未知</el-radio>
                  <el-radio :value="1">男</el-radio>
                  <el-radio :value="2">女</el-radio>
                </el-radio-group>
              </el-form-item>

              <el-form-item>
                <el-button
                  type="primary"
                  class="register-btn"
                  :loading="loading"
                  @click="handleRegister"
                >
                  注 册
                </el-button>
              </el-form-item>
            </el-form>

            <div class="login-link">
              已有账号？
              <el-link type="primary" @click="goLogin">立即登录</el-link>
            </div>
          </div>
        </div>
      </el-splitter-panel>
    </el-splitter>
  </div>
</template>

<style scoped>
.register-page {
  height: 100vh;
  width: 100%;
}

.register-splitter {
  height: 100%;
}

.left-panel {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 100%;
  background-color: #141c2b;
  color: #fff;
}

.brand-block {
  text-align: center;
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

.register-card {
  width: 380px;
  padding: 32px 36px;
  border-radius: 8px;
  box-shadow: 0 4px 20px rgba(0, 0, 0, 0.08);
}

.title {
  margin: 0 0 24px;
  text-align: center;
  font-size: 22px;
  color: #303133;
}

.register-btn {
  width: 100%;
}

.login-link {
  margin-top: 8px;
  text-align: center;
  font-size: 14px;
  color: #909399;
}
</style>
