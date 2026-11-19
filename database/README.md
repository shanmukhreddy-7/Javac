# Database

Start with the [detailed database study guide](STUDY_GUIDE.md). The [explained SQL copies](explained/README.md) put comments beside every table, column, constraint, and migration step. They are study material; `migrations/` remains the runtime source of truth.

## Reading the schema

- `V1__initial_schema.sql` defines the original account, profile, skill, and account-token records.
- `V2__skill_directory.sql` populates the shared skill directory.
- `V3__campus_marketplace.sql` adds jobs, messages, notifications, reviews, and moderation records.
- `V4__shared_sessions.sql` adds shared JDBC session storage for production.
- `V5__campus_requests_wallet.sql` adds ordered job progress, hashed delivery-code state, private Xerox attachments, wallet accounts, ledger entries, and admin-reviewed top-up/withdrawal requests.

In V5, `job_attachments.job_id` associates a private file with one job. Wallet `user_id` fields identify the owning student, while `reviewed_by` records an administrator. `wallet_transactions.reference_id` is an application-level reference; it is not declared as a foreign key to a request table. Amounts use fixed-precision decimal columns. The welcome-credit inserts backfill existing accounts once when that migration is applied.

Explanations belong in this guide rather than edits to already-applied SQL files: Flyway checksums include source comments. See [Code walkthrough](../docs/CODE_WALKTHROUGH.md) for the service flows that use these tables.

The SQL files in migrations/ are the single source of truth. Maven packages them into the Java JAR and Flyway applies them automatically. Do not manually copy or edit already-applied migrations.

Production: Neon PostgreSQL via JDBC with TLS required. Local development: persistent H2 in PostgreSQL compatibility mode, no database installation needed. Test: isolated in-memory H2. The earlier browser-only SQLite tables are legacy presentation data, not verified accounts; they are not automatically trusted or migrated. Existing users must register and verify an account in the secure build.
