package com.javac.campus.user;

import com.javac.campus.common.TimestampedEntity;
import jakarta.persistence.*;

@Entity
@Table(name = "users")
public class User extends TimestampedEntity {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false, unique = true, length = 254)
  private String email;

  @Column(nullable = false)
  private String passwordHash;

  @Column(nullable = false)
  private String role = "STUDENT";

  @Column(nullable = false)
  private boolean emailVerified;

  @Column(nullable = false)
  private String accountStatus = "PENDING";

  @Column(nullable = false)
  private long credentialVersion;

  protected User() {}

  public User(String email, String passwordHash) {
    this.email = email;
    this.passwordHash = passwordHash;
  }

  public Long getId() {
    return id;
  }

  public String getEmail() {
    return email;
  }

  public String getPasswordHash() {
    return passwordHash;
  }

  public boolean isEmailVerified() {
    return emailVerified;
  }

  public String getAccountStatus() {
    return accountStatus;
  }

  public long getCredentialVersion() {
    return credentialVersion;
  }

  public void verify() {
    emailVerified = true;
    accountStatus = "ACTIVE";
  }

  public void changePassword(String hash) {
    passwordHash = hash;
    credentialVersion++;
  }
}
