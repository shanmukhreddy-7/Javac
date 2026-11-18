package com.javac.campus.marketplace;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;

public final class WalletDtos {
  private WalletDtos() {}

  public record Withdrawal(
      @NotNull @DecimalMin("1.00") @DecimalMax("50000.00") @Digits(integer = 5, fraction = 2)
          BigDecimal amount,
      @NotBlank @Size(max = 100) @Pattern(regexp = "[A-Za-z0-9._-]{2,64}@[A-Za-z0-9]{2,32}")
          String upiId) {}

  public record TopupReview(
      @NotBlank @Pattern(regexp = "APPROVED|REJECTED") String status,
      @Size(max = 300) String note) {}

  public record WithdrawalReview(
      @NotBlank @Pattern(regexp = "PAID|REJECTED") String status,
      @Size(max = 40) @Pattern(regexp = "[A-Za-z0-9-]*") String paymentUtr,
      @Size(max = 300) String note) {}
}
