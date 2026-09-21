<script setup lang="ts">
import { useMutation } from '@tanstack/vue-query'
import { ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import AppShell from '@/layouts/AppShell.vue'
import { uploadStatement } from '@/lib/api/bankAccounts'
import { firstFileFrom } from '@/lib/download'
import { ApiError } from '@/lib/http'

const route = useRoute()
const router = useRouter()
const bankAccountId = route.params.id as string

const selectedFile = ref<Parameters<typeof uploadStatement>[1] | null>(null)
const errorText = ref('')

function onFileChosen(event: Parameters<typeof firstFileFrom>[0]) {
  selectedFile.value = firstFileFrom(event)
}

const uploadMutation = useMutation({
  mutationFn: () =>
    uploadStatement(bankAccountId, selectedFile.value as Parameters<typeof uploadStatement>[1]),
  onSuccess: (result) => {
    router.push(`/bank-accounts/${bankAccountId}/imports/${result.importId}`)
  },
  onError: (err) => {
    errorText.value = err instanceof ApiError ? err.message : 'Unable to upload that file.'
  },
})
</script>

<template>
  <AppShell>
    <template #title>Import statement</template>
    <template #sub
      ><RouterLink :to="`/bank-accounts/${bankAccountId}`">← Back to bank account</RouterLink></template
    >

    <div class="card">
      <h2>Step 1 of 4 — Upload</h2>
      <p class="hint">
        A CSV export from your bank, with a header row. The next step lets you tell LedgerFlow which column is
        which.
      </p>
      <label class="dropzone" :class="{ chosen: selectedFile }">
        <input type="file" accept=".csv,text/csv" @change="onFileChosen" />
        <span v-if="selectedFile">{{ selectedFile.name }}</span>
        <span v-else>Choose a CSV file…</span>
      </label>

      <div v-if="errorText" class="field-error" style="margin-top: 12px">{{ errorText }}</div>

      <button
        type="button"
        class="btn btn-primary"
        style="margin-top: 16px"
        :disabled="!selectedFile || uploadMutation.isPending.value"
        @click="uploadMutation.mutate()"
      >
        {{ uploadMutation.isPending.value ? 'Uploading…' : 'Continue' }}
      </button>
    </div>
  </AppShell>
</template>

<style scoped>
.card {
  max-width: 560px;
  border: 1px solid var(--line);
  border-radius: 6px;
  background: var(--raised);
  padding: 24px;
}
.card h2 {
  font-family: 'Source Serif 4', serif;
  font-size: 16px;
  font-weight: 600;
  margin: 0 0 8px;
}
.hint {
  color: var(--ink-soft);
  font-size: 12.5px;
  margin: 0 0 16px;
}
.dropzone {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 90px;
  border: 1px dashed var(--line);
  border-radius: 6px;
  cursor: pointer;
  color: var(--ink-soft);
  font-size: 13px;
}
.dropzone.chosen {
  border-color: var(--green-line);
  color: var(--green);
}
.dropzone input[type='file'] {
  display: none;
}
</style>
