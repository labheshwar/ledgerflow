import { defineStore } from 'pinia'

export type ToastTone = 'success' | 'error' | 'info'

export interface Toast {
  id: number
  tone: ToastTone
  message: string
}

const DEFAULT_TTL_MS = 5000

let nextId = 1

export const useToastStore = defineStore('toast', {
  state: () => ({
    toasts: [] as Toast[],
  }),
  actions: {
    push(message: string, tone: ToastTone = 'info', ttlMs = DEFAULT_TTL_MS) {
      const id = nextId++
      this.toasts.push({ id, tone, message })
      if (ttlMs > 0) {
        setTimeout(() => this.dismiss(id), ttlMs)
      }
      return id
    },
    success(message: string) {
      return this.push(message, 'success')
    },
    error(message: string) {
      return this.push(message, 'error')
    },
    dismiss(id: number) {
      const index = this.toasts.findIndex((toast) => toast.id === id)
      if (index !== -1) this.toasts.splice(index, 1)
    },
  },
})
