<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import AppShell from '../layouts/AppShell.vue'
import { apiFetch } from '../lib/api'
import { formatDateTime } from '../lib/format'
import type { TransactionListItem } from '../lib/types'
import { useAuthStore } from '../stores/auth'

const auth = useAuthStore()
const transactions = ref<TransactionListItem[]>([])
const loading = ref(true)
const errorText = ref('')
const query = ref('')

onMounted(async () => {
  try {
    transactions.value = await apiFetch<TransactionListItem[]>('/transactions')
  } catch {
    errorText.value = 'Unable to load transactions.'
  } finally {
    loading.value = false
  }
})

const filtered = computed(() => {
  const q = query.value.trim().toLowerCase()
  if (q === '') return transactions.value
  return transactions.value.filter(
    (t) => (t.description ?? '').toLowerCase().includes(q) || `txn-${t.id}`.includes(q),
  )
})
</script>

<template>
  <AppShell>
    <template #title>Transactions</template>
    <template #sub>{{ filtered.length }} of {{ transactions.length }} transactions</template>
    <template #actions>
      <RouterLink v-if="auth.isAdmin" class="btn btn-primary" to="/transactions/new">Post transaction</RouterLink>
    </template>

    <p v-if="loading">Loading…</p>
    <p v-else-if="errorText" class="field-error">{{ errorText }}</p>
    <template v-else>
      <div class="toolbar">
        <div class="search">
          <svg viewBox="0 0 16 16"><circle cx="7" cy="7" r="5"></circle><path d="M11 11l3.2 3.2"></path></svg>
          <input v-model="query" placeholder="Search transactions…" />
        </div>
      </div>

      <div class="tablecard">
        <table>
          <tr>
            <th>Reference</th>
            <th>Description</th>
            <th>Status</th>
            <th>Posted at</th>
          </tr>
          <tr v-for="t in filtered" :key="t.id">
            <td class="mono"><RouterLink :to="`/transactions/${t.id}`">TXN-{{ t.id }}</RouterLink></td>
            <td>{{ t.description || '—' }}</td>
            <td><span class="pill pill-green">{{ t.status }}</span></td>
            <td class="mono" style="color: var(--ink-soft)">{{ formatDateTime(t.createdAt) }}</td>
          </tr>
        </table>
        <div v-if="filtered.length === 0" class="empty">No transactions match "{{ query }}".</div>
      </div>
    </template>
  </AppShell>
</template>

<style scoped>
.toolbar {
  display: flex;
  margin-bottom: 14px;
}
.search {
  position: relative;
  width: 260px;
}
.search svg {
  position: absolute;
  left: 10px;
  top: 50%;
  transform: translateY(-50%);
  width: 14px;
  height: 14px;
  stroke: var(--ink-faint);
  fill: none;
  stroke-width: 1.6;
}
.search input {
  width: 100%;
  padding: 8px 10px 8px 30px;
  border-radius: 5px;
  border: 1px solid var(--line);
  background: var(--raised);
  color: var(--ink);
  font-size: 13px;
}
.search input:focus {
  outline: 2px solid var(--focus);
  outline-offset: 1px;
}
.empty {
  padding: 36px 16px;
  text-align: center;
  color: var(--ink-faint);
  font-size: 13px;
}
</style>
