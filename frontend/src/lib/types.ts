export type AccountType = 'ASSET' | 'LIABILITY' | 'EQUITY' | 'REVENUE' | 'EXPENSE'
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
  name: string
  type: AccountType
  currency: string
  /** Derived from the entries on read, not a stored column. */
  balance: number
  /** The same amount in the organization's reporting currency. */
  baseBalance: number
  createdAt: string
  updatedAt: string
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
