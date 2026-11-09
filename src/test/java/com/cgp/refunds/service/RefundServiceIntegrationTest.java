package com.cgp.refunds.service;

import com.cgp.refunds.dto.RefundRequest;
import com.cgp.refunds.dto.RefundResponse;
import com.cgp.refunds.model.Payment;
import com.cgp.refunds.repository.IdempotencyRecordRepository;
import com.cgp.refunds.repository.PaymentRepository;
import com.cgp.refunds.repository.RefundRepository;
import com.cgp.refunds.service.RefundService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.UUID;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties = {
        "app.security.username=test-user",
        "app.security.password=test-password"
})
class RefundServiceIntegrationTest {

    @Autowired
    private RefundService refundService;

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private RefundRepository refundRepository;

    @Autowired
    private IdempotencyRecordRepository idempotencyRepository;

    @BeforeEach
    void setUp() {
        idempotencyRepository.deleteAll();
        refundRepository.deleteAll();
        paymentRepository.deleteAll();
    }

    private Payment createPayment(String amount) {
        return paymentRepository.saveAndFlush(
                new Payment(new BigDecimal(amount), "EUR")
        );
    }

    private RefundRequest request(UUID paymentId, String amount) {
        return new RefundRequest(
                paymentId,
                new BigDecimal(amount),
                "EUR"
        );
    }

    @Test
    void shouldCreateRefundSuccessfully() {
        Payment payment = createPayment("100.00");

        RefundResponse response = refundService.createRefund(
                "refund-success-001",
                request(payment.getId(), "30.00")
        );

        assertNotNull(response.refundId());
        assertEquals(payment.getId(), response.paymentId());
        assertEquals(new BigDecimal("30.00"), response.amount());
        assertEquals(1, refundRepository.count());
        assertEquals(1, idempotencyRepository.count());
    }

    @Test
    void shouldReturnOriginalRefundForSameIdempotencyKey() {
        Payment payment = createPayment("100.00");
        RefundRequest request = request(payment.getId(), "30.00");

        RefundResponse first = refundService.createRefund(
                "refund-retry-001", request
        );

        RefundResponse second = refundService.createRefund(
                "refund-retry-001", request
        );

        assertEquals(first.refundId(), second.refundId());
        assertEquals(1, refundRepository.count());
        assertEquals(1, idempotencyRepository.count());
    }

    @Test
    void shouldRejectSameKeyWithDifferentRequest() {
        Payment payment = createPayment("100.00");

        refundService.createRefund(
                "refund-mismatch-001",
                request(payment.getId(), "30.00")
        );

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> refundService.createRefund(
                        "refund-mismatch-001",
                        request(payment.getId(), "40.00")
                )
        );

        assertEquals(HttpStatus.CONFLICT, exception.getStatusCode());
        assertEquals(1, refundRepository.count());
    }

    @Test
    void shouldRejectRefundExceedingRemainingBalance() {
        Payment payment = createPayment("100.00");

        refundService.createRefund(
                "refund-balance-001",
                request(payment.getId(), "70.00")
        );

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> refundService.createRefund(
                        "refund-balance-002",
                        request(payment.getId(), "40.00")
                )
        );

        assertEquals(HttpStatus.CONFLICT, exception.getStatusCode());
        assertEquals(1, refundRepository.count());
    }

    @Test
    void shouldProcessConcurrentRequestsWithSameKeyOnlyOnce()
            throws Exception {
        Payment payment = createPayment("100.00");
        RefundRequest request = request(payment.getId(), "60.00");

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);

        try {
            Callable<RefundResponse> operation = () -> {
                start.await();
                return refundService.createRefund(
                        "refund-concurrent-same-001",
                        request
                );
            };

            Future<RefundResponse> firstFuture =
                    executor.submit(operation);
            Future<RefundResponse> secondFuture =
                    executor.submit(operation);

            start.countDown();

            RefundResponse first = firstFuture.get(15, TimeUnit.SECONDS);
            RefundResponse second = secondFuture.get(15, TimeUnit.SECONDS);

            assertEquals(first.refundId(), second.refundId());
            assertEquals(1, refundRepository.count());
            assertEquals(1, idempotencyRepository.count());
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void shouldNotOverRefundWithDifferentConcurrentKeys()
            throws Exception {
        Payment payment = createPayment("100.00");

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);

        try {
            Callable<RefundResponse> firstOperation = () -> {
                start.await();
                return refundService.createRefund(
                        "refund-concurrent-a-001",
                        request(payment.getId(), "70.00")
                );
            };

            Callable<RefundResponse> secondOperation = () -> {
                start.await();
                return refundService.createRefund(
                        "refund-concurrent-b-001",
                        request(payment.getId(), "70.00")
                );
            };

            Future<RefundResponse> firstFuture =
                    executor.submit(firstOperation);
            Future<RefundResponse> secondFuture =
                    executor.submit(secondOperation);

            start.countDown();

            int successes = 0;
            int conflicts = 0;

            for (Future<RefundResponse> future :
                    new Future[]{firstFuture, secondFuture}) {
                try {
                    future.get(15, TimeUnit.SECONDS);
                    successes++;
                } catch (ExecutionException exception) {
                    Throwable cause = exception.getCause();

                    if (cause instanceof ResponseStatusException responseException
                            && responseException.getStatusCode()
                            .equals(HttpStatus.CONFLICT)) {
                        conflicts++;
                    } else {
                        throw new AssertionError(
                                "Unexpected concurrent refund failure.",
                                cause
                        );
                    }
                }
            }

            assertEquals(1, successes);
            assertEquals(1, conflicts);
            assertEquals(1, refundRepository.count());

            BigDecimal totalRefunded =
                    refundRepository.sumRefundedByPaymentId(payment.getId());

            assertEquals(new BigDecimal("70.00"), totalRefunded);
        } finally {
            executor.shutdownNow();
        }
    }
}
