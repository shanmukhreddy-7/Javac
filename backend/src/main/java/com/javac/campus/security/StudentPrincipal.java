package com.javac.campus.security;

import java.io.Serializable;

/**
 * Minimal identity saved in the authenticated session: user ID, email, and credential version.
 * It contains no password hash or editable profile data.
 *
 * This record is Serializable because production JDBC sessions persist authentication state. id
 * points to users.id; email identifies the account; credentialVersion snapshots the version at
 * login for later revocation checks.
 */
public record StudentPrincipal(Long id, String email, long credentialVersion)
    implements Serializable {}
