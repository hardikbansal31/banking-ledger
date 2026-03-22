# Banking Core & Distributed Ledger

A production-quality banking backend built with **Java 21**, **Spring Boot 3.5**, and **MySQL**. Implements double-entry accounting, distributed locking, JWT authentication, a tiered fee engine, exchange rate integration, and Quartz-scheduled payments.

Built as a portfolio project targeting backend engineering roles at Tier-1 financial institutions.

---

## Tech Stack

| Layer | Technology |
|---|---|
| Language | Java 21 (virtual threads ready) |
| Framework | Spring Boot 3.5.11 |
| Database | MySQL 8.4 (JPA / Hibernate 6) |
| Cache & Locks | Redis 7.2 via Redisson 3.27.2 |
| Security | Spring Security + JWT (jjwt 0.12.5, HS384) |
| Scheduler | Quartz 2.x (in-memory dev, JDBC prod) |
| HTTP Client | Spring WebFlux WebClient |
| Observability | Spring Boot Actuator + Micrometer |
| Build | Maven 3.9+ |

---

## Architecture

```
HTTP Request
  ├── MdcRequestLoggingFilter     (requestId injected into every log line)
  ├── JwtAuthenticationFilter     (validates Bearer token, checks blacklist)
  └── SecurityFilterChain         (RBAC: ADMIN / USER)
        ↓
  Controller  (thin — validates input, delegates)
        ↓
  Service     (@Transactional, @PreAuthorize, business logic)
     ├── DistributedLockService   (Redisson RLock — outer concurrency guard)
     ├── LedgerService            (double-entry accounting engine)
     ├── FeeEngine                (tiered fee calculation)
     ├── ExchangeRateService      (WebClient + Redis cache)
     └── TransactionStateMachine  (PENDING → AUTHORIZED → SETTLED / FAILED)
        ↓
  Repository  (Spring Data JPA → MySQL)
```

### Double-Entry Ledger

Every financial transaction produces exactly two immutable `LedgerEntry` rows:

```
Transfer $500 from ACC-000001 → ACC-000002

ledger_entries:
  account=ACC-000001  type=DEBIT   amount=500.00  balance_after=4500.00
  account=ACC-000002  type=CREDIT  amount=500.00  balance_after=5500.00
```

Both entries commit atomically via `@Transactional`. If either fails, both roll back. Money is never created or destroyed.

### Concurrency Model

Three guards prevent double-spending:

1. **Redisson distributed lock** — outer guard, acquired at controller level before `@Transactional` opens. Prevents concurrent requests across multiple app instances.
2. **DB pessimistic lock** (`SELECT FOR UPDATE`) — inner guard, acquired inside the transaction in consistent ID order to prevent deadlock.
3. **Optimistic lock** (`@Version` on entities) — catches the rare case where two transactions slip through both locks and try to modify the same row.

---

## Project Structure

```
src/main/java/com/bankingcore/bankingledger/
├── config/          Spring configuration beans
├── controller/      REST controllers (thin layer)
├── domain/
│   ├── entity/      JPA entities (User, Account, Transaction, LedgerEntry, ...)
│   ├── enums/       Domain enums (Role, TransactionStatus, EntryType, ...)
│   └── repository/  Spring Data JPA repositories
├── dto/
│   ├── request/     Validated request DTOs
│   └── response/    Response DTOs (never expose entities directly)
├── exception/       Domain exception hierarchy + GlobalExceptionHandler
├── security/
│   ├── filter/      JwtAuthenticationFilter, MdcRequestLoggingFilter, SecurityExceptionHandler
│   └── service/     JwtService, UserDetailsServiceImpl
└── service/         Business logic (LedgerService, FeeEngine, ExchangeRateService, ...)
```

---

## Prerequisites

- Java 21+
- Maven 3.9+
- MySQL 8.x running locally
- Docker (for Redis only)

---

## Local Setup

### 1. Database

Connect to your local MySQL as root and run:

```sql
CREATE DATABASE banking_ledger CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
GRANT ALL PRIVILEGES ON banking_ledger.* TO 'your_user'@'localhost';
FLUSH PRIVILEGES;
```

### 2. Redis

```bash
docker compose up -d
```

This starts Redis 7.2 on port 6380 with password authentication and AOF persistence.

### 3. Configuration

```bash
cp .env.example .env
```

Edit `src/main/resources/application-development.yml` and set:
```yaml
spring:
  datasource:
    username: your_mysql_user
    password: your_mysql_password
```

The development profile activates automatically. All other config has working defaults.

### 4. Run

```bash
mvn spring-boot:run
```

Spring Boot auto-creates all tables on first start (`ddl-auto: update`).

The app starts at: `http://localhost:8080/api/v1`

---

## API Reference

All endpoints are prefixed with `/api/v1`. Protected endpoints require `Authorization: Bearer <token>`.

### Authentication

| Method | Endpoint | Auth | Description |
|--------|----------|------|-------------|
| POST | `/auth/register` | None | Register new user |
| POST | `/auth/login` | None | Login, receive JWT pair |
| POST | `/auth/refresh` | None | Refresh access token |
| POST | `/auth/logout` | JWT | Blacklist current token |

**Register:**
```bash
curl -X POST http://localhost:8080/api/v1/auth/register \
  -H "Content-Type: application/json" \
  -d '{"username":"alice","email":"alice@test.com","password":"Password1",
       "firstName":"Alice","lastName":"Smith"}'
```

**Login:**
```bash
curl -X POST http://localhost:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"alice","password":"Password1"}'
```

### Accounts

| Method | Endpoint | Auth | Description |
|--------|----------|------|-------------|
| POST | `/accounts` | JWT | Open new account |
| GET | `/accounts` | JWT | List my accounts |
| GET | `/accounts/{number}` | JWT | Get account details |
| POST | `/admin/accounts/{number}/freeze` | ADMIN | Freeze account |
| POST | `/admin/accounts/{number}/activate` | ADMIN | Activate account |
| DELETE | `/admin/accounts/{number}` | ADMIN | Close account |

**Open an account:**
```bash
curl -X POST http://localhost:8080/api/v1/accounts \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"accountType":"CHECKING","currency":"USD","initialDeposit":"1000.00"}'
```

### Transactions

| Method | Endpoint | Auth | Description |
|--------|----------|------|-------------|
| POST | `/transactions/transfer` | JWT | Transfer between accounts |
| GET | `/transactions/{id}` | JWT | Get transaction detail |
| GET | `/accounts/{number}/transactions` | JWT | Paginated transaction list |
| GET | `/accounts/{number}/statement` | JWT | Paginated ledger entries |
| POST | `/admin/transactions/deposit` | ADMIN | Deposit funds |

**Transfer (with idempotency key):**
```bash
curl -X POST http://localhost:8080/api/v1/transactions/transfer \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "sourceAccountNumber": "ACC-000001",
    "destinationAccountNumber": "ACC-000002",
    "amount": "500.00",
    "currency": "USD",
    "description": "Rent payment",
    "idempotencyKey": "unique-client-uuid-here"
  }'
```

**Fee tiers** (applied automatically on every transfer):

| Amount | Rate |
|--------|------|
| < $1,000 | 1.50% |
| $1,000 – $9,999 | 1.00% |
| ≥ $10,000 | 0.50% |

### Scheduled Payments

| Method | Endpoint | Auth | Description |
|--------|----------|------|-------------|
| POST | `/scheduled-payments` | JWT | Create recurring payment |
| GET | `/scheduled-payments` | JWT | List my scheduled payments |
| DELETE | `/scheduled-payments/{id}` | JWT | Cancel scheduled payment |

**Create a monthly recurring payment:**
```bash
curl -X POST http://localhost:8080/api/v1/scheduled-payments \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "sourceAccountNumber": "ACC-000001",
    "destinationAccountNumber": "ACC-000002",
    "amount": "1200.00",
    "currency": "USD",
    "description": "Monthly rent",
    "cronExpression": "0 0 9 1 * ?"
  }'
```

Cron format: `seconds minutes hours dayOfMonth month dayOfWeek`

### Exchange Rates

| Method | Endpoint | Auth | Description |
|--------|----------|------|-------------|
| GET | `/exchange-rates/{from}/{to}` | JWT | Get current rate (cached 60 min) |
| DELETE | `/admin/exchange-rates/{from}/{to}/cache` | ADMIN | Evict cached rate |

### Users (Admin)

| Method | Endpoint | Auth | Description |
|--------|----------|------|-------------|
| GET | `/users/me` | JWT | Get own profile |
| GET | `/admin/users` | ADMIN | List all users |
| GET | `/admin/users/{id}` | ADMIN | Get user by ID |
| POST | `/admin/users/{id}/promote` | ADMIN | Promote to ADMIN |
| POST | `/admin/users/{id}/lock` | ADMIN | Lock user account |
| DELETE | `/admin/users/{id}` | ADMIN | Soft-delete user |

---

## Observability

### Health Check
```bash
curl http://localhost:8080/api/v1/actuator/health
```

### Custom Metrics
```bash
# Requires HTTP Basic: actuator_admin / ActuatorDev@55
curl -u actuator_admin:ActuatorDev@55 \
  http://localhost:8080/api/v1/actuator/metrics/banking.transactions.settled.total

curl -u actuator_admin:ActuatorDev@55 \
  http://localhost:8080/api/v1/actuator/metrics/banking.fees.collected.total
```

### Prometheus Scrape
```bash
curl -u actuator_admin:ActuatorDev@55 \
  http://localhost:8080/api/v1/actuator/prometheus
```

All HTTP requests are logged with:
- `requestId` — unique UUID per request (also returned as `X-Request-Id` response header)
- `userId` — authenticated username
- Method, URI, status code, duration

---

## Error Responses

All errors follow [RFC 7807 Problem Detail](https://www.rfc-editor.org/rfc/rfc7807):

```json
{
  "type": "https://banking-ledger.io/errors/insufficient-funds",
  "title": "Insufficient Funds",
  "status": 422,
  "detail": "Account ACC-000001 has insufficient funds. Available: 100.00, Required: 500.00.",
  "instance": "/api/v1/transactions/transfer",
  "timestamp": "2026-03-22T16:00:00Z",
  "requestId": "a1b2c3d4-..."
}
```

---

## Key Design Decisions

**Why BigDecimal everywhere?**
`double` cannot represent 0.1 exactly in binary floating-point. `0.1 + 0.2 == 0.30000000000000004` in Java. On financial amounts, this causes rounding errors that accumulate across millions of transactions. `BigDecimal` is exact. Every monetary field in the codebase uses `BigDecimal` with `RoundingMode.HALF_EVEN` (banker's rounding).

**Why immutable LedgerEntry?**
Banking regulations require a complete, unalterable audit trail. If a transaction was wrong, you create a REVERSAL with new entries in the opposite direction. The `@PreUpdate` hook on `LedgerEntry` throws `IllegalStateException` if Hibernate tries to issue an UPDATE — making mutation impossible at the application layer.

**Why distributed locks outside @Transactional?**
Spring `@Transactional` works through a proxy. Calling a `@Transactional` method from within the same class bypasses the proxy — the annotation is silently ignored. The distributed lock is acquired at the controller level, which then calls `ledgerService.transfer()` through the injected Spring proxy. This ensures: Redis lock held → DB transaction open → DB pessimistic lock held — all three guards active simultaneously.

**Why soft delete?**
No user or account row is ever physically deleted. Soft deletion sets `deleted = true` and `deleted_at = now()`. All repositories use `@SQLRestriction("deleted = false")` to filter deleted records automatically. This preserves audit trails and allows account recovery.

---

## Running Tests

```bash
mvn test
```

---

## License

MIT