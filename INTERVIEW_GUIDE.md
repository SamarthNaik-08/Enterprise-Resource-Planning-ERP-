# 🎤 Interview Guide — ERP System

Everything you need to **present, defend, and upgrade** this project in interviews.
Read this before every interview. Honesty note: the project genre (ERP/CRUD) is common —
*how you explain it* is what separates you from other candidates.

---

## 1. The 60-second pitch (memorize this)

> "I built a production-style ERP covering the two classic business flows —
> **procure-to-pay** and **order-to-cash** — with Spring Boot 3, React and PostgreSQL.
> The backend enforces real business rules: receiving a purchase order adds stock in a
> single transaction, completing a sales order validates availability of *every* line
> before deducting anything, and completed orders are immutable for audit purposes.
> Security is stateless JWT with three roles enforced on both the API and the UI.
> The whole stack ships as one `docker compose up` command with seeded demo data."

Then let them pick where to go: security, transactions, architecture, or a live demo.

---

## 2. Questions interviewers WILL ask (with strong answers)

### Security / JWT
**Q. Why JWT instead of HTTP sessions?**
> The API is stateless and horizontally scalable — any instance can serve any request
> without a shared session store. The token carries the role claim, so no DB hit is
> needed for authorization on each request. Trade-off: tokens can't be revoked before
> expiry, which I'd solve with short-lived access tokens + refresh tokens (roadmap).

**Q. How does your Spring Security filter chain work?**
> Once per request, my `JwtAuthFilter` extracts the `Authorization: Bearer` header,
> validates the HMAC signature and expiry, loads the `UserDetails`, and populates the
> `SecurityContextHolder`. Rules are ordered: `/api/auth/**` is public, `/api/users/**`
> is ADMIN-only, GET endpoints allow all roles, writes require ADMIN/MANAGER.
> (Real story: I initially placed the GET rule *before* the users rule — Spring Security
> evaluates matchers in order, so employees could read the user list. My integration
> test caught it and I fixed the ordering. First-match-wins is a classic gotcha.)

**Q. Where is the password stored and how?**
> BCrypt-hashed via `PasswordEncoder`. BCrypt is deliberately slow and salted per-user,
> which defeats rainbow tables and brute force.

**Q. How would you implement logout?**
> JWT is stateless, so "logout" = client discards the token. For real revocation:
> a denylist in Redis keyed by token `jti` until expiry, or short-lived access tokens
> with a refresh-token store I can delete on logout.

### Transactions & business logic
**Q. What happens if two sales orders try to buy the last unit at the same moment?**
> Current implementation: both transactions read stock = 1, both pass validation,
> both deduct → oversell. That's a **lost-update race condition**.
> Fix options: (a) optimistic locking with `@Version` on Product — the second commit
> fails and I retry; (b) pessimistic `SELECT ... FOR UPDATE` on the product rows inside
> the transaction; (c) atomic conditional update
> `UPDATE products SET qty = qty - ? WHERE id = ? AND qty >= ?` and check rows affected.
> *(Say you'd pick (a) for low contention + retry, and mention (c) as the scalable option.)*

**Q. Why validate all line items before deducting any stock?**
> All-or-nothing semantics inside one `@Transactional` method. If item 3 of 5 is out of
> stock, the order fails cleanly with a 400 listing *every* insufficient item — instead
> of deducting some items and throwing halfway (which would corrupt inventory if the
> transaction weren't atomic).

**Q. Why `ddl-auto: update` is dangerous and what you'd use instead?**
> It can lose or silently alter columns in production and gives no history. Production
> uses **Flyway/Liquibase**: versioned, reviewable SQL migrations (`V1__init.sql`,
> `V2__add_payments.sql`) applied at startup — repeatable and audit-friendly.

### JPA / Hibernate / data
**Q. What is the N+1 problem and where could it hit this project?**
> Listing 100 orders would fire 1 query for orders + 100 for suppliers + 100 for items.
> My order listings use `JOIN FETCH` on supplier and items so it's a single query.
> The dashboard uses JPQL aggregations (`SUM`, `COUNT`) so totals are computed in the DB.

**Q. Why DTOs instead of exposing entities?**
> Entities have lazy relations and bidirectional references that would serialize as
> infinite recursion or trigger `LazyInitializationException` outside transactions.
> DTOs decouple the API contract from the schema — I can change the DB without breaking
> clients — and prevent mass-assignment (a client can't post `role: ADMIN` into an entity).

**Q. How does pagination work in your products endpoint?**
> Spring Data `Pageable` → SQL `LIMIT/OFFSET`, returning `Page<T>` with total count.
> The search is a case-insensitive `LIKE` across name/SKU/category; at scale I'd move
> that to a full-text index (tsvector in Postgres) and keyset pagination for deep pages.

### Architecture & general
**Q. Walk me through a request end-to-end.**
> React axios → JWT interceptor adds header → Vite/nginx proxies `/api` →
> `JwtAuthFilter` authenticates → Security chain authorizes → Controller validates the
> DTO (`@Valid`) → Service runs business logic in a transaction → Repository/JPA →
> PostgreSQL → DTO response → global exception handler converts any error to a
> consistent JSON contract (`{status, message, timestamp}`).

**Q. Why a `@RestControllerAdvice`?**
> One place maps exceptions to HTTP: `NotFound → 404`, business-rule violations → 400,
> `AccessDenied → 403`, validation → 400 with per-field errors. Controllers stay clean
> and the frontend always gets the same error shape to display.

**Q. How would you scale this to 10,000 concurrent users?**
> Stateless API → scale horizontally behind a load balancer; Postgres: read replicas +
> PgBouncer pooling; cache the dashboard in Redis with short TTL; move stock events to
> Kafka so inventory updates are async per warehouse; extract invoicing into its own
> service if write contention grows. (Show you know *when* — premature microservices
> are a red flag.)

**Q. What would you build next?** → point to the roadmap below, pick 1–2 you've thought about.

---

## 3. Live-demo script (5 minutes, practice it)

1. Login as **EMPLOYEE** → show "New Product" button is hidden
2. Open DevTools → paste the employee's JWT into a `POST /api/products` via curl/Postman → **403** (defense in depth: hiding UI ≠ security)
3. Login as **MANAGER** → create a sales order for more stock than available → try to complete → **400 Insufficient stock** with itemized message
4. Receive a purchase order → show the product's stock number increase
5. Show the dashboard KPIs update
6. (If asked) `docker compose up` on a prepared machine

---

## 4. Current gaps an expert WILL find (know them before they do)

| Gap | Risk | Status |
|---|---|---|
| ~~Stock race condition~~ | Two concurrent completes can oversell | ✅ **FIXED** — `@Version` optimistic locking → 409 |
| ~~`ddl-auto: update`~~ | No migration history | ✅ **FIXED** — Flyway V1/V2, Hibernate validates |
| ~~No automated tests~~ | Regressions | ✅ **FIXED** — 22 tests: unit + integration + Testcontainers |
| ~~No API docs~~ | Consumers read code | ✅ **FIXED** — Swagger UI at /swagger-ui.html |
| ~~No token refresh/revocation~~ | 24h tokens can't be invalidated | ✅ **FIXED** — 15-min access + rotating refresh tokens |
| Unbounded order list endpoint | Table scan on huge datasets | Pagination (already done for products) |
| Monolith by design | Fine to defend! "Right-sized for the domain" | Modules are already split by package |

> Everything in Tier 1 of the roadmap has been implemented — the interview story for
> each fix lives in `TIER1_EXPLAINED.md`. Remaining ideas are Tier 2/3: Redis caching,
> Envers audit trail, Kafka events, Grafana dashboards, Keycloak, Kubernetes.

---

## 5. Upgrade roadmap (impact vs effort)

### Tier 1 — do these first (7 → 9/10, ~1–2 weeks part-time)
1. **Tests**: JUnit 5 unit tests for services (Mockito) + integration tests with
   **Testcontainers** real Postgres — include the RBAC 403 test and the oversell test
2. **Flyway**: move schema to `V1__init.sql`, add demo data migration
3. **Optimistic locking**: `@Version` on `Product`, retry on `OptimisticLockException`
   → *this becomes your best interview story*
4. **Swagger UI**: `springdoc-openapi-starter-webmvc-ui`, one dependency, huge demo value
5. **Refresh tokens**: 15-min access token + 7-day refresh token, rotation on use

### Tier 2 — differentiators (~2–3 weeks)
6. **GitHub Actions CI**: build + test + docker build on every push (badge in README)
7. **Redis cache** for dashboard aggregates (talk about cache invalidation on order complete)
8. **Audit trail**: Spring Data Envers — every price/stock change versioned ("who changed what when")
9. **CSV/PDF export** of orders & inventory
10. **Low-stock email alerts** (Spring `@Scheduled` + SMTP, or SendGrid)

### Tier 3 — big-company signals (~3–6 weeks)
11. **Kafka**: publish `OrderCompleted` events; a consumer updates a read-optimized reporting DB
12. **Observability**: Micrometer + Prometheus + Grafana dashboard; trace IDs in logs
13. **Keycloak / OAuth2-OIDC** instead of hand-rolled auth (then you can compare both!)
14. **Kubernetes manifests** (Deployment, Service, Ingress, HPA) + Helm chart
15. **New module**: invoicing/payments or HR/payroll (proves the design extends)

---

## 6. Resume bullet points (copy-adapt these)

- Built a full-stack ERP (Spring Boot 3, React 18, PostgreSQL, Docker) implementing
  procure-to-pay and order-to-cash flows with transactional inventory guarantees
- Designed stateless JWT authentication with role-based access control (ADMIN/MANAGER/
  EMPLOYEE) enforced at API and UI layers; wrote integration tests covering a real
  authorization-bypass bug and its fix
- Achieved one-command deployment via Docker Compose with multi-stage builds and
  health-checked service orchestration; seeded demo environment for instant onboarding

*(After Tier 1, add: "…with 80%+ test coverage via Testcontainers, versioned Flyway
migrations, and optimistic locking to prevent inventory overselling under concurrency.")*

---

## 7. Final mindset

- **Never oversell.** "It's a monolith and that's the right call at this scale" is a
  *stronger* answer than pretending it's a distributed system.
- **Own your bugs.** The security-ordering bug and the oversell gap are assets —
  "found, fixed, tested" is the engineering maturity interviewers screen for.
- **Depth > breadth.** Two modules you can dissect beat ten you can only name.
