# JAVAC — Job and Value Across Campus

A polished campus services presentation with **no OTP, no email verification, and no separate Java/MySQL server to run**. Create an account, sign in by email, edit a profile, choose skills, and send requests to another account.

This is explicitly a **classroom presentation**: email-only sign-in does not establish identity. Use fictional details. No passwords are requested or stored. Do not treat it as a production identity or private messaging system.

## Run in VS Code

Requires **Node.js 24.x** with npm. Open this `javac-campus` folder in VS Code.

```bash
npm install
npm run dev
```

Open **http://localhost:5173/**. Leave the terminal running. Stop with **Ctrl+C**.

`npm install` at this root installs the frontend dependencies. The application and its small API run together in Vite. Local data is saved in the ignored `frontend/.local-data/campus.sqlite` file; Java, Maven, MySQL, Docker, SMTP and paid APIs are not needed for this build.

Alternatively:

```bash
cd frontend
npm ci
npm run dev
```

### This already-configured Linux machine

The app can also run as the existing user service. Choose **Terminal → Run Task → JAVAC: Start local services (Linux)**, or run:

```bash
bash scripts/local.sh start
bash scripts/local.sh status
bash scripts/local.sh stop
```

The launcher now starts **only the frontend and its integrated API**. It does not start Java or MySQL. Stop that service before starting `npm run dev` manually on the same port. Services are not enabled automatically at boot.

## Try the full flow

1. Create a fictional account, e.g. `alex-demo@srmap.edu.in`, with a unique registration number. No password or verification step appears.
2. Log in immediately using that email.
3. Edit the profile and add/remove skills; save them. Refresh to check persistence.
4. Open a **different browser or an incognito window** and register another account, e.g. `taylor-demo@srmap.edu.in`. Different tabs in the same browser share a sign-in cookie, so they are not independent users.
5. In Alex's account open **Requests**, choose Taylor, and send a request.
6. In Taylor's account open **Requests → Inbox**. The request appears automatically within 5 seconds while the page is visible; **Refresh** also updates it immediately.
7. Accept or decline it. Alex sees that status in **Sent** after the next refresh/poll.
8. Log out. Records remain in the shared data store.

The student directory initially has no invented students. Create the second account to see another recipient. **Explore demo** opens a separate offline sample: it demonstrates profile editing, but shared requests need the normal presentation accounts.

## GitHub

Repository: https://github.com/shanmukhreddy-7/Javac

The `.gitignore` excludes local environment files, database files, session secrets, dependencies, build output and machine-specific VS Code settings. The old Java implementation is retained in `backend/` for reference, but is **not used or deployed by this build**. The earlier setup guide is archived in `docs/JAVA_MODULE_REFERENCE.md`.

## Deploy to Vercel — with real cross-account sharing

Import the GitHub repository and use:

| Setting | Value |
| --- | --- |
| Framework | Vite |
| Root directory | `frontend` |
| Node version | `24.x` |
| Build command | `npm run build` |
| Output directory | `dist` |

The `frontend/api/campus.ts` function deploys **with** the frontend. It is not a separately hosted Java backend. The configuration routes `/api/*` to that function and frontend deep links to the SPA.

**Shared storage is required for requests to synchronize across browsers/devices.** In the Vercel project, open **Storage → Create/Connect Database → Neon Postgres** and select a suitable free plan. Connect the database to the project for Production and Preview, then redeploy. Never enable a paid plan without reviewing its terms yourself.

Set server-only environment variables:

```properties
DATABASE_URL=<the real Neon connection string supplied by the integration>
APP_APPROVED_EMAIL_DOMAINS=srmap.edu.in
```

Optionally set `JAVAC_SESSION_SECRET` to a separate random string of at least 32 characters. If omitted, the application derives its signing key from the private database connection string. Never put these server-only values in a `VITE_*` variable or GitHub file.

Frontend settings (defaults already match today's build):

```properties
VITE_API_BASE_URL=
VITE_PRESENTATION_MODE=true
VITE_DEMO_MODE=true
```

Keep `VITE_API_BASE_URL` **blank**. This makes API calls use the same Vercel origin. A `localhost` backend URL in a deployed site would incorrectly refer to each visitor's computer.

Tables and indexes are created automatically when the connected database is first used. All deployed instances use the same `DATABASE_URL`, which is how requests appear in the other account. A Vercel instance's memory or temporary filesystem is not used as shared storage. If storage is missing, the app displays a clear setup error; it never pretends requests were delivered.

For a strict data separation between preview and production, connect separate Neon branches/databases for those environments. The same URL deliberately shares records between environments.

## Build and test

```bash
npm test
npm run build
```

Tests cover the no-password/no-verification screens, immediate sign-in, profile and skill validation, cookies/CSRF, cross-account request delivery and status propagation, and attempts to respond to another account's request. The server tests run against isolated SQLite databases and independent HTTP sessions.

## Project structure

- `frontend/src/` — React, TypeScript, Tailwind, React Router, Lucide, React Hook Form and Zod.
- `frontend/server/handler.ts` — the shared no-OTP presentation API.
- `frontend/server/store.ts` — SQLite locally; Neon Postgres on Vercel.
- `frontend/api/campus.ts` — Vercel function entry point.
- `frontend/vercel.json` — API routing and SPA fallback.
- `backend/` — earlier Java module, preserved for reference and not required for this deployment.

Read `docs/PRESENTATION_VERIFICATION.md` for checks actually executed and deployment limitations.
