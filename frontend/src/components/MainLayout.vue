<template>
  <div class="layout">
    <aside class="sidebar" :class="{ collapsed: isCollapsed }">
      <div class="sidebar-logo" @click="$router.push('/home')">
        <div class="logo-icon">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <path d="M17 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2"/>
            <circle cx="9" cy="7" r="4"/>
            <path d="M23 21v-2a4 4 0 0 0-3-3.87"/>
            <path d="M16 3.13a4 4 0 0 1 0 7.75"/>
          </svg>
        </div>
        <transition name="fade-text">
          <span v-if="!isCollapsed" class="logo-text">校园互助</span>
        </transition>
      </div>

      <nav class="sidebar-nav">
        <router-link
          v-for="item in navItems"
          :key="item.path"
          :to="item.path"
          class="nav-item"
          :class="{ active: $route.path === item.path }"
        >
          <div class="nav-icon" v-html="item.icon"></div>
          <transition name="fade-text">
            <span v-if="!isCollapsed" class="nav-label">{{ item.label }}</span>
          </transition>
          <span v-if="item.badge && item.badge > 0" class="nav-badge">{{ item.badge > 99 ? '99+' : item.badge }}</span>
        </router-link>
      </nav>

      <div class="sidebar-footer">
        <div v-if="!isCollapsed" class="credit-mini">
          <span class="credit-label">信用分</span>
          <span class="credit-value" :class="creditLevel">{{ userCreditScore }}</span>
        </div>
        <button class="collapse-btn" @click="isCollapsed = !isCollapsed">
          <svg :class="{ rotated: isCollapsed }" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="20" height="20">
            <polyline points="11 17 6 12 11 7"/>
            <polyline points="18 17 13 12 18 7"/>
          </svg>
        </button>
      </div>
    </aside>

    <div class="main-area" :class="{ 'sidebar-collapsed': isCollapsed }">
      <header class="topbar">
        <div class="topbar-left">
          <h1 class="page-title">{{ currentPageTitle }}</h1>
        </div>
        <div class="topbar-right">
          <router-link to="/notifications" class="notif-btn">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="20" height="20">
              <path d="M18 8A6 6 0 0 0 6 8c0 7-3 9-3 9h18s-3-2-3-9"/>
              <path d="M13.73 21a2 2 0 0 1-3.46 0"/>
            </svg>
            <span v-if="unreadCount > 0" class="notif-dot">{{ unreadCount > 99 ? '99+' : unreadCount }}</span>
          </router-link>
          <el-dropdown trigger="click">
            <div class="user-chip">
              <div class="user-avatar">{{ userInitial }}</div>
              <span class="user-name">{{ userName }}</span>
              <el-icon><ArrowDown /></el-icon>
            </div>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item @click="$router.push('/home')">
                  <el-icon><HomeFilled /></el-icon>首页
                </el-dropdown-item>
                <el-dropdown-item @click="$router.push('/profile')">
                  <el-icon><User /></el-icon>个人中心
                </el-dropdown-item>
                <el-dropdown-item @click="$router.push('/notifications')">
                  <el-icon><Bell /></el-icon>消息通知
                  <el-badge v-if="unreadCount > 0" :value="unreadCount" style="margin-left: 8px;" />
                </el-dropdown-item>
                <el-dropdown-item v-if="isAdmin" @click="$router.push('/admin/appeals')">
                  <el-icon><Setting /></el-icon>申诉管理
                </el-dropdown-item>
                <el-dropdown-item divided @click="logout">
                  <el-icon><SwitchButton /></el-icon>退出登录
                </el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </header>

      <main class="content">
        <router-view />
      </main>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { getUnreadCount } from '../api/notification'

const router = useRouter()
const route = useRoute()
const isCollapsed = ref(false)
const unreadCount = ref(0)

const user = JSON.parse(localStorage.getItem('user') || '{}')
if (!user.id) {
  router.push('/login')
}
const userName = computed(() => user.name || '用户')
const userInitial = computed(() => (user.name || '用')[0])
const userCreditScore = computed(() => user.creditScore ?? 100)
const isAdmin = computed(() => user.role === 'ADMIN')

const creditLevel = computed(() => {
  const s = userCreditScore.value
  if (s >= 80) return 'high'
  if (s >= 60) return 'mid'
  if (s >= 40) return 'low'
  return 'critical'
})

const navItems = [
  {
    path: '/home',
    label: '需求广场',
    icon: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="20" height="20"><rect x="3" y="3" width="7" height="7"/><rect x="14" y="3" width="7" height="7"/><rect x="14" y="14" width="7" height="7"/><rect x="3" y="14" width="7" height="7"/></svg>'
  },
  {
    path: '/publish',
    label: '发布需求',
    icon: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="20" height="20"><circle cx="12" cy="12" r="10"/><line x1="12" y1="8" x2="12" y2="16"/><line x1="8" y1="12" x2="16" y2="12"/></svg>'
  },
  {
    path: '/my-demands',
    label: '我的发布',
    icon: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="20" height="20"><path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"/><polyline points="14 2 14 8 20 8"/><line x1="16" y1="13" x2="8" y2="13"/><line x1="16" y1="17" x2="8" y2="17"/></svg>'
  },
  {
    path: '/my-orders',
    label: '我的接单',
    icon: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="20" height="20"><path d="M22 11.08V12a10 10 0 1 1-5.93-9.14"/><polyline points="22 4 12 14.01 9 11.01"/></svg>'
  },
  {
    path: '/notifications',
    label: '消息通知',
    badge: computed(() => unreadCount.value),
    icon: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="20" height="20"><path d="M18 8A6 6 0 0 0 6 8c0 7-3 9-3 9h18s-3-2-3-9"/><path d="M13.73 21a2 2 0 0 1-3.46 0"/></svg>'
  }
]

const currentPageTitle = computed(() => {
  const item = navItems.find(n => n.path === route.path)
  if (item) return item.label
  if (route.path.startsWith('/demand/')) return '需求详情'
  if (route.path.startsWith('/profile')) return '个人中心'
  if (route.path === '/admin/appeals') return '申诉管理'
  return '校园互助平台'
})

const fetchUnreadCount = async () => {
  try {
    const res = await getUnreadCount()
    unreadCount.value = res.count
  } catch { /* ignore */ }
}

let timer = null
onMounted(() => {
  fetchUnreadCount()
  timer = setInterval(fetchUnreadCount, 30000)
})
onUnmounted(() => {
  if (timer) clearInterval(timer)
})

const logout = () => {
  localStorage.removeItem('user')
  localStorage.removeItem('token')
  router.push('/login')
}
</script>

<style scoped>
.layout {
  display: flex;
  min-height: 100vh;
  background: var(--bg-page);
}

.sidebar {
  width: 240px;
  background: linear-gradient(180deg, #1e1b4b 0%, #312e81 100%);
  display: flex;
  flex-direction: column;
  transition: width 0.3s cubic-bezier(0.4, 0, 0.2, 1);
  position: fixed;
  top: 0;
  left: 0;
  bottom: 0;
  z-index: 100;
  overflow: hidden;
}

.sidebar.collapsed {
  width: 72px;
}

.sidebar-logo {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 24px 20px;
  cursor: pointer;
  border-bottom: 1px solid rgba(255,255,255,0.08);
}

.logo-icon {
  width: 36px;
  height: 36px;
  color: #a5b4fc;
  flex-shrink: 0;
  display: flex;
  align-items: center;
  justify-content: center;
}

.logo-icon svg {
  width: 28px;
  height: 28px;
}

.logo-text {
  font-size: 18px;
  font-weight: 700;
  color: white;
  white-space: nowrap;
}

.sidebar-nav {
  flex: 1;
  padding: 16px 12px;
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.nav-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px 14px;
  border-radius: var(--radius);
  color: #c7d2fe;
  text-decoration: none;
  transition: all 0.2s ease;
  cursor: pointer;
  position: relative;
}

.nav-item:hover {
  background: rgba(255,255,255,0.08);
  color: white;
}

.nav-item.active {
  background: rgba(255,255,255,0.15);
  color: white;
  box-shadow: 0 0 0 1px rgba(255,255,255,0.1);
}

.nav-icon {
  width: 20px;
  height: 20px;
  flex-shrink: 0;
  display: flex;
  align-items: center;
  justify-content: center;
}

.nav-label {
  font-size: 14px;
  font-weight: 500;
  white-space: nowrap;
}

.nav-badge {
  position: absolute;
  right: 8px;
  top: 50%;
  transform: translateY(-50%);
  background: #ef4444;
  color: white;
  font-size: 11px;
  font-weight: 600;
  min-width: 20px;
  height: 20px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 10px;
  padding: 0 6px;
}

.sidebar-footer {
  padding: 12px;
  border-top: 1px solid rgba(255,255,255,0.08);
}

.credit-mini {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 8px 12px;
  margin-bottom: 8px;
  background: rgba(255,255,255,0.06);
  border-radius: var(--radius);
  color: #c7d2fe;
  font-size: 13px;
}

.credit-value {
  font-weight: 700;
  font-size: 16px;
}

.credit-value.high { color: #34d399; }
.credit-value.mid { color: #fbbf24; }
.credit-value.low { color: #fb923c; }
.credit-value.critical { color: #f87171; }

.collapse-btn {
  width: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 10px;
  border-radius: var(--radius);
  background: rgba(255,255,255,0.06);
  border: none;
  color: #c7d2fe;
  cursor: pointer;
  transition: all 0.2s;
}

.collapse-btn:hover {
  background: rgba(255,255,255,0.12);
  color: white;
}

.collapse-btn svg.rotated {
  transform: rotate(180deg);
}

.collapse-btn svg {
  transition: transform 0.3s ease;
}

.main-area {
  flex: 1;
  margin-left: 240px;
  transition: margin-left 0.3s cubic-bezier(0.4, 0, 0.2, 1);
  display: flex;
  flex-direction: column;
  min-height: 100vh;
}

.main-area.sidebar-collapsed {
  margin-left: 72px;
}

.topbar {
  height: 64px;
  background: var(--bg-card);
  border-bottom: 1px solid var(--border-light);
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 32px;
  position: sticky;
  top: 0;
  z-index: 50;
}

.page-title {
  font-size: 18px;
  font-weight: 600;
  color: var(--text-primary);
}

.topbar-right {
  display: flex;
  align-items: center;
  gap: 16px;
}

.notif-btn {
  position: relative;
  width: 40px;
  height: 40px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 50%;
  color: var(--text-secondary);
  transition: all 0.2s;
}

.notif-btn:hover {
  background: var(--border-light);
  color: var(--primary);
}

.notif-dot {
  position: absolute;
  top: 2px;
  right: 2px;
  background: #ef4444;
  color: white;
  font-size: 10px;
  font-weight: 600;
  min-width: 16px;
  height: 16px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 8px;
  padding: 0 4px;
}

.user-chip {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 6px 14px 6px 6px;
  border-radius: 100px;
  background: var(--border-light);
  cursor: pointer;
  transition: all 0.2s;
}

.user-chip:hover {
  background: var(--border);
}

.user-avatar {
  width: 32px;
  height: 32px;
  border-radius: 50%;
  background: linear-gradient(135deg, var(--primary), var(--primary-light));
  color: white;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 14px;
  font-weight: 600;
}

.user-name {
  font-size: 14px;
  font-weight: 500;
  color: var(--text-primary);
}

.content {
  flex: 1;
  padding: 28px 32px;
}

.fade-text-enter-active,
.fade-text-leave-active {
  transition: opacity 0.15s ease;
}
.fade-text-enter-from,
.fade-text-leave-to {
  opacity: 0;
}

@media (max-width: 768px) {
  .sidebar {
    width: 72px;
  }
  .sidebar .logo-text,
  .sidebar .nav-label {
    display: none;
  }
  .main-area {
    margin-left: 72px;
  }
  .content {
    padding: 16px;
  }
  .topbar {
    padding: 0 16px;
  }
}
</style>
