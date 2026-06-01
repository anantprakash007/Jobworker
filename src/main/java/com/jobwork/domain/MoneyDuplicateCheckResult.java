package com.jobwork.domain;

import java.util.List;

/**
 * Result returned by MoneyImportService duplicate check.
 *
 * rows()          → (existing DB entry, incoming entry) pairs needing review
 * nonDuplicates() → incoming entries with no DB match — saved directly
 * hasDuplicates() → true if at least one duplicate pair was found
 */
public record MoneyDuplicateCheckResult(
        List<MoneyDuplicateReviewRow> rows,
        List<MoneyReceipt>              nonDuplicates
) {
    public boolean hasDuplicates() {
        return rows != null && !rows.isEmpty();
    }
}