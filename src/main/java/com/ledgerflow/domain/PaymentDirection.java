package com.ledgerflow.domain;

/**
 * Which way the money moved. RECEIVED settles invoices and posts DR Cash;
 * PAID settles bills and posts CR Cash -- the same distinction invoicing
 * and billing already make, carried onto the payment that closes either
 * one out.
 */
public enum PaymentDirection {
    RECEIVED,
    PAID
}
