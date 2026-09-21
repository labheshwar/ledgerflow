package com.ledgerflow.web;

import com.ledgerflow.exception.AccountNotFoundException;
import com.ledgerflow.exception.BillException;
import com.ledgerflow.exception.ChartOfAccountsException;
import com.ledgerflow.exception.InvoiceException;
import com.ledgerflow.exception.PaymentException;
import com.ledgerflow.exception.StatementImportException;
import com.ledgerflow.exception.MasterDataException;
import com.ledgerflow.exception.PeriodException;
import com.ledgerflow.exception.ReconciliationException;
import com.ledgerflow.exception.UnbalancedTransactionException;
import com.ledgerflow.money.CurrencyMismatchException;
import com.ledgerflow.web.dto.ErrorResponse;
import java.util.NoSuchElementException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(UnbalancedTransactionException.class)
    public ResponseEntity<ErrorResponse> handleUnbalanced(UnbalancedTransactionException e) {
        return error(HttpStatus.BAD_REQUEST, "UNBALANCED_TRANSACTION", e.getMessage());
    }

    /**
     * A change the chart of accounts' own rules forbid. The exception carries
     * which rule, so the client can say "that code is taken" rather than
     * rendering a generic failure.
     */
    @ExceptionHandler(ChartOfAccountsException.class)
    public ResponseEntity<ErrorResponse> handleChartOfAccounts(ChartOfAccountsException e) {
        return error(HttpStatus.BAD_REQUEST, e.getCode(), e.getMessage());
    }

    /** A period, or a posting into one, that the period's own rules forbid -- same shape as above. */
    @ExceptionHandler(PeriodException.class)
    public ResponseEntity<ErrorResponse> handlePeriod(PeriodException e) {
        return error(HttpStatus.BAD_REQUEST, e.getCode(), e.getMessage());
    }

    /** A contact, tax rate or item that its own validation forbids -- same shape as above. */
    @ExceptionHandler(MasterDataException.class)
    public ResponseEntity<ErrorResponse> handleMasterData(MasterDataException e) {
        return error(HttpStatus.BAD_REQUEST, e.getCode(), e.getMessage());
    }

    /** An invoice, or a transition of one, that its own rules forbid -- same shape as above. */
    @ExceptionHandler(InvoiceException.class)
    public ResponseEntity<ErrorResponse> handleInvoice(InvoiceException e) {
        return error(HttpStatus.BAD_REQUEST, e.getCode(), e.getMessage());
    }

    /** A bill, or a transition of one, that its own rules forbid -- same shape as above. */
    @ExceptionHandler(BillException.class)
    public ResponseEntity<ErrorResponse> handleBill(BillException e) {
        return error(HttpStatus.BAD_REQUEST, e.getCode(), e.getMessage());
    }

    /** A payment, or an allocation within one, that its own rules forbid -- same shape as above. */
    @ExceptionHandler(PaymentException.class)
    public ResponseEntity<ErrorResponse> handlePayment(PaymentException e) {
        return error(HttpStatus.BAD_REQUEST, e.getCode(), e.getMessage());
    }

    /** A statement import, or a step of its wizard taken out of order, that its own rules forbid -- same shape as above. */
    @ExceptionHandler(StatementImportException.class)
    public ResponseEntity<ErrorResponse> handleStatementImport(StatementImportException e) {
        return error(HttpStatus.BAD_REQUEST, e.getCode(), e.getMessage());
    }

    /** A reconciliation action the workspace's own rules forbid -- same shape as above. */
    @ExceptionHandler(ReconciliationException.class)
    public ResponseEntity<ErrorResponse> handleReconciliation(ReconciliationException e) {
        return error(HttpStatus.BAD_REQUEST, e.getCode(), e.getMessage());
    }

    /**
     * An entry denominated in a currency the account is not held in. A
     * client mistake, not a server one -- the request describes something
     * that cannot exist.
     */
    @ExceptionHandler(CurrencyMismatchException.class)
    public ResponseEntity<ErrorResponse> handleCurrencyMismatch(CurrencyMismatchException e) {
        return error(HttpStatus.BAD_REQUEST, "CURRENCY_MISMATCH", e.getMessage());
    }

    /**
     * A well-formed request the ledger cannot honour yet -- posting in a
     * currency other than the reporting one, which needs exchange rates.
     * 501 rather than 400, because the caller did nothing wrong.
     */
    @ExceptionHandler(UnsupportedOperationException.class)
    public ResponseEntity<ErrorResponse> handleUnsupported(UnsupportedOperationException e) {
        return error(HttpStatus.NOT_IMPLEMENTED, "NOT_IMPLEMENTED", e.getMessage());
    }

    @ExceptionHandler(AccountNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleAccountNotFound(AccountNotFoundException e) {
        return error(HttpStatus.NOT_FOUND, "ACCOUNT_NOT_FOUND", e.getMessage());
    }

    @ExceptionHandler(NoSuchElementException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(NoSuchElementException e) {
        return error(HttpStatus.NOT_FOUND, "NOT_FOUND", e.getMessage());
    }

    @ExceptionHandler(SortWhitelist.InvalidSortException.class)
    public ResponseEntity<ErrorResponse> handleInvalidSort(SortWhitelist.InvalidSortException e) {
        return error(HttpStatus.BAD_REQUEST, "INVALID_SORT", e.getMessage());
    }

    /** An unparseable enum in a query parameter, e.g. ?type=NOPE. */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleTypeMismatch(MethodArgumentTypeMismatchException e) {
        return error(
                HttpStatus.BAD_REQUEST,
                "INVALID_PARAMETER",
                "Invalid value for parameter '" + e.getName() + "'");
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
                .reduce((a, b) -> a + "; " + b)
                .orElse("Validation failed");
        return error(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", message);
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ErrorResponse> handleAuthentication(AuthenticationException e) {
        return error(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", "Invalid username or password");
    }

    /**
     * Authenticated but not allowed -- distinct from bad credentials, which
     * would tell the caller to try signing in again and get them nowhere.
     */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDenied(AccessDeniedException e) {
        return error(HttpStatus.FORBIDDEN, "FORBIDDEN", e.getMessage());
    }

    /** A duplicate username at signup, and similar caller mistakes. */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgument(IllegalArgumentException e) {
        return error(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", e.getMessage());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(Exception e) {
        log.error("Unhandled exception", e);
        return error(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "An unexpected error occurred");
    }

    private ResponseEntity<ErrorResponse> error(HttpStatus status, String code, String message) {
        return ResponseEntity.status(status)
                .body(ErrorResponse.of(status.value(), status.getReasonPhrase(), code, message));
    }
}
