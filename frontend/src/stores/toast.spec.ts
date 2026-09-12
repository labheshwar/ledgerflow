import { createPinia, setActivePinia } from 'pinia'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { useToastStore } from './toast'

describe('toast store', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.useFakeTimers()
  })

  afterEach(() => {
    vi.useRealTimers()
  })

  it('queues toasts with distinct ids', () => {
    const toasts = useToastStore()
    const first = toasts.success('Signed out')
    const second = toasts.error('Something broke')

    expect(first).not.toBe(second)
    expect(toasts.toasts).toHaveLength(2)
    expect(toasts.toasts[0]).toMatchObject({ tone: 'success', message: 'Signed out' })
    expect(toasts.toasts[1]).toMatchObject({ tone: 'error', message: 'Something broke' })
  })

  it('auto-dismisses after the ttl', () => {
    const toasts = useToastStore()
    toasts.push('Saved', 'info', 1000)

    expect(toasts.toasts).toHaveLength(1)
    vi.advanceTimersByTime(999)
    expect(toasts.toasts).toHaveLength(1)
    vi.advanceTimersByTime(1)
    expect(toasts.toasts).toHaveLength(0)
  })

  it('keeps a toast until dismissed when the ttl is zero', () => {
    const toasts = useToastStore()
    const id = toasts.push('Import running', 'info', 0)

    vi.advanceTimersByTime(60_000)
    expect(toasts.toasts).toHaveLength(1)

    toasts.dismiss(id)
    expect(toasts.toasts).toHaveLength(0)
  })

  it('dismissing one toast leaves the others alone', () => {
    const toasts = useToastStore()
    const first = toasts.push('One', 'info', 0)
    toasts.push('Two', 'info', 0)

    toasts.dismiss(first)

    expect(toasts.toasts).toHaveLength(1)
    expect(toasts.toasts[0].message).toBe('Two')
  })

  it('ignores an unknown id', () => {
    const toasts = useToastStore()
    toasts.push('One', 'info', 0)

    expect(() => toasts.dismiss(999)).not.toThrow()
    expect(toasts.toasts).toHaveLength(1)
  })
})
