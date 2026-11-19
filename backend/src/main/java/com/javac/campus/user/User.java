package com.javac.campus.user;

import com.javac.campus.common.TimestampedEntity;
import jakarta.persistence.*;

/**
 * JPA account entity that encapsulates credential, verification, and account-status state.
 * Student-facing profile details are stored separately in StudentProfile.
 *
 * @Entity maps this class to users; @Id is its database identity and IDENTITY asks the database
 * to generate it. Private fields and controlled state-changing methods demonstrate
 * encapsulation. Inherited timestamps come from TimestampedEntity, not another table.
 */
@Entity
@Table(name = "users")
public class User extends TimestampedEntity {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  // Database-generated account primary key; profile/job/wallet foreign keys refer to this value.
  private Long id;

  @Column(nullable = false, unique = true, length = 254)
  // Normalized unique login address. Database uniqueness protects against concurrent duplicates.
  private String email;

  @Column(nullable = false)
  // Encoded password verifier; raw passwords are never persisted in this field.
  private String passwordHash;

  @Column(nullable = false)
  // Account role chosen by trusted login logic; starts as STUDENT.
  private String role = "STUDENT";

  @Column(nullable = false)
  // Tracks proven account mailbox ownership, separate from a delivery handoff.
  private boolean emailVerified;

  @Column(nullable = false)
  // Lifecycle/moderation state: starts PENDING, becomes ACTIVE after verification, or may be
  // SUSPENDED.
  private String accountStatus = "PENDING";

  @Column(nullable = false)
  // Session principals retain this version; changing it revokes previously issued sessions.
  // Revocation counter compared with the value stored in each authenticated session.
  private long credentialVersion;

  /**
   * Protected no-argument constructor required by JPA to rebuild persisted entities.
   */
  protected User() {}

  /**
   * Creates a new unverified PENDING account from normalized email and an already encoded
   * password. Default role/state/version fields remain in effect until later trusted operations
   * change them.
   */
  public User(String email, String passwordHash) {
    this.email = email;
    this.passwordHash = passwordHash;
  }

  /**
   * Returns the stored account role.
   */
  public String getRole() {
    return role;
  }

  /**
   * Stores the role chosen by trusted service logic. This setter itself does not authorize a
   * caller; AuthServiceImpl selects the role from the configured admin email.
   */
  public void setRole(String role) {
    this.role = role;
  }

  /**
   * Returns the database-generated record ID.
   */
  public Long getId() {
    return id;
  }

  /**
   * Returns the normalized login email.
   */
  public String getEmail() {
    return email;
  }

  /**
   * Returns the encoded password for trusted authentication code; never include it in public
   * responses.
   */
  public String getPasswordHash() {
    return passwordHash;
  }

  /**
   * Returns whether mailbox ownership has been verified.
   */
  public boolean isEmailVerified() {
    return emailVerified;
  }

  /**
   * Returns the current account lifecycle/moderation status.
   */
  public String getAccountStatus() {
    return accountStatus;
  }

  /**
   * Returns the revocation version compared against saved session principals.
   */
  public long getCredentialVersion() {
    return credentialVersion;
  }

  /**
   * Changes both ownership proof and account status together: emailVerified becomes true and
   * PENDING becomes ACTIVE. Call this only after successful account email verification.
   */
  public void verify() {
    emailVerified = true;
    accountStatus = "ACTIVE";
  }

  /**
   * Accepts an already encoded password, never raw text. Increments credentialVersion so
   * AccountSessionFilter rejects principals created before this password change.
   */
  public void changePassword(String hash) {
    passwordHash = hash;
    credentialVersion++;
  }
}
