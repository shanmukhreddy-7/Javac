# JAVAC code walkthrough

This guide explains where the running application starts, what each folder owns, and how a request reaches the database. Source comments describe the less obvious rules beside the code that enforces them.

For database code, use the [database study guide](../database/STUDY_GUIDE.md) and [annotated SQL copies](../database/explained/README.md). Java class, member, and method comments explain the code in place, including the test helpers and scenarios.

## Start reading here

1. `backend/src/main/java/com/javac/campus/main/JavacApplication.java` starts Spring Boot. Its scanning annotations include the sibling Java packages.
2. `frontend/src/main/main.tsx` mounts React and loads the shared stylesheet. `frontend/src/App.tsx` defines browser routes.
3. `frontend/src/services/api.ts` sends requests to Java with the session cookie and a CSRF token for writes.
4. A controller validates and decodes input, then calls a service. Services enforce permissions and business rules before using repositories or `JdbcTemplate`.
5. Flyway applies versioned SQL migrations; H2 stores local data and Neon PostgreSQL stores production data.

The Java `main` method and React entry point have different jobs: one starts the server, while the other mounts the browser interface.

## Repository folders

| Folder or file | Responsibility |
| --- | --- |
| `backend/src/main/java/com/javac/campus/` | Java application code, grouped by feature and infrastructure concern. Each package has a `package-info.java` explanation. |
| `backend/src/main/resources/` | Shared and environment-specific Spring configuration, including database, email, and session settings. |
| `backend/src/test/` | Java tests of authentication, job workflows, persistence, and security rules. Test email delivery is mocked. |
| `frontend/src/` | React components, TypeScript request/response contracts, browser state, and styles. |
| `frontend/public/` | Files served without bundling, including the official logo, local fonts, and the standalone `/presentation` website. |
| `frontend/api/` | Optional forwarding gateway for a split frontend/Java deployment. The combined container serves Java's `/api` directly. |
| `database/migrations/` | Ordered Flyway schema changes and seed data. Maven packages these files into the Java application. |
| `scripts/` | Cross-platform commands for development, builds, and tests. `local.sh` also supports older local service controls. |
| `docs/` | Architecture, API, setup, verification, and learning notes. For exact behavior, read the current source and tests. |
| `design-files/` | Visual design references rather than runtime business logic. |
| `archive/` | Earlier implementation material retained for reference. It is not the current Java production backend. |
| `Dockerfile` | Builds React, packages Java with the compiled interface, and runs the resulting JAR in a Java 21 container. |

Generated `frontend/dist/` and `backend/target/` folders are build output. Private `.env` files, development mailboxes, and `.local-data/` are local runtime state; they are not learning-source folders to publish.

## Java packages

| Package | What to learn from it |
| --- | --- |
| `main` | Application startup and component/entity/repository scanning. |
| `config` | Dependency wiring, PBKDF2 password encoding, session security, CORS, datasource setup, production validation, and React route forwarding. |
| `security` | The session principal, per-instance authentication rate limits, and revocation when account state or credential version changes. |
| `auth` | Registration, account verification, recovery, expiring link tokens, and the `EmailDelivery` interface with SMTP and development implementations. |
| `user` | Encapsulated account state and account repository queries. |
| `profile` | Student details, skill associations, profile DTOs, and the `StudentService` contract and implementation. |
| `skill` | The shared skill directory used by profile forms. |
| `marketplace` | Job posting, private Xerox attachments, manual progress, code-based completion, messages, reviews, moderation, offline earnings, and wallet administration. |
| `common` | Consistent API errors and shared timestamp behavior. |

`@RestController` exposes HTTP handlers. `@Valid` checks DTO field constraints. `@Service` registers business logic for dependency injection. `@Transactional` groups participating database writes so a failure can roll them back. JPA annotations map entities and relationships; repositories supply entity queries. `JdbcTemplate` executes marketplace SQL with parameter values supplied separately from SQL text.

## React source folders

| Folder or file | Responsibility |
| --- | --- |
| `App.tsx` | Lazy screen loading, public routes, and nested signed-in routes. |
| `contexts/AuthContext.tsx` | Session restoration, login/logout state, profile updates, and the explicitly enabled demo state. |
| `services/` | The API client, browser validation, demo fixtures, and the current normal-authentication mode. |
| `marketplace/` | Job listings, request forms, delivery details, conversations, earnings, wallets, notifications, admin reviews, and public student feedback. |
| `marketplace/useRemote.ts` | Data loading and optional visible-tab polling. A generation counter prevents an old response from replacing newer state. |
| `pages/` | Public landing, account access, verification/recovery, dashboard, profile, and settings screens. |
| `components/layout/` | Shared public-authentication and signed-in page shells. |
| `components/forms/` | Reusable labelled fields and skill selection. |
| `components/common/` | Brand display, terms dialog, and signed-in navigation guard. |
| `components/ui/` | Shared error/success announcements and loading feedback. |
| `types/` | Shared account/profile and API-error contracts. Marketplace response types live beside marketplace screens. |
| `styles/` | Local font loading, brand tokens, screen layouts, responsive rules, and motion preferences. |
| `test/` | Component and flow tests using a simulated browser environment. |

`PresentationLogin.tsx`, `PresentationRegister.tsx`, and `Requests.tsx` are retained earlier screens. The current mode uses normal account authentication, and `/requests` redirects to `/my-jobs`. Their presence does not mean the old flow is active.

## Follow a food request

1. `PostJob.tsx` collects the title, route, deadline, and agreed reward. The API client sends JSON to `MarketplaceController`.
2. `MarketDtos.Post` checks the input shape. `MarketplaceService.post` validates the deadline and inserts the job.
3. The helper accepts through `JobDetail.tsx`. Java locks the job row, prevents self/duplicate acceptance, assigns the helper, and attempts to email a handoff code to the requester. SMTP failure is logged without secret content and does not roll back acceptance. The database stores the code's hash and expiry.
4. The helper starts the job and advances through the next allowed checklist step. `AT_BLOCK` marks it `DELIVERED`. These are manual updates, not GPS tracking.
5. After receiving the item, the requester shares the code. If email is unavailable or the code is locked/expired, the requester can generate a replacement in-app for a DELIVERED job; this owner-only endpoint returns it privately and stores only the encoded verifier. Only the accepted helper can submit it for a delivered job. Incorrect entries increment a persisted attempt count; valid completion clears the saved hash and expiry.
6. `Earnings.tsx` displays earned and collected totals. A helper may record an offline payment method after completion. This does not transfer money or credit the wallet.

For Xerox, the form sends a multipart request with a JSON DTO part and a file part. Java checks PDF/PNG/JPEG signatures and size, stores print options and document bytes, and authorizes downloads separately from public job metadata.

## Follow a wallet request

Registration creates one wallet account and an INR 100 welcome-credit ledger entry. A top-up submission stores a pending request with a UTR and optional proof. Only administrator approval credits the balance.

A withdrawal locks the wallet row, checks available funds, and reserves the amount while recording the pending request and debit. The administrator pays externally and records the payout UTR, or rejects the request and restores the reserved amount with a refund entry. Locked request rows and pending-state checks prevent repeated review from applying a credit twice.

The wallet has no automatic payment gateway. Job rewards and wallet movements remain distinct records.

## Understand the two code mechanisms

Account codes in `OtpService` establish email ownership or authorize password recovery. When Neon Auth is configured it verifies mailbox ownership; Java still owns application passwords and sessions. The local development path uses `EmailDelivery` and stored code hashes.

Delivery handoff codes belong to `MarketplaceService`. They are generated at acceptance and checked after receipt. They are not account-verification codes. `TokenGenerator` and `ExpiringToken` instead support the separate link-token path.

## Database changes and checks

Flyway records checksums for applied migrations, so even comment edits can make a previously applied file fail validation. Keep existing migration files unchanged; add explanatory notes here or in `database/README.md`, and use a new migration for an actual schema change.

Useful commands from the repository root:

```sh
npm run dev   # Start Java and React together.
npm test      # Run frontend tests, then Java tests.
npm run build # Compile React, then package the Java application with its static files.
```

Read the comments before modifying a guard or transaction: they explain the invariant it protects, not merely the meaning of the adjacent Java or TypeScript keyword.
