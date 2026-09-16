package com.resourceautoscaler.exception;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Tests the consistent JSON error contract produced for common failure modes. */
class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    /** Verifies constraint violations map to 400 with a readable message. */
    @SuppressWarnings("unchecked")
    @Test
    void constraintViolationsReturnBadRequest() {
        ConstraintViolation<Object> violation = mock(ConstraintViolation.class);
        when(violation.getPropertyPath()).thenReturn(mock(jakarta.validation.Path.class));
        when(violation.getMessage()).thenReturn("must be less than 365");

        ResponseEntity<Map<String, Object>> response =
            handler.handleConstraintViolation(new ConstraintViolationException(Set.of(violation)));

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertTrue(response.getBody() != null && response.getBody().get("message").toString()
            .contains("must be less than 365"));
    }

    /** Verifies unconvertible parameters return 400 without leaking internals. */
    @Test
    void typeMismatchReturnsBadRequest() {
        MethodArgumentTypeMismatchException mismatch =
            new MethodArgumentTypeMismatchException("NaN", double.class, "days",
                null, new IllegalStateException());

        ResponseEntity<Map<String, Object>> response = handler.handleTypeMismatch(mismatch);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertTrue(response.getBody() != null
            && response.getBody().get("message").toString().contains("days"));
    }
}