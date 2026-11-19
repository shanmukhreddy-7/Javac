package com.javac.campus.marketplace;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;

/**
 * Validated wallet-review and withdrawal inputs.
 * Monetary limits, UPI syntax, and review statuses are checked before wallet service execution.
 *
 * DTOs validate syntax and allowed review states before the wallet service executes. A valid UPI
 * ID or UTR string is not proof that money moved; only manual admin review records that
 * decision.
 */
public final class WalletDtos {
  /**
   * Private constructor prevents instantiation of this holder of static helpers or DTO types.
   */
  private WalletDtos() {}

  /**
   * Positive bounded amount plus syntactically valid UPI address. Service locking verifies
   * funds and reserves the amount; validation alone does not send money.
   */
  public record Withdrawal(
      @NotNull @DecimalMin("1.00") @DecimalMax("50000.00") @Digits(integer = 5, fraction = 2)
          BigDecimal amount,
      @NotBlank @Size(max = 100) @Pattern(regexp = "[A-Za-z0-9._-]{2,64}@[A-Za-z0-9]{2,32}")
          String upiId) {}

  /**
   * Admin decision APPROVED/REJECTED with an optional bounded note. Approval is the point when
   * the wallet receives credit.
   */
  public record TopupReview(
      @NotBlank @Pattern(regexp = "APPROVED|REJECTED") String status,
      @Size(max = 300) String note) {}

  /**
   * Admin decision PAID/REJECTED, optional reference and note. Service logic requires an
   * outgoing UTR for PAID; REJECTED releases the reservation.
   */
  public record WithdrawalReview(
      @NotBlank @Pattern(regexp = "PAID|REJECTED") String status,
      @Size(max = 40) @Pattern(regexp = "[A-Za-z0-9-]*") String paymentUtr,
      @Size(max = 300) String note) {}
}
