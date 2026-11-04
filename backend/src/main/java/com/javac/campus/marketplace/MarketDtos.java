package com.javac.campus.marketplace;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;

public final class MarketDtos {
  private MarketDtos() {}

  public record Post(
      @NotBlank @Size(max = 120) String title,
      @NotBlank @Size(max = 2000) String description,
      @NotBlank @Pattern(regexp = "Food pickup|Errands|Tutoring|Programming|Design|Events|Other")
          String category,
      @NotNull @DecimalMin("0") @DecimalMax("10000") @Digits(integer = 5, fraction = 2)
          BigDecimal reward,
      @NotBlank @Size(max = 120) String pickup,
      @NotBlank @Size(max = 120) String destination,
      @NotBlank @Size(max = 40) String deadline) {}

  public record Action(
      @NotBlank @Pattern(regexp = "accept|start|deliver|complete|cancel") String action) {}

  public record Message(@NotBlank @Size(max = 1000) String body) {}

  public record Collection(@NotBlank @Pattern(regexp = "CASH|PHONEPE|PAYTM") String method) {}

  public record Review(@Min(1) @Max(5) int rating, @NotNull @Size(max = 500) String comment) {}

  public record Report(@NotBlank @Size(max = 500) String reason) {}

  public record Account(@NotBlank @Pattern(regexp = "ACTIVE|SUSPENDED") String status) {}
}
