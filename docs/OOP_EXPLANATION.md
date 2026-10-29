# Explaining JAVAC with Java OOP

The Java backend is a modular monolith: one Spring Boot process, divided by responsibility. React displays the result; Java validates and carries out the business rules.

| Principle | Actual implementation | How to explain it in class |
| --- | --- | --- |
| Encapsulation | `user/User`, `profile/StudentProfile` | Fields are private. Named methods such as `verify()`, `changePassword()`, `update()` and `setSkills()` control changes. No controller can freely set account status. |
| Abstraction | `auth/AuthService`, `profile/StudentService` | A controller asks for a business operation, without knowing how Hibernate or token delivery implements it. |
| Interfaces | `auth/EmailDelivery`, JPA repository interfaces | Email delivery declares a small contract; repositories declare persistence operations without handwritten CRUD SQL. |
| Polymorphism | `DevelopmentMailbox`, `SmtpEmailDelivery` | Both implement `EmailDelivery.send()`. Spring chooses the implementation by profile; `AuthServiceImpl` works with either. The integration tests substitute a mock implementation. |
| Appropriate inheritance | `common/TimestampedEntity`, `auth/ExpiringToken` | `User` inherits creation/update timestamps. `VerificationToken` and `PasswordResetToken` inherit expiry, consumption, ownership, and hashing fields; they use separate tables for their different purposes. |
| Constructors / dependency injection | `AuthServiceImpl`, `StudentServiceImpl`, `AuthController` | Dependencies are supplied in constructors. This makes required collaborators explicit and easy to replace in tests. |
| Exception handling | `common/ApiException`, `ApiErrors` | Services raise meaningful errors; the global handler converts them into consistent HTTP responses. The UI never sees SQL errors or hashes. |
| Collections | `StudentProfile.skills`, `ProfileDtos.Skills`, allowlist | A `Set<Skill>` avoids duplicates; Lists provide ordered views. A `Set<Long>` validates selected skill IDs against the directory. |
| Validation | `AuthDtos`, `ProfileDtos`, `AuthServiceImpl` | Bean Validation checks shape/strength; service methods check domain rules, duplicate identity, token validity and account eligibility. Database constraints enforce uniqueness even under competing requests. |
| Separation of concerns | Controllers → service interfaces → implementations → repositories | Controllers translate HTTP; services implement business rules; repositories persist entities; DTOs define public data. |

## Walk one operation through the classes

`POST /api/auth/register` → `AuthController.register()` → validated `AuthDtos.Register` → `AuthServiceImpl.register()` → domain/duplicate/confirmation checks → Spring Security PBKDF2 password hashing → `UserRepository` and `StudentProfileRepository` → hashed `VerificationToken` → `EmailDelivery.send()`.

`@Transactional` means related database writes succeed together. If a duplicate constraint or mail delivery failure occurs, the operation rolls back. Email delivery itself is an external effect; the simple synchronous implementation does not provide a transactional outbox.

## Ownership example

`StudentController` receives `StudentPrincipal` from the server session, then calls `students.update(principal.id(), request)`. There is no student ID in the URL/body to change. `StudentServiceImpl.own()` looks up that principal's profile. This prevents one student editing another private profile.

## Why the inheritance is small

A student account is not a fabricated subclass tree of campus roles. `User` represents authentication; `StudentProfile` represents student details via composition. Only shared lifecycle/token behavior uses inheritance. Future jobs/payments modules can reference user IDs without rewriting authentication.

## Where to point in the editor

Start with `backend/src/main/java/com/javac/campus/`. Open `auth/AuthService.java` beside `auth/AuthServiceImpl.java`, then `auth/EmailDelivery.java` beside its two implementations. Open `profile/StudentProfile.java` to explain `@OneToOne`, `@ManyToMany`, constructors and private fields. Finish with `ModuleOneIntegrationTest` to show these rules are executable.
