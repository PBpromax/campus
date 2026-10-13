import { createRouter, createWebHistory } from 'vue-router'

const routes = [
  {
    path: '/',
    component: () => import('../components/MainLayout.vue'),
    redirect: '/home',
    children: [
      {
        path: 'home',
        name: 'Home',
        component: () => import('../views/Home.vue')
      },
      {
        path: 'publish',
        name: 'Publish',
        component: () => import('../views/Publish.vue')
      },
      {
        path: 'my-demands',
        name: 'MyDemands',
        component: () => import('../views/MyDemands.vue')
      },
      {
        path: 'my-orders',
        name: 'MyOrders',
        component: () => import('../views/MyOrders.vue')
      },
      {
        path: 'demand/:id',
        name: 'DemandDetail',
        component: () => import('../views/DemandDetail.vue')
      },
      {
        path: 'profile',
        name: 'UserProfile',
        component: () => import('../views/UserProfile.vue')
      },
      {
        path: 'profile/:id',
        name: 'UserProfileById',
        component: () => import('../views/UserProfile.vue')
      },
      {
        path: 'notifications',
        name: 'Notifications',
        component: () => import('../views/NotificationCenter.vue')
      },
      {
        path: 'admin/appeals',
        name: 'AdminAppeals',
        component: () => import('../views/AdminAppeals.vue')
      }
    ]
  },
  {
    path: '/login',
    name: 'Login',
    component: () => import('../views/Login.vue')
  },
  {
    path: '/register',
    name: 'Register',
    component: () => import('../views/Register.vue')
  },
  {
    path: '/complete-info',
    name: 'CompleteInfo',
    component: () => import('../views/CompleteInfo.vue')
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

router.beforeEach((to, from, next) => {
  const user = localStorage.getItem('user')
  const publicPages = ['/login', '/register', '/complete-info']
  if (!publicPages.includes(to.path) && !user) {
    next('/login')
  } else {
    next()
  }
})

export default router
