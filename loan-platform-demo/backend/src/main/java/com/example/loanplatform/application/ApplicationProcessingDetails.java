package com.example.loanplatform.application;

import java.util.List;
import java.util.Optional;

/** Read-only composition of processing outcome and state history for one application. */
public record ApplicationProcessingDetails(
        Optional<PreprocessingResult> preprocessing,
        List<StatusHistoryEntry> statusHistory) {
}
