
<template>
  <div class="my-orders-page">
    <div class="status-tabs">
      <button
        v-for="tab in tabs"
        :key="tab.value"
        class="tab-btn"
        :class="{ active: currentTab === tab.value }"
        @click="currentTab = tab.value"
      >
        {{ tab.label }}
        <span v-if="tab.count !== undefined" class="tab-count">{{ tab.count }}</span>
      </button>
    </div>

    <div v-if="filteredOrders.length === 0" class="empty-state">
      <div class="empty-icon">
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" width="64" height="64">
          <path d="M22 11.08V12a10 10 0 1 1-5.93-9.14"/><polyline points="22 4 12 14.01 9 11.01"/>
        </svg>
      </div>
      <h3>暂无接单</h3>
      <p>去需求广场看看有什么可以帮忙的吧</p>
    </div>

    <transition-group name="card-list" tag="div" class="order-list" v-else>
      <div
        v-for="order in filteredOrders"
        :key="order.id"
        class="order-item"
        @click="goToDetail(order.id)"
      >
        <div class="item-left">
          <div class="item-top">
            <span class="item-category" :class="order.category.toLowerCase()">
              {{ getCategoryLabel(order.category) }}
            </span>
            <span class="item-status" :class="order.status.toLowerCase()">
              {{ getStatusLabel(order.status) }}
            </span>
          </div>
          <h3 class="item-title">{{ order.title }}</h3>
          <div class="item-meta">
            <span>{{ order.location }}</span>
            <span class="dot">·</span>
            <span>{{ formatTime(order.expectedTime) }}</span>
          </div>
        </div>
        <div class="item-right">
          <div class="item-reward">¥{{ order.reward }}</div>
          <div class="item-publisher">
            发布: {{ order.publisherName }}
          </div>
        </div>
      </div>
    </transition-group>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { getDemandsByAcceptor } from '../api/demand'
import { ElMessage } from 'element-plus'

const router = useRouter()
const user = JSON.parse(localStorage.getItem('user') || 'null')
if (!user) {
  const router = useRouter()
  router.push('/login')
}
const orders = ref([])
const currentTab = ref('ALL')

const tabs = computed(() => [
  { label: '全部', value: 'ALL', count: orders.value.length },
  { label: '进行中', value: 'IN_PROGRESS', count: orders.value.filter(o => o.status === 'IN_PROGRESS').length },
  { label: '待确认', value: 'PENDING_CONFIRMATION', count: orders.value.filter(o => o.status === 'PENDING_CONFIRMATION').length },
  { label: '已完成', value: 'COMPLETED', count: orders.value.filter(o => o.status === 'COMPLETED').length }
])

const filteredOrders = computed(() => {
  if (currentTab.value === 'ALL') return orders.value
  return orders.value.filter(o => o.status === currentTab.value)
})

const getCategoryLabel = (category) => {
  const map = { RUNNING: '跑腿代办', STUDY: '学习辅导', BORROW: '物品借用', OTHER: '其他' }
  return map[category] || category
}

const getStatusLabel = (status) => {
  const map = { PENDING: '待接单', IN_PROGRESS: '进行中', PENDING_CONFIRMATION: '待确认', COMPLETED: '已完成', CANCELLED: '已取消' }
  return map[status] || status
}

const formatTime = (time) => {
  if (!time) return ''
  return new Date(time).toLocaleString('zh-CN', { month: 'short', day: 'numeric', hour: '2-digit', minute: '2-digit' })
}

const loadOrders = async () => {
  if (!user) return
  try {
    orders.value = await getDemandsByAcceptor(user.id)
  } catch (error) {
    ElMessage.error(error.message || '加载失败')
  }
}

const goToDetail = (id) => router.push(`/demand/${id}`)

onMounted(loadOrders)
</script>

<style scoped>
.my-orders-page {
  max-width: 900px;
  margin: 0 auto;
}

.status-tabs {
  display: flex;
  gap: 8px;
  margin-bottom: 24px;
  flex-wrap: wrap;
}

.tab-btn {
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

.tab-btn:hover {
  border-color: var(--primary-light);
  color: var(--primary);
}

.tab-btn.active {
  background: var(--primary);
  color: white;
  border-color: var(--primary);
}

.tab-count {
  font-size: 12px;
  background: rgba(0,0,0,0.1);
  padding: 1px 7px;
  border-radius: 100px;
}

.tab-btn.active .tab-count {
  background: rgba(255,255,255,0.25);
}

.order-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.order-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  background: white;
  border-radius: var(--radius-lg);
  padding: 20px 24px;
  cursor: pointer;
  transition: all 0.2s ease;
  border: 1px solid var(--border-light);
}

.order-item:hover {
  border-color: var(--primary-light);
  box-shadow: var(--shadow-md);
  transform: translateX(4px);
}

.item-left {
  flex: 1;
  min-width: 0;
}

.item-top {
  display: flex;
  gap: 8px;
  margin-bottom: 8px;
}

.item-category {
  padding: 3px 10px;
  border-radius: 100px;
  font-size: 12px;
  font-weight: 600;
}

.item-category.running { background: #fff7ed; color: #ea580c; }
.item-category.study { background: #eff6ff; color: #2563eb; }
.item-category.borrow { background: #f0fdf4; color: #16a34a; }
.item-category.other { background: #faf5ff; color: #9333ea; }

.item-status {
  padding: 3px 10px;
  border-radius: 100px;
  font-size: 12px;
  font-weight: 600;
}

.item-status.pending { background: var(--success-bg); color: var(--success); }
.item-status.in_progress { background: var(--info-bg); color: var(--info); }
.item-status.pending_confirmation { background: #fef3c7; color: #d97706; }
.item-status.completed { background: var(--border-light); color: var(--text-muted); }
.item-status.cancelled { background: var(--danger-bg); color: var(--danger); }

.item-title {
  font-size: 16px;
  font-weight: 600;
  color: var(--text-primary);
  margin-bottom: 6px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.item-meta {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;
  color: var(--text-muted);
}

.dot {
  color: var(--border);
}

.item-right {
  text-align: right;
  flex-shrink: 0;
  margin-left: 20px;
}

.item-reward {
  font-size: 22px;
  font-weight: 700;
  color: #ef4444;
  margin-bottom: 4px;
}

.item-publisher {
  font-size: 12px;
  color: var(--primary);
  font-weight: 500;
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
  transform: translateY(10px);
}
</style>
