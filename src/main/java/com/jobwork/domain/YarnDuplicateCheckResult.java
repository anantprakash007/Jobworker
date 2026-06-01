package com.jobwork.domain;

import java.util.List;

/**
 * Result returned by YarnService duplicate check.
 *
 * rows()          → (existing DB entry, incoming entry) pairs — need user review
 * nonDuplicates() → incoming entries with no DB match — saved directly
 * hasDuplicates() → true if at least one duplicate pair found
 */
public record YarnDuplicateCheckResult(
List<YarnDuplicateReviewRow> rows,
List<YarnEntry>              nonDuplicates
) {
    public boolean hasDuplicates() {
        return rows != null && !rows.isEmpty();
    }
}