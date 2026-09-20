import { describe, expect, it } from 'vitest'
import { computeJournalBalance, isAmountValid } from './journal-balance'

describe('isAmountValid', () => {
  it('accepts a plain positive amount', () => {
    expect(isAmountValid('12.50')).toBe(true)
  })

  it('rejects blank, zero, negative and non-numeric input', () => {
    expect(isAmountValid('')).toBe(false)
    expect(isAmountValid('   ')).toBe(false)
    expect(isAmountValid('0')).toBe(false)
    expect(isAmountValid('-5')).toBe(false)
    expect(isAmountValid('abc')).toBe(false)
  })
})

describe('computeJournalBalance', () => {
  it('reports balanced when debits equal credits and something was entered', () => {
    const balance = computeJournalBalance([
      { direction: 'DEBIT', amount: '100.00' },
      { direction: 'CREDIT', amount: '100.00' },
    ])
    expect(balance).toEqual({ totalDebit: 100, totalCredit: 100, difference: 0, balanced: true })
  })

  it('is not balanced while everything is still zero', () => {
    // Same shape as "balanced" (difference 0) but nothing has been typed
    // yet -- a fresh form must not read as ready to submit.
    const balance = computeJournalBalance([
      { direction: 'DEBIT', amount: '' },
      { direction: 'CREDIT', amount: '' },
    ])
    expect(balance.difference).toBe(0)
    expect(balance.balanced).toBe(false)
  })

  it('reports the exact difference when unbalanced', () => {
    const balance = computeJournalBalance([
      { direction: 'DEBIT', amount: '100.00' },
      { direction: 'CREDIT', amount: '40.00' },
    ])
    expect(balance.difference).toBe(60)
    expect(balance.balanced).toBe(false)
  })

  it('sums more than two lines on either side', () => {
    const balance = computeJournalBalance([
      { direction: 'DEBIT', amount: '10.00' },
      { direction: 'DEBIT', amount: '20.00' },
      { direction: 'CREDIT', amount: '15.00' },
      { direction: 'CREDIT', amount: '15.00' },
    ])
    expect(balance.totalDebit).toBe(30)
    expect(balance.totalCredit).toBe(30)
    expect(balance.balanced).toBe(true)
  })

  it('does not let a half-typed row poison the running total', () => {
    // A row the user has not finished typing must contribute zero, not NaN
    // -- otherwise every total downstream of it goes NaN while they type.
    const balance = computeJournalBalance([
      { direction: 'DEBIT', amount: '50.00' },
      { direction: 'CREDIT', amount: '' },
    ])
    expect(balance.totalCredit).toBe(0)
    expect(Number.isNaN(balance.difference)).toBe(false)
  })

  it('rounds away floating-point noise at the cent', () => {
    // 0.1 + 0.2 is 0.30000000000000004 in IEEE 754 -- exactly the failure
    // this function exists to hide from the balance check.
    const balance = computeJournalBalance([
      { direction: 'DEBIT', amount: '0.10' },
      { direction: 'DEBIT', amount: '0.20' },
      { direction: 'CREDIT', amount: '0.30' },
    ])
    expect(balance.difference).toBe(0)
    expect(balance.balanced).toBe(true)
  })
})
