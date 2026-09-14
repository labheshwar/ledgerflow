<script setup lang="ts">
import { computed, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { signUp } from '@/lib/api/auth'
import { ApiError } from '@/lib/http'
import { useAuthStore } from '@/stores/auth'
import { useToastStore } from '@/stores/toast'

const auth = useAuthStore()
const toasts = useToastStore()
const router = useRouter()

const form = reactive({ organizationName: '', username: '', password: '' })
const submitting = ref(false)
const errorText = ref('')
const submitAttempted = ref(false)

const passwordTooShort = computed(() => form.password.length > 0 && form.password.length < 8)
const canSubmit = computed(
  () =>
    form.organizationName.trim().length > 0 && form.username.trim().length >= 3 && form.password.length >= 8,
)

async function submit() {
  submitAttempted.value = true
  if (!canSubmit.value || submitting.value) return

  submitting.value = true
  errorText.value = ''
  try {
    const { token } = await signUp({
      username: form.username.trim(),
      password: form.password,
      organizationName: form.organizationName.trim(),
    })
    auth.adopt(token)
    toasts.success(`Welcome to ${form.organizationName.trim()}`)
    router.push('/dashboard')
  } catch (e) {
    errorText.value = e instanceof ApiError ? e.message : 'Unable to create your account.'
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <div class="wrap theme-light">
    <div class="card">
      <div class="mark">LF</div>
      <h1 class="serif">Create your organization</h1>
      <div class="sub">Your books, your ledger — separate from everyone else's.</div>

      <form @submit.prevent="submit">
        <div class="field">
          <label for="org">Organization name</label>
          <input id="org" v-model="form.organizationName" class="input" placeholder="Acme Design Studio" />
        </div>
        <div class="field">
          <label for="username">Username</label>
          <input
            id="username"
            v-model="form.username"
            class="input"
            autocomplete="username"
            placeholder="At least 3 characters"
          />
        </div>
        <div class="field">
          <label for="password">Password</label>
          <input
            id="password"
            v-model="form.password"
            class="input"
            :class="{ error: submitAttempted && passwordTooShort }"
            type="password"
            autocomplete="new-password"
            placeholder="At least 8 characters"
          />
          <div v-if="passwordTooShort" class="field-error">Passwords must be at least 8 characters.</div>
        </div>

        <div v-if="errorText" class="field-error">{{ errorText }}</div>

        <button class="btn-primary" type="submit" :disabled="submitting">
          {{ submitting ? 'Creating…' : 'Create organization' }}
        </button>
      </form>

      <div class="foot">
        Already have an account?
        <RouterLink to="/login">Sign in</RouterLink>
      </div>
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
