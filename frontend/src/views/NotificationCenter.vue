<template>
  <div class="notif-page">
    <div class="notif-header">
      <h2>消息通知</h2>
      <el-button v-if="notifications.length > 0" text type="primary" @click="handleMarkAllRead">全部已读</el-button>
    </div>

    <div v-if="notifications.length === 0" class="empty-state">
      <div class="empty-icon">
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" width="48" height="48">
          <path d="M18 8A6 6 0 0 0 6 8c0 7-3 9-3 9h18s-3-2-3-9"/><path d="M13.73 21a2 2 0 0 1-3.46 0"/>
        </svg>
      </div>
      <h3>暂无通知</h3>
    </div>

    <div class="notif-list" v-else>
      <div
        v-for="n in notifications"
        :key="n.id"
        class="notif-item"
        :class="{ unread: !n.isRead }"
        @click="handleClick(n)"
      >
        <div class="notif-dot-indicator" v-if="!n.isRead"></div>
        <div class="notif-body">
          <div class="notif-top">
            <span class="notif-title">{{ n.title }}</span>
            <span class="notif-time">{{ formatTime(n.createdAt) }}</span>
          </div>
          <p class="notif-content">{{ n.content }}</p>
          <span class="notif-type-tag">{{ typeLabel(n.type) }}</span>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { getMyNotifications, markAsRead, markAllAsRead } from '../api/notification'
import { ElMessage } from 'element-plus'

const router = useRouter()
const notifications = ref([])

const typeLabel = (t) => ({
  ACCEPT: '接单', CANCEL: '取消', COMPLETE_CONFIRM: '完成确认',
  CREDIT_CHANGE: '信用变动', REVIEW_RECEIVED: '收到评价', APPEAL_RESULT: '申诉结果'
}[t] || t)

const formatTime = (t) => {
  if (!t) return ''
  const d = new Date(t)
  const now = new Date()
  const diff = now - d
  if (diff < 60000) return '刚刚'
  if (diff < 3600000) return Math.floor(diff / 60000) + '分钟前'
  if (diff < 86400000) return Math.floor(diff / 3600000) + '小时前'
  return d.toLocaleDateString('zh-CN')
}

const loadNotifications = async () => {
  try {
    notifications.value = await getMyNotifications()
  } catch (e) {
    ElMessage.error(e.message || '加载失败')
  }
}

const handleClick = async (n) => {
  if (!n.isRead) {
    try { await markAsRead(n.id) } catch {}
    n.isRead = true
  }
  if (n.relatedId && (n.type === 'ACCEPT' || n.type === 'CANCEL' || n.type === 'COMPLETE_CONFIRM')) {
    router.push(`/demand/${n.relatedId}`)
  }
}

const handleMarkAllRead = async () => {
  try {
    await markAllAsRead()
    notifications.value.forEach(n => n.isRead = true)
    ElMessage.success('已全部标记为已读')
  } catch (e) {
    ElMessage.error('操作失败')
  }
}

onMounted(loadNotifications)
</script>

<style scoped>
.notif-page { max-width: 700px; margin: 0 auto; }
.notif-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 20px; }
.notif-header h2 { font-size: 20px; font-weight: 700; }

.notif-list { display: flex; flex-direction: column; gap: 8px; }
.notif-item {
  display: flex; align-items: flex-start; gap: 12px;
  background: white; border-radius: var(--radius-lg); padding: 16px 20px;
  border: 1px solid var(--border-light); cursor: pointer; transition: all 0.2s;
}
.notif-item:hover { border-color: var(--primary-light); box-shadow: var(--shadow-sm); }
.notif-item.unread { background: #f5f3ff; border-color: #e0e7ff; }

.notif-dot-indicator {
  width: 8px; height: 8px; border-radius: 50%; background: var(--primary);
  flex-shrink: 0; margin-top: 6px;
}

.notif-body { flex: 1; min-width: 0; }
.notif-top { display: flex; justify-content: space-between; align-items: center; margin-bottom: 4px; }
.notif-title { font-weight: 600; font-size: 14px; color: var(--text-primary); }
.notif-time { font-size: 12px; color: var(--text-muted); flex-shrink: 0; margin-left: 12px; }
.notif-content { font-size: 13px; color: var(--text-secondary); line-height: 1.5; margin-bottom: 6px; }
.notif-type-tag {
  display: inline-block; padding: 1px 8px; border-radius: 100px;
  font-size: 11px; font-weight: 600; background: var(--border-light); color: var(--text-muted);
}

.empty-state { text-align: center; padding: 80px 20px; }
.empty-icon { color: var(--text-muted); margin-bottom: 12px; opacity: 0.4; }
.empty-state h3 { font-size: 16px; color: var(--text-secondary); }
</style>
