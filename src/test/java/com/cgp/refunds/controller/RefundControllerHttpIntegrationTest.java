package com.cgp.refunds.controller;

import com.cgp.refunds.model.Payment;
import com.cgp.refunds.repository.IdempotencyRecordRepository;
import com.cgp.refunds.repository.PaymentRepository;
import com.cgp.refunds.repository.RefundRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
        "app.security.username=test-user",
        "app.security.password=test-password"
})
@AutoConfigureMockMvc
class RefundControllerHttpIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private RefundRepository refundRepository;

    @Autowired
    private IdempotencyRecordRepository idempotencyRepository;

    private String paymentId;

    @BeforeEach
    void setUp() {
        idempotencyRepository.deleteAll();
        refundRepository.deleteAll();
        paymentRepository.deleteAll();

        Payment payment = paymentRepository.saveAndFlush(
                new Payment(new BigDecimal("100.00"), "EUR")
        );

        paymentId = payment.getId().toString();
    }

    @Test
    void shouldCreateRefundThroughHttp() throws Exception {
        mockMvc.perform(post("/api/v1/refunds")
                        .with(user("test-user").roles("API"))
                        .header("Idempotency-Key", "http-refund-001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "paymentId": "%s",
                                    "amount": 30.00,
                                    "currency": "EUR"
                                }
                                """.formatted(paymentId)))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.refundId").exists())
                .andExpect(jsonPath("$.paymentId").value(paymentId))
                .andExpect(jsonPath("$.amount").value(30.00))
                .andExpect(jsonPath("$.currency").value("EUR"))
                .andExpect(jsonPath("$.status").value("COMPLETED"));
    }

    @Test
    void shouldReplaySameRefundThroughHttp() throws Exception {
        String body = """
                {
                    "paymentId": "%s",
                    "amount": 30.00,
                    "currency": "EUR"
                }
                """.formatted(paymentId);

        String firstResponse = mockMvc.perform(post("/api/v1/refunds")
                        .with(user("test-user").roles("API"))
                        .header("Idempotency-Key", "http-retry-001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();

        mockMvc.perform(post("/api/v1/refunds")
                        .with(user("test-user").roles("API"))
                        .header("Idempotency-Key", "http-retry-001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(content().json(firstResponse));

        org.junit.jupiter.api.Assertions.assertEquals(
                1,
                refundRepository.count()
        );
    }

    @Test
    void shouldReturnConflictForDifferentRequestWithSameKey()
            throws Exception {
        String firstBody = """
                {
                    "paymentId": "%s",
                    "amount": 30.00,
                    "currency": "EUR"
                }
                """.formatted(paymentId);

        String secondBody = """
                {
                    "paymentId": "%s",
                    "amount": 40.00,
                    "currency": "EUR"
                }
                """.formatted(paymentId);

        mockMvc.perform(post("/api/v1/refunds")
                        .with(user("test-user").roles("API"))
                        .header("Idempotency-Key", "http-mismatch-001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(firstBody))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/refunds")
                        .with(user("test-user").roles("API"))
                        .header("Idempotency-Key", "http-mismatch-001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(secondBody))
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(
                        MediaType.APPLICATION_PROBLEM_JSON
                ))
                .andExpect(jsonPath("$.status").value(409));
    }

    @Test
    void shouldReturnBadRequestWhenIdempotencyKeyIsMissing()
            throws Exception {
        mockMvc.perform(post("/api/v1/refunds")
                        .with(user("test-user").roles("API"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "paymentId": "%s",
                                    "amount": 30.00,
                                    "currency": "EUR"
                                }
                                """.formatted(paymentId)))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(
                        MediaType.APPLICATION_PROBLEM_JSON
                ))
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    void shouldRetrieveRefundThroughHttp() throws Exception {
        String response = mockMvc.perform(post("/api/v1/refunds")
                        .with(user("test-user").roles("API"))
                        .header("Idempotency-Key", "http-get-001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "paymentId": "%s",
                                    "amount": 30.00,
                                    "currency": "EUR"
                                }
                                """.formatted(paymentId)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();

        String refundId = com.fasterxml.jackson.databind.json.JsonMapper
                .builder()
                .build()
                .readTree(response)
                .get("refundId")
                .asText();

        mockMvc.perform(get("/api/v1/refunds/" + refundId)
                        .with(user("test-user").roles("API")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.refundId").value(refundId))
                .andExpect(jsonPath("$.status").value("COMPLETED"));
    }
}
