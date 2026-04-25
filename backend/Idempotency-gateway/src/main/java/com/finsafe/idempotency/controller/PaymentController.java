package com.finsafe.idempotency.controller;

import com.finsafe.idempotency.model.PaymentRequest;
import com.finsafe.idempotency.service.IdempotencyService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/process-payment")
public class PaymentController {

    private final IdempotencyService idempotencyService;

    public PaymentController(IdempotencyService idempotencyService) {
        this.idempotencyService = idempotencyService;
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> processPayment(
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody PaymentRequest request) {

        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Missing required header: Idempotency-Key"));
        }

        return idempotencyService.processPayment(idempotencyKey.trim(), request);
    }
}
