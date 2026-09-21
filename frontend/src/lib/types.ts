export type AccountType = 'ASSET' | 'LIABILITY' | 'EQUITY' | 'REVENUE' | 'EXPENSE'

/** Accounts the application itself has to be able to find, e.g. for invoicing. */
export type SystemAccountRole =
  | 'CASH'
  | 'ACCOUNTS_RECEIVABLE'
  | 'ACCOUNTS_PAYABLE'
  | 'OWNER_EQUITY'
  | 'RETAINED_EARNINGS'
  | 'SALES_REVENUE'
  | 'TAX_PAYABLE'
  | 'TAX_RECEIVABLE'
  | 'FX_GAIN_LOSS'
  | 'ROUNDING'
  | 'CUSTOMER_PREPAYMENTS'
  | 'VENDOR_PREPAYMENTS'

export type EntryDirection = 'DEBIT' | 'CREDIT'
export type TransactionStatus = 'POSTED'
export type ReconciliationStatus = 'PENDING' | 'IN_PROGRESS' | 'COMPLETED' | 'FAILED'
export type ReconciliationResultStatus = 'MATCHED' | 'MISMATCHED'
export type Role = 'ADMIN' | 'VIEWER'

/** Mirrors the backend's PagedResponse envelope. */
export interface Paged<T> {
  content: T[]
  page: number
  size: number
  totalElements: number
  totalPages: number
}

export interface Account {
  id: number
  /** Unique within the organization; the stable handle a name is not. */
  code: string
  name: string
  description: string | null
  type: AccountType
  currency: string
  /** The heading this account is filed under, or null at the top of the chart. */
  parentId: number | null
  /** What the application means by this account, if anything. */
  systemRole: SystemAccountRole | null
  /** False for a heading -- entries cannot be posted directly to it. */
  postable: boolean
  archived: boolean
  /** Derived from the entries on read, not a stored column. */
  balance: number
  /** The same amount in the organization's reporting currency. */
  baseBalance: number
  /** This account's balance plus every descendant's -- the figure that means something for a heading. */
  rollupBalance: number
  createdAt: string
  updatedAt: string
}

/** One account and the accounts filed under it, nested so a client can't reassemble the order wrong. */
export interface AccountNode {
  account: Account
  children: AccountNode[]
}

export interface LedgerEntry {
  id: number
  transactionId: number
  direction: EntryDirection
  amount: number
  runningBalance: number
  /** The accounting date the running balance is folded in order of. */
  txnDate: string
  createdAt: string
}

export interface TransactionListItem {
  id: number
  idempotencyKey: string
  description: string | null
  status: TransactionStatus
  /** The accounting date: what the books count this under. */
  txnDate: string
  /** The transaction this one undoes, or null for an ordinary posting. */
  reversalOfTransactionId: number | null
  /** When the row was written, which is a different fact. */
  createdAt: string
}

export interface TransactionEntry {
  accountId: number
  accountName: string
  direction: EntryDirection
  amount: number
  currency: string
  baseAmount: number
}

export interface TransactionDetail extends TransactionListItem {
  /**
   * The transaction that undoes this one, or null if it never has been.
   * Derived server-side by looking for a row pointing back here, not a
   * status flag stored on this one.
   */
  reversedByTransactionId: number | null
  entries: TransactionEntry[]
}

export type PeriodStatus = 'OPEN' | 'CLOSED'

export interface AccountingPeriod {
  id: number
  startDate: string
  endDate: string
  status: PeriodStatus
  closedAt: string | null
}

export interface ReconciliationResult {
  accountId: number
  accountName: string
  ledgerBalance: number
  externalBalance: number
  status: ReconciliationResultStatus
}

export interface ReconciliationBatch {
  id: number
  status: ReconciliationStatus
  triggeredAt: string
  completedAt: string | null
  results: ReconciliationResult[]
}

/** The list projection: tallies rather than the full result set. */
export interface ReconciliationBatchSummary {
  id: number
  status: ReconciliationStatus
  triggeredAt: string
  completedAt: string | null
  accountsCompared: number
  matched: number
  mismatched: number
}

export interface AuditLogEntry {
  id: number
  entityType: string
  entityId: number
  action: string
  actor: string
  beforeState: string | null
  afterState: string | null
  createdAt: string
}

export interface DashboardSummary {
  totalAccounts: number
  totalLedgerBalance: number
  postingsToday: number
  latestReconciliation: ReconciliationBatch | null
}

/** BOTH exists because plenty of small businesses buy from and sell to the same party. */
export type ContactType = 'CUSTOMER' | 'VENDOR' | 'BOTH'

export interface Contact {
  id: number
  type: ContactType
  name: string
  email: string | null
  phone: string | null
  taxId: string | null
  addressLine1: string | null
  addressLine2: string | null
  city: string | null
  state: string | null
  postalCode: string | null
  country: string | null
  notes: string | null
  archived: boolean
  createdAt: string
  updatedAt: string
}

export interface TaxRate {
  id: number
  name: string
  /** A percentage, e.g. 15 for 15% -- never a fraction. */
  rate: number
  archived: boolean
  createdAt: string
  updatedAt: string
}

export interface Item {
  id: number
  sku: string | null
  name: string
  description: string | null
  defaultUnitPrice: number | null
  defaultTaxRateId: number | null
  archived: boolean
  createdAt: string
  updatedAt: string
}

export type InvoiceStatus = 'DRAFT' | 'SENT' | 'VOID'

export interface InvoiceLine {
  id: number
  itemId: number | null
  description: string
  quantity: number
  unitPrice: number
  taxRateId: number | null
  /** Derived from quantity, unitPrice and the line's own tax rate -- never stored. */
  lineSubtotal: number
  taxAmount: number
  lineTotal: number
}

export interface InvoiceListItem {
  id: number
  contactId: number
  contactName: string
  /** Null until sent. */
  invoiceNumber: string | null
  status: InvoiceStatus
  issueDate: string
  dueDate: string
  currency: string
  subtotal: number
  taxTotal: number
  grandTotal: number
  /** Summed from every non-voided payment allocated against this invoice. */
  amountPaid: number
  balanceDue: number
  /** Derived from amountPaid reaching grandTotal, not a status of its own. */
  paid: boolean
  postedTransactionId: number | null
  /** Derived from status, paid and dueDate on every read, never stored. */
  overdue: boolean
  createdAt: string
  updatedAt: string
}

export interface InvoiceDetail extends InvoiceListItem {
  notes: string | null
  lines: InvoiceLine[]
}

export interface Attachment {
  id: number
  filename: string
  contentType: string
  sizeBytes: number
  createdAt: string
}

export type PaymentDirection = 'RECEIVED' | 'PAID'
export type PaymentStatus = 'POSTED' | 'VOID'
export type DocumentType = 'INVOICE' | 'BILL'

export interface PaymentAllocation {
  id: number
  documentType: DocumentType
  documentId: number
  amount: number
}

export interface Payment {
  id: number
  contactId: number
  contactName: string
  direction: PaymentDirection
  status: PaymentStatus
  paymentDate: string
  amount: number
  currency: string
  notes: string | null
  postedTransactionId: number | null
  createdAt: string
  allocations: PaymentAllocation[]
}

/** One invoice or bill still owed against, as offered by a payment's own allocation picker. */
export interface OpenDocument {
  documentType: DocumentType
  documentId: number
  number: string | null
  dueDate: string
  balance: number
}

export type BillStatus = 'DRAFT' | 'OPEN' | 'VOID'

export interface BillLine {
  id: number
  accountId: number
  itemId: number | null
  description: string
  quantity: number
  unitPrice: number
  taxRateId: number | null
  /** Derived from quantity, unitPrice and the line's own tax rate -- never stored. */
  lineSubtotal: number
  taxAmount: number
  lineTotal: number
}

export interface BillListItem {
  id: number
  contactId: number
  contactName: string
  /** Null until posted. */
  billNumber: string | null
  /** The vendor's own bill number, entered by whoever keys this one in. */
  vendorReference: string
  status: BillStatus
  billDate: string
  dueDate: string
  currency: string
  subtotal: number
  taxTotal: number
  grandTotal: number
  /** Summed from every non-voided payment allocated against this bill. */
  amountPaid: number
  balanceDue: number
  /** Derived from amountPaid reaching grandTotal, not a status of its own. */
  paid: boolean
  postedTransactionId: number | null
  /** Derived from status, paid and dueDate on every read, never stored. */
  overdue: boolean
  createdAt: string
  updatedAt: string
}

export interface BillDetail extends BillListItem {
  notes: string | null
  lines: BillLine[]
}
