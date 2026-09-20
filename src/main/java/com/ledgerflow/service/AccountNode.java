package com.ledgerflow.service;

import com.ledgerflow.repository.AccountWithBalance;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The chart as a tree, with each heading carrying the total of everything
 * beneath it.
 *
 * A heading has no balance of its own -- nothing can be posted to it -- so
 * without the roll-up it would render as zero next to children holding real
 * money, which reads as though the section were empty.
 */
public record AccountNode(AccountWithBalance account, List<AccountNode> children) {

    /**
     * Assembles the tree from the flat list in one pass, then sums upward.
     *
     * Done in memory rather than with a recursive SQL query because a chart of
     * accounts is small -- tens to low hundreds of rows -- and it is already
     * being fetched whole. A WITH RECURSIVE query would be more code, harder
     * to read, and would still return every row.
     *
     * An account whose parent is missing from the list is treated as a root
     * rather than dropped. That only happens when the caller filters the list
     * (hiding archived accounts, say), and silently swallowing rows would make
     * money disappear from the screen with no indication why.
     */
    public static List<AccountNode> treeOf(List<AccountWithBalance> accounts) {
        Map<Long, List<AccountWithBalance>> childrenByParent = new HashMap<>();
        Map<Long, AccountWithBalance> present = new HashMap<>();
        for (AccountWithBalance account : accounts) {
            present.put(account.id(), account);
        }
        for (AccountWithBalance account : accounts) {
            Long parentId = account.parentId();
            if (parentId != null && present.containsKey(parentId)) {
                childrenByParent.computeIfAbsent(parentId, key -> new ArrayList<>()).add(account);
            }
        }

        List<AccountNode> roots = new ArrayList<>();
        for (AccountWithBalance account : accounts) {
            if (account.parentId() == null || !present.containsKey(account.parentId())) {
                roots.add(build(account, childrenByParent));
            }
        }
        roots.sort(Comparator.comparing(node -> node.account().code()));
        return roots;
    }

    private static AccountNode build(
            AccountWithBalance account, Map<Long, List<AccountWithBalance>> childrenByParent) {

        List<AccountNode> children = childrenByParent.getOrDefault(account.id(), List.of()).stream()
                .map(child -> build(child, childrenByParent))
                .sorted(Comparator.comparing(node -> node.account().code()))
                .toList();

        BigDecimal rollup = account.balance() == null ? BigDecimal.ZERO : account.balance();
        for (AccountNode child : children) {
            rollup = rollup.add(child.account().rollupBalance());
        }

        return new AccountNode(account.withRollup(rollup), children);
    }

    /** Depth-first, parents before children -- the order the chart is read in. */
    public List<AccountNode> flatten() {
        List<AccountNode> flat = new ArrayList<>();
        flatten(this, flat);
        return flat;
    }

    private static void flatten(AccountNode node, List<AccountNode> into) {
        into.add(node);
        node.children().forEach(child -> flatten(child, into));
    }
}
