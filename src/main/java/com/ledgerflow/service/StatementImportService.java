package com.ledgerflow.service;

import com.ledgerflow.domain.StatementImport;
import com.ledgerflow.domain.StatementImportStatus;
import com.ledgerflow.domain.StatementLine;
import com.ledgerflow.exception.StatementImportException;
import com.ledgerflow.messaging.StatementImportProducer;
import com.ledgerflow.repository.StatementImportRepository;
import com.ledgerflow.repository.StatementLineRepository;
import com.ledgerflow.storage.ObjectStorageService;
import com.ledgerflow.tenancy.TenantContext;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.HexFormat;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * The CSV wizard: upload, map columns, preview, commit.
 *
 * {@link #processPreview} is deliberately not wrapped in one transaction --
 * it saves the import's own progress after every row, and a caller polling
 * {@code GET .../imports/{id}} needs to see that progress land as it
 * happens, not all at once when the whole method finally returns. A crash
 * partway through leaves the import PROCESSING with a partial preview;
 * {@link #processPreview} clears whatever a prior attempt staged before it
 * starts, so RabbitMQ's own in-process retry (before
 * StatementImportFailureRecoverer marks the import FAILED) simply starts
 * over cleanly rather than doubling up.
 */
@Service
public class StatementImportService {

    private static final List<DateTimeFormatter> DATE_FORMATS =
            List.of(DateTimeFormatter.ISO_LOCAL_DATE, DateTimeFormatter.ofPattern("MM/dd/yyyy"));

    private final BankAccountService bankAccountService;
    private final StatementImportRepository importRepository;
    private final StatementLineRepository lineRepository;
    private final ObjectStorageService objectStorageService;
    private final StatementImportProducer producer;
    private final TransactionTemplate transactionTemplate;

    public StatementImportService(
            BankAccountService bankAccountService,
            StatementImportRepository importRepository,
            StatementLineRepository lineRepository,
            ObjectStorageService objectStorageService,
            StatementImportProducer producer,
            PlatformTransactionManager transactionManager) {
        this.bankAccountService = bankAccountService;
        this.importRepository = importRepository;
        this.lineRepository = lineRepository;
        this.objectStorageService = objectStorageService;
        this.producer = producer;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    @Transactional(readOnly = true)
    public StatementImport get(Long id) {
        return require(id);
    }

    /** Re-read on demand rather than stored, so a page refresh on the mapping step never loses them. */
    @Transactional(readOnly = true)
    public List<String> headersFor(Long id) {
        StatementImport statementImport = require(id);
        return parseHeaders(objectStorageService.get(statementImport.getStorageKey()));
    }

    @Transactional(readOnly = true)
    public Page<StatementImport> listForBankAccount(Long bankAccountId, Pageable pageable) {
        return importRepository.findByBankAccountIdOrderByCreatedAtDesc(bankAccountId, pageable);
    }

    @Transactional(readOnly = true)
    public Page<StatementLine> previewLines(Long importId, Pageable pageable) {
        return lineRepository.findByImportIdAndCommittedOrderByTxnDateAsc(importId, false, pageable);
    }

    @Transactional(readOnly = true)
    public Page<StatementLine> committedLines(Long bankAccountId, Pageable pageable) {
        return lineRepository.findByBankAccountIdAndCommittedOrderByTxnDateDesc(bankAccountId, true, pageable);
    }

    @Transactional
    public UploadedStatement upload(Long bankAccountId, String filename, byte[] content) {
        bankAccountService.get(bankAccountId); // 404s cleanly if the bank account does not exist

        List<String> headers;
        try {
            headers = parseHeaders(content);
        } catch (UncheckedIOException e) {
            throw new StatementImportException("INVALID_CSV", "Could not read that file as CSV");
        }
        if (headers.isEmpty()) {
            throw new StatementImportException("EMPTY_FILE", "That file has no header row to map columns from");
        }

        String storageKey = "statement-imports/%d/%s-%s".formatted(bankAccountId, UUID.randomUUID(), filename);
        objectStorageService.put(storageKey, content, "text/csv");

        StatementImport statementImport = new StatementImport();
        statementImport.setOrgId(TenantContext.require());
        statementImport.setBankAccountId(bankAccountId);
        statementImport.setOriginalFilename(filename);
        statementImport.setStorageKey(storageKey);
        statementImport.setStatus(StatementImportStatus.UPLOADED);
        return new UploadedStatement(importRepository.save(statementImport), headers);
    }

    /**
     * Saves the mapping and queues the worker job that actually parses the
     * file. The save and the publish are deliberately two separate steps,
     * the save committed before the publish ever happens -- the same
     * outer-transaction-trap-adjacent reason {@code InvoiceService#send}
     * splits drawing a document number from posting: a message published
     * from inside a still-open transaction can reach a worker before that
     * transaction's own write has committed, and the worker would read the
     * mapping this call was supposed to have just saved as still null.
     */
    public StatementImport requestPreview(Long importId, ColumnMapping mapping) {
        requireMapping(mapping);

        StatementImport saved = transactionTemplate.execute(status -> {
            StatementImport statementImport = require(importId);
            if (statementImport.getStatus() == StatementImportStatus.PROCESSING) {
                throw new StatementImportException(
                        "IMPORT_ALREADY_PROCESSING", "This import is already being previewed; wait for it to finish");
            }
            if (statementImport.getStatus() == StatementImportStatus.COMMITTED) {
                throw new StatementImportException(
                        "IMPORT_ALREADY_COMMITTED", "This import has already been committed");
            }

            statementImport.setDateColumn(mapping.dateColumn());
            statementImport.setDescriptionColumn(mapping.descriptionColumn());
            statementImport.setAmountColumn(mapping.amountColumn());
            statementImport.setExternalIdColumn(blankToNull(mapping.externalIdColumn()));
            statementImport.setStatus(StatementImportStatus.PROCESSING);
            statementImport.setErrorMessage(null);
            statementImport.setTotalRows(0);
            statementImport.setProcessedRows(0);
            statementImport.setNewRows(0);
            statementImport.setDuplicateRows(0);
            statementImport.setErrorRows(0);
            return importRepository.save(statementImport);
        });

        producer.publish(saved.getOrgId(), saved.getId());
        return saved;
    }

    /**
     * Runs to completion or throws -- the caller (StatementImportListener,
     * via the retry-then-dead-letter container factory) is responsible for
     * retrying transient failures and marking the import FAILED once
     * retries are exhausted, the same split {@code ReconciliationService.reconcile}
     * uses.
     */
    public void processPreview(Long importId) {
        StatementImport statementImport = require(importId);
        lineRepository.deleteByImportIdAndCommittedFalse(importId);

        List<CSVRecord> records = parseRecords(objectStorageService.get(statementImport.getStorageKey()));
        statementImport.setTotalRows(records.size());
        importRepository.save(statementImport);

        int processed = 0;
        int added = 0;
        int duplicate = 0;
        int errors = 0;
        for (CSVRecord record : records) {
            try {
                stageLine(statementImport, record);
                added++;
            } catch (DuplicateRowException e) {
                duplicate++;
            } catch (RuntimeException e) {
                errors++;
            }
            processed++;
            statementImport.setProcessedRows(processed);
            statementImport.setNewRows(added);
            statementImport.setDuplicateRows(duplicate);
            statementImport.setErrorRows(errors);
            importRepository.save(statementImport);
        }

        statementImport.setStatus(StatementImportStatus.PREVIEWED);
        statementImport.setCompletedAt(OffsetDateTime.now());
        importRepository.save(statementImport);
    }

    @Transactional
    public StatementImport commit(Long importId) {
        StatementImport statementImport = require(importId);
        if (statementImport.getStatus() != StatementImportStatus.PREVIEWED) {
            throw new StatementImportException(
                    "IMPORT_NOT_PREVIEWED", "This import has to finish previewing before it can be committed");
        }
        lineRepository.commitByImportId(importId);
        statementImport.setStatus(StatementImportStatus.COMMITTED);
        return importRepository.save(statementImport);
    }

    private void stageLine(StatementImport statementImport, CSVRecord record) {
        LocalDate date = parseDate(column(record, statementImport.getDateColumn()));
        String description = requireDescription(column(record, statementImport.getDescriptionColumn()));
        BigDecimal amount = parseAmount(column(record, statementImport.getAmountColumn()));
        String externalId = statementImport.getExternalIdColumn() != null
                ? column(record, statementImport.getExternalIdColumn())
                : computedExternalId(statementImport.getBankAccountId(), date, description, amount);

        if (lineRepository.existsByBankAccountIdAndExternalIdAndCommittedTrue(statementImport.getBankAccountId(), externalId)) {
            throw new DuplicateRowException();
        }

        StatementLine line = new StatementLine();
        line.setOrgId(statementImport.getOrgId());
        line.setBankAccountId(statementImport.getBankAccountId());
        line.setImportId(statementImport.getId());
        line.setExternalId(externalId);
        line.setTxnDate(date);
        line.setDescription(description);
        line.setAmount(amount);
        line.setCommitted(false);
        lineRepository.save(line);
    }

    private String column(CSVRecord record, String columnName) {
        if (!record.isMapped(columnName)) {
            throw new IllegalArgumentException("No column named \"" + columnName + "\" in this row");
        }
        return record.get(columnName);
    }

    private List<String> parseHeaders(byte[] content) {
        try (CSVParser parser = CSVParser.parse(
                new InputStreamReader(new ByteArrayInputStream(content), StandardCharsets.UTF_8),
                CSVFormat.DEFAULT.builder().setHeader().setSkipHeaderRecord(true).build())) {
            return List.copyOf(parser.getHeaderNames());
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read that file as CSV", e);
        }
    }

    private List<CSVRecord> parseRecords(byte[] content) {
        try (CSVParser parser = CSVParser.parse(
                new InputStreamReader(new ByteArrayInputStream(content), StandardCharsets.UTF_8),
                CSVFormat.DEFAULT.builder().setHeader().setSkipHeaderRecord(true).build())) {
            return parser.getRecords();
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read that file as CSV", e);
        }
    }

    private LocalDate parseDate(String raw) {
        String trimmed = raw.trim();
        for (DateTimeFormatter format : DATE_FORMATS) {
            try {
                return LocalDate.parse(trimmed, format);
            } catch (DateTimeParseException ignored) {
                // Try the next recognized format.
            }
        }
        throw new IllegalArgumentException("Unrecognized date \"" + raw + "\"");
    }

    /** Strips the punctuation a bank export routinely adds -- a currency symbol, thousands commas, parens for negative. */
    private BigDecimal parseAmount(String raw) {
        String trimmed = raw.trim();
        boolean parenNegative = trimmed.startsWith("(") && trimmed.endsWith(")");
        if (parenNegative) {
            trimmed = trimmed.substring(1, trimmed.length() - 1);
        }
        trimmed = trimmed.replaceAll("[^0-9.\\-]", "");
        BigDecimal amount = new BigDecimal(trimmed);
        return parenNegative ? amount.negate() : amount;
    }

    /**
     * When the bank's own CSV carries no unique reference, this is the
     * fallback -- deterministic, so re-importing the same file dedupes
     * itself, and honest about the cost: two genuinely different
     * transactions on the same day with the same description and amount
     * collide and one is treated as a duplicate. See the README's own
     * limitations for why that trade-off is accepted rather than hidden.
     */
    private String computedExternalId(Long bankAccountId, LocalDate date, String description, BigDecimal amount) {
        String raw = bankAccountId + "|" + date + "|" + description.trim().toLowerCase() + "|" + amount.toPlainString();
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(raw.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }

    private void requireMapping(ColumnMapping mapping) {
        if (mapping.dateColumn() == null || mapping.dateColumn().isBlank()) {
            throw new StatementImportException("INVALID_MAPPING", "A date column is required");
        }
        if (mapping.descriptionColumn() == null || mapping.descriptionColumn().isBlank()) {
            throw new StatementImportException("INVALID_MAPPING", "A description column is required");
        }
        if (mapping.amountColumn() == null || mapping.amountColumn().isBlank()) {
            throw new StatementImportException("INVALID_MAPPING", "An amount column is required");
        }
    }

    private String requireDescription(String description) {
        if (description == null || description.isBlank()) {
            throw new IllegalArgumentException("Blank description");
        }
        return description.trim();
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private StatementImport require(Long id) {
        return importRepository.findById(id).orElseThrow(() -> new NoSuchElementException("No statement import with id " + id));
    }

    /** Internal signal only -- never escapes this class, so it carries no code of its own. */
    private static final class DuplicateRowException extends RuntimeException {
        DuplicateRowException() {
            super(null, null, false, false);
        }
    }
}
