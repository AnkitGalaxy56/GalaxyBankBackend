# ABC Bank registration API module

This module adds the first two registration endpoints to the existing `com.abcbank.mobile_banking` Spring Boot project. It is designed for Spring Boot 3.x / Java 17+ and assumes PostgreSQL, Spring Web, Spring Validation, Spring Data JPA, Spring Security, and JJWT dependencies are present.

## Important schema note
The supplied spreadsheet lists `emp_no`, `username`, `password_hash`, `mobile`, `user_type`, etc., but does not show a `customer_id` field or the actual table names for codes `U001`, `1002`, `1003`. Do not assume those codes are table names. This module therefore uses a separate `customer_registry` table as a trusted adapter to the bank's customer master. In production, populate it from your core banking/customer master integration, not from app requests. If you already have a customer master table, change `CustomerRegistry` and its repository to map that table instead.

## Endpoints

### 1) Start registration and request OTP
`POST /api/v1/registration/start`
```json
{"customerId":"CUST10001","mobileNumber":"9876543210"}
```
Returns HTTP 202 with a masked mobile and expiry. It deliberately does not return the OTP.

### 2) Verify OTP and receive a registration-scoped Bearer token
`POST /api/v1/registration/verify-otp`
```json
{"customerId":"CUST10001","otp":"123456"}
```
Returns a short-lived JWT with `user_type=CU` and `scope=registration:complete`. This is NOT a normal banking access token. A subsequent completion endpoint must validate the JWT signature, expiry, subject, and scope before setting up MPIN/password and activating the app profile. Do not use this token for balances, transfers, cards, or statements.

## Required dependencies
Ensure your `pom.xml` includes the equivalent of:
- `spring-boot-starter-web`
- `spring-boot-starter-validation`
- `spring-boot-starter-data-jpa`
- `spring-boot-starter-security`
- `org.postgresql:postgresql` (runtime)
- `io.jsonwebtoken:jjwt-api`, `jjwt-impl` (runtime), `jjwt-jackson` (runtime), all on the same JJWT version

Use Spring Boot's dependency management for Spring dependencies. If your project already has `SecurityConfig`, merge the stateless settings rather than creating a duplicate filter-chain bean. The current config permits the two onboarding endpoints; it does not yet configure JWT authentication for protected banking APIs.

## Database setup
1. Back up the database.
2. Review and apply `src/main/resources/db/migration/V1__registration_tables.sql` in `bankmobileapp_db` (or adapt the migration to your real customer master schema).
3. Seed `customer_registry` from trusted records, for example through a controlled DBA/import process. Never seed production customer data manually from user input.
4. Grant the app role only necessary privileges on these tables.
5. Set `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, and `APP_JWT_SECRET` in your environment or secret manager. Do not put passwords or JWT secrets in source control.

## OTP provider
`OtpDeliveryService` is the integration boundary. The included `LoggingOtpDeliveryService` is only a `dev` profile placeholder and prints the OTP only when the `dev` profile is explicitly enabled. This is strictly for local testing; add a real SMS gateway adapter before production and never log OTPs in production. Enable the `dev` profile only for local development.

## Banking hardening before production
- Add API gateway/WAF rate limits per IP, device, customer ID and mobile; CAPTCHA/risk checks where appropriate.
- Add resend cooldown and durable attempt/rate limits across instances (Redis or database constraints); lock out and alert on abuse.
- Use TLS only, secure secret manager/KMS, key rotation, token issuer/audience validation, and short expiry.
- Keep registration tokens scope-limited and implement a JWT validation filter before any protected endpoints.
- Do not reveal whether a customer ID or mobile number exists; keep responses generic.
- Add device binding, audit events without OTP/token/PII, fraud monitoring, SIM-swap/risk controls and an approved SMS provider.
- Add tests for valid/invalid OTP, expiry, attempt limits, wrong mobile, duplicate enrollment, and database failures.

## Existing project integration
Copy the package files into your existing `src/main/java/com/abcbank/mobile_banking/` tree and review the YAML values. Your uploaded ZIP did not include `pom.xml`, so dependency versions cannot be verified from it. This module intentionally does not modify your existing `MobileBankingApplication`, MPIN login DTO, or current database tables.
