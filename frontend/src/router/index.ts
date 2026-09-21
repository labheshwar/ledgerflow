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
    {
      path: '/public/invoices/:token',
      name: 'public-invoice',
      component: () => import('../views/PublicInvoiceView.vue'),
    },
    { path: '/bills', name: 'bills', component: () => import('../views/BillsView.vue') },
    { path: '/bills/new', name: 'bill-new', component: () => import('../views/BillFormView.vue') },
    {
      path: '/bills/:id',
      name: 'bill-detail',
      component: () => import('../views/BillDetailView.vue'),
    },
    { path: '/payments', name: 'payments', component: () => import('../views/PaymentsView.vue') },
    { path: '/payments/new', name: 'payment-new', component: () => import('../views/PaymentFormView.vue') },
    {
      path: '/payments/:id',
      name: 'payment-detail',
      component: () => import('../views/PaymentDetailView.vue'),
    },
    {
      path: '/bank-accounts',
      name: 'bank-accounts',
      component: () => import('../views/BankAccountsView.vue'),
    },
    {
      path: '/bank-accounts/:id',
      name: 'bank-account-detail',
      component: () => import('../views/BankAccountDetailView.vue'),
    },
    {
      path: '/bank-accounts/:id/imports/new',
      name: 'statement-import-new',
      component: () => import('../views/StatementImportUploadView.vue'),
    },
    {
      path: '/bank-accounts/:id/imports/:importId',
      name: 'statement-import-detail',
      component: () => import('../views/StatementImportView.vue'),
    },
    {
      path: '/bank-accounts/:id/reconcile',
      name: 'reconciliation',
      component: () => import('../views/ReconciliationView.vue'),
    },
  ],
})

const PUBLIC_ROUTES = new Set(['login', 'signup'])
// Open to anyone with the link, signed in or not -- unlike login/signup,
// an authenticated admin previewing what a customer sees should not be
// bounced to their own dashboard.
const ALWAYS_OPEN_ROUTES = new Set(['public-invoice'])

router.beforeEach((to) => {
  const auth = useAuthStore()
  if (ALWAYS_OPEN_ROUTES.has(String(to.name))) return

  const isPublic = PUBLIC_ROUTES.has(String(to.name))

  if (!isPublic && !auth.isAuthenticated) {
    return { name: 'login', query: { redirect: to.fullPath } }
  }
  if (isPublic && auth.isAuthenticated) {
    return { name: 'dashboard' }
  }
})

export default router
