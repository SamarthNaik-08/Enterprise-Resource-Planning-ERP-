# 🚀 Running the ERP System in Google Antigravity

Antigravity is Google's agentic IDE (a VS Code fork), so everything here works both
**manually (terminal / F5)** and **agentically (ask the Gemini agent to do it)**.

---

## 0. Get the code onto your machine

1. Download **`erp-system.zip`** (in this workspace, next to the `erp-system/` folder)
2. Extract it somewhere, e.g. `C:\dev\erp-system` or `~/dev/erp-system`
3. (Later) push it to GitHub so you can `git clone` anywhere.

## 1. Install Antigravity + prerequisites

| Tool | Version | Why | Check |
|---|---|---|---|
| [Antigravity](https://antigravity.google) | latest | the IDE | opens 🎉 |
| **JDK** | 17 (Temurin 17 recommended) | backend | `java -version` |
| **Node.js** | 18+ (20 LTS recommended) | frontend | `node -v` |
| **Docker Desktop** | optional | PostgreSQL / full compose | `docker --version` |

> With the Maven wrapper (`mvnw`) included in the repo you do **NOT** need to install Maven.

Open the project: **Antigravity → File → Open Folder → `erp-system`**.
If Antigravity recommends the *Extension Pack for Java* / *Spring Boot* extensions, accept.

## 2. Run it — Option A: manual terminals (fastest)

Open Antigravity's terminal (**Ctrl+`**) and split it in two:

**Terminal 1 — backend** (uses the in-memory H2 DB, zero setup):
```bash
cd backend
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev     # macOS / Linux
mvnw.cmd spring-boot:run -Dspring-boot.run.profiles=dev   # Windows PowerShell
```
Wait for `Started ErpApplication` — API on http://localhost:8080,
Swagger on http://localhost:8080/swagger-ui.html

**Terminal 2 — frontend:**
```bash
cd frontend
npm install
npm run dev
```
→ open **http://localhost:5173** and log in (`admin@erp.com` / `admin123`)

## 3. Run it — Option B: press F5 (debug mode)

The repo ships `.vscode/launch.json` (works in Antigravity like in VS Code):
- **"ERP Backend (dev/H2)"** — starts the backend with breakpoints support
- **"ERP Backend (PostgreSQL)"** — use after `docker compose up db`

Set a breakpoint in `SalesOrderService.complete()` in the editor, press **F5**,
then complete an order in the UI and watch it hit 🎯 — great for understanding
(and for demos).

## 4. Run it — Option C: let the Antigravity agent do it

Open the Agent panel (top-right agent icon / **Ctrl+L**) and paste:

> Open a terminal and start the Spring Boot backend: `cd backend` then
> `./mvnw spring-boot:run -Dspring-boot.run.profiles=dev` (use `mvnw.cmd` on Windows).
> Wait until the logs say "Started ErpApplication", then in a second terminal run
> `cd frontend && npm install && npm run dev`. Report both URLs when ready.

More prompts that work well:
- *"Run all backend tests with ./mvnw test and summarize failures if any"*
- *"Start postgres with docker compose up db -d, then run the backend against it"*
- *"Give me a walkthrough of the sales order flow from controller to repository"*
- *"Add rate limiting to the login endpoint"* (agentic coding at work!)

## 5. Full stack with PostgreSQL (production-like)

```bash
docker compose up --build
```
Frontend http://localhost:3000 · API http://localhost:8080 · Postgres :5432

## 6. Test-first sanity check (recommended on a new machine)

```bash
cd backend && ./mvnw test
```
→ expect **BUILD SUCCESS, 20 passed, 1 skipped** (the skip is the Testcontainers
PostgreSQL test — it runs automatically wherever Docker is available).

---

### Demo accounts (seeded automatically)

| Role | Email | Password |
|---|---|---|
| ADMIN | `admin@erp.com` | `admin123` |
| MANAGER | `manager@erp.com` | `manager123` |
| EMPLOYEE | `employee@erp.com` | `employee123` |
