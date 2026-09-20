package com.ledgerflow.web.dto;

import com.ledgerflow.service.AccountNode;
import java.util.List;

/**
 * One account and the accounts filed under it.
 *
 * Nested rather than flat with a depth marker, so the client cannot get the
 * nesting wrong: a flat list plus a level number has to be reassembled by
 * every consumer, and each one has its own chance to mis-order it.
 */
public record AccountNodeResponse(AccountResponse account, List<AccountNodeResponse> children) {

    public static AccountNodeResponse from(AccountNode node) {
        return new AccountNodeResponse(
                AccountResponse.from(node.account()),
                node.children().stream().map(AccountNodeResponse::from).toList());
    }
}
