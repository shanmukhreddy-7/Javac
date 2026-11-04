# Secure Java build verification — 4 November 2026

## Completed

- Root `npm run build`: React TypeScript/Vite build and Maven executable JAR packaging passed.
- Root `npm test`: 12 frontend tests and 27 Java tests passed.
- Java tests include the previous authentication/profile coverage plus OTP expiry/cooldown, persisted failed-attempt limits, password reset session revocation, job messages/state rules, offline collection, reviews, admin suspension, two simultaneous acceptance attempts (one success, one conflict), and a real-cookie JDBC session flow through verification/login/logout.
- Actual local HTTP flow through localhost:5173 → Vite proxy → Java on 8080 → persistent H2: registered two fictional accounts, read their private development mailboxes, verified codes, logged in with passwords, posted/accepted/started/delivered/completed a food pickup, recorded ₹30 collected through PhonePe, and added feedback. No actual money moved or inbox email was sent.
- Browser: verified password login, updated navigation, completed job details/review and earnings showing earned ₹30, pending ₹0, collected ₹30. Screenshot: screenshots/secure-earnings.png.

## Email service confirmed

The user confirmed that an OTP email arrived in the designated university inbox and the local JAVAC account was verified. No password or OTP was shared in chat. Java uses a managed one-time sign-in code to prove email ownership, while retaining its own password hashes and application sessions.

## Still required before a live release

- Neon Auth email signup and verification codes are enabled; Vercel supplied NEON_AUTH_BASE_URL, and the service /ok endpoint returned HTTP 200. The user confirmed actual OTP inbox delivery and account verification for the designated admin on localhost. Configure the hosted frontend origin and repeat the full flow on the cloud preview before release.
- Build and test a Vercel container preview using the production PostgreSQL schema and shared JDBC sessions, then promote only after that flow passes. The previous Vercel presentation deployment is not evidence the new Java build works online.
- Neon’s production checklist requires a custom email provider for full production use. University sender identity needs university authorization; shared Neon sender is suitable for the classroom setup subject to its limits.

Local test records are named Classroom Boy/Girl and are not real university accounts. They are only verification fixtures in the development database and must not be seeded into production.
