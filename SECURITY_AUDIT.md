# Security Audit – Secure Idempotent Refund Service

Version: 1.0.0

## 1. Executive Summary

The application is a Spring Boot REST API for payment and refund management.

Its security objectives are:

- Prevent unauthorised API access.
- Validate incoming payment and refund data.
- Prevent duplicate refunds caused by client retries.
- Prevent refunds exceeding the refundable balance.
- Avoid exposing internal implementation details in error responses.
- Avoid collecting or storing cardholder data.

This is a technical challenge implementation. It is not production-ready and does not constitute PCI DSS certification.

## 2. Implemented Security Controls

| Area | Control | Status |
|---|---|---|
| Authentication | HTTP Basic on all endpoints | Implemented |
| Password storage | BCrypt password encoder | Implemented |
| Credentials | Environment variables | Implemented |
| Session management | Stateless configuration | Implemented |
| Input validation | Jakarta Bean Validation | Implemented |
| Monetary values | BigDecimal and precision validation | Implemented |
| Currency | Three-letter uppercase validation | Implemented |
| Idempotency | Unique database key and SHA-256 fingerprint | Implemented |
| Concurrency | Pessimistic payment locking | Implemented |
| Error responses | Centralised ProblemDetail responses | Implemented |
| H2 console | Disabled | Implemented |
| Cardholder data | No PAN or CVV fields | Implemented |

CSRF is disabled because this API uses stateless HTTP Basic authentication and does not rely on browser sessions or cookie-based authentication. HTTPS is mandatory for real deployments.

## 3. OWASP Top 10 (2021) Mapping

### A01 – Broken Access Control

Implemented:
- All API requests require authentication.
- Stateless security configuration.

Limitations:
- A single in-memory API user is configured.
- There is no customer or merchant ownership model.
- Resource-level authorisation is not implemented.

Recommendations:
- Introduce customer or merchant identities.
- Verify resource ownership on every relevant operation.
- Apply least-privilege roles.

### A02 – Cryptographic Failures

Implemented:
- BCrypt password encoding.
- No PAN or CVV collection or storage.

Limitations:
- TLS is not configured by the application.
- Database encryption at rest and secret rotation are not configured.

Recommendations:
- Enforce HTTPS.
- Use a secrets manager.
- Define encryption and key rotation policies.

### A03 – Injection

Implemented:
- Spring Data JPA repositories.
- Parameterised repository queries.
- Request validation.

Recommendations:
- Avoid concatenating untrusted input into SQL.
- Review future native queries.
- Add static analysis and dependency checks.

### A04 – Insecure Design

Implemented:
- Refund balance validation.
- Pessimistic locking during refund processing.
- Idempotency key uniqueness.
- Request fingerprint comparison.
- Transactional local persistence.

Limitations:
- Payment processor is a mock.
- No outbox or reconciliation workflow exists.
- External provider timeout scenarios are not implemented.

Recommendations:
- Use provider-side idempotency.
- Persist provider references.
- Reconcile uncertain provider outcomes.
- Consider an outbox or durable workflow for real integrations.

### A05 – Security Misconfiguration

Implemented:
- H2 console disabled.
- SQL statement logging disabled.
- Credentials supplied through environment variables.

Limitations:
- H2 is in-memory.
- Hibernate uses ddl-auto update.
- Production TLS and deployment hardening are not configured.

Recommendations:
- Use a persistent production database.
- Use Flyway or Liquibase migrations.
- Review production HTTP headers, TLS and network configuration.

### A06 – Vulnerable and Outdated Components

Implemented:
- Dependencies managed by the Spring Boot Maven parent.

Limitations:
- No automated dependency vulnerability scan is configured.

Recommendations:
- Add OWASP Dependency-Check or equivalent.
- Keep Java and dependencies patched.
- Review vulnerability reports before releases.

### A07 – Identification and Authentication Failures

Implemented:
- HTTP Basic authentication.
- BCrypt password encoding.
- Stateless sessions.

Limitations:
- Single in-memory user.
- No MFA, rate limiting or account lockout.
- No credential lifecycle management.

Recommendations:
- Use a dedicated identity provider for production.
- Add rate limiting and authentication monitoring.
- Rotate credentials regularly.

### A08 – Software and Data Integrity Failures

Implemented:
- Database uniqueness constraint for idempotency.
- Transactional persistence.

Limitations:
- No CI pipeline or signed build artefacts are included.

Recommendations:
- Protect CI pipelines.
- Verify build artefact integrity.
- Review changes and run tests before deployment.

### A09 – Security Logging and Monitoring Failures

Implemented:
- Generic unexpected-error responses.
- No cardholder data required by the application.

Limitations:
- No centralised audit logging or alerting.
- No production log-retention policy.

Recommendations:
- Add structured security audit events.
- Monitor authentication failures and refund conflicts.
- Never log passwords, authentication headers, PAN, CVV or payment secrets.
- Define retention and access policies.

### A10 – Server-Side Request Forgery

Assessment:
- The current mock processor does not make outbound HTTP requests.
- The API does not accept user-controlled URLs.

Recommendations:
- Use fixed trusted provider endpoints in future integrations.
- Do not construct outbound URLs from untrusted values.
- Apply network egress restrictions.

## 4. Idempotency and Concurrency

The refund workflow:

1. Validates the Idempotency-Key header.
2. Calculates a SHA-256 fingerprint from the canonical payment ID, amount and currency.
3. Persists a record protected by a unique database constraint.
4. Locks the payment while checking the refundable balance.
5. Creates the refund and updates payment status transactionally.
6. Returns the original refund for a matching repeated request.
7. Rejects reuse of the same key with a different payload using HTTP 409.

These controls protect the local transactional workflow. They do not guarantee exactly-once execution across an external payment provider. Provider-side idempotency and reconciliation are required for that scenario.

## 5. Input Validation

- Amounts use BigDecimal.
- Amounts must be positive and limited to two decimal places.
- Currency must use a three-letter uppercase code.
- Refund currency must match payment currency.
- Refunds cannot exceed the remaining refundable balance.
- Identifiers use UUID.
- Idempotency keys are limited to 128 characters and a restricted character set.

## 6. PCI DSS Scope Considerations

The application models payments and refunds but does not process real card data. Refund execution is delegated to a mock processor.

No PCI DSS compliance claim is made.

For a real payment environment:
- Use a PCI DSS-compliant payment provider.
- Prefer provider tokens and references over storing PAN.
- Never store CVV.
- Assess network segmentation, access controls, monitoring and operational responsibilities.
- Determine PCI DSS scope with the relevant payment provider and qualified compliance advisers.

## 7. Production Readiness Gaps

| Limitation | Required action |
|---|---|
| H2 in-memory database | Use persistent production database |
| ddl-auto update | Use versioned migrations |
| Single in-memory user | Add identity and resource authorisation |
| HTTP Basic | Enforce TLS and review service authentication |
| Mock payment processor | Integrate provider with idempotency |
| No rate limiting | Add gateway or application limits |
| No audit pipeline | Add structured audit logging |
| No dependency scanning | Add vulnerability scanning |
| No deployment hardening | Review TLS, secrets and network controls |

## 8. Security Test Evidence

The current automated suite contains 15 passing tests.

Coverage includes:
- Unauthenticated requests are rejected.
- Invalid payment amounts are rejected.
- Refund creation succeeds.
- Same-key retries return the original refund.
- Same key with a different request is rejected.
- Over-refunds are rejected.
- Concurrent same-key requests do not create duplicate refunds.
- Concurrent different-key requests cannot over-refund the same payment.

Latest recorded result: 15 tests, 0 failures, 0 errors, 0 skipped.

## 9. Conclusion

The project demonstrates authentication, validation, idempotency, concurrency protection and safe API error handling.

It is suitable as a technical challenge implementation, but requires further architecture, infrastructure and operational security work before production payment processing.
