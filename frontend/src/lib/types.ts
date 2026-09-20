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
  entries: TransactionEntry[]
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
