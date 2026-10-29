# Presentation build verification

Verified locally on 29 October 2026 using Node.js 24.22.1.

- `npm test`: 18 tests passed across 3 test files.
- `npm run build`: TypeScript and Vite production build passed.
- Live HTTP smoke test against the running Vite server at `http://localhost:5173`: registered two fictional profiles with no passwords, OTP or verification, signed in using independent cookie sessions, sent a request, read it from the recipient account, accepted it, and confirmed the sender saw ACCEPTED. Disposable smoke records were removed afterward.
- Browser check: `/login` renders the email-only presentation form and the no-identity-verification notice. Screenshot: `screenshots/presentation-login.png`.
- API integration tests cover independent sessions, request visibility and responses, unrelated account restrictions, profile/skill validation, CSRF/logout, and Vercel's function rewrite route.

## Verified cloud deployment

- Production URL: https://javac-iota.vercel.app/
- Vercel project: `javac`, team `shannus-projects-5c3ca9cf`, GitHub source `shanmukhreddy-7/Javac`, root `frontend`, Node 24.
- Free Neon Postgres resource `javac-campus`, Singapore region, connected via server-only environment variables. No separate Java/MySQL host is needed.
- Production source commit `b38d350c43e134caa3d05210b7f0fe82c9e5a433` passed build and reached READY.
- Actual HTTPS smoke test against the production URL registered two fictional accounts, signed in through separate cookie sessions, sent a request, read it from the other account, accepted it, and confirmed ACCEPTED in the sender account. This exercised the deployed function and actual Neon database.
- Production configuration endpoint returned HTTP 200 with `srmap.edu.in`, presentation mode enabled and verification disabled.
- Local SQLite is not used as cloud storage. Local and deployed records are separate.

The original deployment revealed Node ESM extension resolution differences. Explicit runtime import extensions and JSON import attributes fixed that failure.

Use fictional accounts. Presentation sign-in proves no university identity; anyone who knows a presentation email can sign in to that profile.
