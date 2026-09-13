package com.backendtest.similarproducts.adapter.out.telemetry;

import com.backendtest.similarproducts.application.port.out.telemetry.SimilarProductsTelemetry;
import com.backendtest.similarproducts.application.port.out.telemetry.SimilarProductsTelemetryEvent;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public final class MicrometerSimilarProductsTelemetry implements SimilarProductsTelemetry {

    static final String REQUESTS_METRIC = "similar.products.requests";
    static final String OMISSIONS_METRIC = "similar.products.omissions";

    private static final Logger LOGGER = LoggerFactory.getLogger(MicrometerSimilarProductsTelemetry.class);

    private final MeterRegistry meterRegistry;

    public MicrometerSimilarProductsTelemetry(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    @Override
    public void record(SimilarProductsTelemetryEvent event) {
        meterRegistry.counter(REQUESTS_METRIC, "outcome", event.outcome().tagValue()).increment();
        event.omissions().forEach((reason, count) -> meterRegistry
                .counter(OMISSIONS_METRIC, "reason", reason.tagValue())
                .increment(count));

        if (event.outcome() != SimilarProductsTelemetryEvent.Outcome.COMPLETE) {
            LOGGER.atWarn()
                    .addKeyValue("event", "similar_products_summary")
                    .addKeyValue("outcome", event.outcome().tagValue())
                    .addKeyValue("durationMs", event.duration().toMillis())
                    .addKeyValue("requestedDetailCount", event.requestedDetailCount())
                    .addKeyValue("returnedDetailCount", event.returnedDetailCount())
                    .addKeyValue("omittedDetailCount", event.omittedDetailCount())
                    .addKeyValue("omissions", event.omissions())
                    .log(
                            "similar_products_summary outcome={} duration_ms={} "
                                    + "requested_detail_count={} returned_detail_count={} "
                                    + "omitted_detail_count={} omissions={}",
                            event.outcome().tagValue(),
                            event.duration().toMillis(),
                            event.requestedDetailCount(),
                            event.returnedDetailCount(),
                            event.omittedDetailCount(),
                            event.omissions());
        }
    }
}
