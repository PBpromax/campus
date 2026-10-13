<template>
  <div class="detail-page" v-if="demand">
    <div class="detail-grid">
      <div class="detail-main">
        <div class="detail-card">
          <div class="card-top-bar">
            <span class="card-category" :class="demand.category.toLowerCase()">
              {{ getCategoryLabel(demand.category) }}
            </span>
            <span class="card-status" :class="demand.status.toLowerCase()">
              {{ getStatusLabel(demand.status) }}
            </span>
          </div>

          <h1 class="detail-title">{{ demand.title }}</h1>

          <div class="detail-meta">
            <div class="meta-item">
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="16" height="16"><path d="M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2"/><circle cx="12" cy="7" r="4"/></svg>
              <span>{{ demand.publisherName }}</span>
            </div>
            <div class="meta-item">
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="16" height="16"><path d="M21 10c0 7-9 13-9 13s-9-6-9-13a9 9 0 0 1 18 0z"/><circle cx="12" cy="10" r="3"/></svg>
              <span>{{ demand.location }}</span>
            </div>
            <div class="meta-item">
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="16" height="16"><circle cx="12" cy="12" r="10"/><polyline points="12 6 12 12 16 14"/></svg>
              <span>{{ formatTime(demand.expectedTime) }}</span>
            </div>
          </div>

          <div class="detail-desc-section">
            <h3>需求描述</h3>
            <p>{{ demand.description }}</p>
          </div>

          <!-- 资金担保状态 -->
          <div v-if="demand.paymentStatus && demand.paymentStatus !== 'UNPAID'" class="payment-status">
            <span class="payment-label">资金状态：</span>
            <span class="payment-tag" :class="demand.paymentStatus.toLowerCase()">
              {{ paymentStatusLabel(demand.paymentStatus) }}
            </span>
          </div>

          <!-- 完成凭证展示 -->
          <div v-if="demand.completionProof" class="proof-section">
            <h3>完成凭证</h3>
            <div class="proof-content">{{ demand.completionProof }}</div>
          </div>

          <!-- 取消信息展示 -->
          <div v-if="demand.status === 'CANCELLED' && demand.cancelReason" class="cancel-info-section">
            <h3>取消信息</h3>
            <div class="cancel-info">
              <div class="cancel-row">
                <span class="cancel-label">取消方：</span>
                <span>{{ demand.canceledBy === 'PUBLISHER' ? '发布者' : '接单者' }}</span>
              </div>
              <div class="cancel-row">
                <span class="cancel-label">取消理由：</span>
                <span>{{ demand.cancelReason }}</span>
              </div>
              <div v-if="demand.canceledAt" class="cancel-row">
                <span class="cancel-label">取消时间：</span>
                <span>{{ formatTime(demand.canceledAt) }}</span>
              </div>
            </div>
          </div>

          <!-- 评价区域 -->
          <div v-if="demand.status === 'COMPLETED'" class="review-section">
            <h3>订单评价</h3>
            <div v-if="reviews.length > 0" class="review-list">
              <div v-for="r in reviews" :key="r.id" class="review-card">
                <div class="review-card-top">
                  <span class="reviewer-name">{{ r.reviewerName }}</span>
                  <span class="review-stars">{{ '★'.repeat(r.rating) }}{{ '☆'.repeat(5 - r.rating) }}</span>
                </div>
                <p v-if="r.comment" class="review-text">{{ r.comment }}</p>
                <span class="review-time">{{ formatTime(r.createdAt) }}</span>
              </div>
            </div>
            <div v-if="canReview" class="review-form">
              <div class="review-rate">
                <span>评分：</span>
                <button v-for="i in 5" :key="i" class="star-btn" :class="{ on: myRating >= i }" @click="myRating = i">
                  {{ i <= myRating ? '★' : '☆' }}
                </button>
              </div>
              <el-input v-model="myComment" type="textarea" :rows="2" placeholder="写一段评价（可选）" maxlength="500" show-word-limit style="margin: 10px 0;" />
              <el-button type="primary" size="small" @click="submitReview" :loading="reviewLoading">提交评价</el-button>
            </div>
          </div>

          <!-- 沟通留痕 - 消息区域 -->
          <div v-if="canMessage" class="message-section">
            <h3>沟通记录</h3>
            <div class="message-list" ref="msgList">
              <div v-for="m in messages" :key="m.id" class="msg-bubble" :class="{ mine: m.senderId === user.id }">
                <div class="msg-sender">{{ m.senderName }}</div>
                <div class="msg-text">{{ m.content }}</div>
                <div class="msg-time">{{ formatTime(m.createdAt) }}</div>
              </div>
              <div v-if="messages.length === 0" class="msg-empty">暂无消息，发送第一条消息吧</div>
            </div>
            <div class="message-input-row">
              <el-input v-model="newMessage" placeholder="输入消息..." @keyup.enter="sendMsg" size="small" />
              <el-button type="primary" size="small" @click="sendMsg" :loading="msgSending">发送</el-button>
            </div>
          </div>

          <!-- 操作按钮区域 -->
          <div class="detail-actions" v-if="canAccept || canComplete || canConfirm || canCancel || canDelete">
            <el-button v-if="canAccept" type="primary" size="large" @click="handleAccept" :loading="loading" class="action-btn accept">
              <el-icon><Check /></el-icon> 接单
            </el-button>

            <el-button v-if="canComplete" type="success" size="large" @click="showCompleteDialog = true" class="action-btn">
              <el-icon><Select /></el-icon> 提交完成凭证
            </el-button>

            <el-button v-if="canConfirm" type="success" size="large" @click="handleConfirm" :loading="loading" class="action-btn">
              <el-icon><Select /></el-icon> 确认完成
            </el-button>

            <el-button v-if="canCancel" type="warning" size="large" @click="handleCancelClick" :loading="loading" class="action-btn">
              <el-icon><CloseBold /></el-icon> 取消订单
            </el-button>

            <el-button v-if="canDelete" type="danger" size="large" @click="handleDelete" :loading="loading" class="action-btn">
              <el-icon><Delete /></el-icon> 删除
            </el-button>
          </div>
        </div>
      </div>

      <div class="detail-sidebar">
        <div class="reward-card">
          <div class="reward-label">报酬金额</div>
          <div class="reward-value">¥{{ demand.reward }}</div>
        </div>

        <div class="info-card">
          <h4>需求信息</h4>
          <div class="info-row">
            <span class="info-label">发布者</span>
            <span>
              <a href="javascript:void(0)" @click="$router.push(`/profile/${demand.publisherId}`)" class="profile-link">{{ demand.publisherName }}</a>
            </span>
          </div>
          <div class="info-row">
            <span class="info-label">服务位置</span>
            <span>{{ demand.location }}</span>
          </div>
          <div class="info-row">
            <span class="info-label">期望时间</span>
            <span>{{ formatTime(demand.expectedTime) }}</span>
          </div>
          <div class="info-row">
            <span class="info-label">发布时间</span>
            <span>{{ formatTime(demand.createdAt) }}</span>
          </div>
          <div class="info-row">
            <span class="info-label">当前状态</span>
            <span class="status-text" :class="demand.status.toLowerCase()">{{ getStatusLabel(demand.status) }}</span>
          </div>
          <div v-if="demand.acceptorName" class="info-row">
            <span class="info-label">接单者</span>
            <span>
              <a href="javascript:void(0)" @click="$router.push(`/profile/${demand.acceptorId}`)" class="profile-link">{{ demand.acceptorName }}</a>
            </span>
          </div>
        </div>
      </div>
    </div>

    <!-- 提交完成凭证对话框 -->
    <el-dialog v-model="showCompleteDialog" title="提交完成凭证" width="480px" destroy-on-close>
      <el-form @submit.prevent="handleComplete">
        <el-form-item label="完成说明 / 凭证">
          <el-input v-model="completeProof" type="textarea" :rows="4" placeholder="请描述完成情况，或提供凭证链接（如图片URL、文件链接等）" maxlength="1000" show-word-limit />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showCompleteDialog = false">取消</el-button>
        <el-button type="primary" @click="handleComplete" :loading="loading">提交</el-button>
      </template>
    </el-dialog>

    <!-- 取消订单对话框 -->
    <el-dialog v-model="showCancelDialog" :title="cancelDialogTitle" width="480px" destroy-on-close>
      <el-form @submit.prevent="handleCancel">
        <div v-if="isAcceptorCancel" class="cancel-warning">
          <el-alert title="接单者取消将扣除5信用分" type="warning" :closable="false" show-icon style="margin-bottom: 16px;" />
        </div>
        <el-form-item v-if="isAcceptorCancel" label="取消理由" required>
          <el-input v-model="cancelReason" type="textarea" :rows="3" placeholder="请说明取消理由..." maxlength="500" show-word-limit />
        </el-form-item>
        <div v-else class="cancel-notice">
          <p>发布后30分钟内可无责取消。超过30分钟将无法取消。</p>
        </div>
      </el-form>
      <template #footer>
        <el-button @click="showCancelDialog = false">返回</el-button>
        <el-button type="warning" @click="handleCancel" :loading="loading" :disabled="isAcceptorCancel && !cancelReason.trim()">
          确认取消
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, nextTick } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { getDemandById, acceptDemand, completeDemand, confirmDemand, cancelDemand, deleteDemand } from '../api/demand'
import { getReviewsByDemand, createReview } from '../api/review'
import { getMessagesByDemand, sendMessage } from '../api/message'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Check, Select, CloseBold, Delete } from '@element-plus/icons-vue'

const router = useRouter()
const route = useRoute()
const user = JSON.parse(localStorage.getItem('user') || 'null')
const demand = ref(null)
const loading = ref(false)
const showCompleteDialog = ref(false)
const showCancelDialog = ref(false)
const completeProof = ref('')
const cancelReason = ref('')

// 评价相关
const reviews = ref([])
const myRating = ref(0)
const myComment = ref('')
const reviewLoading = ref(false)

// 消息相关
const messages = ref([])
const newMessage = ref('')
const msgSending = ref(false)
const msgList = ref(null)

const isAcceptorCancel = computed(() => user && demand.value?.acceptorId === user.id)
const cancelDialogTitle = computed(() => isAcceptorCancel.value ? '取消订单（接单方）' : '取消订单（发布方）')

const canAccept = computed(() => demand.value?.status === 'PENDING' && user && demand.value.publisherId !== user.id)
const canComplete = computed(() => demand.value?.status === 'IN_PROGRESS' && user && demand.value.acceptorId === user.id)
const canConfirm = computed(() => demand.value?.status === 'PENDING_CONFIRMATION' && user && demand.value.publisherId === user.id)
const canCancel = computed(() => {
  if (!user) return false
  if (demand.value?.status === 'COMPLETED' || demand.value?.status === 'CANCELLED') return false
  if (demand.value?.publisherId === user.id) {
    if (demand.value.status !== 'PENDING') return false
    return (Date.now() - new Date(demand.value.createdAt).getTime()) <= 30 * 60 * 1000
  }
  if (demand.value?.acceptorId === user.id) {
    return demand.value.status === 'IN_PROGRESS' || demand.value.status === 'PENDING_CONFIRMATION'
  }
  return false
})
const canDelete = computed(() => user && demand.value?.publisherId === user.id)

// 是否可评价：订单已完成且当前用户是参与方且未评价过
const canReview = computed(() => {
  if (demand.value?.status !== 'COMPLETED' || !user) return false
  const isParty = user.id === demand.value.publisherId || user.id === demand.value.acceptorId
  if (!isParty) return false
  return !reviews.value.some(r => r.reviewerId === user.id)
})

// 是否可发消息：订单进行中且当前用户是参与方
const canMessage = computed(() => {
  if (!demand.value || !user) return false
  const s = demand.value.status
  if (s !== 'IN_PROGRESS' && s !== 'PENDING_CONFIRMATION') return false
  return user.id === demand.value.publisherId || user.id === demand.value.acceptorId
})

const getCategoryLabel = (category) => {
  const map = { RUNNING: '跑腿代办', STUDY: '学习辅导', BORROW: '物品借用', OTHER: '其他' }
  return map[category] || category
}

const getStatusLabel = (status) => {
  const map = { PENDING: '待接单', IN_PROGRESS: '进行中', PENDING_CONFIRMATION: '待确认', COMPLETED: '已完成', CANCELLED: '已取消' }
  return map[status] || status
}

const paymentStatusLabel = (s) => ({ UNPAID: '未担保', HELD: '已担保', RELEASED: '已释放', REFUNDED: '已退回' }[s] || s)

const formatTime = (time) => {
  if (!time) return ''
  return new Date(time).toLocaleString('zh-CN')
}

const loadDemand = async () => {
  try {
    demand.value = await getDemandById(route.params.id)
    await loadReviews()
    await loadMessages()
  } catch (error) {
    ElMessage.error(error.message || '加载失败')
  }
}

const loadReviews = async () => {
  try {
    reviews.value = await getReviewsByDemand(demand.value.id)
  } catch { /* ignore */ }
}

const loadMessages = async () => {
  if (!canMessage.value) return
  try {
    messages.value = await getMessagesByDemand(demand.value.id)
    nextTick(() => { if (msgList.value) msgList.value.scrollTop = msgList.value.scrollHeight })
  } catch { /* ignore */ }
}

const submitReview = async () => {
  if (myRating.value === 0) { ElMessage.warning('请选择评分'); return }
  reviewLoading.value = true
  try {
    const targetId = user.id === demand.value.publisherId ? demand.value.acceptorId : demand.value.publisherId
    await createReview({ demandId: demand.value.id, targetUserId: targetId, rating: myRating.value, comment: myComment.value.trim() })
    ElMessage.success('评价成功')
    await loadReviews()
  } catch (e) {
    ElMessage.error(e.message || '评价失败')
  } finally { reviewLoading.value = false }
}

const sendMsg = async () => {
  if (!newMessage.value.trim()) return
  msgSending.value = true
  try {
    const receiverId = user.id === demand.value.publisherId ? demand.value.acceptorId : demand.value.publisherId
    await sendMessage({ demandId: demand.value.id, receiverId, content: newMessage.value.trim() })
    newMessage.value = ''
    await loadMessages()
  } catch (e) {
    ElMessage.error(e.message || '发送失败')
  } finally { msgSending.value = false }
}

const handleAccept = async () => {
  loading.value = true
  try { await acceptDemand(demand.value.id); ElMessage.success('接单成功'); await loadDemand() }
  catch (error) { ElMessage.error(error.message || '接单失败') }
  finally { loading.value = false }
}

const handleComplete = async () => {
  if (!completeProof.value.trim()) { ElMessage.warning('请填写完成说明或凭证'); return }
  loading.value = true
  try {
    await completeDemand(demand.value.id, { proof: completeProof.value.trim() })
    ElMessage.success('完成凭证已提交，等待发布者确认')
    showCompleteDialog.value = false; completeProof.value = ''; await loadDemand()
  } catch (error) { ElMessage.error(error.message || '操作失败') }
  finally { loading.value = false }
}

const handleConfirm = async () => {
  try {
    await ElMessageBox.confirm('确认该需求已完成？', '确认完成', { confirmButtonText: '确认', cancelButtonText: '取消' })
    loading.value = true
    await confirmDemand(demand.value.id)
    ElMessage.success('已确认完成')
    await loadDemand()
  } catch (error) { if (error !== 'cancel') ElMessage.error(error.message || '操作失败') }
  finally { loading.value = false }
}

const handleCancelClick = () => { cancelReason.value = ''; showCancelDialog.value = true }

const handleCancel = async () => {
  if (isAcceptorCancel.value && !cancelReason.value.trim()) { ElMessage.warning('请填写取消理由'); return }
  loading.value = true
  try {
    const data = isAcceptorCancel.value ? { reason: cancelReason.value.trim() } : {}
    await cancelDemand(demand.value.id, data)
    ElMessage.success('已取消')
    showCancelDialog.value = false; await loadDemand()
  } catch (error) { ElMessage.error(error.message || '取消失败') }
  finally { loading.value = false }
}

const handleDelete = async () => {
  try {
    await ElMessageBox.confirm('确定要删除这个需求吗？此操作不可恢复', '警告', { confirmButtonText: '确定', cancelButtonText: '取消', type: 'warning' })
    loading.value = true
    await deleteDemand(demand.value.id)
    ElMessage.success('已删除')
    router.push('/home')
  } catch (error) { if (error !== 'cancel') ElMessage.error(error.message || '删除失败') }
  finally { loading.value = false }
}

onMounted(loadDemand)
</script>

<style scoped>
.detail-page { max-width: 1000px; margin: 0 auto; }
.detail-grid { display: grid; grid-template-columns: 1fr 300px; gap: 24px; align-items: start; }
.detail-card { background: white; border-radius: var(--radius-lg); padding: 36px; border: 1px solid var(--border-light); }

.card-top-bar { display: flex; gap: 10px; margin-bottom: 20px; }
.card-category { padding: 5px 14px; border-radius: 100px; font-size: 13px; font-weight: 600; }
.card-category.running { background: #fff7ed; color: #ea580c; }
.card-category.study { background: #eff6ff; color: #2563eb; }
.card-category.borrow { background: #f0fdf4; color: #16a34a; }
.card-category.other { background: #faf5ff; color: #9333ea; }

.card-status { padding: 5px 14px; border-radius: 100px; font-size: 13px; font-weight: 600; }
.card-status.pending { background: var(--success-bg); color: var(--success); }
.card-status.in_progress { background: var(--info-bg); color: var(--info); }
.card-status.pending_confirmation { background: #fef3c7; color: #d97706; }
.card-status.completed { background: var(--border-light); color: var(--text-muted); }
.card-status.cancelled { background: var(--danger-bg); color: var(--danger); }

.detail-title { font-size: 26px; font-weight: 700; color: var(--text-primary); line-height: 1.4; margin-bottom: 20px; }
.detail-meta { display: flex; flex-wrap: wrap; gap: 20px; margin-bottom: 28px; padding-bottom: 24px; border-bottom: 1px solid var(--border-light); }
.meta-item { display: flex; align-items: center; gap: 6px; font-size: 14px; color: var(--text-secondary); }

.detail-desc-section h3 { font-size: 16px; font-weight: 600; color: var(--text-primary); margin-bottom: 12px; }
.detail-desc-section p { font-size: 15px; color: var(--text-secondary); line-height: 1.8; }

.payment-status { margin: 16px 0; display: flex; align-items: center; gap: 8px; font-size: 14px; }
.payment-tag { padding: 3px 10px; border-radius: 100px; font-size: 12px; font-weight: 600; }
.payment-tag.held { background: #e0e7ff; color: #4f46e5; }
.payment-tag.released { background: #d1fae5; color: #059669; }
.payment-tag.refunded { background: #fee2e2; color: #dc2626; }

.proof-section { margin-top: 24px; padding-top: 24px; border-top: 1px solid var(--border-light); }
.proof-section h3 { font-size: 16px; font-weight: 600; color: var(--text-primary); margin-bottom: 12px; }
.proof-content { background: #f8fafc; border-radius: var(--radius); padding: 16px; font-size: 14px; color: var(--text-secondary); line-height: 1.6; white-space: pre-wrap; }

.cancel-info-section { margin-top: 24px; padding-top: 24px; border-top: 1px solid var(--border-light); }
.cancel-info-section h3 { font-size: 16px; font-weight: 600; color: var(--text-primary); margin-bottom: 12px; }
.cancel-info { background: #fef2f2; border-radius: var(--radius); padding: 16px; }
.cancel-row { font-size: 14px; color: var(--text-secondary); padding: 4px 0; }
.cancel-label { font-weight: 500; color: var(--text-primary); }

/* 评价区域 */
.review-section { margin-top: 24px; padding-top: 24px; border-top: 1px solid var(--border-light); }
.review-section h3 { font-size: 16px; font-weight: 600; color: var(--text-primary); margin-bottom: 16px; }
.review-list { display: flex; flex-direction: column; gap: 10px; margin-bottom: 16px; }
.review-card { background: #f8fafc; border-radius: var(--radius); padding: 14px 16px; }
.review-card-top { display: flex; justify-content: space-between; align-items: center; margin-bottom: 4px; }
.reviewer-name { font-size: 13px; font-weight: 600; color: var(--text-primary); }
.review-stars { color: #f59e0b; font-size: 14px; letter-spacing: 2px; }
.review-text { font-size: 13px; color: var(--text-secondary); line-height: 1.5; margin-bottom: 4px; }
.review-time { font-size: 11px; color: var(--text-muted); }

.review-form { background: #fffbeb; border-radius: var(--radius); padding: 16px; }
.review-rate { display: flex; align-items: center; gap: 4px; font-size: 14px; }
.star-btn { background: none; border: none; font-size: 22px; cursor: pointer; color: #d1d5db; padding: 0 2px; transition: color 0.15s; }
.star-btn.on { color: #f59e0b; }

/* 消息区域 */
.message-section { margin-top: 24px; padding-top: 24px; border-top: 1px solid var(--border-light); }
.message-section h3 { font-size: 16px; font-weight: 600; color: var(--text-primary); margin-bottom: 12px; }
.message-list { max-height: 300px; overflow-y: auto; display: flex; flex-direction: column; gap: 8px; padding: 8px 0; }
.msg-bubble { max-width: 75%; padding: 10px 14px; border-radius: 12px; background: #f3f4f6; align-self: flex-start; }
.msg-bubble.mine { background: #e0e7ff; align-self: flex-end; }
.msg-sender { font-size: 11px; font-weight: 600; color: var(--text-muted); margin-bottom: 2px; }
.msg-text { font-size: 13px; color: var(--text-primary); line-height: 1.5; word-break: break-word; }
.msg-time { font-size: 10px; color: var(--text-muted); text-align: right; margin-top: 2px; }
.msg-empty { text-align: center; color: var(--text-muted); font-size: 13px; padding: 20px 0; }
.message-input-row { display: flex; gap: 8px; margin-top: 10px; }

.detail-actions { display: flex; gap: 12px; margin-top: 32px; padding-top: 24px; border-top: 1px solid var(--border-light); flex-wrap: wrap; }
.action-btn { border-radius: var(--radius); font-weight: 600; }
.action-btn.accept { background: linear-gradient(135deg, var(--primary) 0%, var(--primary-light) 100%); border: none; }
.cancel-warning { margin-bottom: 8px; }
.cancel-notice { padding: 8px 0; color: var(--text-secondary); font-size: 14px; line-height: 1.6; }

.detail-sidebar { display: flex; flex-direction: column; gap: 20px; position: sticky; top: 92px; }
.reward-card { background: linear-gradient(135deg, var(--primary) 0%, #7c3aed 100%); border-radius: var(--radius-lg); padding: 28px; text-align: center; color: white; }
.reward-label { font-size: 14px; opacity: 0.85; margin-bottom: 8px; }
.reward-value { font-size: 40px; font-weight: 800; }
.info-card { background: white; border-radius: var(--radius-lg); padding: 24px; border: 1px solid var(--border-light); }
.info-card h4 { font-size: 15px; font-weight: 600; color: var(--text-primary); margin-bottom: 16px; }
.info-row { display: flex; justify-content: space-between; padding: 10px 0; border-bottom: 1px solid var(--border-light); font-size: 14px; }
.info-row:last-child { border-bottom: none; }
.info-label { color: var(--text-muted); }
.profile-link { color: var(--primary); text-decoration: none; font-weight: 500; }
.profile-link:hover { text-decoration: underline; }

.status-text.pending { color: var(--success); font-weight: 600; }
.status-text.in_progress { color: var(--info); font-weight: 600; }
.status-text.pending_confirmation { color: #d97706; font-weight: 600; }
.status-text.completed { color: var(--text-muted); }
.status-text.cancelled { color: var(--danger); font-weight: 600; }
.acceptor-name { color: var(--primary); font-weight: 600; }

@media (max-width: 768px) {
  .detail-grid { grid-template-columns: 1fr; }
  .detail-sidebar { position: static; }
}
</style>
