import type { EntryDirection } from './types'

/** The one shape every draft entry needs for balance purposes, nothing more. */
export interface DraftEntryLike {
  direction: EntryDirection
  amount: string
}

export interface JournalBalance {
  totalDebit: number
  totalCredit: number
  /** Debit total minus credit total, rounded to the cent. */
  difference: number
  /** Zero difference and at least some non-zero amount entered. */
  balanced: boolean
}

/**
 * Whether a typed amount is one the ledger would accept: a positive number,
 * not blank, not NaN. Direction carries the sign here, not the amount --
 * the same rule PostingCommand enforces server-side -- so this is a
 * narrower check than "is this a number", on purpose.
 */
export function isAmountValid(amount: string): boolean {
  const value = parseFloat(amount)
  return amount.trim().length > 0 && !Number.isNaN(value) && value > 0
}

/**
 * Folds a draft journal's lines into the numbers a balance check needs.
 *
 * Pulled out of the posting view itself so it can be unit tested directly --
 * a floating-point rounding mistake here is exactly the kind of thing that
 * looks fine in a manual click-through and is wrong every hundredth time.
 * parseFloat on an invalid amount contributes 0 rather than NaN, so a
 * half-typed row does not poison the running total while someone is still
 * typing.
 */
export function computeJournalBalance(entries: readonly DraftEntryLike[]): JournalBalance {
  const totalDebit = sumFor(entries, 'DEBIT')
  const totalCredit = sumFor(entries, 'CREDIT')
  const difference = roundToCent(totalDebit - totalCredit)
  const balanced = difference === 0 && totalDebit > 0
  return { totalDebit, totalCredit, difference, balanced }
}

function sumFor(entries: readonly DraftEntryLike[], direction: EntryDirection): number {
  return entries
    .filter((entry) => entry.direction === direction)
    .reduce((sum, entry) => sum + (parseFloat(entry.amount) || 0), 0)
}

/**
 * Plain multiply-round-divide, not a decimal library, because this is a
 * display-layer sanity check -- the server, using Money and BigDecimal, is
 * what actually enforces the balance invariant. This only has to be right
 * enough to tell a user "looks balanced" before they submit.
 */
function roundToCent(amount: number): number {
  return Math.round(amount * 100) / 100
}
