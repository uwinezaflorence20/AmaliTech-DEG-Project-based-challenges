package com.finsafe.idempotency.service;

import com.finsafe.idempotency.exception.ConflictException;
import com.finsafe.idempotency.model.PaymentRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.*;

@Service
public class IdempotencyService {

    private static final Logger log = LoggerFactory.getLogger(IdempotencyService.class);
    private static final long TTL_HOURS = 24;

    private final ConcurrentHashMap<String, IdempotencyEntry> store = new ConcurrentHashMap<>();

    // Internal types — not serialized to JSON
    record ProcessedPayment(int statusCode, Map<String, Object> body) {}

    record IdempotencyEntry(String requestHash, CompletableFuture<ProcessedPayment> result, Instant expiresAt) {}

    public ResponseEntity<Map<String, Object>> processPayment(String key, PaymentRequest request) {
        String requestHash = computeHash(request);
        Instant expiresAt = Instant.now().plus(TTL_HOURS, ChronoUnit.HOURS);

        CompletableFuture<ProcessedPayment> newFuture = new CompletableFuture<>();
        IdempotencyEntry newEntry = new IdempotencyEntry(requestHash, newFuture, expiresAt);

        // Atomically claim the key; returns null if we successfully inserted
        IdempotencyEntry existing = store.putIfAbsent(key, newEntry);

        if (existing == null) {
            // We own this key — process the payment
            return executePayment(key, request, newFuture);
        }

        // Key already present — check TTL
        if (Instant.now().isAfter(existing.expiresAt())) {
            // Expired: try atomic replace and re-process
            if (store.replace(key, existing, newEntry)) {
                return executePayment(key, request, newFuture);
            }
            // Another thread replaced it concurrently; recurse once to pick up the winner
            return processPayment(key, request);
        }

        // Active key: body hash must match (US3 — fraud/error check)
        if (!existing.requestHash().equals(requestHash)) {
            throw new ConflictException("Idempotency key already used for a different request body.");
        }

        // Bonus US: wait for an in-flight request to complete (race condition handling)
        try {
            ProcessedPayment result = existing.result().get(30, TimeUnit.SECONDS);
            return ResponseEntity.status(result.statusCode())
                    .header("X-Cache-Hit", "true")
                    .body(result.body());
        } catch (TimeoutException e) {
            throw new RuntimeException("Timed out waiting for in-flight payment to complete", e);
        } catch (ExecutionException e) {
            throw new RuntimeException("In-flight payment failed", e.getCause());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Interrupted while waiting for in-flight payment", e);
        }
    }

    private ResponseEntity<Map<String, Object>> executePayment(
            String key, PaymentRequest request, CompletableFuture<ProcessedPayment> future) {
        try {
            // Simulate payment processing (US1 — 2-second delay)
            Thread.sleep(2000);

            Map<String, Object> body = new LinkedHashMap<>();
            body.put("status", "success");
            body.put("message", "Charged "
                    + request.getAmount().stripTrailingZeros().toPlainString()
                    + " " + request.getCurrency().toUpperCase());
            body.put("amount", request.getAmount());
            body.put("currency", request.getCurrency().toUpperCase());
            body.put("transactionId", key);
            body.put("timestamp", Instant.now().toString());

            ProcessedPayment result = new ProcessedPayment(201, body);
            future.complete(result);

            log.info("Payment processed: {} {} (key={})",
                    request.getAmount(), request.getCurrency(), key);
            return ResponseEntity.status(201).body(body);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            store.remove(key);
            future.completeExceptionally(e);
            throw new RuntimeException("Payment processing interrupted", e);
        }
    }

    private String computeHash(PaymentRequest request) {
        try {
            // Normalise amount and currency so "100.00 GHS" == "100 GHS"
            String data = request.getAmount().stripTrailingZeros().toPlainString()
                    + ":" + request.getCurrency().toUpperCase();
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(data.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : hash) hex.append(String.format("%02x", b));
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    }

    // Developer's Choice: scheduled cleanup of expired keys to prevent unbounded memory growth
    @Scheduled(fixedRate = 3_600_000)
    public void cleanupExpiredKeys() {
        int removed = 0;
        for (Map.Entry<String, IdempotencyEntry> entry : store.entrySet()) {
            if (Instant.now().isAfter(entry.getValue().expiresAt())) {
                if (store.remove(entry.getKey(), entry.getValue())) {
                    removed++;
                }
            }
        }
        if (removed > 0) {
            log.info("Cleaned up {} expired idempotency key(s)", removed);
        }
    }
}
