import { describe, expect, it } from 'vitest'
import { computeInvoiceTotals } from './invoice-totals'

describe('computeInvoiceTotals', () => {
  it('totals one untaxed line to its own subtotal', () => {
    const totals = computeInvoiceTotals([{ quantity: '2', unitPrice: '50.00', taxRatePercent: null }])

    expect(totals.subtotal).toBe(100)
    expect(totals.taxTotal).toBe(0)
    expect(totals.grandTotal).toBe(100)
  })

  it('computes tax per line from that line’s own rate', () => {
    const totals = computeInvoiceTotals([
      { quantity: '1', unitPrice: '100.00', taxRatePercent: 15 },
      { quantity: '1', unitPrice: '50.00', taxRatePercent: 5 },
    ])

    expect(totals.subtotal).toBe(150)
    expect(totals.taxTotal).toBe(17.5)
    expect(totals.grandTotal).toBe(167.5)
    expect(totals.lines[0].taxAmount).toBe(15)
    expect(totals.lines[1].taxAmount).toBe(2.5)
  })

  it('rounds each line before summing, matching the backend', () => {
    const totals = computeInvoiceTotals([{ quantity: '3', unitPrice: '33.335', taxRatePercent: null }])

    expect(totals.lines[0].lineSubtotal).toBe(100.01)
    expect(totals.subtotal).toBe(100.01)
  })

  it('treats a half-typed row as zero rather than poisoning the total with NaN', () => {
    const totals = computeInvoiceTotals([
      { quantity: '2', unitPrice: '10.00', taxRatePercent: null },
      { quantity: '', unitPrice: '', taxRatePercent: null },
    ])

    expect(totals.subtotal).toBe(20)
    expect(Number.isNaN(totals.grandTotal)).toBe(false)
  })

  it('totals to zero with no lines', () => {
    const totals = computeInvoiceTotals([])

    expect(totals.subtotal).toBe(0)
    expect(totals.taxTotal).toBe(0)
    expect(totals.grandTotal).toBe(0)
    expect(totals.lines).toHaveLength(0)
  })
})
