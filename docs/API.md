# Active Java API

All endpoints are served by Java. Obtain `/api/auth/csrf` and send its token/header for every mutation. Session cookies are HTTP-only. Registration/config/skills/verification/recovery endpoints are public but still have CSRF and rate controls. Other API endpoints require a verified active student.

## Identity

- GET `/api/auth/config`, `/api/auth/csrf`, `/api/skills`
- POST `/api/auth/register`: fullName, email, registrationNumber, department, studyYear, password, confirmPassword, termsAccepted.
- POST `/api/auth/verify-otp`: email, code (six digits).
- POST `/api/auth/resend-verification`: email.
- POST `/api/auth/login`: email, password. Returns student including role.
- GET `/api/auth/me`; POST `/api/auth/logout`.
- POST `/api/auth/forgot-password`: email. Generic eligibility response.
- POST `/api/auth/reset-otp`: email, code, password.
- POST `/api/auth/change-password`: currentPassword, password. Revokes sessions.
- Earlier long-token endpoints remain for legacy token compatibility; normal registrations use OTP.

## Profiles

- GET/PATCH `/api/students/me`: self profile, editable fullName/department/studyYear/bio/preferredLocation.
- PUT `/api/students/me/skills`: skillIds.
- GET `/api/students/{id}`: public profile and recent reviews; no private email.

## Jobs

- GET `/api/jobs?q=&category=&scope=all|mine&page=0`: 30 items per page. Browse shows unexpired open jobs; mine includes lifecycle history.
- POST `/api/jobs`: title, description, category, reward (INR, up to two decimals, 0–10000), pickup, destination, deadline (ISO instant, within 90 days).
- GET `/api/jobs/{id}`.
- POST `/api/jobs/{id}/actions`: action = accept/start/deliver/complete/cancel. Ownership and transition rules enforced in Java.
- GET/POST `/api/jobs/{id}/messages`: participants only, POST body = {body}.
- GET/POST `/api/jobs/{id}/reviews`: POST rating 1–5, comment; only completed participants may review, once each.
- POST `/api/jobs/{id}/reports`: reason.

## Earnings and notifications

- GET `/api/earnings`: earned, pending, collected, jobs.
- POST `/api/jobs/{id}/collection`: method = CASH/PHONEPE/PAYTM. Completed helper only; records self-confirmed receipt and does not move money.
- GET `/api/notifications`; POST `/api/notifications/{id}/read` (recipient only).

## Admin

All admin routes check the authenticated, verified admin email on the server.

- GET `/api/admin`: recent accounts/listings/reports.
- PATCH `/api/admin/users/{id}`: status ACTIVE/SUSPENDED. Cannot elevate roles, activate unverified accounts, or suspend the administrator.
- POST `/api/admin/jobs/{id}/hide`: removes listing, cancels unfinished work, notifies participants.
- POST `/api/admin/reports/{id}/resolve`.

Browser pages refresh relevant data every five seconds while visible. API mutations are never represented as successful until the server confirms them.
