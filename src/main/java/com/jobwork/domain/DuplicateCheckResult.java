package com.jobwork.domain;

import java.util.List;

/**
 * Result returned by ProductService.checkDuplicates().
 *
 * rows()          → (existing DB entry, incoming entry) pairs — need user review
 * nonDuplicates() → incoming entries with no DB match — saved directly
 * hasDuplicates() → true if at least one duplicate pair was found
 */
public record DuplicateCheckResult(
        List<DuplicateReviewRow> rows,
        List<ProductEntry>       nonDuplicates
) {
    public boolean hasDuplicates() {
        return rows != null && !rows.isEmpty();
    }
}