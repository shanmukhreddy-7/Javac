# JAVAC — Job and Value Across Campus

A university campus marketplace with a **Java 21 / Spring Boot backend**, **React + TypeScript frontend**, email OTP verification, jobs, messages, notifications, reviews, admin moderation and offline payment records.

## Run in VS Code

Open this `javac-campus` folder. Install Java 21 and Node.js 24. The Maven wrapper is included.

Run these commands individually in VS Code's terminal:

```bash
npm install
```

```bash
npm run dev
```

Open **http://localhost:5173/**. Keep that terminal open. Ctrl+C stops both Java and React. If you already have an earlier JAVAC service running, stop it first with `bash scripts/local.sh stop`.

Local development needs no database installation: Java uses persistent **H2** in `backend/.local-data/`. It replaces SQLite for this Java build. Your old presentation records remain untouched and are not treated as verified accounts.

## Verify a local account

Register with an `@srmap.edu.in` address and a password, then open the newest HTML file in **backend/dev-mailbox/**. Enter its six-digit code on the verification page, then log in. This private mailbox is a development substitute for email delivery; it does not send to a real inbox. Codes expire after five minutes, allow five guesses, are stored hashed, and have a 60-second resend cooldown. Never publish mailbox files.

The administrator is **shanmukha_thiyyagura@srmap.edu.in**, after email verification and password login. There is no pre-created admin password or public role-assignment endpoint. On a development machine with the private mailbox, verification only simulates email ownership; do not expose the dev profile publicly.

## Features

- University allowlist, password hashing, OTP signup, OTP password recovery, logout and session revocation.
- Editable student profiles and skills; public student feedback without private email exposure.
- Post, search and filter jobs by category; pickup/destination, deadline and agreed reward.
- Job lifecycle: Open → Accepted → In progress → Delivered → Completed. Only the requester confirms completion. Cancellation is available before delivery.
- Atomic acceptance prevents two helpers from claiming the same job.
- Participant-only messages and notifications, refreshed every five seconds while visible.
- Earned, awaiting collection and collected totals. Helpers confirm payments received through cash, PhonePe or Paytm outside JAVAC. No money is transferred; collection is self-reported.
- One review per participant after completion; reports and admin account suspension/listing moderation.

## Folder guide

```text
javac-campus/
  backend/                       Java application and Java tests
    src/main/java/com/javac/campus/
      main/JavacApplication.java  Java main() entry point
      auth/                      Passwords, OTPs and email delivery
      marketplace/               Job, message, earnings and moderation rules
      profile/ skill/ user/      Student data and services
      config/ security/ common/  Runtime configuration and protections
  frontend/
    src/main/main.tsx            React entry point
    src/App.tsx                 Page routing
    src/marketplace/            Marketplace pages, types and data loading
    src/contexts/              Student session state
    src/services/              Java API client and validation
  database/migrations/          Single source of SQL migrations (packaged by Maven)
  scripts/                      Start, build and test commands
  docs/                         Setup, architecture, OOP and verification notes
  archive/                      Earlier presentation API and MySQL reference configuration
  Dockerfile                    Combined Java + React production image
```

Java's main folder starts the backend. React's main folder mounts the browser application; it is not a Java main method. Business logic remains in services, not in the entry point.

## Build and test

```bash
npm test
```

```bash
npm run build
```

The build produces `backend/target/campus-1.0.0.jar`, containing the compiled frontend. You can also run it with `java -jar backend/target/campus-1.0.0.jar` and open http://localhost:8080/ (stop the dev backend first).

Separate terminals are supported:

```bash
npm run dev:backend
```

```bash
npm run dev:frontend
```

On Windows, these npm commands use `mvnw.cmd`; Java 21 must be on PATH.

## Real OTP email through Neon Auth

The database itself does not deliver emails. Enable **Neon Auth / Managed Better Auth**, Email signup and **Verify at Sign-up → Verification code** in your Neon project. Set `NEON_AUTH_URL` to the displayed Auth URL, including `/auth`. Add the exact frontend origin under trusted domains and set the application name to JAVAC. This build uses Neon only to deliver/validate email ownership codes; Java hashes and verifies the application's passwords and manages sessions.

Neon Auth supplies a one-time sign-in code as proof of mailbox ownership. Java validates the returned verified email, then activates the JAVAC account or authorizes a password reset. This works for new and existing Neon identities without sharing JAVAC passwords. Provider session cookies are not forwarded to the browser; Java manages its own application session.

Neon's shared sender can be used for development/classroom codes. Its production checklist requires a custom email provider before a full production launch. Sending from the university address additionally needs university-authorized SMTP; knowing the address is not permission to send as it. Alternatively, configure real SMTP directly in production. See [Neon email verification](https://neon.com/docs/auth/guides/email-verification) and [production requirements](https://neon.com/docs/auth/production-checklist).

Copy `.env.example` to ignored `.env.backend` only if overriding local defaults. No secrets belong in `VITE_*` variables or GitHub.

## Deployment

The earlier website at https://javac-iota.vercel.app/ is the presentation build until the secure Java deployment is explicitly verified and promoted. Do not assume new features are live simply because they work locally.

The root Dockerfile builds React and Java together. Vercel's Container runtime is a deployment option; use repository root, framework **Container**, not the earlier `frontend`/Vite settings. The Java server serves both HTML and `/api` on one origin. Use a preview first.

Set the function region to **Singapore (`sin1`)**, beside this project's Neon database. The Singapore preview passed hosted API/session checks; the earlier US-region startup exceeded the startup deadline while running database migrations. Local accounts use H2 and do not automatically exist in cloud Neon storage.

Required production environment:

```properties
SPRING_PROFILES_ACTIVE=prod
DATABASE_URL_UNPOOLED=<private Neon direct connection string supplied by integration>
DB_SCHEMA=javac_secure
APP_ADMIN_EMAIL=shanmukha_thiyyagura@srmap.edu.in
APP_APPROVED_EMAIL_DOMAINS=srmap.edu.in
APP_FRONTEND_URL=https://your-exact-site.vercel.app
APP_ALLOWED_ORIGINS=https://your-exact-site.vercel.app
COOKIE_SECURE=true
PORT=8080
NEON_AUTH_URL=<real enabled Neon Auth URL ending in /auth>
```

Alternatively supply `DATABASE_URL`; the direct/unpooled URL is preferred for Java's bounded Hikari connection pool. TLS certificate/hostname verification is enabled. Flyway creates the isolated `javac_secure` schema and applies migrations; the previous presentation tables remain untouched. Production sessions use PostgreSQL via Spring Session JDBC so separate instances share login state. Production must never use H2 or a dev mailbox.

The optional `frontend/api/campus.ts` is only a same-origin proxy if hosting Java separately. It uses server-only `JAVA_BACKEND_URL`; business logic and account verification remain in Java. It is not needed for the combined container.

See docs/VERIFICATION.md for actual checks and outstanding deployment/email setup.
