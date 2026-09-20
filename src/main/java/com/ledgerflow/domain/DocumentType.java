package com.ledgerflow.domain;

/** One counter series per member, via {@link com.ledgerflow.service.DocumentNumberingService}. */
public enum DocumentType {
    INVOICE("INV"),
    BILL("BILL");

    private final String prefix;

    DocumentType(String prefix) {
        this.prefix = prefix;
    }

    public String prefix() {
        return prefix;
    }
}
