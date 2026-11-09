# Secure Idempotent Refund Service

## 1. Overview

### 1.1 Purpose

The purpose of this project is to design and implement a secure REST API for managing payments and processing refunds.

The main objective is to guarantee that refund operations are idempotent, including when clients retry requests or submit identical requests concurrently.

The service must prevent duplicate refunds, validate refundable balances, protect sensitive information, and provide predictable API responses.

### 1.2 Context

Refund operations are particularly sensitive because processing the same request more than once may result in duplicate financial transactions.

Network failures, client retries, and concurrent requests can cause the same operation to reach the service multiple times.

This project addresses these scenarios through:

- Idempotency keys.
- Database-level uniqueness constraints.
- Transactional processing.
- Pessimistic locking when accessing payment records.
- Input validation.
- Controlled error responses.
- Secure logging practices.

### 1.3 Goals

The application must:

- Provide REST endpoints for basic payment management.
- Process refunds securely.
- Prevent duplicate refunds for repeated requests.
- Handle concurrent refund requests safely.
- Ensure that refunds do not exceed the original payment amount.
- Validate payment status and currency.
- Avoid exposing sensitive information in API responses and logs.
- Include automated tests for business rules, concurrency, and security.

### 1.4 Out of Scope

The following capabilities are intentionally excluded to keep the implementation focused:

- Integration with a real payment gateway.
- Real card processing or storage of cardholder data.
- Currency conversion.
- Distributed messaging systems such as Kafka or RabbitMQ.
- Distributed caching or Redis.
- Kubernetes or cloud infrastructure.
- Multi-service orchestration.
- User registration and role management.
- Production-grade payment reconciliation.

The payment processor will be simulated through a mock implementation.

---

## 2. Technical Context

### 2.1 Technology Stack

| Component | Technology |
|---|---|
| Programming language | Java 21 |
| Framework | Spring Boot 3.5.x |
| Build tool | Maven 3.10.0 |
| API | Spring MVC / REST |
| Persistence | Spring Data JPA |
| Database | H2 |
| Security | Spring Security |
| Validation | Jakarta Bean Validation |
| Testing | JUnit 5, Mockito, Spring Boot Test |
| API error format | RFC 9457 Problem Details |

### 2.2 Application Type

The application will be implemented as a single, independently deployable Spring Boot service.

The codebase will follow a layered architecture, separating:

- Controllers: HTTP request handling.
- Services: business logic and transaction boundaries.
- Repositories: persistence operations.
- Models: domain entities.
- DTOs: API request and response representations.
- Payment processor: abstraction for payment-provider operations.
- Exceptions: centralized error handling.

This structure provides clear separation of responsibilities without introducing unnecessary architectural complexity.

### 2.3 Persistence

H2 will be used as the database for this technical challenge.

JPA entities and Spring Data repositories will manage persistence.

Database constraints and transactions will be used to protect critical business rules, especially idempotency and concurrent refund processing.

The application must not rely exclusively on in-memory synchronization mechanisms to guarantee correctness.

---

## 3. Functional Requirements and Business Rules

### 3.1 Payment Management

The application must support the following operations:

- Create a payment.
- Retrieve all payments.
- Retrieve a payment by ID.
- Update a payment's status when business rules allow it.
- Delete a payment when business rules allow it.

Each payment must contain:

- Unique identifier.
- Amount.
- Currency.
- Status.
- Creation timestamp.

A newly created payment must have the status `CAPTURED`.

Payment amounts and currencies are immutable through the API after creation.

### 3.2 Refund Processing

The application must allow clients to create refunds against existing payments.

A refund request must include:

- Payment ID.
- Refund amount.
- Currency.
- Idempotency key provided through the HTTP request header.

The service must validate:

1. The payment exists.
2. The payment is eligible for refunds.
3. The requested amount is greater than zero.
4. The requested currency matches the payment currency.
5. The requested amount does not exceed the remaining refundable balance.
6. The idempotency key is valid.

A payment in `CANCELLED` status cannot be refunded.

A payment in `CAPTURED` or `PARTIALLY_REFUNDED` status can be refunded if sufficient refundable balance remains.

### 3.3 Refund Status

For this implementation, a refund has one persisted successful status:

- `COMPLETED`

A refund is persisted only after the mock payment processor reports successful processing.

If processing fails, the transaction is rolled back and no completed refund or idempotency record is committed.

This deliberately avoids introducing asynchronous processing states that are not required by the scope of this project.

### 3.4 Payment Status Transitions

The initial payment status is:

- `CAPTURED`

The following statuses may be reached:

- `CAPTURED`: the payment has not been refunded.
- `PARTIALLY_REFUNDED`: at least one refund has been completed, but a refundable balance remains.
- `REFUNDED`: the total payment amount has been refunded.
- `CANCELLED`: the payment has been cancelled before any refund.

The `PARTIALLY_REFUNDED` and `REFUNDED` statuses are managed by the refund service and must not be set directly through the payment update endpoint.

The only manual status transition exposed through the payment API is:

`CAPTURED -> CANCELLED`

This transition is allowed only when the payment has no completed refunds.

A cancelled payment cannot be reactivated or refunded through the API.

### 3.5 Payment Deletion

A payment can only be deleted if it has no completed refunds.

Payments with completed refunds cannot be deleted, preserving the relationship and history required for refund operations.

### 3.6 Error Handling

The API must return consistent error responses using Spring's `ProblemDetail` representation.

Expected HTTP status codes:

| HTTP Status | Scenario |
|---|---|
| 400 Bad Request | Invalid request, missing or malformed idempotency key |
| 401 Unauthorized | Missing or invalid authentication credentials |
| 404 Not Found | Payment or refund does not exist |
| 409 Conflict | Idempotency key reused with a different request, insufficient refundable balance, or invalid business-state transition |
| 500 Internal Server Error | Unexpected internal failure |

Internal exception details, database information, credentials, and sensitive information must never be exposed in error responses.

---

## 4. Application Architecture

### 4.1 Architectural Style

The application will use a layered architecture within a single Spring Boot application.

Business logic must remain in service classes rather than controllers.

Controllers must be responsible for HTTP concerns, validation integration, and response mapping.

Repositories must handle database access.

The payment processor must be accessed through an interface to separate refund business logic from the external payment-processing implementation.

### 4.2 Proposed Package Structure

```text
com.cgp.refunds
├── controller
│   ├── PaymentController.java
│   └── RefundController.java
│
├── service
│   ├── PaymentService.java
│   ├── RefundService.java
│   └── impl
│       ├── PaymentServiceImpl.java
│       └── RefundServiceImpl.java
│
├── repository
│   ├── PaymentRepository.java
│   ├── RefundRepository.java
│   └── IdempotencyRecordRepository.java
│
├── payment
│   ├── PaymentProcessor.java
│   └── MockPaymentProcessor.java
│
├── model
│   ├── Payment.java
│   ├── Refund.java
│   └── IdempotencyRecord.java
│
├── dto
│   ├── PaymentRequest.java
│   ├── PaymentUpdateRequest.java
│   ├── PaymentResponse.java
│   ├── RefundRequest.java
│   └── RefundResponse.java
│
├── exception
│   └── GlobalExceptionHandler.java
│
└── SecureIdempotentRefundServiceApplication.java
```

Spring Data JPA repository interfaces will extend `JpaRepository` where appropriate. No additional manual repository implementation is required for standard database operations.

Additional configuration classes may be introduced when needed, particularly for security and application configuration.

### 4.3 SOLID Principles and Design Practices

The implementation should apply SOLID principles pragmatically:

- **Single Responsibility Principle:** controllers, services, repositories, and payment processors have separate responsibilities.
- **Open/Closed Principle:** the payment processor abstraction allows alternative implementations without changing refund business logic.
- **Liskov Substitution Principle:** implementations of `PaymentProcessor` must respect its defined contract.
- **Interface Segregation Principle:** interfaces should expose only the operations required by their consumers.
- **Dependency Inversion Principle:** services should depend on abstractions where this provides a meaningful separation, particularly for payment processing.

Spring dependency injection will be used to manage application components.

The implementation should avoid unnecessary abstractions and design patterns.

---

## 5. Payment Management Module

### 5.1 Payment Creation

A payment is created with:

- A generated identifier.
- A positive monetary amount.
- A valid ISO 4217 currency code.
- Initial status `CAPTURED`.
- Creation timestamp.

The API client must not provide the payment identifier, status, or creation timestamp.

### 5.2 Payment Retrieval

The application must support retrieving one payment by ID and listing existing payments.

Responses must expose only the information required by the API contract.

### 5.3 Payment Update

The payment update endpoint will use `PATCH` because only the payment status can be modified.

The only allowed manual update is cancellation of a payment that has no completed refunds.

The payment amount and currency cannot be modified through this endpoint.

### 5.4 Payment Deletion

A payment can be deleted only when it has no completed refunds.

If completed refunds exist, the API must return `409 Conflict`.

---

## 6. Refund and Payment Integration

### 6.1 Refundable Balance

The refundable balance is calculated as:

`Refundable Balance = Payment Amount - Sum of Completed Refunds`

The sum of completed refunds must never exceed the original payment amount.

All monetary calculations must use Java `BigDecimal`.

Floating-point types such as `double` and `float` must not be used for monetary values.

Amounts will be stored using a database decimal type, such as `DECIMAL(19,2)`.

### 6.2 Currency Validation

Currency codes must be valid ISO 4217 codes.

The requested refund currency must match the original payment currency.

Currency conversion is outside the scope of this implementation.

### 6.3 Payment Processor Abstraction

Refund processing must use the `PaymentProcessor` interface.

The initial implementation will be `MockPaymentProcessor`, which simulates successful processing without connecting to a real payment provider.

The abstraction must allow a future implementation to integrate with an external provider without requiring changes to the core refund business rules.

### 6.4 Transactional Processing

Refund creation must execute within a database transaction.

The following operations must be committed atomically:

- Claiming the idempotency key.
- Validating the payment and refundable balance.
- Processing the refund through the mock processor.
- Persisting the completed refund.
- Updating the payment status.
- Linking the idempotency record to the resulting refund.

If processing fails before commit, all database changes must be rolled back.

Because the current processor is a mock without external side effects, database rollback is sufficient for this challenge.

A real external payment provider may produce an uncertain outcome if a network timeout occurs after the provider has processed a refund. Handling this situation would require provider-side idempotency and reconciliation mechanisms, which are outside the current scope.

---

## 7. REST API Contract

All endpoints will use the base path:

`/api/v1`

All request and response bodies will use JSON.

### 7.1 Payment Endpoints

#### Create Payment

`POST /api/v1/payments`

Example request:

```json
{
  "amount": 100.00,
  "currency": "EUR"
}
```

Successful response:

`201 Created`

```json
{
  "paymentId": "generated-uuid",
  "amount": 100.00,
  "currency": "EUR",
  "status": "CAPTURED",
  "createdAt": "2026-11-09T10:00:00Z"
}
```

#### List Payments

`GET /api/v1/payments`

Successful response:

`200 OK`

The response contains a collection of payment representations.

#### Get Payment

`GET /api/v1/payments/{paymentId}`

Successful response:

`200 OK`

Returns the payment representation.

#### Update Payment Status

`PATCH /api/v1/payments/{paymentId}`

Example request:

```json
{
  "status": "CANCELLED"
}
```

Successful response:

`200 OK`

Only the cancellation transition described in section 3.4 is allowed.

#### Delete Payment

`DELETE /api/v1/payments/{paymentId}`

Successful response:

`204 No Content`

The operation must fail with `409 Conflict` if the payment has completed refunds.

### 7.2 Refund Endpoints

#### Create Refund

`POST /api/v1/refunds`

Required HTTP header:

`Idempotency-Key: <unique-client-generated-key>`

Example request:

```json
{
  "paymentId": "generated-uuid",
  "amount": 25.00,
  "currency": "EUR"
}
```

Successful response:

`201 Created`

```json
{
  "refundId": "generated-uuid",
  "paymentId": "generated-uuid",
  "amount": 25.00,
  "currency": "EUR",
  "status": "COMPLETED",
  "createdAt": "2026-11-09T10:05:00Z"
}
```

If the same idempotency key is reused with the same logical request, the service must return the original refund result without creating another refund.

#### Get Refund

`GET /api/v1/refunds/{refundId}`

Successful response:

`200 OK`

Returns the refund representation.

### 7.3 API Error Representation

Errors must use the `ProblemDetail` format.

Example:

```json
{
  "type": "about:blank",
  "title": "Conflict",
  "status": 409,
  "detail": "The idempotency key was already used with a different request.",
  "instance": "/api/v1/refunds"
}
```

Error messages must be useful to API consumers without exposing internal implementation details.

---

## 8. Idempotency and Concurrency Strategy

### 8.1 Idempotency Record

Each refund request must provide an `Idempotency-Key` header.

The key must:

- Be present and non-blank.
- Contain between 1 and 128 characters.
- Match the allowed character set `[A-Za-z0-9._:-]`.
- Be unique within the application.

The database must enforce a unique constraint on the idempotency key.

An idempotency record will contain:

- Internal record ID.
- Idempotency key.
- Request fingerprint.
- Associated refund ID.
- Creation timestamp.

The idempotency record must be stored in the same transaction as the refund.

For this challenge, idempotency records will not expire automatically. Their lifetime is tied to the lifetime of the application database.

### 8.2 Request Fingerprint

The service must calculate a SHA-256 fingerprint of the normalized logical refund request.

The fingerprint must include:

- Payment ID.
- Normalized amount.
- Currency.

The amount must be normalized so logically equivalent values such as `25.5` and `25.50` produce the same fingerprint.

The fingerprint must be generated from an unambiguous canonical representation, not from naïve concatenation of raw request strings.

The service must not store or log the original request body as part of the idempotency record.

### 8.3 Processing Flow

The refund processing sequence must be:

1. Validate the request body and idempotency key.
2. Normalize the request values.
3. Calculate the request fingerprint.
4. Attempt to insert a new idempotency record with the unique key.
5. Acquire a pessimistic write lock on the associated payment.
6. Validate payment existence and status.
7. Calculate the remaining refundable balance.
8. Validate that the requested amount does not exceed the refundable balance.
9. Invoke the mock payment processor.
10. Persist the completed refund.
11. Update the payment status based on the remaining refundable balance.
12. Associate the idempotency record with the refund.
13. Commit the transaction.

All database operations must be executed within the same transaction.

### 8.4 Duplicate and Concurrent Requests

The service must handle the following cases.

**Same key and same logical request, sequentially**

If a request is repeated after the original transaction has committed:

- The existing idempotency record is retrieved.
- The request fingerprint is compared.
- The original refund result is returned.
- No additional refund is created.

**Same key and different logical request**

If a client reuses an existing key with a different request fingerprint:

- The request is rejected.
- The API returns `409 Conflict`.
- No additional refund is created.

**Same key and same logical request, concurrently**

When two identical requests arrive simultaneously:

- Both attempt to claim the same unique idempotency key.
- The database unique constraint serializes the conflicting inserts.
- The first transaction to claim the key processes and commits the refund.
- The competing request waits for the outcome of the first transaction.
- If the first transaction commits, the competing request retrieves the existing record, compares fingerprints, and returns the original refund result.
- If the first transaction rolls back, the competing request may claim the key and process the refund.

The API must not expose a committed `PROCESSING` state for this synchronous implementation.

Duplicate-key handling must take place outside the failed transaction. The service must not attempt to query the existing record inside a transaction already marked as rollback-only.

If necessary, the existing idempotency record must be retrieved in a new transaction after the failed insert transaction has rolled back.

**Different keys against the same payment, concurrently**

Requests with different idempotency keys may still compete for the same refundable balance.

The service must acquire a pessimistic write lock on the payment record before calculating the remaining refundable balance.

This ensures that concurrent refund operations cannot collectively exceed the original payment amount.

### 8.5 Database Constraints and Locking

The following protections are required:

- Unique database constraint on the idempotency key.
- Pessimistic write lock on the payment record during refund processing.
- Transactional refund creation.
- Atomic persistence of the idempotency record and refund.

Application-level checks alone are not sufficient to guarantee correctness under concurrent requests.

---

## 9. Security Requirements

### 9.1 Authentication

All API endpoints must require authentication.

For this technical challenge, the application will use HTTP Basic authentication with credentials configured through environment variables.

Passwords must be stored and compared using BCrypt hashing.

Credentials must never be hardcoded in source code or committed to the repository.

This is a deliberately simple authentication mechanism for a demonstration service. Production deployment would require HTTPS and a more complete identity and access management strategy.

### 9.2 Request Validation

All incoming requests must be validated using Jakarta Bean Validation and explicit business-rule checks.

The application must reject:

- Missing required fields.
- Invalid identifiers.
- Invalid currency codes.
- Zero or negative amounts.
- Excessive monetary precision where unsupported.
- Invalid idempotency keys.
- Unsupported payment status transitions.

### 9.3 Sensitive Data Protection

The application does not require cardholder data.

The API must not accept, store, return, or log:

- Primary Account Numbers (PAN).
- Card Verification Values (CVV/CVC).
- Card expiration details.
- Authentication credentials.
- Authorization headers.
- Full request bodies containing sensitive information.

Logs must contain only the information needed for operational diagnosis, such as correlation identifiers, resource identifiers where appropriate, and error categories.

### 9.4 Error and Log Security

The application must:

- Avoid exposing stack traces to API clients.
- Avoid returning database or framework internals.
- Avoid logging credentials or authorization headers.
- Avoid logging complete request or response bodies.
- Sanitize externally supplied values before logging.
- Use centralized exception handling.

### 9.5 Database and Development Security

The H2 console must not be enabled in the application configuration used for normal API execution.

Database credentials and authentication secrets must be supplied through environment variables or local configuration excluded from version control.

---

## 10. Testing Requirements

Automated tests must cover the main business rules, idempotency behavior, concurrency scenarios, and security protections.

### 10.1 Payment Tests

- Create a payment successfully.
- Reject a zero or negative amount.
- Reject an invalid currency.
- Retrieve an existing payment.
- Return `404 Not Found` for a missing payment.
- Cancel a payment without refunds.
- Reject cancellation after a completed refund.
- Reject deletion when completed refunds exist.

### 10.2 Refund Tests

- Create a refund successfully.
- Reject a refund for a nonexistent payment.
- Reject a refund for a cancelled payment.
- Reject a zero or negative refund amount.
- Reject a refund with a different currency.
- Reject a refund exceeding the refundable balance.
- Update payment status after a successful refund.
- Return the correct refund when retrieving by ID.

### 10.3 Idempotency Tests

- Process a refund with a new idempotency key.
- Repeat the same request with the same key and return the original result.
- Verify that repeated requests create only one refund.
- Reuse the same key with a different amount and return `409 Conflict`.
- Reuse the same key with a different payment ID and return `409 Conflict`.
- Submit simultaneous identical requests with the same key and verify that only one refund is created.
- Submit concurrent requests with different keys against the same payment and verify that the refundable balance is never exceeded.

### 10.4 Failure and Transaction Tests

- Simulate a payment processor failure.
- Verify that no refund or idempotency record is committed after rollback.
- Verify that the same idempotency key can be retried after a failed transaction.
- Verify that payment status remains consistent after rollback.

### 10.5 Security Tests

- Reject requests without authentication.
- Reject requests with invalid credentials.
- Verify that invalid input does not expose internal exception details.
- Verify that sensitive information is not included in application logs.
- Verify that API responses do not expose internal entity fields or credentials.

Concurrency tests must use actual concurrent execution and verify persisted database state, rather than relying only on sequential calls.

---

## 11. Deliverables

The project repository must contain:

- `SPEC.md`: functional and technical specification.
- `README.md`: setup instructions, execution instructions, API usage, and testing instructions.
- `PROMPTS.md`: relevant AI prompts used during development, including at least one AI-generated error, bias, or vulnerability and its correction.
- `SECURITY_AUDIT.md`: OWASP-related security review, identified risks, and implemented mitigations.
- Maven project configuration and Maven Wrapper.
- Application source code.
- Automated tests.

The project must be executable locally using the documented commands.

---

## 12. Acceptance Criteria

The implementation will be considered complete when:

1. The application starts successfully using Java 21 and Maven.
2. Payment CRUD operations work according to the documented business rules.
3. Refunds can be created successfully.
4. Repeated requests with the same idempotency key and logical payload do not create duplicate refunds.
5. Reusing an idempotency key with a different payload returns `409 Conflict`.
6. Concurrent requests cannot create duplicate refunds or exceed the refundable balance.
7. Failed refund processing does not leave partial database changes.
8. Authentication is required for all API endpoints.
9. Sensitive information is not exposed in API responses or application logs.
10. Automated tests cover the critical functional, concurrency, and security scenarios.
11. `README.md`, `SPEC.md`, `PROMPTS.md`, and `SECURITY_AUDIT.md` are included in the repository.

---

## 13. PCI DSS Scope Considerations

The application is designed not to collect, process, or store cardholder data such as PAN or CVV.

This reduces the application's exposure to payment-card data and may reduce the scope of applicable PCI DSS requirements, depending on the real deployment and integration architecture.

However, this design does not establish PCI DSS compliance or certification.

Any future integration with a real payment provider must be assessed separately, including the provider's responsibilities, data flows, and the organization's applicable PCI DSS obligations.