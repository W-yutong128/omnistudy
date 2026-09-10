package com.omnistudy.exception;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GlobalExceptionHandlerTest {
    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void mapsRejectedProviderCredentialWithoutLeakingUpstreamBody() {
        var exception = WebClientResponseException.create(
                401,
                "Unauthorized",
                HttpHeaders.EMPTY,
                "{\"message\":\"provider secret diagnostic\"}".getBytes(StandardCharsets.UTF_8),
                StandardCharsets.UTF_8);

        var response = handler.handleWebClient(exception);

        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("AI_CREDENTIAL_REJECTED", response.getBody().code());
        assertNotNull(response.getBody().traceId());
        assertTrue(response.getBody().error().contains("API Key"));
        assertFalse(response.getBody().error().contains("provider secret diagnostic"));
    }

    @Test
    void hidesRuntimeExceptionDetailsAndReturnsTraceId() {
        var response = handler.handleRuntime(new IllegalStateException("database password was exposed"));

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("INTERNAL_ERROR", response.getBody().code());
        assertEquals(12, response.getBody().traceId().length());
        assertTrue(response.getBody().error().contains(response.getBody().traceId()));
        assertFalse(response.getBody().error().contains("database password"));
    }

    @Test
    void preservesRateLimitSemanticsWithAStableCode() {
        var exception = WebClientResponseException.create(
                429, "Too Many Requests", HttpHeaders.EMPTY, new byte[0], StandardCharsets.UTF_8);

        var response = handler.handleWebClient(exception);

        assertEquals(HttpStatus.TOO_MANY_REQUESTS, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("AI_RATE_LIMITED", response.getBody().code());
    }
}
