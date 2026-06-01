package com.jobwork.domain;

import java.util.List;

/**
 * Result returned by BheemService duplicate check.
 *
 * rows()          → (existing DB entry, incoming entry) pairs — need user review
 * nonDuplicates() → incoming entries with no DB match — saved directly
 * hasDuplicates() → true if at least one duplicate pair found
 */
public record BheemDuplicateCheckResult(
        List<BheemDuplicateReviewRow> rows,
        List<BheemEntry>              nonDuplicates
) {
    public boolean hasDuplicates() {
        return rows != null && !rows.isEmpty();
    }
}