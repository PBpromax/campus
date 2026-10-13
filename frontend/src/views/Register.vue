
<template>
  <div class="register-page">
    <div class="register-brand">
      <div class="brand-content">
        <div class="brand-icon">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5">
            <path d="M17 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2"/>
            <circle cx="9" cy="7" r="4"/>
            <path d="M23 21v-2a4 4 0 0 0-3-3.87"/>
            <path d="M16 3.13a4 4 0 0 1 0 7.75"/>
          </svg>
        </div>
        <h1>加入我们</h1>
        <p>注册成为校园互助平台的一员，开始你的互助之旅</p>
      </div>
      <div class="brand-decoration">
        <div class="circle c1"></div>
        <div class="circle c2"></div>
      </div>
    </div>

    <div class="register-form-side">
      <div class="form-wrapper">
        <div class="form-header">
          <h2>创建账号</h2>
          <p>填写以下信息完成注册</p>
        </div>

        <el-form :model="form" class="register-form" @submit.prevent="handleRegister">
          <el-form-item>
            <el-input v-model="form.username" placeholder="请输入用户名" size="large" :prefix-icon="User" />
          </el-form-item>

          <el-form-item>
            <el-input v-model="form.password" type="password" show-password placeholder="请设置密码" size="large" :prefix-icon="Lock" />
          </el-form-item>

          <el-form-item>
            <el-input v-model="form.confirmPassword" type="password" show-password placeholder="请确认密码" size="large" :prefix-icon="Lock" />
          </el-form-item>

          <el-button
            type="primary"
            size="large"
            @click="handleRegister"
            :loading="loading"
            class="submit-btn"
          >
            注 册
          </el-button>
        </el-form>

        <div class="form-footer">
          <span>已有账号？</span>
          <a class="link" @click="goToLogin">立即登录</a>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { register } from '../api/user'
import { ElMessage } from 'element-plus'
import { User, Lock } from '@element-plus/icons-vue'

const router = useRouter()
const form = ref({ username: '', password: '', confirmPassword: '' })
const loading = ref(false)

const handleRegister = async () => {
  if (!form.value.username || !form.value.password || !form.value.confirmPassword) {
    ElMessage.warning('请填写完整信息')
    return
  }
  if (form.value.password !== form.value.confirmPassword) {
    ElMessage.warning('两次输入的密码不一致')
    return
  }
  if (form.value.password.length < 6) {
    ElMessage.warning('密码长度不能少于6位')
    return
  }
  loading.value = true
  try {
    const response = await register({
      username: form.value.username,
      password: form.value.password
    })
    localStorage.setItem('user', JSON.stringify(response.user))
    localStorage.setItem('token', response.token)
    ElMessage.success('注册成功')
    router.push('/complete-info')
  } catch (error) {
    ElMessage.error(error.message || '注册失败')
  } finally {
    loading.value = false
  }
}

const goToLogin = () => router.push('/login')
</script>

<style scoped>
.register-page {
  display: flex;
  min-height: 100vh;
}

.register-brand {
  flex: 1;
  background: linear-gradient(135deg, #312e81 0%, #4f46e5 50%, #7c3aed 100%);
  display: flex;
  align-items: center;
  justify-content: center;
  position: relative;
  overflow: hidden;
  padding: 60px;
}

.brand-content {
  position: relative;
  z-index: 2;
  color: white;
  max-width: 480px;
}

.brand-icon {
  width: 64px;
  height: 64px;
  margin-bottom: 32px;
  color: #c4b5fd;
}

.brand-icon svg { width: 100%; height: 100%; }

.brand-content h1 {
  font-size: 42px;
  font-weight: 800;
  margin-bottom: 16px;
}

.brand-content p {
  font-size: 18px;
  opacity: 0.8;
  line-height: 1.6;
}

.brand-decoration {
  position: absolute;
  inset: 0;
  z-index: 1;
}

.circle {
  position: absolute;
  border-radius: 50%;
  border: 1px solid rgba(255,255,255,0.08);
}
.c1 { width: 500px; height: 500px; bottom: -150px; right: -100px; }
.c2 { width: 300px; height: 300px; top: -80px; left: -60px; }

.register-form-side {
  width: 520px;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 60px;
  background: white;
}

.form-wrapper {
  width: 100%;
  max-width: 380px;
}

.form-header {
  margin-bottom: 36px;
}

.form-header h2 {
  font-size: 28px;
  font-weight: 700;
  color: var(--text-primary);
  margin-bottom: 8px;
}

.form-header p {
  font-size: 15px;
  color: var(--text-muted);
}

.register-form :deep(.el-input__wrapper) {
  border-radius: var(--radius);
  padding: 4px 12px;
  box-shadow: 0 0 0 1px var(--border) inset;
}

.register-form :deep(.el-input__wrapper:hover) {
  box-shadow: 0 0 0 1px var(--primary-light) inset;
}

.register-form :deep(.el-input__wrapper.is-focus) {
  box-shadow: 0 0 0 2px var(--primary) inset;
}

.submit-btn {
  width: 100%;
  height: 48px;
  border-radius: var(--radius);
  font-size: 16px;
  font-weight: 600;
  margin-top: 8px;
  background: linear-gradient(135deg, var(--primary) 0%, var(--primary-light) 100%);
  border: none;
  transition: all 0.3s ease;
}

.submit-btn:hover {
  transform: translateY(-1px);
  box-shadow: 0 8px 20px rgba(79, 70, 229, 0.35);
}

.form-footer {
  text-align: center;
  margin-top: 28px;
  font-size: 14px;
  color: var(--text-muted);
}

.form-footer .link {
  color: var(--primary);
  font-weight: 600;
  cursor: pointer;
  margin-left: 4px;
  transition: color 0.2s;
}

.form-footer .link:hover {
  color: var(--primary-dark);
}

@media (max-width: 900px) {
  .register-brand { display: none; }
  .register-form-side { width: 100%; }
}
</style>
