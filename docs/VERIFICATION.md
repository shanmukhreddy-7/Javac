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

### Browser-origin regression

The user reported an unreadable response on hosted login. Replaying the POST with the browser's exact preview `Origin` reproduced HTTP 403 with plain-text `Invalid CORS request`. The baseline CLI smoke omitted `Origin`, so it did not detect this failure. The container now enables `SERVER_FORWARD_HEADERS_STRATEGY=framework` to preserve the public HTTPS host/scheme through Vercel's proxy. Two new real-HTTP tests passed: the preview-origin POST with a valid cookie/CSRF token reaches authentication (401 for an intentionally nonexistent account), and an unrelated origin remains rejected (403). Retest the deployed browser-origin request before asking the user to log in again.

The corrected commit `8e9b9552876b2fda717b8b3cc96fc7a36868b6e5` was deployed as `dpl_BmUccEzno3b3Z7yRAxYrUgED8xfW`. Hosted requests with the exact preview Origin passed the authentication/CSRF tests, and an unrelated Origin remained blocked. The user then reported a registration error. A focused provider request reproduced `403 INVALID_ORIGIN` from Neon Auth when Java's configured frontend origin was supplied. The administrator must add `https://javac-iota.vercel.app` to Neon Auth's trusted domains. Localhost permission alone does not authorize it. No OTP was sent by that rejected provider request, and registration remains uncommitted. Provider setup and code-delivery failures now have separate messages from invalid student codes.

## Cloud email and login confirmed

The administrator saved `https://javac-iota.vercel.app` in Neon Auth's trusted domains. A subsequent email-code request returned HTTP 200 with success, and the user confirmed successful cloud registration/verification/login. No password or code was shared for this verification. The release uses the same Neon database/schema as the verified preview, so cloud accounts are retained.

## Release checks and remaining limits

- The latest Java suite passed 29 tests, including both real-HTTP proxy-origin regression tests; frontend coverage remains 12 passing tests. Full local two-account marketplace checks are described above. A manual two-account marketplace walkthrough has not yet been repeated in cloud storage.
- Before assigning the stable domain, verify the production build's API configuration, cookie/CSRF round trip, authentication rejection paths, and frontend routes. Then verify the stable public URL without a Vercel protection bypass.
- Neon’s production checklist requires a custom email provider for full production use. University sender identity needs university authorization; shared Neon sender is suitable for the classroom setup subject to its limits.

Local test records are named Classroom Boy/Girl and are not real university accounts. They are only verification fixtures in the development database and must not be seeded into production.
