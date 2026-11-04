# Secure Java build verification — 4 November 2026

## Completed

- Root `npm run build`: React TypeScript/Vite build and Maven executable JAR packaging passed.
- Root `npm test`: 12 frontend tests and 27 Java tests passed.
- Java tests include the previous authentication/profile coverage plus OTP expiry/cooldown, persisted failed-attempt limits, password reset session revocation, job messages/state rules, offline collection, reviews, admin suspension, two simultaneous acceptance attempts (one success, one conflict), and a real-cookie JDBC session flow through verification/login/logout.
- Actual local HTTP flow through localhost:5173 → Vite proxy → Java on 8080 → persistent H2: registered two fictional accounts, read their private development mailboxes, verified codes, logged in with passwords, posted/accepted/started/delivered/completed a food pickup, recorded ₹30 collected through PhonePe, and added feedback. No actual money moved or inbox email was sent.
- Browser: verified password login, updated navigation, completed job details/review and earnings showing earned ₹30, pending ₹0, collected ₹30. Screenshot: screenshots/secure-earnings.png.

## Email service confirmed

The user confirmed that an OTP email arrived in the designated university inbox and the local JAVAC account was verified. No password or OTP was shared in chat. Java uses a managed one-time sign-in code to prove email ownership, while retaining its own password hashes and application sessions.

## Cloud preview verified

- Commit `15e4b86d43962b0742cd5b67831ba621628e603e`, deployment `dpl_99evweD6ffsmkdKE14P5NxAZJUP9`: Container build READY in `sin1`, beside the Singapore Neon database. The earlier `iad1` startup timed out during migrations; the Singapore preview started and returned JSON successfully.
- Preview: https://javac-pwmwz3nla-shannus-projects-5c3ca9cf.vercel.app/. This preview is protected by Vercel authentication; authenticated CLI access does not prove public access.
- Hosted `/api/auth/config` and `/api/auth/csrf` returned 200. The session cookie is Secure, HttpOnly, SameSite=Lax. A fresh `/api/auth/me` returned 401; login without CSRF returned 403; login with a valid persisted CSRF session and a nonexistent account returned 401. `/dashboard` served the React application successfully.
- These requests exercise Java startup, Flyway/PostgreSQL, JDBC-backed sessions, CSRF, credential lookup, and SPA routing. No cloud student accounts were seeded, no email was sent by these checks, and no user passwords were accessed.
- Runtime error/fatal log scan for this deployment returned no matching records after the smoke checks.

## Still required before a live release

- Neon Auth email signup and verification codes are enabled; Vercel supplied NEON_AUTH_BASE_URL, and the service /ok endpoint returned HTTP 200. The user confirmed actual OTP inbox delivery and account verification for the designated admin on localhost. Configure the hosted frontend origin and repeat the full flow on the cloud preview before release.
- The user must verify the cloud signup, emailed OTP, and password login. Local H2 accounts are separate from cloud Neon accounts. Repeat the two-account marketplace flow on the cloud, then stage and verify a production build before promotion. The previous Vercel presentation deployment is not evidence the new Java build works online.
- Neon’s production checklist requires a custom email provider for full production use. University sender identity needs university authorization; shared Neon sender is suitable for the classroom setup subject to its limits.

Local test records are named Classroom Boy/Girl and are not real university accounts. They are only verification fixtures in the development database and must not be seeded into production.
