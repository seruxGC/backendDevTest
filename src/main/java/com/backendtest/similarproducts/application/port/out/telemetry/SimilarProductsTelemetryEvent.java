package com.backendtest.similarproducts.application.port.out.telemetry;

import java.time.Duration;
import java.util.Map;
import java.util.Objects;

public record SimilarProductsTelemetryEvent(
        Outcome outcome,
        Duration duration,
        int requestedDetailCount,
        int returnedDetailCount,
        Map<DetailFailureReason, Integer> omissions) {

    public SimilarProductsTelemetryEvent {
        Objects.requireNonNull(outcome, "outcome");
        Objects.requireNonNull(duration, "duration");
        Objects.requireNonNull(omissions, "omissions");
        if (duration.isNegative()) {
            throw new IllegalArgumentException("Duration must not be negative");
        }
        if (requestedDetailCount < 0 || returnedDetailCount < 0) {
            throw new IllegalArgumentException("Detail counts must not be negative");
        }
        if (returnedDetailCount > requestedDetailCount) {
            throw new IllegalArgumentException("Returned detail count cannot exceed requested count");
        }
        omissions = Map.copyOf(omissions);
        if (omissions.entrySet().stream()
                .anyMatch(entry -> entry.getKey() == null || entry.getValue() == null || entry.getValue() < 1)) {
            throw new IllegalArgumentException("Omission counts must be positive");
        }
        if (outcome == Outcome.COMPLETE && !omissions.isEmpty()) {
            throw new IllegalArgumentException("A complete request cannot contain omissions");
        }
        if (outcome == Outcome.PARTIAL && omissions.isEmpty()) {
            throw new IllegalArgumentException("A partial request must contain omissions");
        }
    }

    public int omittedDetailCount() {
        return omissions.values().stream().mapToInt(Integer::intValue).sum();
    }

    public enum Outcome {
        COMPLETE("complete"),
        PARTIAL("partial"),
        FAILED("failed");

        private final String tagValue;

        Outcome(String tagValue) {
            this.tagValue = tagValue;
        }

        public String tagValue() {
            return tagValue;
        }
    }
}
