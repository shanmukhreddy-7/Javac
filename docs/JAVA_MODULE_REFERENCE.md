# Archived Java module setup

This describes the earlier Java/MySQL implementation, not the current no-OTP presentation build.

# JAVAC — Job and Value Across Campus

A university-exclusive student services marketplace. **Module 1** implements registration, university email verification, session authentication, account recovery, student profiles, and skills. Marketplace jobs, payments, and notifications are explicitly marked **Coming soon**.

## What is included

- React 19 + TypeScript, Vite 7, Tailwind 4, React Router, Lucide, React Hook Form, Zod.
- Java **21** / Spring Boot 3.5.16, Security, JPA/Hibernate, Jakarta validation, Maven Wrapper.
- **MySQL 8+**, with Flyway schema and skill-directory migrations; no seeded real students.
- Seven requested pages, plus forgot/reset password and a 404 page.
- Official uploaded logo, preserved original PNG, optimized transparent-padding crop, favicon.
- Locally hosted **DM Sans** with its SIL Open Font License; no font CDN needed in class.
- Separate opt-in demo mode, responsive student workspace, searchable skills, loading/error/empty states.
- Backend integration tests and frontend interaction tests.

## Quick start in VS Code

For the already-configured Fedora installation, use [local Start/Stop instructions](docs/LOCAL_SETUP.md).

Install a **JDK 21** (not just a JRE), **Node 22.12+** (Node 24 LTS also works), and **MySQL 8+**. Maven is downloaded by the wrapper on first use; internet is needed for initial Maven/npm dependencies. Open this `javac-campus` folder in VS Code. Recommended extensions are in `.vscode/extensions.json`.

### 1. Configure the database

Create a MySQL database named `javac` with `utf8mb4`. Use a dedicated local account with privileges only on `javac.*`. For example, run in your MySQL administrative client, replacing the password:

```sql
CREATE DATABASE javac CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE USER 'javac'@'localhost' IDENTIFIED BY 'choose-your-own-local-password';
GRANT ALL PRIVILEGES ON javac.* TO 'javac'@'localhost';
```

Alternatively, if Docker Compose is installed, copy/configure `.env` as below, set `DB_ROOT_PASSWORD` as well, and run `docker compose up -d db`. The optional Compose file uses MySQL 8.4 and binds only to loopback. Never use the root database account for the application in production.

### 2. Configure the backend

From `javac-campus`, copy `.env.example` to `.env` (`cp .env.example .env`, or `Copy-Item .env.example .env` in PowerShell). Edit:

```properties
APP_APPROVED_EMAIL_DOMAINS=your-verified-university-domain.edu
DB_URL=jdbc:mysql://localhost:3306/javac?serverTimezone=UTC
DB_USER=javac
DB_PASSWORD=your-local-database-password
SPRING_PROFILES_ACTIVE=dev
APP_FRONTEND_URL=http://localhost:5173
APP_ALLOWED_ORIGINS=http://localhost:5173
COOKIE_SECURE=false
COOKIE_SAME_SITE=lax
```

The institution's actual email domain is **not assumed**. Ask its administrator before configuring it. Multiple exact domains are comma-separated; subdomains are not implicitly trusted. `example.edu` in the example is fictional and only suitable for local testing. With no allowlist, registration returns a useful configuration error. The frontend fetches the allowlist from Java; the server always enforces it.

Spring imports the root `.env` when launched from `backend/`. This is a Java properties file: use plain values without surrounding shell quotes. Environment variables override this file. Keep the file ignored.

Terminal 1:

```bash
cd backend
./mvnw spring-boot:run
```

PowerShell / Command Prompt: `cd backend`, then `.\mvnw.cmd spring-boot:run`.

The backend listens at `http://localhost:8080`. Flyway runs automatically; Hibernate validates rather than recreating the schema.

### 3. Configure and start the frontend

In a second VS Code terminal, from `javac-campus`:

```bash
cd frontend
cp .env.example .env.local
npm install
npm run dev
```

PowerShell copy: `Copy-Item .env.example .env.local`.

`frontend/.env.local`:

```properties
VITE_API_BASE_URL=http://localhost:8080
VITE_DEMO_MODE=true
```

Open `http://localhost:5173`. Use the same hostname consistently: `localhost` and `127.0.0.1` are different cookie/origin hosts. If you use another origin, update both `APP_FRONTEND_URL` and `APP_ALLOWED_ORIGINS`. Restart Vite after environment changes. Port 5173 is strict to prevent silent origin changes.

## Classroom walkthrough

1. Open the homepage and introduce **Job and Value Across Campus**.
2. Register using an address on your configured domain, a unique student ID, and a strong password. The account is **pending**; login is blocked.
3. With `SPRING_PROFILES_ACTIVE=dev`, open the newest file in `backend/dev-mailbox/` in a browser. This **development-only mailbox** contains the verification link. Click it; links expire in 30 minutes and can be used once. No token is returned by an API or printed to logs. If you paste the link, include its `#token=...` fragment.
4. Log in and refresh the dashboard to demonstrate the server session.
5. Edit your bio, department, year, and preferred location. Save and refresh to show MySQL persistence. Email and registration ID are locked. Initials provide the avatar.
6. Search/filter the skill directory, add/remove skills, and **Save my skills**. Show the completion indicator changing based on actual profile fields.
7. Open Settings; demonstrate password validation and logout. A changed/reset password invalidates existing sessions.
8. Explain the Java classes using [OOP explanation](docs/OOP_EXPLANATION.md) and the MySQL relationships using [architecture](docs/ARCHITECTURE.md).
9. For a frontend-only presentation, enable `VITE_DEMO_MODE=true` and choose **Explore demo** at the bottom of the homepage. The orange banner identifies fictional data. Demo edits last while navigating the current visit; reload resets the sample profile. Only a demo-entry flag goes into sessionStorage. No password is collected and no verification/authentication is simulated.

Local mailbox files are sensitive: keep the directory private and delete the files after class. Production uses SMTP and must never enable `dev`.

## Tests and builds

```bash
cd backend
./mvnw test
./mvnw package
```

The default tests use **H2 in MySQL compatibility mode only for tests**, run the real Flyway migrations, and mock only email delivery. The runtime application always uses MySQL. To run the same integration suite against a separate, disposable MySQL test database:

```bash
./mvnw test -Dspring.datasource.url='jdbc:mysql://localhost:3306/javac_test?serverTimezone=UTC' -Dspring.datasource.driver-class-name=com.mysql.cj.jdbc.Driver -Dspring.datasource.username=javac_test
```

Set the test password via `SPRING_DATASOURCE_PASSWORD` in your terminal environment or an ignored local Maven settings/profile; do not put it in shell history. Create the empty `javac_test` database and grant its dedicated test user privileges first. Do not point tests at production: Flyway creates tables and tests write temporary records. Test transactions roll back the student data.

```bash
cd frontend
npm test
npm run build
npm run preview
```

See [verification results](docs/VERIFICATION.md) for checks actually executed, commands, and limits. Frontend tests cover validation, login navigation, protected routing, profile editing, skill saving, and explicit demo behavior. Backend tests cover registration, duplicates, domains, weak passwords, email verification/expiry/replay, login, profile ownership, CSRF, skills, resend cooldown, reset, and logout.

## Deploy the frontend to Vercel

Import this repository. If its root is this folder, use **Root Directory: `frontend`**. If the Git repository contains the outer `JAVAC` workspace, use **`javac-campus/frontend`** instead.

| Setting | Value |
| --- | --- |
| Framework preset | Vite |
| Root directory | frontend |
| Build command | npm run build |
| Output directory | dist |
| Node version | 24.x (or supported 22.x >=22.12) |

Set `VITE_API_BASE_URL=https://your-java-backend.example` and optionally `VITE_DEMO_MODE=true` in the Vercel dashboard **before building**. With no backend deployed, leave the API URL blank and use the explicitly labeled demo. Real authentication will show an honest configuration/unavailable message; it never silently falls back to demo. Vite env values are public build-time settings. **Never put DB passwords, SMTP credentials, or Java secrets in `VITE_*` variables.** `frontend/vercel.json` provides SPA route fallback and security headers.

The Java backend must be deployed **independently**, as a JVM process/container on Java-compatible hosting; standard Vercel Node functions cannot run it. Build with `./mvnw package`, then `java -jar target/campus-1.0.0.jar`. A multi-stage backend Dockerfile is provided as an alternative.

For production backend configuration:

- `SPRING_PROFILES_ACTIVE=prod` (never `dev,prod`).
- Set MySQL connection, dedicated DB credentials, and real SMTP host/port/from/user/password. Use `MAIL_AUTH=true`, `MAIL_TLS=true` for STARTTLS providers; use the appropriate TLS settings for your provider.
- Set `APP_FRONTEND_URL=https://your-frontend-domain`, `APP_ALLOWED_ORIGINS=https://your-frontend-domain` (exact origin, no trailing slash).
- Set `COOKIE_SECURE=true`. For cross-site frontend/backend deployments use `COOKIE_SAME_SITE=none`; the session remains HTTP-only. Prefer `app.example.edu` and `api.example.edu` under the same site to avoid third-party-cookie blocking. `SameSite=None` alone cannot override browser privacy restrictions.
- Set a verified `APP_APPROVED_EMAIL_DOMAINS` allowlist.
- Serve both over HTTPS. Do not expose database ports publicly. The `prod` profile fails early for missing core security settings.

The simple in-memory session store and rate limiter target one backend instance; sessions expire after 30 minutes idle and on backend restart. Add a shared Spring Session store and distributed limiter before horizontal scaling. Forwarded client-IP headers are deliberately not trusted by the local limiter; configure rate limits at a trusted reverse proxy for production traffic. SMTP delivery runs synchronously for clarity; durable email retries/outbox are a later operational improvement.

## Project guide

```text
frontend/src/components/    Shared forms, navigation, feedback, and UI
frontend/src/pages/         Landing, auth, dashboard, profile, settings
frontend/src/contexts/      Real-session and explicit-demo state
frontend/src/services/      Typed API client, validation, demo sample
frontend/src/styles/        Centralized exact brand tokens and responsive styling
backend/.../auth/           Registration, verification, recovery, email delivery
backend/.../user/           Account entity/repository
backend/.../profile/        Student entity, DTOs, service, controller
backend/.../skill/          Skill directory and many-to-many entity
backend/.../security/       Principal, session checks, rate limiting
backend/.../config/         CORS, CSRF, session security, prod validation
backend/.../common/         Errors and reusable timestamp lifecycle
backend/src/main/resources/db/migration/   MySQL schema and skills
```

More detail: [API reference](docs/API.md), [architecture](docs/ARCHITECTURE.md), [OOP explanation](docs/OOP_EXPLANATION.md).

## Troubleshooting

- **`release version 21 not supported` / `javac` missing:** install a full JDK 21 and point `JAVA_HOME` to it. Check `java -version`, `javac -version`, and `./mvnw -v`. A system Java runtime alone is insufficient.
- **Maven Wrapper download fails:** check internet/proxy settings and Maven Central access. Do not disable TLS verification. On Unix, `chmod +x mvnw` if Git did not preserve executable permissions.
- **MySQL connection refused / access denied:** start MySQL, create the database, check port/user/grants, and confirm `.env` is at this project's root. Start the backend from `backend/`.
- **Domain rejected:** correct `APP_APPROVED_EMAIL_DOMAINS` and restart Java. Domains must match exactly; no unverified SRM domain is assumed.
- **No real email arrives in class:** `dev` writes HTML into `backend/dev-mailbox/`; it does not send email. Without `dev`, configure SMTP or use a local SMTP mailbox such as Mailpit.
- **Expired email link:** request another after 60 seconds. Resend returns the same generic response for missing, already-verified, and cooling-down accounts.
- **403 after login / cookies missing:** check exact CORS origin, hostname consistency, HTTP vs secure cookies, browser cookie policies, and fetch credentials. Every write must obtain `/api/auth/csrf` and send its header. The API client handles this automatically.
- **Backend unavailable:** start Java/MySQL or deliberately enter the separate demo from the homepage.
- **Frontend deep links return 404 on Vercel:** ensure the Vercel project root contains the included `vercel.json`.
- **Profile edits disappear in demo:** expected on reload; demo state is temporary. Real edits persist in MySQL.

No paid APIs or hosted authentication services are required. Initials are the intentional avatar fallback; file uploads and avatar storage are outside this module.
