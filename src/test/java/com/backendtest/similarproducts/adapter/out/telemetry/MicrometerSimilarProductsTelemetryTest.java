package com.backendtest.similarproducts.adapter.out.telemetry;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.Map;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.backendtest.similarproducts.application.port.out.telemetry.DetailFailureReason;
import com.backendtest.similarproducts.application.port.out.telemetry.SimilarProductsTelemetryEvent;
import com.backendtest.similarproducts.application.port.out.telemetry.SimilarProductsTelemetryEvent.Outcome;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

class MicrometerSimilarProductsTelemetryTest {

    @Test
    void recordsOutcomesAndGroupedOmissionsUsingOnlyBoundedTags() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        MicrometerSimilarProductsTelemetry telemetry = new MicrometerSimilarProductsTelemetry(registry);

        telemetry.record(event(Outcome.COMPLETE, 3, 3, Map.of()));
        telemetry.record(event(Outcome.PARTIAL, 4, 1, Map.of(
                DetailFailureReason.NOT_FOUND, 1,
                DetailFailureReason.TIMEOUT, 2)));
        telemetry.record(event(Outcome.FAILED, 2, 0, Map.of(
                DetailFailureReason.SERVER_ERROR, 1,
                DetailFailureReason.CONNECTION_ERROR, 1)));

        assertThat(registry.get(MicrometerSimilarProductsTelemetry.REQUESTS_METRIC)
                .tag("outcome", "complete").counter().count()).isEqualTo(1);
        assertThat(registry.get(MicrometerSimilarProductsTelemetry.REQUESTS_METRIC)
                .tag("outcome", "partial").counter().count()).isEqualTo(1);
        assertThat(registry.get(MicrometerSimilarProductsTelemetry.REQUESTS_METRIC)
                .tag("outcome", "failed").counter().count()).isEqualTo(1);
        assertThat(registry.get(MicrometerSimilarProductsTelemetry.OMISSIONS_METRIC)
                .tag("reason", "not_found").counter().count()).isEqualTo(1);
        assertThat(registry.get(MicrometerSimilarProductsTelemetry.OMISSIONS_METRIC)
                .tag("reason", "timeout").counter().count()).isEqualTo(2);
        assertThat(registry.get(MicrometerSimilarProductsTelemetry.OMISSIONS_METRIC)
                .tag("reason", "server_error").counter().count()).isEqualTo(1);
        assertThat(registry.get(MicrometerSimilarProductsTelemetry.OMISSIONS_METRIC)
                .tag("reason", "connection_error").counter().count()).isEqualTo(1);

        assertThat(registry.getMeters())
                .flatMap(meter -> meter.getId().getTags())
                .extracting(tag -> tag.getKey())
                .containsOnly("outcome", "reason");
    }

    @Test
    void logsOneStructuredSummaryForEachPartialOrFailedRequest() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        MicrometerSimilarProductsTelemetry telemetry = new MicrometerSimilarProductsTelemetry(registry);
        Logger logger = (Logger) LoggerFactory.getLogger(MicrometerSimilarProductsTelemetry.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);

        try {
            telemetry.record(event(Outcome.COMPLETE, 2, 2, Map.of()));
            telemetry.record(event(
                    Outcome.PARTIAL, 2, 1, Map.of(DetailFailureReason.NOT_FOUND, 1)));
            telemetry.record(event(
                    Outcome.FAILED, 1, 0, Map.of(DetailFailureReason.TIMEOUT, 1)));
        } finally {
            logger.detachAppender(appender);
            appender.stop();
        }

        assertThat(appender.list).hasSize(2);
        assertThat(appender.list).allSatisfy(log -> {
            assertThat(log.getLevel()).isEqualTo(Level.WARN);
            assertThat(log.getFormattedMessage())
                    .startsWith("similar_products_summary outcome=")
                    .contains("duration_ms=125", "omitted_detail_count=1", "omissions=");
            assertThat(log.getKeyValuePairs())
                    .extracting(pair -> pair.key)
                    .containsExactly(
                            "event",
                            "outcome",
                            "durationMs",
                            "requestedDetailCount",
                            "returnedDetailCount",
                            "omittedDetailCount",
                            "omissions");
        });
    }

    private SimilarProductsTelemetryEvent event(
            Outcome outcome,
            int requestedDetails,
            int returnedDetails,
            Map<DetailFailureReason, Integer> omissions) {
        return new SimilarProductsTelemetryEvent(
                outcome,
                Duration.ofMillis(125),
                requestedDetails,
                returnedDetails,
                omissions);
    }
}
