package com.ledgerflow.web.dto;

import java.time.OffsetDateTime;

/**
 * The {@code code} is the stable, machine-readable part of the contract --
 * clients branch on it, while {@code message} stays free to be reworded.
 */
public record ErrorResponse(
        OffsetDateTime timestamp, int status, String error, String code, String message) {

    public static ErrorResponse of(int status, String error, String code, String message) {
        return new ErrorResponse(OffsetDateTime.now(), status, error, code, message);
    }
}
