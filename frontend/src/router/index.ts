import { createRouter, createWebHistory } from 'vue-router'
import { useAuthStore } from '../stores/auth'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/', redirect: '/dashboard' },
    { path: '/login', name: 'login', component: () => import('../views/LoginView.vue') },
    { path: '/dashboard', name: 'dashboard', component: () => import('../views/DashboardView.vue') },
    { path: '/accounts', name: 'accounts', component: () => import('../views/AccountsView.vue') },
    {
      path: '/accounts/:id',
      name: 'account-detail',
      component: () => import('../views/AccountDetailView.vue'),
    },
    { path: '/transactions', name: 'transactions', component: () => import('../views/TransactionsView.vue') },
    {
      path: '/transactions/new',
      name: 'post-transaction',
      component: () => import('../views/PostTransactionView.vue'),
    },
    {
      path: '/transactions/:id',
      name: 'transaction-detail',
      component: () => import('../views/TransactionDetailView.vue'),
    },
    {
      path: '/reconciliation',
      name: 'reconciliation',
      component: () => import('../views/ReconciliationView.vue'),
    },
    {
      path: '/reconciliation/:id',
      name: 'batch-detail',
      component: () => import('../views/BatchDetailView.vue'),
    },
    { path: '/audit-log', name: 'audit-log', component: () => import('../views/AuditLogView.vue') },
  ],
})

router.beforeEach((to) => {
  const auth = useAuthStore()

  if (to.name !== 'login' && !auth.isAuthenticated) {
    return { name: 'login', query: { redirect: to.fullPath } }
  }
  if (to.name === 'login' && auth.isAuthenticated) {
    return { name: 'dashboard' }
  }
})

export default router
