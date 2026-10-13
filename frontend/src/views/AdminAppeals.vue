<template>
  <div class="admin-page">
    <div class="admin-header">
      <h2>申诉管理</h2>
      <div class="filter-btns">
        <button :class="{ active: filter === 'ALL' }" @click="filter = 'ALL'">全部</button>
        <button :class="{ active: filter === 'PENDING' }" @click="filter = 'PENDING'">待处理</button>
        <button :class="{ active: filter === 'APPROVED' }" @click="filter = 'APPROVED'">已通过</button>
        <button :class="{ active: filter === 'REJECTED' }" @click="filter = 'REJECTED'">已驳回</button>
      </div>
    </div>

    <div v-if="filteredAppeals.length === 0" class="empty-state">
      <h3>暂无申诉</h3>
    </div>

    <div class="appeal-list" v-else>
      <div v-for="a in filteredAppeals" :key="a.id" class="appeal-card">
        <div class="appeal-card-header">
          <div class="appeal-user">
            <span class="appeal-name">{{ a.userName }}</span>
            <span class="appeal-type-tag">{{ a.type === 'CREDIT_DEDUCTION' ? '信用扣除' : '封禁' }}</span>
          </div>
          <span class="appeal-status-tag" :class="a.status.toLowerCase()">{{ statusMap[a.status] }}</span>
        </div>
        <div class="appeal-body">
          <div class="appeal-reason-section">
            <div class="section-label">申诉理由</div>
            <p>{{ a.reason }}</p>
          </div>
          <div v-if="a.adminNote" class="appeal-note-section">
            <div class="section-label">处理备注</div>
            <p>{{ a.adminNote }}</p>
          </div>
        </div>
        <div class="appeal-footer" v-if="a.status === 'PENDING'">
          <el-input v-model="handleNotes[a.id]" placeholder="处理备注（可选）" size="small" style="flex: 1; margin-right: 12px;" />
          <el-button size="small" @click="handleAppealAction(a.id, 'REJECTED')">驳回</el-button>
          <el-button size="small" type="primary" @click="handleAppealAction(a.id, 'APPROVED')">通过</el-button>
        </div>
        <div class="appeal-time">提交时间：{{ formatTime(a.createdAt) }}</div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { getAllAppeals, handleAppeal } from '../api/appeal'
import { ElMessage } from 'element-plus'

const appeals = ref([])
const filter = ref('PENDING')
const handleNotes = ref({})

const statusMap = { PENDING: '处理中', APPROVED: '已通过', REJECTED: '已驳回' }

const filteredAppeals = computed(() => {
  if (filter.value === 'ALL') return appeals.value
  return appeals.value.filter(a => a.status === filter.value)
})

const formatTime = (t) => t ? new Date(t).toLocaleString('zh-CN') : ''

const loadAppeals = async () => {
  try {
    appeals.value = await getAllAppeals()
  } catch (e) {
    ElMessage.error(e.message || '加载失败')
  }
}

const handleAppealAction = async (id, action) => {
  try {
    await handleAppeal(id, { action, adminNote: handleNotes.value[id] || '' })
    ElMessage.success(action === 'APPROVED' ? '已通过' : '已驳回')
    await loadAppeals()
  } catch (e) {
    ElMessage.error(e.message || '操作失败')
  }
}

onMounted(loadAppeals)
</script>

<style scoped>
.admin-page { max-width: 800px; margin: 0 auto; }
.admin-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 24px; flex-wrap: wrap; gap: 12px; }
.admin-header h2 { font-size: 20px; font-weight: 700; }

.filter-btns { display: flex; gap: 6px; }
.filter-btns button {
  padding: 6px 14px; border-radius: 100px; border: 1px solid var(--border);
  background: white; font-size: 13px; cursor: pointer; transition: all 0.2s;
}
.filter-btns button.active { background: var(--primary); color: white; border-color: var(--primary); }

.appeal-list { display: flex; flex-direction: column; gap: 12px; }
.appeal-card {
  background: white; border-radius: var(--radius-lg); padding: 20px 24px;
  border: 1px solid var(--border-light);
}
.appeal-card-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 12px; }
.appeal-user { display: flex; align-items: center; gap: 10px; }
.appeal-name { font-weight: 600; font-size: 15px; }
.appeal-type-tag {
  padding: 2px 10px; border-radius: 100px; font-size: 12px; font-weight: 600;
  background: var(--primary-bg); color: var(--primary);
}
.appeal-status-tag { padding: 3px 10px; border-radius: 100px; font-size: 12px; font-weight: 600; }
.appeal-status-tag.pending { background: #fef3c7; color: #d97706; }
.appeal-status-tag.approved { background: #d1fae5; color: #059669; }
.appeal-status-tag.rejected { background: #fee2e2; color: #dc2626; }

.appeal-body { margin-bottom: 12px; }
.section-label { font-size: 12px; font-weight: 600; color: var(--text-muted); margin-bottom: 4px; text-transform: uppercase; }
.appeal-reason-section p { font-size: 14px; color: var(--text-secondary); line-height: 1.5; }
.appeal-note-section { margin-top: 8px; padding-top: 8px; border-top: 1px solid var(--border-light); }
.appeal-note-section p { font-size: 13px; color: var(--primary); }

.appeal-footer { display: flex; align-items: center; margin-top: 12px; padding-top: 12px; border-top: 1px solid var(--border-light); }
.appeal-time { font-size: 12px; color: var(--text-muted); margin-top: 8px; }

.empty-state { text-align: center; padding: 60px 20px; }
.empty-state h3 { font-size: 16px; color: var(--text-secondary); }
</style>
