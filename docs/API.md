# JAVAC Module 1 API

Base: Java backend origin, e.g. `http://localhost:8080`. All bodies are JSON. Every write requires CSRF, including anonymous register/login/reset requests. Sensitive responses use `Cache-Control: no-store` through Spring Security. Unknown JSON properties are rejected, including attempts to submit another `userId` or protected identity fields.

## Session / CSRF protocol

1. `GET /api/auth/csrf` with credentials. It creates/uses an HTTP-only `JAVAC_SESSION` cookie and returns `{ "token": "...", "headerName": "X-CSRF-TOKEN" }`.
2. Send cookie + `X-CSRF-TOKEN` on POST/PATCH/PUT. Fetch must use `credentials: 'include'`.
3. Login changes the session ID and clears the previous CSRF token. Fetch a fresh one for the next write. Logout invalidates the session and deletes its cookie.
4. `/api/auth/me` restores session state after refresh. Password change/reset increments a credential version; every authenticated request checks it against the database, invalidating older sessions.

The CSRF token is a request-defense token, not an authentication secret. Email/reset tokens and password hashes are never included in API responses. No JWT/localStorage access-token mechanism is used.

## Public endpoints

| Method/path | Body | Success |
| --- | --- | --- |
| GET /api/auth/csrf | — | 200 token/headerName |
| GET /api/auth/config | — | 200 `{approvedDomains: string[]}` |
| GET /api/skills | — | 200 array of `{id,name,category}` |
| POST /api/auth/register | fullName, email, registrationNumber, department, studyYear (1–6), password, confirmPassword, termsAccepted | 201 message; pending account |
| POST /api/auth/login | email, password | 200 student profile + session cookie |
| POST /api/auth/verify-email | token | 200 message |
| POST /api/auth/resend-verification | email | 200 generic message |
| POST /api/auth/forgot-password | email | 200 generic message |
| POST /api/auth/reset-password | token, password | 200 message |

Registration requires 2–80 character name; student ID 3–40 letters/numbers/hyphens; department up to 80; valid email <=254; exact allowlisted email domain; terms=true. Passwords require 12–64 characters, upper/lowercase, number and symbol. Password confirmation must match. Email is normalized to lowercase and student ID to uppercase before unique checks and storage.

Email verification links use a 256-bit random, URL-safe token, stored only as SHA-256 in MySQL. TTL: 30 minutes. Reset TTL: 15 minutes. Tokens are one-use; resending invalidates older unused links. Verification and reset issuance are limited to once per account per 60 seconds; generic resend/reset responses also cover cooldown and unknown accounts. Sensitive auth POSTs are limited to `AUTH_RATE_LIMIT` per remote IP/minute (default 30), returning 429 and `Retry-After: 60`. The limiter is bounded in memory.

Registration example:

```json
{
 "fullName": "Your Student Name",
 "email": "student@approved-domain.edu",
 "registrationNumber": "CS-2026-001",
 "department": "Computer Science",
 "studyYear": 2,
 "password": "Use-your-own-strong-password!",
 "confirmPassword": "Use-your-own-strong-password!",
 "termsAccepted": true
}
```

## Authenticated endpoints

| Method/path | Body | Success |
| --- | --- | --- |
| POST /api/auth/logout | — | 200 message, session invalidated |
| GET /api/auth/me | — | 200 student profile |
| GET /api/students/me | — | 200 student profile |
| PATCH /api/students/me | fullName, department, studyYear, bio, preferredLocation | 200 updated profile |
| PUT /api/students/me/skills | `{ "skillIds": [1,2] }` | 200 updated profile |
| POST /api/auth/change-password | currentPassword, password | 200 message, current/all older sessions invalidated |

PATCH accepts an editable profile form snapshot; all five listed fields are required, although bio/location may be empty. It does not accept email, registrationNumber, avatarUrl, userId, or account status. An empty skills array removes all skills; maximum 20 distinct directory IDs. A student ID is never accepted from the client for ownership: the controller passes the authenticated principal's ID to `StudentService`.

Profile response:

```json
{
 "id": 1,
 "fullName": "Your Student Name",
 "email": "student@approved-domain.edu",
 "registrationNumber": "CS-2026-001",
 "department": "Computer Science",
 "studyYear": 2,
 "bio": "",
 "preferredLocation": "",
 "emailVerified": true,
 "accountStatus": "ACTIVE",
 "createdAt": "2026-10-28T03:00:00Z",
 "skills": [{"id":1,"name":"Java","category":"Programming"}],
 "completion": 75
}
```

Completion is calculated: core registered details 60%, nonblank bio +15%, preferred location +10%, one or more skills +15%. The email verification flag means control of an approved-domain email address, not an administrator's independent verification of identity.

## Errors

```json
{"message":"Please check the highlighted fields.","fields":{"password":"Use 12–64 characters with upper/lowercase, a number and a symbol."},"timestamp":"2026-10-28T03:00:00Z"}
```

Validation/domain/token errors: 400; incorrect credentials/missing session: 401; unverified/inactive/CSRF failure: 403; missing endpoint: 404; duplicate identity: 409; throttled: 429; configuration/mail unavailability: 503. Security-filter responses include message/fields; advice-handled errors also include timestamp. Production never returns stack traces or SQL details. SMTP failure rolls back issuance and account creation so the UI can retry.

Frontend demo actions do not call these authentication/profile APIs, send mail, or create database records.
