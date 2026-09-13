package com.backendtest.similarproducts.application.port.out.telemetry;

@FunctionalInterface
public interface SimilarProductsTelemetry {

    void record(SimilarProductsTelemetryEvent event);
}
