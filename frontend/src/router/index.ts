import { createRouter, createWebHistory } from 'vue-router'
import { useAuthStore } from '../stores/auth'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/', redirect: '/dashboard' },
    { path: '/login', name: 'login', component: () => import('../views/LoginView.vue') },
    { path: '/signup', name: 'signup', component: () => import('../views/SignUpView.vue') },
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
      component: () => import('../views/PostJournalView.vue'),
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
    { path: '/periods', name: 'periods', component: () => import('../views/PeriodsView.vue') },
    { path: '/contacts', name: 'contacts', component: () => import('../views/ContactsView.vue') },
    { path: '/tax-rates', name: 'tax-rates', component: () => import('../views/TaxRatesView.vue') },
    { path: '/items', name: 'items', component: () => import('../views/ItemsView.vue') },
    { path: '/invoices', name: 'invoices', component: () => import('../views/InvoicesView.vue') },
    { path: '/invoices/new', name: 'invoice-new', component: () => import('../views/InvoiceFormView.vue') },
    {
      path: '/invoices/:id',
      name: 'invoice-detail',
      component: () => import('../views/InvoiceDetailView.vue'),
    },
  ],
})

const PUBLIC_ROUTES = new Set(['login', 'signup'])

router.beforeEach((to) => {
  const auth = useAuthStore()
  const isPublic = PUBLIC_ROUTES.has(String(to.name))

  if (!isPublic && !auth.isAuthenticated) {
    return { name: 'login', query: { redirect: to.fullPath } }
  }
  if (isPublic && auth.isAuthenticated) {
    return { name: 'dashboard' }
  }
})

export default router
