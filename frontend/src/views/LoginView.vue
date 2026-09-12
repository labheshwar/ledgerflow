<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ApiError } from '../lib/api'
import { useAuthStore } from '../stores/auth'

const auth = useAuthStore()
const router = useRouter()
const route = useRoute()

const form = reactive({ username: '', password: '' })
const submitting = ref(false)
const errorText = ref('')

async function submit() {
  if (!form.username.trim() || !form.password || submitting.value) return

  submitting.value = true
  errorText.value = ''
  try {
    await auth.login(form.username.trim(), form.password)
    const redirect = typeof route.query.redirect === 'string' ? route.query.redirect : '/dashboard'
    router.push(redirect)
  } catch (e) {
    errorText.value = e instanceof ApiError ? e.message : 'Unable to sign in. Please try again.'
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <div class="wrap theme-light">
    <div class="card">
      <div class="mark">LF</div>
      <h1 class="serif">Sign in to LedgerFlow</h1>
      <div class="sub">Internal ledger &amp; reconciliation console</div>

      <form @submit.prevent="submit">
        <div class="field">
          <label>Username</label>
          <input
            v-model="form.username"
            class="input"
            type="text"
            placeholder="admin"
            autocomplete="username"
          />
        </div>
        <div class="field">
          <label>Password</label>
          <input
            v-model="form.password"
            class="input"
            type="password"
            placeholder="••••••••"
            autocomplete="current-password"
          />
        </div>

        <div v-if="errorText" class="field-error">{{ errorText }}</div>

        <button class="btn-primary" type="submit" :disabled="submitting">
          {{ submitting ? 'Signing in…' : 'Sign in' }}
        </button>
      </form>
      <div class="foot">Role is assigned to your account and determines which actions you can take.</div>
    </div>
  </div>
</template>

<style scoped>
.wrap {
  width: 100%;
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
}
.card {
  width: 360px;
  background: var(--raised);
  border: 1px solid var(--line);
  border-radius: 8px;
  padding: 32px;
  box-shadow:
    0 2px 8px rgba(27, 36, 32, 0.06),
    0 1px 0 rgba(27, 36, 32, 0.02);
}
.mark {
  width: 34px;
  height: 34px;
  border-radius: 8px;
  background: var(--green);
  color: var(--paper);
  display: flex;
  align-items: center;
  justify-content: center;
  font-family: 'IBM Plex Mono', monospace;
  font-size: 15px;
  font-weight: 600;
  margin-bottom: 16px;
}
.card h1 {
  font-size: 21px;
  font-weight: 600;
  margin: 0 0 4px;
}
.card .sub {
  color: var(--ink-soft);
  font-size: 12.5px;
  margin-bottom: 22px;
}
.btn-primary {
  width: 100%;
  padding: 10px 0;
  border-radius: 5px;
  border: 1px solid var(--green);
  background: var(--green);
  color: var(--paper);
  font-size: 13px;
  font-weight: 500;
}
.btn-primary:disabled {
  opacity: 0.6;
  cursor: default;
}
.foot {
  margin-top: 16px;
  text-align: center;
  font-size: 11.5px;
  color: var(--ink-faint);
}
</style>
