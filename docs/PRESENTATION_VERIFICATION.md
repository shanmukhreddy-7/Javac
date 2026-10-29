# Presentation build verification

Verified locally on 29 October 2026 using Node.js 24.22.1.

- `npm test`: 18 tests passed across 3 test files.
- `npm run build`: TypeScript and Vite production build passed.
- Live HTTP smoke test against the running Vite server at `http://localhost:5173`: registered two fictional profiles with no passwords, OTP or verification, signed in using independent cookie sessions, sent a request, read it from the recipient account, accepted it, and confirmed the sender saw ACCEPTED. Disposable smoke records were removed afterward.
- Browser check: `/login` renders the email-only presentation form and the no-identity-verification notice. Screenshot: `screenshots/presentation-login.png`.
- API integration tests cover independent sessions, request visibility and responses, unrelated account restrictions, profile/skill validation, CSRF/logout, and Vercel's function rewrite route.

The live Vercel/Neon flow still requires connected shared storage and a deployed build. A successful local test does not prove cloud database connectivity. Local SQLite is not used as shared cloud storage.

Use fictional accounts. Presentation sign-in proves no university identity; anyone who knows a presentation email can sign in to that profile.
