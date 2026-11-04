# Explaining JAVAC in a Java presentation

1. **Main method:** `backend/src/main/java/com/javac/campus/main/JavacApplication.java` calls `SpringApplication.run`. It starts the server and finds our components. Keeping a separate main folder makes the starting point easy to demonstrate.
2. **Encapsulation:** `user/User.java`, `profile/StudentProfile.java` and token entities keep fields private. Methods such as `verify`, `changePassword` and `update` enforce meaningful changes.
3. **Abstraction and interfaces:** `AuthService` and `StudentService` describe application operations. Controllers depend on these contracts; `AuthServiceImpl` and `StudentServiceImpl` implement them.
4. **Polymorphism:** `EmailDelivery` has `DevelopmentMailbox` and `SmtpEmailDelivery` implementations. Spring selects the delivery implementation for the active environment. Calling `send` uses that implementation without changing the authentication service.
5. **Inheritance:** account entities extend `TimestampedEntity`; verification/reset tokens extend `ExpiringToken`, sharing lifecycle and validity behavior.
6. **Constructors and dependency injection:** services receive repositories, the password encoder and database client through constructors. Dependencies are explicit and tests can substitute email delivery.
7. **Collections:** skill selections, job search results, messages and reviews use sets/lists. No custom inheritance hierarchy is invented merely for a demonstration.
8. **Exception handling:** `ApiException` represents expected rule failures; `ApiErrors` translates exceptions into safe API responses without database or credential details.
9. **Transactions:** `MarketplaceService` locks jobs during acceptance and progression. The concurrent acceptance test proves only one helper succeeds. `OtpService` commits failed attempt counts separately so rollback cannot reset guessing limits.
10. **Separation of concerns:** main starts the app; controllers receive requests; services enforce rules; repositories/JdbcTemplate access data; React renders pages. Frontend `src/main/main.tsx` is a TypeScript browser entry point, not a Java method.

Suggested demonstration: register and verify two local accounts, post a Gate 1 food pickup, accept/start/deliver it as the helper, confirm completion as requester, and show earned versus collected. Then explain the row lock, password hash, OTP transaction and interface implementations in the actual source files.
