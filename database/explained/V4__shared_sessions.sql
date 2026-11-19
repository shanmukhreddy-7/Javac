-- STUDY COPY ONLY — annotated explanation of migrations/V4__shared_sessions.sql. Do not execute
-- this copy or configure Flyway to scan explained/. The original migration remains the runtime
-- source of truth.

-- Spring Session JDBC metadata, used by the production shared-session configuration.
CREATE TABLE SPRING_SESSION (
 -- Internal session row UUID used by the attributes foreign key.
 PRIMARY_ID CHAR(36) NOT NULL PRIMARY KEY,
 -- Browser-session identifier; separately unique for session lookup.
 SESSION_ID CHAR(36) NOT NULL,
 -- Session creation time in epoch milliseconds.
 CREATION_TIME BIGINT NOT NULL,
 -- Last request access time in epoch milliseconds.
 LAST_ACCESS_TIME BIGINT NOT NULL,
 -- Allowed inactivity duration in seconds; configuration selects the value.
 MAX_INACTIVE_INTERVAL INT NOT NULL,
 -- Calculated expiration time in epoch milliseconds; indexed for cleanup.
 EXPIRY_TIME BIGINT NOT NULL,
 -- Optional indexed principal-name value used by Spring Session lookups.
 PRINCIPAL_NAME VARCHAR(100)
);

-- Unique session ID lookup prevents duplicate browser-session identifiers.
CREATE UNIQUE INDEX SPRING_SESSION_IX1 ON SPRING_SESSION(SESSION_ID);

-- Expiration-time index supports session cleanup.
CREATE INDEX SPRING_SESSION_IX2 ON SPRING_SESSION(EXPIRY_TIME);

-- Supports looking up sessions by stored principal name.
CREATE INDEX SPRING_SESSION_IX3 ON SPRING_SESSION(PRINCIPAL_NAME);

-- Serialized values belonging to a JDBC session, including the Spring Security context/principal.
CREATE TABLE SPRING_SESSION_ATTRIBUTES (
 -- Parent session UUID; ON DELETE CASCADE removes attributes with their session.
 SESSION_PRIMARY_ID CHAR(36) NOT NULL REFERENCES SPRING_SESSION(PRIMARY_ID) ON DELETE CASCADE,
 -- Attribute key such as the security-context attribute; unique within its parent session.
 ATTRIBUTE_NAME VARCHAR(200) NOT NULL,
 -- BYTEA stores serialized attribute bytes. StudentPrincipal implements Serializable for this
 -- persistence path.
 ATTRIBUTE_BYTES BYTEA NOT NULL,
 -- Composite key permits each attribute name once within a session, while the same name may occur
 -- in other sessions.
 PRIMARY KEY(SESSION_PRIMARY_ID,ATTRIBUTE_NAME)
);
