package com.cgp.refunds.controller;

import com.cgp.refunds.repository.PaymentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "app.security.username=test-user",
        "app.security.password=test-password"
})
@AutoConfigureMockMvc
class PaymentControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private PaymentRepository paymentRepository;

    @BeforeEach
    void setUp() {
        paymentRepository.deleteAll();
    }

    @Test
    void shouldRejectUnauthenticatedRequest() throws Exception {
        mockMvc.perform(get("/api/v1/payments"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void shouldCreatePaymentSuccessfully() throws Exception {
        mockMvc.perform(post("/api/v1/payments")
                        .with(user("test-user").roles("API"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "amount": 100.00,
                                    "currency": "EUR"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.paymentId").exists())
                .andExpect(jsonPath("$.amount").value(100.00))
                .andExpect(jsonPath("$.currency").value("EUR"))
                .andExpect(jsonPath("$.status").value("CAPTURED"));
    }

    @Test
    void shouldRejectNegativePaymentAmount() throws Exception {
        mockMvc.perform(post("/api/v1/payments")
                        .with(user("test-user").roles("API"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "amount": -10.00,
                                    "currency": "EUR"
                                }
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldReturnAllPayments() throws Exception {
        paymentRepository.save(
                new com.cgp.refunds.model.Payment(
                        new java.math.BigDecimal("100.00"),
                        "EUR"
                )
        );

        mockMvc.perform(get("/api/v1/payments")
                        .with(user("test-user").roles("API")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].amount").value(100.00))
                .andExpect(jsonPath("$[0].currency").value("EUR"))
                .andExpect(jsonPath("$[0].status").value("CAPTURED"));
    }
}

