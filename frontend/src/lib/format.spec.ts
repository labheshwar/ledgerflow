import { describe, expect, it } from 'vitest'
import {
  accountTypePillClass,
  directionPillClass,
  formatMoney,
  formatRelativeTime,
  signedAmount,
} from './format'

describe('formatMoney', () => {
  it('always shows two decimal places', () => {
    expect(formatMoney(5)).toBe('$5.00')
    expect(formatMoney(5.1)).toBe('$5.10')
  })

  it('groups thousands', () => {
    expect(formatMoney(1234567.891)).toBe('$1,234,567.89')
  })

  it('keeps the sign on negative balances', () => {
    expect(formatMoney(-25)).toBe('-$25.00')
  })

  it('labels the amount with the currency it is actually in', () => {
    // The symbol used to be hardcoded, so every account rendered as though
    // it were held in one currency no matter what it was denominated in.
    expect(formatMoney(100, 'EUR')).toBe('€100.00')
    expect(formatMoney(100, 'GBP')).toBe('£100.00')
  })

  it('drops the decimals for a currency that has no minor unit', () => {
    // Same rule the Money type applies on the server: there is no such thing
    // as a tenth of a yen, so printing one would be inventing precision.
    expect(formatMoney(1234, 'JPY')).toBe('¥1,234')
  })

  it('prints the code itself for a currency with no known symbol', () => {
    // Intl accepts any well-formed code and falls back to printing it, which
    // is exactly the desired behaviour.
    //
    // Note the normalization: Intl separates a bare code from the figure with
    // a non-breaking space, which looks identical to a normal one in a failure
    // message and makes for a genuinely baffling assertion error.
    expect(formatMoney(12.5, 'ZZZ').replace(/\u00a0/g, ' ')).toBe('ZZZ 12.50')
  })

  it('still shows the figure when the currency code is malformed', () => {
    // A malformed code makes Intl throw. Better a number with the bad code
    // beside it than a blank cell where a balance should be.
    expect(formatMoney(12.5, 'NOT-A-CURRENCY')).toBe('12.50 NOT-A-CURRENCY')
  })
})

describe('signedAmount', () => {
  it('marks debits positive and credits negative, matching the running-balance fold', () => {
    expect(signedAmount('DEBIT', 100)).toBe('+$100.00')
    expect(signedAmount('CREDIT', 100)).toBe('-$100.00')
  })

  it('carries the currency through', () => {
    expect(signedAmount('DEBIT', 100, 'EUR')).toBe('+€100.00')
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
