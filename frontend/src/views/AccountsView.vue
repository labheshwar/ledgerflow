<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import AppShell from '../layouts/AppShell.vue'
import { apiFetch } from '../lib/api'
import { accountTypePillClass, formatMoney, formatRelativeTime } from '../lib/format'
import type { Account, AccountType, Paged } from '../lib/types'

const TYPES: AccountType[] = ['ASSET', 'LIABILITY', 'EQUITY', 'REVENUE', 'EXPENSE']

const accounts = ref<Account[]>([])
const loading = ref(true)
const errorText = ref('')
const query = ref('')
const filter = ref<AccountType | 'ALL'>('ALL')

onMounted(async () => {
  try {
    accounts.value = (await apiFetch<Paged<Account>>('/accounts?size=200')).content
  } catch {
    errorText.value = 'Unable to load accounts.'
  } finally {
    loading.value = false
  }
})

const filtered = computed(() => {
  const q = query.value.trim().toLowerCase()
  return accounts.value.filter(
    (a) =>
      (filter.value === 'ALL' || a.type === filter.value) && (q === '' || a.name.toLowerCase().includes(q)),
  )
})

function chipClass(type: AccountType | 'ALL') {
  return 'chip' + (filter.value === type ? ' on' : '')
}
</script>

<template>
  <AppShell>
    <template #title>Accounts</template>
    <template #sub>{{ filtered.length }} of {{ accounts.length }} accounts</template>

    <p v-if="loading">Loading…</p>
    <p v-else-if="errorText" class="field-error">{{ errorText }}</p>
    <template v-else>
      <div class="toolbar">
        <div class="search">
          <svg viewBox="0 0 16 16">
            <circle cx="7" cy="7" r="5"></circle>
            <path d="M11 11l3.2 3.2"></path>
          </svg>
          <input v-model="query" placeholder="Search accounts…" />
        </div>
        <div class="filters">
          <button :class="chipClass('ALL')" @click="filter = 'ALL'">All</button>
          <button v-for="t in TYPES" :key="t" :class="chipClass(t)" @click="filter = t">
            {{ t.charAt(0) + t.slice(1).toLowerCase() }}
          </button>
        </div>
      </div>

      <div class="tablecard">
        <table>
          <tr>
            <th>Name</th>
            <th>Type</th>
            <th>Currency</th>
            <th style="text-align: right">Balance</th>
            <th>Last updated</th>
          </tr>
          <tr v-for="a in filtered" :key="a.id">
            <td>
              <RouterLink :to="`/accounts/${a.id}`">{{ a.name }}</RouterLink>
            </td>
            <td>
              <span :class="accountTypePillClass(a.type)">{{ a.type }}</span>
            </td>
            <td class="mono">{{ a.currency }}</td>
            <td class="num">{{ formatMoney(a.balance) }}</td>
            <td class="mono" style="color: var(--ink-soft)">{{ formatRelativeTime(a.updatedAt) }}</td>
          </tr>
        </table>
        <div v-if="filtered.length === 0" class="empty">No accounts match "{{ query }}".</div>
      </div>
    </template>
  </AppShell>
</template>
