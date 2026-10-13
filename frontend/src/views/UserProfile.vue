<template>
  <div class="profile-page">
    <div class="profile-grid">
      <div class="profile-card">
        <div class="profile-header">
          <div class="profile-avatar">{{ userName[0] }}</div>
          <div class="profile-info">
            <h2>{{ userName }}</h2>
            <p>{{ userInfo.college }} · {{ userInfo.major }}</p>
          </div>
        </div>

        <div class="credit-section">
          <div class="credit-big">
            <span class="credit-number" :class="creditLevel">{{ userInfo.creditScore ?? 100 }}</span>
            <span class="credit-unit">信用分</span>
          </div>
          <div class="credit-level-tag" :class="creditLevel">
            {{ creditLevelText }}
          </div>
          <div v-if="userInfo.restrictedUntil && new Date(userInfo.restrictedUntil).getTime() > Date.now()" class="restrict-notice">
            <el-alert
              :title="userInfo.creditScore < 40 ? '账号已封禁' : '发布限制中'"
              :description="userInfo.creditScore < 40 ? '您的信用分低于40，账号已被封禁' : '信用分不足60，在 ' + formatDate(userInfo.restrictedUntil) + ' 前无法发布需求'"
              :type="userInfo.creditScore < 40 ? 'error' : 'warning'"
              show-icon
              :closable="false"
            />
          </div>
        </div>

        <div class="info-section">
          <div class="info-item"><span>学号</span><span>{{ userInfo.studentId }}</span></div>
          <div class="info-item"><span>手机</span><span>{{ userInfo.phone }}</span></div>
          <div class="info-item"><span>学院</span><span>{{ userInfo.college || '未填写' }}</span></div>
          <div class="info-item"><span>专业</span><span>{{ userInfo.major || '未填写' }}</span></div>
        </div>

        <div v-if="isSelf" class="danger-section">
          <button class="delete-account-btn" @click="showDeleteDialog = true">注销账号</button>
        </div>
      </div>

      <div class="profile-right">
        <div class="section">
          <h3>收到的评价</h3>
          <div v-if="reviews.length === 0" class="empty-small">暂无评价</div>
          <div v-for="r in reviews" :key="r.id" class="review-item">
            <div class="review-top">
              <span class="review-stars">{{ '★'.repeat(r.rating) }}{{ '☆'.repeat(5 - r.rating) }}</span>
              <span class="review-date">{{ formatDate(r.createdAt) }}</span>
            </div>
            <p v-if="r.comment" class="review-comment">{{ r.comment }}</p>
            <div class="reviewer">来自：{{ r.reviewerName }}</div>
          </div>
        </div>

        <div class="section" v-if="isSelf">
          <h3>我的申诉</h3>
          <button class="appeal-btn" @click="showAppealDialog = true">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="16" height="16"><circle cx="12" cy="12" r="10"/><line x1="12" y1="8" x2="12" y2="12"/><line x1="12" y1="16" x2="12.01" y2="16"/></svg>
            发起申诉
          </button>
          <div v-if="myAppeals.length === 0" class="empty-small">暂无申诉记录</div>
          <div v-for="a in myAppeals" :key="a.id" class="appeal-item">
            <div class="appeal-top">
              <span class="appeal-type">{{ a.type === 'CREDIT_DEDUCTION' ? '信用分扣除' : a.type === 'BAN' ? '账号封禁' : a.type }}</span>
              <span class="appeal-status" :class="a.status.toLowerCase()">{{ statusLabel(a.status) }}</span>
            </div>
            <div class="appeal-reason">{{ a.reason }}</div>
            <div v-if="a.adminNote" class="appeal-note">管理员回复：{{ a.adminNote }}</div>
          </div>
        </div>
      </div>
    </div>

    <!-- 申诉对话框 -->
    <el-dialog v-model="showAppealDialog" title="发起申诉" width="480px" destroy-on-close>
      <el-form @submit.prevent="submitAppeal">
        <el-form-item label="申诉类型" required>
          <el-select v-model="appealForm.type" style="width: 100%">
            <el-option label="信用分扣除申诉" value="CREDIT_DEDUCTION" />
            <el-option label="账号封禁申诉" value="BAN" />
          </el-select>
        </el-form-item>
        <el-form-item label="申诉理由" required>
          <el-input v-model="appealForm.reason" type="textarea" :rows="4" placeholder="请详细说明申诉理由..." maxlength="500" show-word-limit />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showAppealDialog = false">取消</el-button>
        <el-button type="primary" @click="submitAppeal" :loading="loading">提交申诉</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="showDeleteDialog" title="注销账号" width="420px" destroy-on-close>
      <div class="delete-warning">
        <svg viewBox="0 0 24 24" fill="none" stroke="#ef4444" stroke-width="2" width="48" height="48"><circle cx="12" cy="12" r="10"/><line x1="12" y1="8" x2="12" y2="12"/><line x1="12" y1="16" x2="12.01" y2="16"/></svg>
        <p class="delete-warning-text">确定要注销账号吗？此操作不可恢复，所有数据将被永久删除。</p>
        <el-input v-model="deleteConfirmText" placeholder='请输入"注销"以确认' size="large" />
      </div>
      <template #footer>
        <el-button @click="showDeleteDialog = false">取消</el-button>
        <el-button type="danger" @click="handleDeleteAccount" :loading="deleteLoading" :disabled="deleteConfirmText !== '注销'">确认注销</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { getUserInfo, deleteUser } from '../api/user'
import { getReviewsByUser } from '../api/review'
import { getMyAppeals, createAppeal } from '../api/appeal'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useRouter } from 'vue-router'

const route = useRoute()
const router = useRouter()
const user = JSON.parse(localStorage.getItem('user') || '{}')
const isSelf = computed(() => !route.params.id || Number(route.params.id) === user.id)
const userId = computed(() => route.params.id ? Number(route.params.id) : user.id)

const userInfo = ref({})
const reviews = ref([])
const myAppeals = ref([])
const showAppealDialog = ref(false)
const appealForm = ref({ type: 'CREDIT_DEDUCTION', reason: '' })
const loading = ref(false)
const showDeleteDialog = ref(false)
const deleteConfirmText = ref('')
const deleteLoading = ref(false)

const userName = computed(() => isSelf.value ? (user.name || '用户') : (userInfo.value.name || '用户'))

const creditLevel = computed(() => {
  const s = userInfo.value.creditScore ?? 100
  if (s >= 80) return 'high'
  if (s >= 60) return 'mid'
  if (s >= 40) return 'low'
  return 'critical'
})

const creditLevelText = computed(() => {
  const s = userInfo.value.creditScore ?? 100
  if (s >= 80) return '信用优秀'
  if (s >= 60) return '信用良好'
  if (s >= 40) return '信用较低'
  return '信用危险'
})

const formatDate = (d) => d ? new Date(d).toLocaleString('zh-CN') : ''
const statusLabel = (s) => ({ PENDING: '处理中', APPROVED: '已通过', REJECTED: '已驳回' }[s] || s)

const loadData = async () => {
  try {
    userInfo.value = isSelf.value ? user : await getUserInfo(userId.value)
    if (isSelf.value) {
      localStorage.setItem('user', JSON.stringify({ ...user, ...userInfo.value }))
    }
    reviews.value = await getReviewsByUser(userId.value)
    if (isSelf.value) myAppeals.value = await getMyAppeals()
  } catch (e) {
    ElMessage.error(e.message || '加载失败')
  }
}

const submitAppeal = async () => {
  if (!appealForm.value.type || !appealForm.value.reason.trim()) {
    ElMessage.warning('请填写完整信息')
    return
  }
  loading.value = true
  try {
    await createAppeal(appealForm.value)
    ElMessage.success('申诉已提交')
    showAppealDialog.value = false
    appealForm.value = { type: 'CREDIT_DEDUCTION', reason: '' }
    myAppeals.value = await getMyAppeals()
  } catch (e) {
    ElMessage.error(e.message || '提交失败')
  } finally { loading.value = false }
}

const handleDeleteAccount = async () => {
  if (deleteConfirmText.value !== '注销') {
    ElMessage.warning('请输入"注销"以确认操作')
    return
  }
  deleteLoading.value = true
  try {
    await deleteUser(user.id)
    ElMessage.success('账号已注销')
    localStorage.removeItem('user')
    localStorage.removeItem('token')
    router.push('/login')
  } catch (e) {
    ElMessage.error(e.message || '注销失败')
  } finally {
    deleteLoading.value = false
  }
}

onMounted(loadData)
</script>

<style scoped>
.profile-page { max-width: 900px; margin: 0 auto; }
.profile-grid { display: grid; grid-template-columns: 360px 1fr; gap: 24px; align-items: start; }

.profile-card {
  background: white; border-radius: var(--radius-lg); padding: 32px;
  border: 1px solid var(--border-light); position: sticky; top: 92px;
}

.profile-header { display: flex; align-items: center; gap: 16px; margin-bottom: 24px; }
.profile-avatar {
  width: 64px; height: 64px; border-radius: 50%;
  background: linear-gradient(135deg, var(--primary), #7c3aed);
  color: white; display: flex; align-items: center; justify-content: center;
  font-size: 28px; font-weight: 700;
}
.profile-info h2 { font-size: 20px; font-weight: 700; color: var(--text-primary); margin-bottom: 4px; }
.profile-info p { font-size: 14px; color: var(--text-muted); }

.credit-section { text-align: center; padding: 20px 0; border-top: 1px solid var(--border-light); border-bottom: 1px solid var(--border-light); margin-bottom: 20px; }
.credit-number { font-size: 48px; font-weight: 800; }
.credit-number.high { color: #10b981; }
.credit-number.mid { color: #f59e0b; }
.credit-number.low { color: #f97316; }
.credit-number.critical { color: #ef4444; }
.credit-unit { font-size: 14px; color: var(--text-muted); margin-left: 4px; }
.credit-level-tag {
  display: inline-block; margin-top: 8px; padding: 4px 14px; border-radius: 100px;
  font-size: 13px; font-weight: 600;
}
.credit-level-tag.high { background: #d1fae5; color: #059669; }
.credit-level-tag.mid { background: #fef3c7; color: #d97706; }
.credit-level-tag.low { background: #ffedd5; color: #ea580c; }
.credit-level-tag.critical { background: #fee2e2; color: #dc2626; }
.restrict-notice { margin-top: 12px; text-align: left; }

.info-section { display: flex; flex-direction: column; gap: 4px; }
.info-item { display: flex; justify-content: space-between; padding: 8px 0; font-size: 14px; color: var(--text-secondary); }
.info-item span:first-child { color: var(--text-muted); }

.profile-right { display: flex; flex-direction: column; gap: 24px; }
.section {
  background: white; border-radius: var(--radius-lg); padding: 24px;
  border: 1px solid var(--border-light);
}
.section h3 { font-size: 16px; font-weight: 600; color: var(--text-primary); margin-bottom: 16px; }

.empty-small { text-align: center; color: var(--text-muted); padding: 24px 0; font-size: 14px; }

.review-item {
  padding: 14px 0; border-bottom: 1px solid var(--border-light);
}
.review-item:last-child { border-bottom: none; }
.review-top { display: flex; justify-content: space-between; align-items: center; margin-bottom: 6px; }
.review-stars { color: #f59e0b; font-size: 14px; letter-spacing: 2px; }
.review-date { font-size: 12px; color: var(--text-muted); }
.review-comment { font-size: 14px; color: var(--text-secondary); line-height: 1.5; margin-bottom: 4px; }
.reviewer { font-size: 12px; color: var(--text-muted); }

.appeal-btn {
  display: flex; align-items: center; gap: 6px; padding: 8px 16px;
  border-radius: var(--radius); border: 1px dashed var(--border);
  background: none; color: var(--text-secondary); font-size: 14px; cursor: pointer;
  width: 100%; justify-content: center; margin-bottom: 12px; transition: all 0.2s;
}
.appeal-btn:hover { border-color: var(--primary); color: var(--primary); }

.appeal-item { padding: 12px 0; border-bottom: 1px solid var(--border-light); }
.appeal-item:last-child { border-bottom: none; }
.appeal-top { display: flex; justify-content: space-between; align-items: center; margin-bottom: 6px; }
.appeal-type { font-weight: 600; font-size: 13px; }
.appeal-status { font-size: 12px; padding: 2px 8px; border-radius: 100px; font-weight: 600; }
.appeal-status.pending { background: #fef3c7; color: #d97706; }
.appeal-status.approved { background: #d1fae5; color: #059669; }
.appeal-status.rejected { background: #fee2e2; color: #dc2626; }
.appeal-reason { font-size: 13px; color: var(--text-secondary); }
.appeal-note { margin-top: 4px; font-size: 12px; color: var(--primary); }

.danger-section {
  margin-top: 20px;
  padding-top: 20px;
  border-top: 1px solid var(--border-light);
}

.delete-account-btn {
  width: 100%;
  padding: 10px;
  border-radius: var(--radius);
  border: 1px solid #fecaca;
  background: #fff5f5;
  color: #dc2626;
  font-size: 14px;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.2s;
}

.delete-account-btn:hover {
  background: #fef2f2;
  border-color: #fca5a5;
}

.delete-warning {
  text-align: center;
  padding: 12px 0;
}

.delete-warning-text {
  font-size: 15px;
  color: var(--text-secondary);
  margin: 16px 0 20px;
  line-height: 1.6;
}

@media (max-width: 768px) {
  .profile-grid { grid-template-columns: 1fr; }
  .profile-card { position: static; }
}
</style>
