<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import AppShell from '../layouts/AppShell.vue'
import { apiFetch } from '../lib/api'
import { accountTypePillClass, formatMoney, formatRelativeTime } from '../lib/format'
import type { Account, AccountType } from '../lib/types'

const TYPES: AccountType[] = ['ASSET', 'LIABILITY', 'EQUITY', 'REVENUE', 'EXPENSE']

const accounts = ref<Account[]>([])
const loading = ref(true)
const errorText = ref('')
const query = ref('')
const filter = ref<AccountType | 'ALL'>('ALL')

onMounted(async () => {
  try {
    accounts.value = await apiFetch<Account[]>('/accounts')
  } catch {
    errorText.value = 'Unable to load accounts.'
  } finally {
    loading.value = false
  }
})

const filtered = computed(() => {
  const q = query.value.trim().toLowerCase()
  return accounts.value.filter(
    (a) => (filter.value === 'ALL' || a.type === filter.value) && (q === '' || a.name.toLowerCase().includes(q)),
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
          <svg viewBox="0 0 16 16"><circle cx="7" cy="7" r="5"></circle><path d="M11 11l3.2 3.2"></path></svg>
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
            <td><RouterLink :to="`/accounts/${a.id}`">{{ a.name }}</RouterLink></td>
            <td><span :class="accountTypePillClass(a.type)">{{ a.type }}</span></td>
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

<style scoped>
.toolbar {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 14px;
  flex-wrap: wrap;
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
.filters {
  display: flex;
  gap: 6px;
  flex-wrap: wrap;
}
.chip {
  font-family: 'IBM Plex Mono', monospace;
  font-size: 11px;
  font-weight: 500;
  padding: 6px 11px;
  border-radius: 20px;
  border: 1px solid var(--line);
  background: var(--raised);
  color: var(--ink-soft);
}
.chip:hover {
  border-color: var(--green-line);
  color: var(--ink);
}
.chip.on {
  background: var(--green);
  border-color: var(--green);
  color: var(--paper);
}
.empty {
  padding: 36px 16px;
  text-align: center;
  color: var(--ink-faint);
  font-size: 13px;
}
</style>
