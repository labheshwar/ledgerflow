import type { Account, AccountNode } from '@/lib/types'

/**
 * Headings and archived accounts excluded here rather than merely rejected
 * on submit -- an account that cannot receive an entry should not be
 * offered as though it could.
 */
export function flattenPostableAccounts(nodes: AccountNode[]): Account[] {
  return nodes.flatMap((node) => [
    ...(node.account.postable && !node.account.archived ? [node.account] : []),
    ...flattenPostableAccounts(node.children),
  ])
}
