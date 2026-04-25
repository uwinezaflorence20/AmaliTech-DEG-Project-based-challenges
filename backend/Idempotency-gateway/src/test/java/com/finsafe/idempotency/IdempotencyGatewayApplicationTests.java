package com.finsafe.idempotency;

import com.finsafe.idempotency.model.PaymentRequest;
import com.finsafe.idempotency.service.IdempotencyService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
class IdempotencyGatewayApplicationTests {

    @Autowired
    private IdempotencyService idempotencyService;

    @Test
    void contextLoads() {
    }

    @Test
    void firstRequestReturns201() {
        String key = UUID.randomUUID().toString();
        PaymentRequest req = new PaymentRequest(new BigDecimal("100"), "GHS");

        ResponseEntity<Map<String, Object>> response = idempotencyService.processPayment(key, req);

        assertThat(response.getStatusCode().value()).isEqualTo(201);
        assertThat(response.getHeaders().getFirst("X-Cache-Hit")).isNull();
    }

    @Test
    void duplicateRequestReturnsCachedResponseWithCacheHitHeader() {
        String key = UUID.randomUUID().toString();
        PaymentRequest req = new PaymentRequest(new BigDecimal("50"), "USD");

        ResponseEntity<Map<String, Object>> first = idempotencyService.processPayment(key, req);
        ResponseEntity<Map<String, Object>> second = idempotencyService.processPayment(key, req);

        assertThat(second.getStatusCode().value()).isEqualTo(201);
        assertThat(second.getHeaders().getFirst("X-Cache-Hit")).isEqualTo("true");
        assertThat(second.getBody()).isEqualTo(first.getBody());
    }

    @Test
    void differentBodySameKeyThrowsConflictException() {
        String key = UUID.randomUUID().toString();
        PaymentRequest original = new PaymentRequest(new BigDecimal("100"), "GHS");
        PaymentRequest tampered = new PaymentRequest(new BigDecimal("500"), "GHS");

        idempotencyService.processPayment(key, original);

        assertThatThrownBy(() -> idempotencyService.processPayment(key, tampered))
                .isInstanceOf(com.finsafe.idempotency.exception.ConflictException.class)
                .hasMessageContaining("different request body");
    }
}
