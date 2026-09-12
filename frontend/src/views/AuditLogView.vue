<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import AppShell from '../layouts/AppShell.vue'
import { apiFetch } from '../lib/api'
import { formatDateTime } from '../lib/format'
import type { AuditLogEntry } from '../lib/types'

const ENTITY_TYPES = ['TRANSACTION', 'ACCOUNT']

const entries = ref<AuditLogEntry[]>([])
const loading = ref(true)
const errorText = ref('')
const query = ref('')
const filter = ref<string>('ALL')

onMounted(async () => {
  try {
    entries.value = await apiFetch<AuditLogEntry[]>('/audit-log')
  } catch {
    errorText.value = 'Unable to load the audit log.'
  } finally {
    loading.value = false
  }
})

function actionPillClass(action: string): string {
  return action === 'CREATE' ? 'pill pill-green' : 'pill pill-neutral'
}

function detailFor(entry: AuditLogEntry): string {
  const state = entry.afterState ?? entry.beforeState
  if (!state) return '—'
  try {
    const obj = JSON.parse(state) as Record<string, unknown>
    return Object.entries(obj)
      .map(([k, v]) => `${k}: ${v}`)
      .join(', ')
  } catch {
    return state
  }
}

const filtered = computed(() => {
  const q = query.value.trim().toLowerCase()
  return entries.value.filter(
    (e) =>
      (filter.value === 'ALL' || e.entityType === filter.value) &&
      (q === '' ||
        e.actor.toLowerCase().includes(q) ||
        e.action.toLowerCase().includes(q) ||
        e.entityType.toLowerCase().includes(q)),
  )
})

function chipClass(type: string) {
  return 'chip' + (filter.value === type ? ' on' : '')
}
</script>

<template>
  <AppShell>
    <template #title>Audit Log</template>
    <template #sub>Append-only record of every posting and account change</template>

    <p v-if="loading">Loading…</p>
    <p v-else-if="errorText" class="field-error">{{ errorText }}</p>
    <template v-else>
      <div class="toolbar">
        <div class="search">
          <svg viewBox="0 0 16 16">
            <circle cx="7" cy="7" r="5"></circle>
            <path d="M11 11l3.2 3.2"></path>
          </svg>
          <input v-model="query" placeholder="Search actor, entity, action…" />
        </div>
        <div class="filters">
          <button :class="chipClass('ALL')" @click="filter = 'ALL'">All</button>
          <button v-for="t in ENTITY_TYPES" :key="t" :class="chipClass(t)" @click="filter = t">
            {{ t.charAt(0) + t.slice(1).toLowerCase() }}
          </button>
        </div>
      </div>

      <div class="tablecard">
        <table>
          <tr>
            <th>Time</th>
            <th>Actor</th>
            <th>Action</th>
            <th>Entity</th>
            <th>Detail</th>
          </tr>
          <tr v-for="e in filtered" :key="e.id">
            <td class="mono">{{ formatDateTime(e.createdAt) }}</td>
            <td>{{ e.actor }}</td>
            <td>
              <span :class="actionPillClass(e.action)">{{ e.action }}</span>
            </td>
            <td class="mono">
              <RouterLink v-if="e.entityType === 'TRANSACTION'" :to="`/transactions/${e.entityId}`"
                >TXN-{{ e.entityId }}</RouterLink
              >
              <RouterLink v-else-if="e.entityType === 'ACCOUNT'" :to="`/accounts/${e.entityId}`"
                >Account #{{ e.entityId }}</RouterLink
              >
              <template v-else>{{ e.entityType }} #{{ e.entityId }}</template>
            </td>
            <td class="detail">{{ detailFor(e) }}</td>
          </tr>
        </table>
        <div v-if="filtered.length === 0" class="empty">No audit entries match "{{ query }}".</div>
      </div>
      <div class="foot-note">
        Entries are immutable — nothing here can be edited or deleted, only added to.
      </div>
    </template>
  </AppShell>
</template>

<style scoped>
.detail {
  color: var(--ink-soft);
  font-size: 12.5px;
}
.foot-note {
  margin-top: 12px;
  font-size: 11.5px;
  color: var(--ink-faint);
}
</style>
