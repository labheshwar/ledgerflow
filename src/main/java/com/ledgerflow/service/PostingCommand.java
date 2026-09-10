package com.ledgerflow.service;

import java.util.List;

public record PostingCommand(String idempotencyKey, String description, List<EntryLine> entries) {
}
