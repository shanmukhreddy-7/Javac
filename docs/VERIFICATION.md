# Verification — 28 October 2026

Checks were executed locally using a downloaded full Eclipse Temurin JDK 21, Maven Wrapper 3.9.11, bundled Node 24.22.1, and a temporary, loopback-only MySQL Community Server 8.4.13. The host's installed Java 25 is a runtime without `javac`; it could not compile the project, so verification used the full JDK 21. No system Java/Node/MySQL configuration was changed.

| Check | Result |
| --- | --- |
| `backend/./mvnw package` with JDK 21 | PASS; executable Spring Boot JAR built; 19 tests, zero failures/errors/skips |
| Same backend tests with MySQL datasource overrides | PASS; 19 tests, zero failures/errors/skips |
| Flyway V1 schema + V2 skills on an empty MySQL database | PASS; both migrations applied; Hibernate schema validation succeeded |
| Live HTTP session/CSRF workflow against running Java + MySQL | PASS; register, pending-login rejection, development email link, verification, login, session read, profile PATCH, skill PUT, persisted GET, logout, unauthorized GET |
| `frontend/npm test` | PASS; 9 interaction/validation tests |
| `frontend/npm run build` | PASS; TypeScript and Vite production bundle |
| `frontend/npm audit` | PASS; zero reported vulnerabilities |
| Browser demo navigation | PASS; explicit demo banner and fictional/unverified identity |
| Browser demo profile and skills edits | PASS; temporary save feedback and selected skill state visibly confirmed |
| Browser responsive navigation | PASS at the available narrow viewport; closed navigation is inert and open menu links navigate correctly |
| Browser horizontal overflow | None observed; document width stayed within viewport |
| Windows / macOS execution | Not executed; scripts, Maven Wrapper, and documented commands provided |
| Docker image / Compose execution | Not executed; Docker was not installed on this host |
| Vercel deployment / external SMTP | Not executed; configuration and exact setup instructions provided |

## Reproducing the checks

From `backend/`, with `JAVA_HOME` pointing at a full JDK 21:

```bash
./mvnw package
```

For real MySQL coverage, create a separate disposable `javac_test` database, set its password in `SPRING_DATASOURCE_PASSWORD`, and run:

```bash
./mvnw test -Dspring.datasource.url='jdbc:mysql://localhost:3306/javac_test?serverTimezone=UTC' -Dspring.datasource.driver-class-name=com.mysql.cj.jdbc.Driver -Dspring.datasource.username=javac_test
```

The test environment used port 3307 to avoid any existing database. Application requests were tested separately on port 8080 with an isolated development mailbox. The temporary database did not replace or change a user database.

From `frontend/`:

```bash
npm ci
npm test
npm run build
npm audit
```

## Coverage details

Backend tests exercise duplicate normalized identities, invalid domains, weak passwords, required terms/confirmation, one-use verification, expired verification, correct/incorrect/unverified login, persistent session, CSRF enforcement and rotation, protected profile update, independent student ownership, unknown-field ownership tampering, suspended-account access rejection, skills validation, resend cooldown, reset replay/session invalidation, current-password checks, logout, and IP rate limits.

Frontend tests exercise login/registration errors, password confirmation, successful login navigation, anonymous protected-route redirect, sample dashboard disclosure, temporary profile/skill saves, no silent demo on API failure, and the race where an older real-session request fails after explicit demo entry.

## Warnings and practical limits

- Spring Boot's managed Flyway 11.7.2 logs a MySQL 8.4 compatibility advisory. Both migrations and all 19 backend tests passed on MySQL 8.4.13; the advisory did not prevent startup or migration.
- Vite removes two misplaced pure-comment annotations in the installed Zod dependency. The build still succeeds.
- Browser checks used the available narrow in-app viewport. There was no automated viewport matrix or browser-driven real registration flow. The real Java/MySQL path was verified with HTTP requests and integration tests; frontend forms were covered with interaction tests.
- External SMTP deliverability, production HTTPS/cookie behavior on chosen domains, hosted availability, Docker, and other operating systems must be checked in their target environments. Cross-site cookies may be blocked by browser privacy settings; prefer frontend/backend subdomains under one site.
- The classroom app uses a single backend instance, in-memory sessions, synchronous mail, and a bounded in-memory rate limiter. See the README before scaling it.

![Verified sample dashboard](screenshots/dashboard.png)


## Installed local environment follow-up

The required tools have now been installed persistently under the user's local tool directory. The actual application database and generated private credentials were configured, and all three services were left running under systemd user services. Approved domain: `srmap.edu.in`, supplied by the user.

- Reinstalled frontend dependencies with `npm ci`: zero audit vulnerabilities.
- Frontend: 9 tests passed; production build passed.
- Backend: 20 tests passed; executable JAR built. The added regression test verifies that the `.env`-style `SPRING_PROFILES_ACTIVE` key activates the development mailbox.
- Fixed the profile-selection configuration caught by the installation smoke test.
- Live HTTP check against the newly installed Java/MySQL services passed: registration, pending-account login rejection, local mailbox link, verification, login, session persistence, profile update, skills assignment, logout, and unauthorized read.
- Removed the disposable installation-check account and its mailbox file afterward. No sample student was retained in MySQL.
- Frontend returned HTTP 200; database, backend, and frontend services were active.
- The in-app browser security policy blocked automatic access to the existing tab during this installation turn. No new browser verification was claimed; refresh that tab manually.

See `docs/LOCAL_SETUP.md` for VS Code Start/Stop/Status tasks and service commands.
