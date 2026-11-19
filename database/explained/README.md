# Annotated SQL for study

These are commented study copies of all five runtime migrations. They explain each table, column, constraint, index, and backfill beside the SQL. They differ only in comments and formatting from the migration versions present when these notes were written.

1. [V1 — accounts and profiles](V1__initial_schema.sql)
2. [V2 — shared skill directory](V2__skill_directory.sql)
3. [V3 — campus marketplace and account OTPs](V3__campus_marketplace.sql)
4. [V4 — JDBC sessions](V4__shared_sessions.sql)
5. [V5 — delivery, Xerox, and wallet workflows](V5__campus_requests_wallet.sql)

**Do not execute these copies or add this folder to Flyway's locations.** Maven packages only `database/migrations/`; applied migration checksums remain unchanged. If runtime migrations change later, refresh these study copies as documentation rather than treating them as an independent schema.

Read the [database study guide](../STUDY_GUIDE.md) for query syntax, relationship meanings, transaction boundaries, and a complete trace through jobs and wallets.
