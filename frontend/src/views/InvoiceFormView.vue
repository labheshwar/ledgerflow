<script setup lang="ts">
import { useMutation, useQuery, useQueryClient } from '@tanstack/vue-query'
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import InvoiceForm from '@/components/invoices/InvoiceForm.vue'
import AppShell from '@/layouts/AppShell.vue'
import { listContacts } from '@/lib/api/contacts'
import { createInvoice, invoiceKeys, type InvoiceRequestBody } from '@/lib/api/invoices'
import { listItems } from '@/lib/api/items'
import { listTaxRates } from '@/lib/api/taxRates'
import { ApiError } from '@/lib/http'

const router = useRouter()
const queryClient = useQueryClient()

const { data: contacts } = useQuery({
  queryKey: ['contacts', 'list', { includeArchived: false, size: 200 }],
  queryFn: ({ signal }) => listContacts({ includeArchived: false, size: 200 }, signal),
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
  mutationFn: (body: InvoiceRequestBody) => createInvoice(body),
  onSuccess: async (invoice) => {
    await queryClient.invalidateQueries({ queryKey: invoiceKeys.all })
    router.push(`/invoices/${invoice.id}`)
  },
  onError: (err) => {
    errorText.value = err instanceof ApiError ? err.message : 'Unable to create this invoice.'
  },
})
</script>

<template>
  <AppShell>
    <template #title>New invoice</template>
    <template #sub>Saved as a draft -- send it once it's ready to charge</template>

    <InvoiceForm
      :contacts="contacts?.content ?? []"
      :items="items?.content ?? []"
      :tax-rates="taxRates?.content ?? []"
      :submitting="createMutation.isPending.value"
      :error-text="errorText"
      @submit="(body) => createMutation.mutate(body)"
    />
  </AppShell>
</template>
