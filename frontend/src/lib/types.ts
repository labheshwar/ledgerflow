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
  /** Marks the realized gain/loss leg a foreign-currency settlement posts when its rate differs from the invoice's own. */
  fxAdjustment: boolean
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
  openArTotal: number
  overdueArTotal: number
  openApTotal: number
  overdueApTotal: number
  updatedAt: string
}

export type AgingBucket = 'CURRENT' | 'DAYS_1_30' | 'DAYS_31_60' | 'DAYS_61_90' | 'DAYS_OVER_90'

export interface ArAgingRow {
  invoiceId: number
  contactId: number
  contactName: string
  invoiceNumber: string | null
  dueDate: string
  currency: string
  balance: number
  bucket: AgingBucket
}

export interface ApAgingRow {
  billId: number
  contactId: number
  contactName: string
  billNumber: string | null
  dueDate: string
  currency: string
  balance: number
  bucket: AgingBucket
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
  /** Set only when this payment was scoped to a specific bank account -- see the reconciliation workspace's own settle action. */
  bankAccountId: number | null
  bankAccountName: string | null
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
  currency: string
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

export interface BankAccount {
  id: number
  accountId: number
  accountName: string
  name: string
  accountNumberLast4: string | null
  currency: string
  archived: boolean
  createdAt: string
  updatedAt: string
}

export type StatementImportStatus = 'UPLOADED' | 'PROCESSING' | 'PREVIEWED' | 'COMMITTED' | 'FAILED'

export interface StatementImport {
  id: number
  bankAccountId: number
  status: StatementImportStatus
  originalFilename: string
  dateColumn: string | null
  descriptionColumn: string | null
  amountColumn: string | null
  externalIdColumn: string | null
  totalRows: number
  processedRows: number
  newRows: number
  duplicateRows: number
  errorRows: number
  errorMessage: string | null
  createdAt: string
  completedAt: string | null
}

/** How many units of the organization's own base currency equal one unit of this currency, as of a given date. */
export interface FxRate {
  id: number
  currency: string
  rate: number
  asOfDate: string
  createdAt: string
}

export interface StatementLine {
  id: number
  externalId: string
  txnDate: string
  description: string
  amount: number
  committed: boolean
  /** Set once the reconciliation workspace has matched this line to an entry. */
  matchedEntryId: number | null
  matchedAt: string | null
}

/** What the reconciliation workspace is offering as a candidate match for one statement line. */
export type MatchKind = 'ENTRY' | 'INVOICE' | 'BILL'

export interface MatchSuggestion {
  kind: MatchKind
  id: number
  label: string
  date: string
  /** Signed the same way a statement line reads, so it can be compared or shown alongside one directly. */
  amount: number
  score: number
}

export interface ReconciliationSummary {
  totalLines: number
  matchedLines: number
  unmatchedLines: number
}
