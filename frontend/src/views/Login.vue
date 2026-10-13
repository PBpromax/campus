
<template>
  <div class="login-page">
    <div class="login-brand">
      <div class="brand-content">
        <div class="brand-icon">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5">
            <path d="M17 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2"/>
            <circle cx="9" cy="7" r="4"/>
            <path d="M23 21v-2a4 4 0 0 0-3-3.87"/>
            <path d="M16 3.13a4 4 0 0 1 0 7.75"/>
          </svg>
        </div>
        <h1>校园互助平台</h1>
        <p>连接每一位同学，让校园生活更便捷</p>
        <div class="brand-features">
          <div class="feature-item">
            <div class="feature-icon">
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="20" height="20"><path d="M22 11.08V12a10 10 0 1 1-5.93-9.14"/><polyline points="22 4 12 14.01 9 11.01"/></svg>
            </div>
            <span>安全可靠</span>
          </div>
          <div class="feature-item">
            <div class="feature-icon">
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="20" height="20"><circle cx="12" cy="12" r="10"/><polyline points="12 6 12 12 16 14"/></svg>
            </div>
            <span>即时响应</span>
          </div>
          <div class="feature-item">
            <div class="feature-icon">
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="20" height="20"><path d="M17 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2"/><circle cx="9" cy="7" r="4"/><path d="M23 21v-2a4 4 0 0 0-3-3.87"/><path d="M16 3.13a4 4 0 0 1 0 7.75"/></svg>
            </div>
            <span>互助共赢</span>
          </div>
        </div>
      </div>
      <div class="brand-decoration">
        <div class="circle c1"></div>
        <div class="circle c2"></div>
        <div class="circle c3"></div>
      </div>
    </div>

    <div class="login-form-side">
      <div class="form-wrapper">
        <div class="form-header">
          <h2>欢迎回来</h2>
          <p>登录你的账号继续使用</p>
        </div>

        <el-form :model="form" class="login-form" @submit.prevent="handleLogin">
          <el-form-item>
            <el-input
              v-model="form.username"
              placeholder="请输入用户名"
              size="large"
              :prefix-icon="User"
            />
          </el-form-item>

          <el-form-item>
            <el-input
              v-model="form.password"
              type="password"
              placeholder="请输入密码"
              size="large"
              show-password
              :prefix-icon="Lock"
            />
          </el-form-item>

          <el-button
            type="primary"
            size="large"
            @click="handleLogin"
            :loading="loading"
            class="submit-btn"
          >
            登 录
          </el-button>
        </el-form>

        <div class="form-footer">
          <span>还没有账号？</span>
          <a class="link" @click="goToRegister">立即注册</a>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { login } from '../api/user'
import { ElMessage } from 'element-plus'
import { User, Lock } from '@element-plus/icons-vue'

const router = useRouter()
const form = ref({ username: '', password: '' })
const loading = ref(false)

const handleLogin = async () => {
  if (!form.value.username || !form.value.password) {
    ElMessage.warning('请填写完整信息')
    return
  }
  loading.value = true
  try {
    const response = await login({
      username: form.value.username,
      password: form.value.password
    })
    localStorage.setItem('user', JSON.stringify(response.user))
    localStorage.setItem('token', response.token)
    ElMessage.success('登录成功')
    if (!response.user.name) {
      router.push('/complete-info')
    } else {
      router.push('/home')
    }
  } catch (error) {
    ElMessage.error(error.message || '登录失败')
  } finally {
    loading.value = false
  }
}

const goToRegister = () => router.push('/register')
</script>

<style scoped>
.login-page {
  display: flex;
  min-height: 100vh;
}

.login-brand {
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

.brand-icon svg {
  width: 100%;
  height: 100%;
}

.brand-content h1 {
  font-size: 42px;
  font-weight: 800;
  line-height: 1.2;
  margin-bottom: 16px;
}

.brand-content p {
  font-size: 18px;
  opacity: 0.8;
  line-height: 1.6;
  margin-bottom: 40px;
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
.c1 { width: 600px; height: 600px; top: -200px; right: -100px; }
.c2 { width: 400px; height: 400px; bottom: -100px; left: -50px; }
.c3 { width: 200px; height: 200px; top: 50%; left: 50%; transform: translate(-50%, -50%); }

.brand-features {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.brand-features .feature-item {
  display: flex;
  align-items: center;
  gap: 12px;
  font-size: 15px;
  opacity: 0.9;
}

.feature-icon {
  width: 40px;
  height: 40px;
  border-radius: 10px;
  background: rgba(255,255,255,0.12);
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.login-form-side {
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

.login-form :deep(.el-input__wrapper) {
  border-radius: var(--radius);
  padding: 4px 12px;
  box-shadow: 0 0 0 1px var(--border) inset;
}

.login-form :deep(.el-input__wrapper:hover) {
  box-shadow: 0 0 0 1px var(--primary-light) inset;
}

.login-form :deep(.el-input__wrapper.is-focus) {
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
  text-decoration: none;
  transition: color 0.2s;
}

.form-footer .link:hover {
  color: var(--primary-dark);
}

@media (max-width: 900px) {
  .login-brand { display: none; }
  .login-form-side { width: 100%; }
}
</style>
