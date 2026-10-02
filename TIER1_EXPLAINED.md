# Tier 1 Upgrades — Explained (Study Notes)

Understand each upgrade **before** implementing it. Each section: what it is →
the problem in *this* project → how it works → the interview angle.

---

## 1. Automated Testing — JUnit 5 + Mockito + Testcontainers

**What:** Code that tests your code automatically.
- **JUnit 5** — test framework (`@Test`, assertions)
- **Mockito** — fake dependencies for fast unit tests (no DB)
- **Testcontainers** — real PostgreSQL in Docker for integration tests

**Problem it solves here:** the app was verified once by manual API calls; any future
change means re-testing everything by hand. Tests also *document* business rules.

**Unit test example (service level):**
```java
@Test
void complete_shouldReject_whenStockInsufficient() {
    Product kb = Product.builder().name("Keyboard").quantityInStock(8).build();
    SalesOrder order = draftOrderWith(item(kb, 999));
    when(salesOrderRepository.findWithItemsById(1L)).thenReturn(Optional.of(order));

    assertThatThrownBy(() -> service.complete(1L))
        .isInstanceOf(BadRequestException.class)
        .hasMessageContaining("Insufficient stock");
}
```

**Integration test example (HTTP + security + DB):**
```java
@Test
void employeeCannotCreateProduct() {
    given().header(authAs("employee@erp.com")).body(newProductJson())
        .when().post("/api/products")
        .then().statusCode(403);
}
```

**Why Testcontainers over H2?** H2's SQL dialect differs from PostgreSQL → tests can
pass while prod breaks. Testcontainers runs the *real* Postgres 16 per test run.

**Planned tests (~15):** auth (login/bad-password/register/duplicate) · RBAC 403 matrix
(would have caught our /users bug) · product CRUD/SKU-duplicate/search · order lifecycle
(PO receive ↑stock, SO complete ↓stock, oversell → 400, completed = immutable) · validation.

**Interview terms:** test pyramid, Arrange-Act-Assert, mocking, regression,
coverage. Killer line: *"a test caught an authorization bypass in my own app."*

---

## 2. Flyway — Database Migrations

**What:** version control for the DB schema. Replaces `ddl-auto: update`
(Hibernate *guessing* SQL from entities — no history, no review, risky alters).

**How:** hand-written, numbered SQL files:
```
db/migration/V1__init_schema.sql
db/migration/V2__seed_demo_data.sql
db/migration/V3__add_invoices.sql
```
On startup Flyway applies unapplied versions in order (in a transaction) and records
them + checksums in `flyway_schema_history`. Editing an applied file → loud failure.
`ddl-auto` switches to `validate` (check, never change).

**Interview question it answers:** "How do you manage schema changes in production?"
Mention Flyway (SQL-first) vs Liquibase (XML/YAML).

---

## 3. Optimistic Locking — `@Version`  ⭐ (best interview story)

**The bug we have TODAY (lost update / race condition):** stock = 10, two concurrent
completes for 10 units each:
```
A: read 10 → B: read 10 → A: write 0, commit → B: write 0, commit  ← A's write lost
```
20 units sold; validate-then-deduct is only safe if the row is untouched between
read and write — which we cannot guarantee without locking.

**The fix (one field):**
```java
@Version
private Long version;   // Hibernate-managed compare-and-swap
```
Every UPDATE becomes: `UPDATE products SET qty=?, version=? WHERE id=? AND version=?`.
Second writer matches 0 rows → `OptimisticLockException` → rollback → "retry" to user.
Add automatic retry (reload + revalidate, 1–2 attempts).

**Optimistic vs pessimistic:** optimistic = assume conflicts are rare, detect at write,
no read locks (web apps, low contention). Pessimistic = `SELECT ... FOR UPDATE`,
blocks readers (hot rows, constant contention).

**Interview line:** *"I found an oversell race condition in my own order flow,
reproduced it in a multi-threaded test, and fixed it with JPA optimistic locking."*

---

## 4. Swagger UI — springdoc-openapi

**What:** auto-generated, interactive API docs. One dependency, ~zero code.

**How:** `springdoc-openapi-starter-webmvc-ui` scans controllers + DTO validation
annotations and serves:
- `/swagger-ui.html` — every endpoint, schemas, "Try it out" with JWT Authorize button
- `/v3/api-docs` — OpenAPI 3 JSON spec (client SDK generation)

Optional enrichment: `@Tag`, `@Operation`, `@ApiResponse` per controller.

**Talking point:** code-first (springdoc) vs contract-first (design spec first).
Demo value: interviewer can *see and poke* the API in 10 seconds.

---

## 5. Refresh Tokens

**Problem with a single 24h JWT:** can't revoke it (stateless), "logout" is cosmetic,
and shortening lifetime wrecks UX.

**Design:** two tokens.
| | Access | Refresh |
|---|---|---|
| Lifetime | 15 min | 7 days |
| Used for | API calls | `POST /auth/refresh` |
| Server state | none | DB row → revocable |

**Flow:** login → both tokens; access expires → 401 → interceptor calls /auth/refresh →
new access (+ rotated refresh, old invalidated). Logout → delete DB record → dead forever.
Rotation also detects theft: reused refresh token ⇒ revoke the whole family.

**Implementation:** `refresh_tokens` table (user_id, token hash, expiry, revoked flag),
`/auth/refresh` + `/auth/logout` endpoints, axios retry-once interceptor.

**Interview line:** answers "How does logout work with JWT?" — the #1 JWT follow-up.
Bonus: localStorage vs httpOnly cookie trade-off (XSS).

---

## Combined resume line (after implementation)

> "Hardened a Spring Boot ERP with automated Testcontainers tests on real PostgreSQL,
> versioned Flyway migrations, optimistic locking that eliminated an oversell race
> condition, live OpenAPI docs, and short-lived JWTs with refresh-token rotation."
