
<template>
  <div class="publish-page">
    <div class="publish-card">
      <div class="card-header">
        <h2>发布新需求</h2>
        <p>填写需求详情，让同学来帮你</p>
      </div>

      <el-form :model="form" class="publish-form" @submit.prevent="handleSubmit">
        <div class="form-section">
          <label class="section-label">需求类别</label>
          <div class="category-picker">
            <button
              v-for="cat in categories"
              :key="cat.value"
              type="button"
              class="cat-option"
              :class="{ selected: form.category === cat.value }"
              @click="form.category = cat.value"
            >
              <span class="cat-icon" v-html="cat.icon"></span>
              {{ cat.label }}
            </button>
          </div>
        </div>

        <el-form-item>
          <el-input v-model="form.title" placeholder="需求标题" size="large" maxlength="50" show-word-limit />
        </el-form-item>

        <el-form-item>
          <el-input
            v-model="form.description"
            type="textarea"
            placeholder="详细描述你的需求，包括具体要求、注意事项等"
            :rows="5"
            maxlength="500"
            show-word-limit
          />
        </el-form-item>

        <div class="form-row">
          <el-form-item class="half">
            <el-date-picker
              v-model="form.expectedTime"
              type="datetime"
              placeholder="期望完成时间"
              size="large"
              style="width: 100%"
            />
          </el-form-item>
          <el-form-item class="half">
            <el-input-number
              v-model="form.reward"
              placeholder="报酬金额"
              size="large"
              :min="0"
              :precision="2"
              style="width: 100%"
              controls-position="right"
            />
          </el-form-item>
        </div>

        <el-form-item>
          <el-input v-model="form.location" placeholder="服务位置（如：图书馆3楼、宿舍6号楼）" size="large" :prefix-icon="Location" />
        </el-form-item>

        <div class="form-actions">
          <el-button size="large" @click="$router.back()">取消</el-button>
          <el-button type="primary" size="large" @click="handleSubmit" :loading="loading" class="submit-btn">
            发布需求
          </el-button>
        </div>
      </el-form>
    </div>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { createDemand } from '../api/demand'
import { ElMessage } from 'element-plus'
import { Location } from '@element-plus/icons-vue'

const router = useRouter()
const form = ref({
  category: '',
  title: '',
  description: '',
  expectedTime: '',
  reward: null,
  location: ''
})
const loading = ref(false)

const categories = [
  { label: '跑腿代办', value: 'RUNNING', icon: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="18" height="18"><path d="M13 2L3 14h9l-1 8 10-12h-9l1-8z"/></svg>' },
  { label: '学习辅导', value: 'STUDY', icon: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="18" height="18"><path d="M2 3h6a4 4 0 0 1 4 4v14a3 3 0 0 0-3-3H2z"/><path d="M22 3h-6a4 4 0 0 0-4 4v14a3 3 0 0 1 3-3h7z"/></svg>' },
  { label: '物品借用', value: 'BORROW', icon: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="18" height="18"><path d="M21 16V8a2 2 0 0 0-1-1.73l-7-4a2 2 0 0 0-2 0l-7 4A2 2 0 0 0 3 8v8a2 2 0 0 0 1 1.73l7 4a2 2 0 0 0 2 0l7-4A2 2 0 0 0 21 16z"/></svg>' },
  { label: '其他', value: 'OTHER', icon: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="18" height="18"><circle cx="12" cy="12" r="1"/><circle cx="19" cy="12" r="1"/><circle cx="5" cy="12" r="1"/></svg>' }
]

const handleSubmit = async () => {
  if (!form.value.category || !form.value.title || !form.value.description || !form.value.expectedTime || form.value.reward === null || !form.value.location) {
    ElMessage.warning('请填写完整信息')
    return
  }
  loading.value = true
  try {
    await createDemand({
      category: form.value.category,
      title: form.value.title,
      description: form.value.description,
      expectedTime: form.value.expectedTime,
      reward: parseFloat(form.value.reward),
      location: form.value.location
    })
    ElMessage.success('发布成功')
    router.push('/home')
  } catch (error) {
    ElMessage.error(error.message || '发布失败')
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.publish-page {
  max-width: 680px;
  margin: 0 auto;
}

.publish-card {
  background: white;
  border-radius: var(--radius-lg);
  padding: 40px;
  border: 1px solid var(--border-light);
}

.card-header {
  margin-bottom: 32px;
}

.card-header h2 {
  font-size: 24px;
  font-weight: 700;
  color: var(--text-primary);
  margin-bottom: 6px;
}

.card-header p {
  font-size: 15px;
  color: var(--text-muted);
}

.form-section {
  margin-bottom: 24px;
}

.section-label {
  display: block;
  font-size: 14px;
  font-weight: 600;
  color: var(--text-secondary);
  margin-bottom: 10px;
}

.category-picker {
  display: flex;
  gap: 10px;
  flex-wrap: wrap;
}

.cat-option {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 10px 20px;
  border-radius: var(--radius);
  border: 2px solid var(--border);
  background: white;
  font-size: 14px;
  font-weight: 500;
  color: var(--text-secondary);
  cursor: pointer;
  transition: all 0.2s ease;
}

.cat-option:hover {
  border-color: var(--primary-light);
  color: var(--primary);
}

.cat-option.selected {
  border-color: var(--primary);
  background: var(--primary-bg);
  color: var(--primary);
}

.publish-form :deep(.el-input__wrapper),
.publish-form :deep(.el-textarea__inner) {
  border-radius: var(--radius);
}

.publish-form :deep(.el-input__wrapper) {
  box-shadow: 0 0 0 1px var(--border) inset;
}

.publish-form :deep(.el-input__wrapper:hover) {
  box-shadow: 0 0 0 1px var(--primary-light) inset;
}

.publish-form :deep(.el-input__wrapper.is-focus) {
  box-shadow: 0 0 0 2px var(--primary) inset;
}

.form-row {
  display: flex;
  gap: 16px;
}

.form-row .half {
  flex: 1;
}

.form-actions {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
  margin-top: 24px;
  padding-top: 24px;
  border-top: 1px solid var(--border-light);
}

.submit-btn {
  padding: 0 32px;
  border-radius: var(--radius);
  font-weight: 600;
  background: linear-gradient(135deg, var(--primary) 0%, var(--primary-light) 100%);
  border: none;
}

@media (max-width: 640px) {
  .publish-card { padding: 24px; }
  .form-row { flex-direction: column; }
}
</style>
