/**
 * The arithmetic behind an invoice's totals, mirrored line for line from the
 * backend's InvoiceTotalsCalculator -- the same split journal-balance.ts
 * made from the posting form, so this view can show a live total without a
 * round trip, while the number that actually gets posted is computed on the
 * server, from the same inputs, independently.
 *
 * Each line is rounded on its own before the totals are summed, matching how
 * a paper invoice is always totaled. Two decimal places throughout, for the
 * same reason the backend hardcodes it: every organization here reports in
 * a currency with a two-digit minor unit today.
 */
export interface InvoiceLineDraftLike {
  quantity: string
  unitPrice: string
  /** The line's own tax rate percentage (e.g. 15 for 15%), or null for untaxed. */
  taxRatePercent: number | null
}

export interface InvoiceLineTotal {
  lineSubtotal: number
  taxAmount: number
  lineTotal: number
}

export interface InvoiceTotals {
  lines: InvoiceLineTotal[]
  subtotal: number
  taxTotal: number
  grandTotal: number
}

export function computeInvoiceTotals(lines: readonly InvoiceLineDraftLike[]): InvoiceTotals {
  const lineTotals: InvoiceLineTotal[] = []
  let subtotal = 0
  let taxTotal = 0

  for (const line of lines) {
    const quantity = parseFloat(line.quantity) || 0
    const unitPrice = parseFloat(line.unitPrice) || 0
    const lineSubtotal = roundToCent(quantity * unitPrice)
    const taxRate = line.taxRatePercent ?? 0
    const taxAmount = roundToCent((lineSubtotal * taxRate) / 100)

    lineTotals.push({ lineSubtotal, taxAmount, lineTotal: roundToCent(lineSubtotal + taxAmount) })
    subtotal = roundToCent(subtotal + lineSubtotal)
    taxTotal = roundToCent(taxTotal + taxAmount)
  }

  return { lines: lineTotals, subtotal, taxTotal, grandTotal: roundToCent(subtotal + taxTotal) }
}

/**
 * Plain multiply-round-divide, not a decimal library, for the same reason
 * journal-balance.ts's own roundToCent is: this is a display-layer preview,
 * and the server's BigDecimal arithmetic is what actually gets posted.
 */
function roundToCent(amount: number): number {
  return Math.round(amount * 100) / 100
}
