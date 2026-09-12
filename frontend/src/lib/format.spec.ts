import { describe, expect, it } from 'vitest'
import {
  accountTypePillClass,
  directionPillClass,
  formatDuration,
  formatMoney,
  formatRelativeTime,
  reconciliationPillClass,
  reconciliationResultPillClass,
  signedAmount,
} from './format'

describe('formatMoney', () => {
  it('always shows two decimal places', () => {
    expect(formatMoney(5)).toBe('Rs 5.00')
    expect(formatMoney(5.1)).toBe('Rs 5.10')
  })

  it('groups thousands', () => {
    expect(formatMoney(1234567.891)).toBe('Rs 1,234,567.89')
  })

  it('keeps the sign on negative balances', () => {
    expect(formatMoney(-25)).toBe('Rs -25.00')
  })
})

describe('signedAmount', () => {
  it('marks debits positive and credits negative, matching the running-balance fold', () => {
    expect(signedAmount('DEBIT', 100)).toBe('+Rs 100.00')
    expect(signedAmount('CREDIT', 100)).toBe('-Rs 100.00')
  })
})

describe('formatDuration', () => {
  it('reports an unfinished batch as running', () => {
    expect(formatDuration('2026-09-11T10:00:00Z', null)).toBe('Running…')
  })

  it('formats sub-minute durations in seconds only', () => {
    expect(formatDuration('2026-09-11T10:00:00Z', '2026-09-11T10:00:42Z')).toBe('42s')
  })

  it('formats longer durations as minutes and seconds', () => {
    expect(formatDuration('2026-09-11T10:00:00Z', '2026-09-11T10:03:12Z')).toBe('3m 12s')
  })

  it('never returns a negative duration when timestamps are out of order', () => {
    expect(formatDuration('2026-09-11T10:05:00Z', '2026-09-11T10:00:00Z')).toBe('0s')
  })
})

describe('formatRelativeTime', () => {
  it('describes the recent past in the largest sensible unit', () => {
    const minutesAgo = (n: number) => new Date(Date.now() - n * 60_000).toISOString()
    expect(formatRelativeTime(minutesAgo(0))).toBe('just now')
    expect(formatRelativeTime(minutesAgo(5))).toBe('5 min ago')
    expect(formatRelativeTime(minutesAgo(90))).toBe('2 hr ago')
    expect(formatRelativeTime(minutesAgo(60 * 24))).toBe('1 day ago')
    expect(formatRelativeTime(minutesAgo(60 * 24 * 3))).toBe('3 days ago')
  })
})

describe('pill classes', () => {
  it('maps reconciliation batch status to a tone', () => {
    expect(reconciliationPillClass('COMPLETED')).toContain('pill-green')
    expect(reconciliationPillClass('FAILED')).toContain('pill-red')
    expect(reconciliationPillClass('PENDING')).toContain('pill-amber')
    expect(reconciliationPillClass('IN_PROGRESS')).toContain('pill-amber')
  })

  it('maps a per-account result to matched or not', () => {
    expect(reconciliationResultPillClass('MATCHED')).toContain('pill-green')
    expect(reconciliationResultPillClass('MISMATCHED')).toContain('pill-red')
  })

  it('tones account types by whether a credit balance is the normal one', () => {
    expect(accountTypePillClass('ASSET')).toContain('pill-green')
    expect(accountTypePillClass('LIABILITY')).toContain('pill-red')
    expect(accountTypePillClass('EXPENSE')).toContain('pill-red')
  })

  it('tones entry direction the same way signedAmount does', () => {
    expect(directionPillClass('DEBIT')).toContain('pill-green')
    expect(directionPillClass('CREDIT')).toContain('pill-red')
  })
})
