package com.backendtest.similarproducts.application.port.out.telemetry;

public enum DetailFailureReason {
    NOT_FOUND("not_found"),
    SERVER_ERROR("server_error"),
    TIMEOUT("timeout"),
    CONNECTION_ERROR("connection_error");

    private final String tagValue;

    DetailFailureReason(String tagValue) {
        this.tagValue = tagValue;
    }

    public String tagValue() {
        return tagValue;
    }
}
