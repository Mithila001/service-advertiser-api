package mithila001.service_advertiser.common.exception;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class GlobalExceptionHandlerTests {
    @Test
    void unexpectedErrorsDoNotExposeExceptionDetails() {
        var response = new GlobalExceptionHandler()
                .handleUnexpected(new IllegalStateException("internal SQL and stack detail"));

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        assertEquals("Service is temporarily unavailable", response.getBody().message());
        assertFalse(response.getBody().toString().contains("internal SQL"));
    }
}
