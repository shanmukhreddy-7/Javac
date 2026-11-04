# Database

The SQL files in migrations/ are the single source of truth. Maven packages them into the Java JAR and Flyway applies them automatically. Do not manually copy or edit already-applied migrations.

Production: Neon PostgreSQL via JDBC with TLS required. Local development: persistent H2 in PostgreSQL compatibility mode, no database installation needed. Test: isolated in-memory H2. The earlier browser-only SQLite tables are legacy presentation data, not verified accounts; they are not automatically trusted or migrated. Existing users must register and verify an account in the secure build.
