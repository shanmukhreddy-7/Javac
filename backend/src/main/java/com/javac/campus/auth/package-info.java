/**
 * Account registration, email ownership checks, password recovery, and email delivery contracts.
 * Account OTPs here are separate from delivery handoff codes in marketplace.
 *
 * Read AuthController for HTTP/session handling, AuthServiceImpl for account rules, and
 * OtpService for code persistence/provider checks. Repositories serve link tokens; EmailDelivery
 * implementations choose real SMTP or a private local mailbox.
 */
package com.javac.campus.auth;
