
<template>
  <div class="complete-page">
    <div class="complete-card">
      <div class="card-header">
        <div class="step-indicator">
          <div class="step active">1</div>
          <div class="step-line"></div>
          <div class="step active">2</div>
        </div>
        <h2>完善个人信息</h2>
        <p>补充你的个人资料，让大家更了解你</p>
      </div>

      <el-form :model="form" class="info-form" @submit.prevent="handleSubmit">
        <el-form-item>
          <el-input v-model="form.name" placeholder="请输入姓名" size="large" :prefix-icon="User" />
        </el-form-item>

        <el-form-item>
          <el-input v-model="form.studentId" placeholder="请输入学号" size="large" :prefix-icon="User" />
        </el-form-item>

        <el-form-item>
          <el-input v-model="form.phone" placeholder="请输入手机号" size="large" :prefix-icon="Phone" />
        </el-form-item>

        <el-form-item>
          <el-input v-model="form.college" placeholder="请输入学院" size="large" :prefix-icon="School" />
        </el-form-item>

        <el-form-item>
          <el-input v-model="form.major" placeholder="请输入专业" size="large" :prefix-icon="Reading" />
        </el-form-item>

        <el-button type="primary" size="large" @click="handleSubmit" :loading="loading" class="submit-btn">
          完成注册
        </el-button>
      </el-form>
    </div>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { updateUserInfo } from '../api/user'
import { ElMessage } from 'element-plus'
import { User, School, Reading, Phone } from '@element-plus/icons-vue'

const router = useRouter()
const form = ref({ name: '', studentId: '', phone: '', college: '', major: '' })
const loading = ref(false)
const user = JSON.parse(localStorage.getItem('user') || 'null')
if (!user) {
  router.push('/login')
}

const handleSubmit = async () => {
  if (!user) {
    ElMessage.error('请重新登录')
    router.push('/login')
    return
  }
  if (!form.value.name || !form.value.studentId || !form.value.phone || !form.value.college || !form.value.major) {
    ElMessage.warning('请填写完整信息')
    return
  }
  loading.value = true
  try {
    const response = await updateUserInfo(user.id, {
      name: form.value.name,
      studentId: form.value.studentId,
      phone: form.value.phone,
      college: form.value.college,
      major: form.value.major
    })
    localStorage.setItem('user', JSON.stringify(response))
    ElMessage.success('信息完善成功')
    router.push('/home')
  } catch (error) {
    ElMessage.error(error.message || '保存失败')
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.complete-page {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, #312e81 0%, #4f46e5 50%, #7c3aed 100%);
  padding: 20px;
}

.complete-card {
  width: 100%;
  max-width: 440px;
  background: white;
  border-radius: var(--radius-xl);
  padding: 48px 40px;
  box-shadow: var(--shadow-xl);
}

.card-header {
  text-align: center;
  margin-bottom: 36px;
}

.step-indicator {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 0;
  margin-bottom: 28px;
}

.step {
  width: 32px;
  height: 32px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 14px;
  font-weight: 600;
  background: var(--border-light);
  color: var(--text-muted);
}

.step.active {
  background: var(--primary);
  color: white;
}

.step-line {
  width: 60px;
  height: 2px;
  background: var(--primary-light);
  margin: 0 8px;
}

.card-header h2 {
  font-size: 24px;
  font-weight: 700;
  color: var(--text-primary);
  margin-bottom: 8px;
}

.card-header p {
  font-size: 15px;
  color: var(--text-muted);
}

.info-form :deep(.el-input__wrapper) {
  border-radius: var(--radius);
  padding: 4px 12px;
  box-shadow: 0 0 0 1px var(--border) inset;
}

.info-form :deep(.el-input__wrapper:hover) {
  box-shadow: 0 0 0 1px var(--primary-light) inset;
}

.info-form :deep(.el-input__wrapper.is-focus) {
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
</style>
