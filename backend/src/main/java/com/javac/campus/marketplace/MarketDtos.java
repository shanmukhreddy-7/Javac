package com.javac.campus.marketplace;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;

/**
 * Validated records for jobs, progress actions, messages, reviews, reports, and moderation.
 * These shape checks complement the participant and state checks in MarketplaceService.
 *
 * These immutable records define the input shape, not database entities. Optional fields may
 * remain null unless annotated @NotNull/@NotBlank; the service enforces which action/category
 * actually requires each optional value.
 */
public final class MarketDtos {
  /**
   * Private constructor prevents instantiation of this holder of static helpers or DTO types.
   */
  private MarketDtos() {}

  /**
   * Job input with optional Xerox mode/copy count. reward is exact decimal money; deadline is
   * parsed as an Instant by the service. File bytes travel in a multipart part, not in this
   * record.
   */
  public record Post(
      @NotBlank @Size(max = 120) String title,
      @NotBlank @Size(max = 2000) String description,
      @NotBlank @Pattern(regexp = "Food pickup|Errands|Tutoring|Programming|Design|Events|Other|Xerox")
          String category,
      @NotNull @DecimalMin("0") @DecimalMax("10000") @Digits(integer = 5, fraction = 2)
          BigDecimal reward,
      @NotBlank @Size(max = 120) String pickup,
      @NotBlank @Size(max = 120) String destination,
      @NotBlank @Size(max = 40) String deadline,
      @Pattern(regexp = "|BLACK_WHITE|COLOR") String xeroxMode,
      @Min(1) @Max(100) Integer xeroxCopies) {}

  /**
   * Action name plus optional manual-checklist step or completion code. Pattern constraints
   * allow known values; the service checks which value is required in the current state.
   */
  public record Action(
      @NotBlank @Pattern(regexp = "accept|start|progress|complete|cancel") String action,
      @Pattern(regexp = "FILE_RECEIVED|XEROX_COMPLETE|PICKED_UP|ON_THE_WAY|AT_BLOCK") String step,
      @Pattern(regexp = "[0-9]{6}") String otp) {}

  /**
   * Nonempty conversation text bounded to 1,000 characters; participant/state permission is
   * checked by the service.
   */
  public record Message(@NotBlank @Size(max = 1000) String body) {}

  /**
   * Reported offline collection method, restricted to the currently supported labels. It is not
   * a payment instruction.
   */
  public record Collection(@NotBlank @Pattern(regexp = "CASH|PHONEPE|PAYTM") String method) {}

  /**
   * Rating from 1 to 5 and a non-null bounded comment. The service requires a completed job and
   * prevents duplicate reviews.
   */
  public record Review(@Min(1) @Max(5) int rating, @NotNull @Size(max = 500) String comment) {}

  /**
   * Nonempty moderation reason; creating a report does not itself remove the job.
   */
  public record Report(@NotBlank @Size(max = 500) String reason) {}

  /**
   * Administrator-selected moderation status. Only ACTIVE or SUSPENDED is accepted here.
   */
  public record Account(@NotBlank @Pattern(regexp = "ACTIVE|SUSPENDED") String status) {}
}
