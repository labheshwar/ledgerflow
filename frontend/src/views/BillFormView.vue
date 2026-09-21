<script setup lang="ts">
import { useMutation, useQuery, useQueryClient } from '@tanstack/vue-query'
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import BillForm from '@/components/bills/BillForm.vue'
import AppShell from '@/layouts/AppShell.vue'
import { getAccountTree } from '@/lib/api/accounts'
import { billKeys, createBill, type BillRequestBody } from '@/lib/api/bills'
import { listContacts } from '@/lib/api/contacts'
import { listItems } from '@/lib/api/items'
import { listTaxRates } from '@/lib/api/taxRates'
import { ApiError } from '@/lib/http'
import { flattenPostableAccounts } from '@/lib/accounts'

const router = useRouter()
const queryClient = useQueryClient()

const { data: contacts } = useQuery({
  queryKey: ['contacts', 'list', { includeArchived: false, size: 200 }],
  queryFn: ({ signal }) => listContacts({ includeArchived: false, size: 200 }, signal),
})
const { data: accountTree } = useQuery({
  queryKey: ['accounts', 'tree', false],
  queryFn: ({ signal }) => getAccountTree(false, signal),
})
const { data: items } = useQuery({
  queryKey: ['items', 'list', { includeArchived: false, size: 200 }],
  queryFn: ({ signal }) => listItems({ includeArchived: false, size: 200 }, signal),
})
const { data: taxRates } = useQuery({
  queryKey: ['tax-rates', 'list', { includeArchived: false, size: 200 }],
  queryFn: ({ signal }) => listTaxRates({ includeArchived: false, size: 200 }, signal),
})

const errorText = ref('')

const createMutation = useMutation({
  mutationFn: (body: BillRequestBody) => createBill(body),
  onSuccess: async (bill) => {
    await queryClient.invalidateQueries({ queryKey: billKeys.all })
    router.push(`/bills/${bill.id}`)
  },
  onError: (err) => {
    errorText.value = err instanceof ApiError ? err.message : 'Unable to create this bill.'
  },
})
</script>

<template>
  <AppShell>
    <template #title>New bill</template>
    <template #sub>Saved as a draft -- post it once it's ready to owe</template>

    <BillForm
      :contacts="contacts?.content ?? []"
      :accounts="flattenPostableAccounts(accountTree ?? [])"
      :items="items?.content ?? []"
      :tax-rates="taxRates?.content ?? []"
      :submitting="createMutation.isPending.value"
      :error-text="errorText"
      @submit="(body) => createMutation.mutate(body)"
    />
  </AppShell>
</template>
