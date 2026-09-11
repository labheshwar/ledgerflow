import type { EntryDirection, ReconciliationResultStatus, ReconciliationStatus } from './types'

export function formatMoney(amount: number): string {
  return `Rs ${amount.toLocaleString('en-US', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`
}

export function formatRelativeTime(iso: string): string {
  const diffSec = Math.round((Date.now() - new Date(iso).getTime()) / 1000)
  if (diffSec < 60) return 'just now'
  const diffMin = Math.round(diffSec / 60)
  if (diffMin < 60) return `${diffMin} min ago`
  const diffHr = Math.round(diffMin / 60)
  if (diffHr < 24) return `${diffHr} hr ago`
  const diffDay = Math.round(diffHr / 24)
  return `${diffDay} day${diffDay === 1 ? '' : 's'} ago`
}

export function formatDateTime(iso: string): string {
  return new Date(iso).toLocaleString('en-US', {
    dateStyle: 'medium',
    timeStyle: 'short',
  })
}

export function formatDate(iso: string): string {
  return new Date(iso).toLocaleDateString('en-US', { dateStyle: 'medium' })
}

export function directionPillClass(direction: EntryDirection): string {
  return direction === 'DEBIT' ? 'pill pill-green' : 'pill pill-red'
}

export function signedAmount(direction: EntryDirection, amount: number): string {
  return (direction === 'DEBIT' ? '+' : '-') + formatMoney(amount)
}

export function formatDuration(startIso: string, endIso: string | null): string {
  if (!endIso) return 'Running…'
  const totalSec = Math.max(0, Math.round((new Date(endIso).getTime() - new Date(startIso).getTime()) / 1000))
  const min = Math.floor(totalSec / 60)
  const sec = totalSec % 60
  return min > 0 ? `${min}m ${sec}s` : `${sec}s`
}

export function reconciliationPillClass(status: ReconciliationStatus): string {
  switch (status) {
    case 'COMPLETED':
      return 'pill pill-green'
    case 'FAILED':
      return 'pill pill-red'
    default:
      return 'pill pill-amber'
  }
}

export function reconciliationResultPillClass(status: ReconciliationResultStatus): string {
  return status === 'MATCHED' ? 'pill pill-green' : 'pill pill-red'
}

export function accountTypePillClass(type: string): string {
  return type === 'LIABILITY' || type === 'EXPENSE' ? 'pill pill-red' : 'pill pill-green'
}
