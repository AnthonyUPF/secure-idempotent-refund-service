# Secure Idempotent Refund Service

A Java 21 and Spring Boot REST API demonstrating payment management and secure, idempotent refund processing.

**Public repository:**  
https://github.com/AnthonyUPF/secure-idempotent-refund-service

## Technology Stack

- Java 21
- Spring Boot 3.5.16
- Maven Wrapper
- Spring Web
- Spring Data JPA
- Spring Security
- Jakarta Bean Validation
- H2 in-memory database
- JUnit 5 and Spring Boot Test

## Features

- Create, retrieve, update and delete payments.
- Create and retrieve refunds.
- Validate monetary amounts using `BigDecimal`.
- Validate currency codes.
- Prevent refunds exceeding the remaining refundable balance.
- Support idempotent refund requests through the `Idempotency-Key` header.
- Return the original refund when the same key and request are repeated.
- Reject reuse of an idempotency key with a different request.
- Use pessimistic database locking to prevent concurrent over-refunds.
- Require HTTP Basic authentication.
- Encode passwords with BCrypt.
- Return structured errors using Spring `ProblemDetail`.

The payment processor is a mock implementation. No real payment provider is contacted.

## Requirements

- Java 21
- Git
- Bash terminal (Linux, macOS or WSL), or Windows PowerShell.

The Maven Wrapper is included, so a separate Maven installation is not required.

Check your Java installation:

```bash
java -version
```

## Clone the Repository

```bash
git clone https://github.com/AnthonyUPF/secure-idempotent-refund-service.git

cd secure-idempotent-refund-service
```

## Configuration

The application requires two environment variables for HTTP Basic authentication:

| Variable | Description |
|---|---|
| `APP_SECURITY_USERNAME` | Authentication username |
| `APP_SECURITY_PASSWORD` | Authentication password |

### Step 1. Create your local environment file

The repository includes an `.env.example` file containing example development credentials.

Copy it to create your own local configuration:

**Linux, macOS and WSL:**

```bash
cp .env.example .env
```

**Windows PowerShell:**

```powershell
Copy-Item .env.example .env
```

Edit `.env` and replace the example values with your own credentials.

For example:

```dotenv
APP_SECURITY_USERNAME=refund-user
APP_SECURITY_PASSWORD=Change-This-Local-Password
```

Use a strong password of your own choice.

**Important:** `.env` is a local configuration file and is excluded from Git. Never commit real credentials or secrets to version control.

### Step 2. Load the environment variables

Spring Boot does not automatically load `.env` files. You must load the variables into your terminal session before starting the application.

**Linux, macOS and WSL:**

```bash
set -a
source .env
set +a
```

**Windows PowerShell:**

```powershell
Get-Content .env | ForEach-Object {
    if ($_ -match '^\s*([^#\s][^=]*)=(.*)$') {
        $name = $matches[1].Trim()
        $value = $matches[2].Trim().Trim('"').Trim("'")
        [Environment]::SetEnvironmentVariable($name, $value, "Process")
    }
}
```

These commands load the credentials into the current terminal session. Run the application from that same session.

### Database configuration

The current configuration uses an H2 in-memory database:

- JDBC URL: `jdbc:h2:mem:refunddb`
- Application port: `8080`
- H2 console: disabled

Data is not durable across application restarts.

## Build and Run

### Run tests

**Linux, macOS and WSL:**

```bash
./mvnw clean verify
```

**Windows PowerShell:**

```powershell
.\mvnw.cmd clean verify
```

### Build the application

**Linux, macOS and WSL:**

```bash
./mvnw clean package
```

**Windows PowerShell:**

```powershell
.\mvnw.cmd clean package
```

### Start the application

First, complete the configuration steps and load the environment variables.

**Linux, macOS and WSL:**

```bash
./mvnw spring-boot:run
```

**Windows PowerShell:**

```powershell
.\mvnw.cmd spring-boot:run
```

The API will be available at:

http://localhost:8080

All endpoints require HTTP Basic authentication. Use the username and password configured in `.env`.

## API Endpoints

### Payments

| Method | Endpoint | Description |
|---|---|---|
| POST | `/api/v1/payments` | Create payment |
| GET | `/api/v1/payments` | List payments |
| GET | `/api/v1/payments/{paymentId}` | Retrieve payment |
| PATCH | `/api/v1/payments/{paymentId}` | Cancel payment |
| DELETE | `/api/v1/payments/{paymentId}` | Delete payment subject to business rules |

Create a payment:

```bash
curl -u "$APP_SECURITY_USERNAME:$APP_SECURITY_PASSWORD" \
  -H 'Content-Type: application/json' \
  -X POST http://localhost:8080/api/v1/payments \
  -d '{"amount":100.00,"currency":"EUR"}'
```

The response includes the generated `paymentId`. Keep this UUID to use it when creating a refund.

Cancel a payment:

```bash
curl -u "$APP_SECURITY_USERNAME:$APP_SECURITY_PASSWORD" \
  -H 'Content-Type: application/json' \
  -X PATCH http://localhost:8080/api/v1/payments/REPLACE_WITH_PAYMENT_UUID \
  -d '{"status":"CANCELLED"}'
```

### Refunds

| Method | Endpoint | Description |
|---|---|---|
| POST | `/api/v1/refunds` | Create refund |
| GET | `/api/v1/refunds/{refundId}` | Retrieve refund |

Create a refund:

```bash
curl -u "$APP_SECURITY_USERNAME:$APP_SECURITY_PASSWORD" \
  -H 'Content-Type: application/json' \
  -H 'Idempotency-Key: refund-example-001' \
  -X POST http://localhost:8080/api/v1/refunds \
  -d '{"paymentId":"REPLACE_WITH_PAYMENT_UUID","amount":30.00,"currency":"EUR"}'
```

Replace the payment identifier with the UUID returned by payment creation.

Repeat the same request with the same idempotency key to retrieve the original refund result. Reusing that key with a different payload returns HTTP `409 Conflict`.

## HTTP Status Codes

- `200 OK`: successful retrieval or update.
- `201 Created`: resource created.
- `204 No Content`: resource successfully deleted.
- `400 Bad Request`: invalid or missing request data.
- `401 Unauthorized`: missing or invalid authentication.
- `404 Not Found`: resource does not exist.
- `409 Conflict`: business rule or idempotency conflict.
- `500 Internal Server Error`: unexpected server error.

Errors use Spring `ProblemDetail`.

## Idempotency Design

The refund API requires an `Idempotency-Key` header.

1. Validate the key format and length.
2. Calculate a SHA-256 fingerprint from payment ID, normalised amount and currency.
3. Persist an idempotency record with a unique key constraint.
4. Lock the payment while checking its refundable balance.
5. Create the refund and update payment status transactionally.
6. Return the original refund for a repeated key and matching fingerprint.
7. Reject the same key with a different fingerprint.

A real external payment integration would additionally require provider-side idempotency and reconciliation.

## Tests

Run the complete verification suite:

```bash
./mvnw clean verify
```

The integration tests cover:

- Authentication.
- Payment operations.
- Refund creation and retrieval.
- Idempotent retries.
- Idempotency key conflicts.
- Refund balance validation.
- Concurrent refund requests.

Latest verified result: **15 tests, 0 failures, 0 errors, 0 skipped.**

## Documentation

- `SPEC.md`: requirements, architecture, business rules and API design.
- `SECURITY_AUDIT.md`: security controls, OWASP mapping and production recommendations.
- `PROMPTS.md`: AI prompts, review process and documented correction.
- `openapi.yaml`: OpenAPI 3.1 API contract.

## Production Considerations

This is a technical challenge implementation, not a production payment platform.

Before production use:

- Replace H2 with a persistent production database.
- Replace Hibernate schema auto-update with database migrations.
- Integrate a real payment provider with provider-side idempotency.
- Implement resource-level authorisation.
- Enforce HTTPS and secure secret management.
- Add rate limiting, audit logging and monitoring.
- Add dependency vulnerability scanning.
- Review deployment hardening and recovery procedures.

The application does not collect or store PAN or CVV and does not claim PCI DSS compliance.