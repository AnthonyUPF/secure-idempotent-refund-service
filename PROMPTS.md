# AI Usage and Prompt Log

## 1. Purpose

AI assistance was used during requirements analysis, architecture design, implementation, testing and review of the Secure Idempotent Refund Service.

The developer remained responsible for reviewing suggestions, applying changes and validating the result through automated tests.

## 2. Representative Prompts

### Prompt 1 – Requirements Analysis

"Analyse the technical challenge before writing code. Extract functional requirements, security requirements, idempotency rules, concurrency risks, testing expectations and deliverables. Propose the simplest architecture that satisfies the requirements."

Use: Requirements analysis and initial design.

### Prompt 2 – Specification

"Prepare a detailed SPEC.md for a Java 21 and Spring Boot REST API managing payments and refunds. Include API contracts, business rules, payment states, refund validation, idempotency, database constraints, concurrency, security, tests and PCI DSS scope considerations."

Use: Phase 1 specification.

### Prompt 3 – Payment Module

"Implement payment management using Spring Boot, Spring Data JPA, DTO validation, service interfaces and REST controllers. Use BigDecimal for money, UUID identifiers and clear HTTP errors. Keep the architecture simple and testable."

Use: Payment domain, persistence and API.

### Prompt 4 – Idempotent Refunds

"Implement refund creation with an Idempotency-Key header. Store a unique idempotency record and a SHA-256 fingerprint of the canonical request. Repeated requests with the same key and payload must return the original refund. Reusing a key with a different payload must return HTTP 409. Prevent concurrent over-refunds by locking the payment while checking and updating its refundable balance."

Use: Refund workflow and concurrency.

### Prompt 5 – Security and Tests

"Configure Spring Security with stateless HTTP Basic authentication, BCrypt password encoding and credentials supplied through environment variables. Add integration tests for unauthenticated access, invalid input, refund creation, idempotent retries, key conflicts and concurrent refund requests."

Use: Security and test implementation.

### Prompt 6 – Test Failure Diagnosis

"Analyse the Maven Surefire test report. Identify the exact assertion failure before changing code. Explain whether the failure is caused by business logic, persistence precision or HTTP response handling, and propose the smallest safe correction."

Use: Test diagnosis.

## 3. Concrete AI-Assisted Issue: Timestamp Precision

### Observed behaviour

During the HTTP idempotency replay test, the first refund response returned:

    2026-11-09T21:52:33.551574891Z

The repeated request returned the same persisted refund, but H2 returned:

    2026-11-09T21:52:33.551575Z

The refund ID and business data were identical. The JSON assertion failed because timestamp precision differed.

### Diagnosis

The problem was not a failure of idempotency. The repeated request correctly returned HTTP 201 and the original refund identifier.

The issue was caused by creating a timestamp with nanosecond precision and persisting it in a database representation with lower precision.

### Correction

The constructor was changed from:

    this.createdAt = Instant.now();

to:

    this.createdAt = Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MICROS);

This normalises the timestamp before persistence and makes the initial response consistent with the value subsequently read from H2.

### Verification

After the correction:

- 15 tests executed.
- 0 failures.
- 0 errors.
- 0 skipped.
- Maven result: BUILD SUCCESS.

## 4. AI Limitations and Developer Review

AI-generated suggestions are not automatically correct.

This project demonstrates why developers must:
- Read actual test reports rather than assume the cause of a failure.
- Check database persistence behaviour and precision.
- Run the complete test suite after changes.
- Review security claims against implemented controls.
- Avoid claiming PCI DSS compliance without a formal assessment.
- Document limitations such as the mock processor and in-memory database.

The timestamp issue is a concrete example of the need for developer judgement and test evidence.

## 5. Human Responsibility

The developer is responsible for reviewing the code, validating requirements, running tests, reviewing security implications and deciding whether the application is suitable for deployment.

AI assistance was used as a development aid, not as a substitute for engineering review or security validation.
