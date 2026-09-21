<script setup lang="ts">
import { useMutation, useQuery, useQueryClient } from '@tanstack/vue-query'
import { computed, ref } from 'vue'
import { useRoute } from 'vue-router'
import PageState from '@/components/feedback/PageState.vue'
import AppShell from '@/layouts/AppShell.vue'
import { getPayment, paymentKeys, voidPayment } from '@/lib/api/payments'
import { formatDateTime, formatMoney } from '@/lib/format'
import { ApiError } from '@/lib/http'
import { useAuthStore } from '@/stores/auth'
import { useToastStore } from '@/stores/toast'

const route = useRoute()
const auth = useAuthStore()
const toasts = useToastStore()
const queryClient = useQueryClient()
const paymentId = computed(() => route.params.id as string)

const {
  data: payment,
  isPending,
  error,
} = useQuery({
  queryKey: computed(() => paymentKeys.detail(paymentId.value)),
  queryFn: ({ signal }) => getPayment(paymentId.value, signal),
})

const voiding = ref(false)
const voidReason = ref('')
const voidError = ref('')
const voidMutation = useMutation({
  mutationFn: () => voidPayment(paymentId.value, voidReason.value.trim() || null),
  onSuccess: async () => {
    await queryClient.invalidateQueries({ queryKey: paymentKeys.all })
    await queryClient.invalidateQueries({ queryKey: ['invoices'] })
    await queryClient.invalidateQueries({ queryKey: ['bills'] })
    voiding.value = false
    voidReason.value = ''
    toasts.success('Payment voided')
  },
  onError: (err) => {
    voidError.value = err instanceof ApiError ? err.message : 'Unable to void this payment.'
  },
})

function documentLink(documentType: string, documentId: number): string {
  return documentType === 'INVOICE' ? `/invoices/${documentId}` : `/bills/${documentId}`
}
</script>

<template>
  <AppShell>
    <template #title>
      <template v-if="payment">
        {{ payment.direction === 'RECEIVED' ? 'Payment received' : 'Payment sent' }}
        <span :class="payment.status === 'VOID' ? 'pill pill-neutral' : 'pill pill-green'">{{
          payment.status
        }}</span>
      </template>
      <template v-else>Payment</template>
    </template>
    <template #sub><RouterLink to="/payments">← Back to payments</RouterLink></template>
    <template #actions>
      <button
        v-if="auth.isAdmin && payment && payment.status === 'POSTED' && !voiding"
        type="button"
        class="btn"
        @click="voiding = true"
      >
        Void
      </button>
    </template>

    <PageState :loading="isPending" :error="error" error-text="Unable to load this payment.">
      <template v-if="payment">
        <div v-if="voiding" class="void-card">
          <div class="field" style="margin-bottom: 10px">
            <label>Reason (optional)</label>
            <input v-model="voidReason" class="input" placeholder="Why is this being voided?" />
          </div>
          <div style="display: flex; gap: 8px">
            <button
              class="btn btn-primary"
              type="button"
              :disabled="voidMutation.isPending.value"
              @click="voidMutation.mutate()"
            >
              {{ voidMutation.isPending.value ? 'Voiding…' : 'Reverse the posting and void this payment' }}
            </button>
            <button class="btn" type="button" @click="voiding = false">Cancel</button>
          </div>
          <div v-if="voidError" class="field-error" style="margin-top: 10px">{{ voidError }}</div>
        </div>

        <div class="layout">
          <div class="card">
            <h2>Allocations</h2>
            <div v-if="payment.allocations.length === 0" class="notes" style="margin-top: 0">
              None -- the whole amount is a prepayment.
            </div>
            <div v-for="allocation in payment.allocations" :key="allocation.id" class="entry-row">
              <RouterLink :to="documentLink(allocation.documentType, allocation.documentId)">
                {{ allocation.documentType === 'INVOICE' ? 'Invoice' : 'Bill' }} #{{ allocation.documentId }}
              </RouterLink>
              <span class="amt">{{ formatMoney(allocation.amount, payment.currency) }}</span>
            </div>
            <div class="totalsblock">
              <div class="num-row grand">
                <span>Total</span><span class="v">{{ formatMoney(payment.amount, payment.currency) }}</span>
              </div>
            </div>
            <p v-if="payment.notes" class="notes">{{ payment.notes }}</p>
          </div>

          <div class="card">
            <h2>Details</h2>
            <div class="meta-row">
              <span class="k">Contact</span><span class="v">{{ payment.contactName }}</span>
            </div>
            <div class="meta-row">
              <span class="k">Date</span><span class="v">{{ formatDateTime(payment.createdAt) }}</span>
            </div>
            <div v-if="payment.postedTransactionId" class="meta-row">
              <span class="k">Journal</span>
              <RouterLink class="v" :to="`/transactions/${payment.postedTransactionId}`">
                TXN-{{ payment.postedTransactionId }}
              </RouterLink>
            </div>
            <div v-else class="meta-row"><span class="k">Journal</span><span class="v">posting…</span></div>
          </div>
        </div>
      </template>
    </PageState>
  </AppShell>
</template>

<style scoped>
.layout {
  display: grid;
  grid-template-columns: 1fr 300px;
  gap: 20px;
  align-items: start;
}
.card {
  border: 1px solid var(--line);
  border-radius: 6px;
  background: var(--raised);
  padding: 20px;
}
.card h2 {
  font-family: 'Source Serif 4', serif;
  font-size: 15px;
  font-weight: 600;
  margin: 0 0 14px;
}
.void-card {
  border: 1px solid var(--line);
  border-radius: 6px;
  background: var(--raised);
  padding: 16px 20px;
  margin-bottom: 16px;
}
.entry-row {
  display: flex;
  justify-content: space-between;
  padding: 8px 2px;
  border-bottom: 1px solid var(--line-soft);
  font-size: 13px;
}
.amt {
  font-family: 'IBM Plex Mono', monospace;
}
.totalsblock {
  margin-top: 8px;
  padding-top: 8px;
}
.totalsblock .num-row {
  display: flex;
  justify-content: space-between;
  padding: 5px 2px;
  font-size: 13px;
}
.totalsblock .num-row .v {
  font-family: 'IBM Plex Mono', monospace;
}
.totalsblock .num-row.grand {
  font-weight: 600;
  font-size: 14px;
  border-top: 1px solid var(--line);
  margin-top: 4px;
  padding-top: 8px;
}
.notes {
  margin-top: 14px;
  padding-top: 12px;
  border-top: 1px dashed var(--line);
  font-size: 12.5px;
  color: var(--ink-soft);
}
.meta-row {
  display: flex;
  justify-content: space-between;
  padding: 7px 0;
  border-bottom: 1px solid var(--line-soft);
  font-size: 12.5px;
  gap: 12px;
}
.meta-row:last-child {
  border-bottom: none;
}
.meta-row .k {
  color: var(--ink-soft);
  flex: none;
}
.meta-row .v {
  text-align: right;
}
</style>
