<template>
  <div class="home-page">
    <div class="home-toolbar">
      <div class="search-box">
        <el-input
          v-model="searchQuery"
          placeholder="搜索需求..."
          size="large"
          :prefix-icon="Search"
          clearable
          class="search-input"
        />
      </div>
      <el-button type="primary" size="large" @click="$router.push('/publish')" class="publish-btn">
        <el-icon><Plus /></el-icon>
        发布需求
      </el-button>
    </div>

    <div class="filter-row">
      <div class="category-tabs">
        <button
          v-for="cat in categories"
          :key="cat.value"
          class="cat-tab"
          :class="{ active: currentCategory === cat.value }"
          @click="currentCategory = cat.value"
        >
          <span class="cat-icon" v-html="cat.icon"></span>
          {{ cat.label }}
        </button>
      </div>
      <el-select v-model="currentStatus" placeholder="状态筛选" clearable class="status-select" @change="loadDemands">
        <el-option label="全部状态" value="" />
        <el-option label="待接单" value="PENDING" />
        <el-option label="进行中" value="IN_PROGRESS" />
        <el-option label="待确认" value="PENDING_CONFIRMATION" />
        <el-option label="已完成" value="COMPLETED" />
        <el-option label="已取消" value="CANCELLED" />
      </el-select>
    </div>

    <div v-if="demands.length === 0" class="empty-state">
      <div class="empty-icon">
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" width="64" height="64">
          <rect x="3" y="3" width="18" height="18" rx="2"/>
          <line x1="9" y1="9" x2="15" y2="15"/>
          <line x1="15" y1="9" x2="9" y2="15"/>
        </svg>
      </div>
      <h3>暂无需求</h3>
      <p>当前条件下还没有需求，快来发布第一个吧！</p>
    </div>

    <transition-group name="card-list" tag="div" class="demand-grid" v-else>
      <div
        v-for="demand in demands"
        :key="demand.id"
        class="demand-card"
        @click="goToDetail(demand.id)"
      >
        <div class="card-top">
          <span class="card-category" :class="demand.category.toLowerCase()">
            {{ getCategoryLabel(demand.category) }}
          </span>
          <span class="card-status" :class="demand.status.toLowerCase()">
            {{ getStatusLabel(demand.status) }}
          </span>
        </div>
        <h3 class="card-title">{{ demand.title }}</h3>
        <p class="card-desc">{{ demand.description }}</p>
        <div class="card-meta">
          <div class="meta-item">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="14" height="14"><path d="M21 10c0 7-9 13-9 13s-9-6-9-13a9 9 0 0 1 18 0z"/><circle cx="12" cy="10" r="3"/></svg>
            {{ demand.location }}
          </div>
          <div class="meta-item">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="14" height="14"><circle cx="12" cy="12" r="10"/><polyline points="12 6 12 12 16 14"/></svg>
            {{ formatTime(demand.expectedTime) }}
          </div>
        </div>
        <div class="card-footer">
          <div class="publisher">
            <div class="publisher-avatar">{{ (demand.publisherName || '?')[0] }}</div>
            <span>{{ demand.publisherName }}</span>
          </div>
          <div class="reward">
            <span class="reward-amount">¥{{ demand.reward }}</span>
          </div>
        </div>
      </div>
    </transition-group>
  </div>
</template>

<script setup>
import { ref, onMounted, watch } from 'vue'
import { useRouter } from 'vue-router'
import { getAllDemands } from '../api/demand'
import { ElMessage } from 'element-plus'
import { Search, Plus } from '@element-plus/icons-vue'

const router = useRouter()
const demands = ref([])
const currentCategory = ref('ALL')
const currentStatus = ref('')
const searchQuery = ref('')

const categories = [
  { label: '全部', value: 'ALL', icon: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="16" height="16"><rect x="3" y="3" width="7" height="7"/><rect x="14" y="3" width="7" height="7"/><rect x="14" y="14" width="7" height="7"/><rect x="3" y="14" width="7" height="7"/></svg>' },
  { label: '跑腿代办', value: 'RUNNING', icon: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="16" height="16"><path d="M13 2L3 14h9l-1 8 10-12h-9l1-8z"/></svg>' },
  { label: '学习辅导', value: 'STUDY', icon: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="16" height="16"><path d="M2 3h6a4 4 0 0 1 4 4v14a3 3 0 0 0-3-3H2z"/><path d="M22 3h-6a4 4 0 0 0-4 4v14a3 3 0 0 1 3-3h7z"/></svg>' },
  { label: '物品借用', value: 'BORROW', icon: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="16" height="16"><path d="M21 16V8a2 2 0 0 0-1-1.73l-7-4a2 2 0 0 0-2 0l-7 4A2 2 0 0 0 3 8v8a2 2 0 0 0 1 1.73l7 4a2 2 0 0 0 2 0l7-4A2 2 0 0 0 21 16z"/></svg>' },
  { label: '其他', value: 'OTHER', icon: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="16" height="16"><circle cx="12" cy="12" r="1"/><circle cx="19" cy="12" r="1"/><circle cx="5" cy="12" r="1"/></svg>' }
]

const loadDemands = async () => {
  try {
    const params = {}
    params.category = currentCategory.value !== 'ALL' ? currentCategory.value : undefined
    params.status = currentStatus.value || undefined
    params.keyword = searchQuery.value || undefined
    // 清理 undefined 值
    Object.keys(params).forEach(k => params[k] === undefined && delete params[k])
    demands.value = await getAllDemands(params)
  } catch (error) {
    ElMessage.error(error.message || '加载失败')
  }
}

const getCategoryLabel = (category) => {
  const cat = categories.find(c => c.value === category)
  return cat ? cat.label : category
}

const getStatusLabel = (status) => {
  const map = { PENDING: '待接单', IN_PROGRESS: '进行中', PENDING_CONFIRMATION: '待确认', COMPLETED: '已完成', CANCELLED: '已取消' }
  return map[status] || status
}

const formatTime = (time) => {
  if (!time) return ''
  return new Date(time).toLocaleString('zh-CN', { month: 'short', day: 'numeric', hour: '2-digit', minute: '2-digit' })
}

const goToDetail = (id) => router.push(`/demand/${id}`)

watch(currentCategory, () => {
  loadDemands()
})

let searchTimer = null
watch(searchQuery, () => {
  clearTimeout(searchTimer)
  searchTimer = setTimeout(() => loadDemands(), 300)
})

onMounted(loadDemands)
</script>

<style scoped>
.home-page {
  max-width: 1200px;
  margin: 0 auto;
}

.home-toolbar {
  display: flex;
  gap: 16px;
  margin-bottom: 16px;
}

.search-box {
  flex: 1;
}

.search-input :deep(.el-input__wrapper) {
  border-radius: var(--radius);
  box-shadow: var(--shadow-sm);
  border: 1px solid var(--border-light);
}

.publish-btn {
  border-radius: var(--radius);
  font-weight: 600;
  padding: 0 24px;
  background: linear-gradient(135deg, var(--primary) 0%, var(--primary-light) 100%);
  border: none;
  transition: all 0.3s ease;
}

.publish-btn:hover {
  transform: translateY(-1px);
  box-shadow: 0 6px 16px rgba(79, 70, 229, 0.3);
}

.filter-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 28px;
  gap: 12px;
  flex-wrap: wrap;
}

.category-tabs {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}

.status-select {
  width: 140px;
  flex-shrink: 0;
}

.cat-tab {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 8px 18px;
  border-radius: 100px;
  border: 1px solid var(--border);
  background: white;
  color: var(--text-secondary);
  font-size: 14px;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.2s ease;
}

.cat-tab:hover {
  border-color: var(--primary-light);
  color: var(--primary);
  background: var(--primary-bg);
}

.cat-tab.active {
  background: var(--primary);
  color: white;
  border-color: var(--primary);
}

.cat-tab.active .cat-icon :deep(svg) {
  stroke: white;
}

.demand-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(320px, 1fr));
  gap: 20px;
}

.demand-card {
  background: white;
  border-radius: var(--radius-lg);
  padding: 24px;
  cursor: pointer;
  transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
  border: 1px solid var(--border-light);
}

.demand-card:hover {
  transform: translateY(-4px);
  box-shadow: var(--shadow-lg);
  border-color: transparent;
}

.card-top {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 14px;
}

.card-category {
  padding: 4px 12px;
  border-radius: 100px;
  font-size: 12px;
  font-weight: 600;
}

.card-category.running { background: #fff7ed; color: #ea580c; }
.card-category.study { background: #eff6ff; color: #2563eb; }
.card-category.borrow { background: #f0fdf4; color: #16a34a; }
.card-category.other { background: #faf5ff; color: #9333ea; }

.card-status {
  font-size: 12px;
  font-weight: 600;
  padding: 4px 10px;
  border-radius: 100px;
}

.card-status.pending { background: var(--success-bg); color: var(--success); }
.card-status.in_progress { background: var(--info-bg); color: var(--info); }
.card-status.pending_confirmation { background: #fef3c7; color: #d97706; }
.card-status.completed { background: var(--border-light); color: var(--text-muted); }
.card-status.cancelled { background: var(--danger-bg); color: var(--danger); }

.card-title {
  font-size: 17px;
  font-weight: 600;
  color: var(--text-primary);
  margin-bottom: 8px;
  line-height: 1.4;
}

.card-desc {
  font-size: 14px;
  color: var(--text-secondary);
  line-height: 1.6;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
  margin-bottom: 16px;
}

.card-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 16px;
  margin-bottom: 16px;
}

.meta-item {
  display: flex;
  align-items: center;
  gap: 4px;
  font-size: 13px;
  color: var(--text-muted);
}

.card-footer {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding-top: 16px;
  border-top: 1px solid var(--border-light);
}

.publisher {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 13px;
  color: var(--text-secondary);
}

.publisher-avatar {
  width: 28px;
  height: 28px;
  border-radius: 50%;
  background: linear-gradient(135deg, var(--primary), var(--primary-light));
  color: white;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 12px;
  font-weight: 600;
}

.reward-amount {
  font-size: 20px;
  font-weight: 700;
  color: #ef4444;
}

.empty-state {
  text-align: center;
  padding: 80px 20px;
}

.empty-icon {
  color: var(--text-muted);
  margin-bottom: 16px;
  opacity: 0.4;
}

.empty-state h3 {
  font-size: 18px;
  color: var(--text-secondary);
  margin-bottom: 8px;
}

.empty-state p {
  font-size: 14px;
  color: var(--text-muted);
}

.card-list-enter-active,
.card-list-leave-active {
  transition: all 0.3s ease;
}
.card-list-enter-from {
  opacity: 0;
  transform: translateY(20px);
}
.card-list-leave-to {
  opacity: 0;
  transform: scale(0.95);
}

@media (max-width: 640px) {
  .demand-grid {
    grid-template-columns: 1fr;
  }
  .home-toolbar {
    flex-direction: column;
  }
  .filter-row {
    flex-direction: column;
    align-items: stretch;
  }
  .status-select {
    width: 100%;
  }
}
</style>
