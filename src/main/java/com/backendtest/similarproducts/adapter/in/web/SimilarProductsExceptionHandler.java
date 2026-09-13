package com.backendtest.similarproducts.adapter.in.web;

import com.backendtest.similarproducts.application.error.SimilarProductsNotFoundException;
import com.backendtest.similarproducts.application.error.SimilarProductsTimeoutException;
import com.backendtest.similarproducts.application.error.SimilarProductsUnavailableException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public final class SimilarProductsExceptionHandler {

    @ExceptionHandler(SimilarProductsNotFoundException.class)
    ResponseEntity<Void> handleNotFound() {
        return ResponseEntity.notFound().build();
    }

    @ExceptionHandler(SimilarProductsUnavailableException.class)
    ResponseEntity<Void> handleUnavailable() {
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY).build();
    }

    @ExceptionHandler(SimilarProductsTimeoutException.class)
    ResponseEntity<Void> handleTimeout() {
        return ResponseEntity.status(HttpStatus.GATEWAY_TIMEOUT).build();
    }
}
