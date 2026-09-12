import { createApp } from 'vue'
import { createPinia } from 'pinia'
import { VueQueryPlugin } from '@tanstack/vue-query'
import App from './App.vue'
import router from './router'
import { setUnauthorizedHandler } from './lib/http'
import { useAuthStore } from './stores/auth'
import { useToastStore } from './stores/toast'
import './styles/tokens.css'
import './styles/base.css'
import './styles/patterns.css'

const app = createApp(App)
const pinia = createPinia()

app.use(pinia)
app.use(VueQueryPlugin, {
  queryClientConfig: {
    defaultOptions: {
      queries: {
        staleTime: 30_000,
        retry: 1,
        refetchOnWindowFocus: false,
      },
      // A failed write must surface, not silently replay against a ledger.
      mutations: { retry: 0 },
    },
  },
})
app.use(router)

// Registered here rather than inside lib/http, which the auth store imports.
setUnauthorizedHandler(() => {
  const auth = useAuthStore(pinia)
  if (!auth.token) return

  auth.logout()
  useToastStore(pinia).error('Your session expired — please sign in again.')
  router.replace({ name: 'login', query: { redirect: router.currentRoute.value.fullPath } })
})

app.mount('#app')
