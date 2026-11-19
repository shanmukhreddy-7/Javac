package com.javac.campus.marketplace;

import com.javac.campus.security.StudentPrincipal;
import jakarta.validation.Valid;
import java.math.BigDecimal;
import java.util.*;
import org.springframework.http.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/**
 * HTTP adapter for campus jobs, conversations, earnings, wallets, and administration.
 * Services enforce permissions and business rules; controllers decode validated input and build responses.
 *
 * @GetMapping reads resources; @PostMapping creates requests or performs actions; @PatchMapping
 * changes account state. Parameter annotations identify JSON, multipart parts, path IDs, or
 * session identity. Returning Object lets Spring serialize service maps/lists as JSON.
 */
@RestController
@RequestMapping("/api")
public class MarketplaceController {
  // Job/participant/moderation rules and SQL persistence.
  private final MarketplaceService market;
  // Balance, ledger, and manually reviewed payment-request rules.
  private final WalletService wallet;

  /**
   * Constructor injection: Spring supplies the collaborators declared here, so this class can
   * delegate work without constructing its own repositories or services.
   */
  public MarketplaceController(MarketplaceService market, WalletService wallet) {
    this.market = market;
    this.wallet = wallet;
  }

  /**
   * GET /api/jobs: binds search/category/scope/page query parameters and delegates filtering to
   * browse. The signed-in principal supplies the account ID for the mine scope.
   */
  @GetMapping("/jobs")
  public Object jobs(
      @AuthenticationPrincipal StudentPrincipal p,
      @RequestParam(required = false) String q,
      @RequestParam(required = false) String category,
      @RequestParam(defaultValue = "all") String scope,
      @RequestParam(defaultValue = "0") int page) {
    return market.browse(p, q, category, scope, page);
  }

  // Ordinary requests use JSON; Xerox requests use the multipart overload below.
  /**
   * POST /api/jobs with JSON: validates the request DTO and creates an ordinary job without a
   * file. Returns HTTP 201 through @ResponseStatus.
   */
  @PostMapping(value = "/jobs", consumes = MediaType.APPLICATION_JSON_VALUE)
  @ResponseStatus(org.springframework.http.HttpStatus.CREATED)
  public Object post(
      @AuthenticationPrincipal StudentPrincipal p, @Valid @RequestBody MarketDtos.Post r) {
    return market.post(p, r, null);
  }

  /**
   * POST /api/jobs with multipart data: binds a JSON request part plus optional file bytes. The
   * service checks that uploads and print options belong only to Xerox requests.
   */
  @PostMapping(value = "/jobs", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  @ResponseStatus(org.springframework.http.HttpStatus.CREATED)
  public Object postWithFile(
      @AuthenticationPrincipal StudentPrincipal p,
      @Valid @RequestPart("request") MarketDtos.Post r,
      @RequestPart(value = "file", required = false) MultipartFile file) {
    return market.post(p, r, file);
  }

  // Build a private download response only after the service authorizes attachment access.
  /**
   * GET /api/jobs/{id}/file: asks the service to authorize participant access, then returns
   * private bytes with their MIME type, download disposition, nosniff, and no-store headers.
   */
  @GetMapping("/jobs/{id}/file")
  public ResponseEntity<byte[]> jobFile(
      @AuthenticationPrincipal StudentPrincipal p, @PathVariable long id) {
    var f = market.attachment(p, id);
    return ResponseEntity.ok()
        .contentType(MediaType.parseMediaType((String) f.get("content_type")))
        .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=javac-xerox-file")
        .header("X-Content-Type-Options", "nosniff")
        .header(HttpHeaders.CACHE_CONTROL, "private, no-store")
        .body((byte[]) f.get("file_data"));
  }

  /**
   * GET /api/jobs/{id}: returns the service's sanitized visible job detail. A URL ID selects a
   * job, while the principal determines whether the caller may see hidden details.
   */
  @GetMapping("/jobs/{id}")
  public Object detail(@AuthenticationPrincipal StudentPrincipal p, @PathVariable long id) {
    return market.detail(p, id);
  }

  /**
   * POST /api/jobs/{id}/actions: validates action, optional checklist step, and optional six-
   * digit code before delegating state transitions and permissions to the service.
   */
  @PostMapping("/jobs/{id}/actions")
  public Object act(
      @AuthenticationPrincipal StudentPrincipal p,
      @PathVariable long id,
      @Valid @RequestBody MarketDtos.Action r) {
    return market.act(p, id, r);
  }

  /**
   * POST /api/jobs/{id}/handoff-code: creates a replacement code after delivery for the
   * requester alone. A POST requires CSRF protection; private no-store prevents secret caching.
   */
  @PostMapping("/jobs/{id}/handoff-code")
  public ResponseEntity<Map<String, String>> handoffCode(
      @AuthenticationPrincipal StudentPrincipal p, @PathVariable long id) {
    return ResponseEntity.ok()
        .header(HttpHeaders.CACHE_CONTROL, "private, no-store")
        .body(market.handoffCode(p, id));
  }

  /**
   * GET /api/jobs/{id}/messages: reads a conversation only after the service checks participant
   * membership.
   */
  @GetMapping("/jobs/{id}/messages")
  public Object messages(@AuthenticationPrincipal StudentPrincipal p, @PathVariable long id) {
    return market.messages(p, id);
  }

  /**
   * POST /api/jobs/{id}/messages: validates message text; the service authorizes the
   * conversation, stores it, and notifies the other participant.
   */
  @PostMapping("/jobs/{id}/messages")
  public void message(
      @AuthenticationPrincipal StudentPrincipal p,
      @PathVariable long id,
      @Valid @RequestBody MarketDtos.Message r) {
    market.message(p, id, r.body());
  }

  /**
   * GET /api/notifications: returns only records belonging to the authenticated account.
   */
  @GetMapping("/notifications")
  public Object notifications(@AuthenticationPrincipal StudentPrincipal p) {
    return market.notifications(p);
  }

  /**
   * POST /api/notifications/{id}/read: marks one notification read using both its ID and
   * principal ownership.
   */
  @PostMapping("/notifications/{id}/read")
  public void read(@AuthenticationPrincipal StudentPrincipal p, @PathVariable long id) {
    market.read(p, id);
  }

  /**
   * GET /api/earnings: returns this helper's earned, collected, and pending offline reward
   * totals, separate from the wallet.
   */
  @GetMapping("/earnings")
  public Object earnings(@AuthenticationPrincipal StudentPrincipal p) {
    return market.earnings(p);
  }

  // Wallet endpoints represent balances and manual admin requests, not automatic job settlement.
  /**
   * GET /api/wallet: returns the signed-in student's available balance, ledger, and manual
   * payment-request histories.
   */
  @GetMapping("/wallet")
  public Object wallet(@AuthenticationPrincipal StudentPrincipal p) {
    return wallet.view(p);
  }

  /**
   * POST /api/wallet/topups: accepts a multipart amount, UTR, and optional screenshot. Service
   * validation stores a pending review request; HTTP 201 means request creation, not verified
   * payment.
   */
  @PostMapping(value = "/wallet/topups", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  @ResponseStatus(org.springframework.http.HttpStatus.CREATED)
  public Object topup(
      @AuthenticationPrincipal StudentPrincipal p,
      @RequestParam BigDecimal amount,
      @RequestParam String utr,
      @RequestPart(value = "proof", required = false) MultipartFile proof) {
    return wallet.topup(p, amount, utr, proof);
  }

  /**
   * POST /api/wallet/withdrawals: validates amount/UPI syntax and asks the service to reserve
   * available funds and create a pending payout request.
   */
  @PostMapping("/wallet/withdrawals")
  @ResponseStatus(org.springframework.http.HttpStatus.CREATED)
  public Object withdrawal(
      @AuthenticationPrincipal StudentPrincipal p, @Valid @RequestBody WalletDtos.Withdrawal r) {
    return wallet.withdraw(p, r);
  }

  // Services repeat the admin check; a hidden admin screen is not an authorization boundary.
  /**
   * GET /api/admin/wallet: service-side administrator authorization protects the combined
   * payment review queue.
   */
  @GetMapping("/admin/wallet")
  public Object walletQueue(@AuthenticationPrincipal StudentPrincipal p) {
    return wallet.adminQueue(p);
  }

  /**
   * GET /api/admin/wallet/topups/{id}/proof: retrieves proof only after admin authorization.
   * Returns its checked content type with nosniff and private no-store headers.
   */
  @GetMapping("/admin/wallet/topups/{id}/proof")
  public ResponseEntity<byte[]> topupProof(
      @AuthenticationPrincipal StudentPrincipal p, @PathVariable long id) {
    var f = wallet.topupProof(p, id);
    return ResponseEntity.ok()
        .contentType(MediaType.parseMediaType((String) f.get("proof_type")))
        .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=payment-proof")
        .header("X-Content-Type-Options", "nosniff")
        .header(HttpHeaders.CACHE_CONTROL, "private, no-store")
        .body((byte[]) f.get("proof_data"));
  }

  /**
   * POST /api/admin/wallet/topups/{id}/review: validates APPROVED/REJECTED and optional note;
   * the service locks and reviews the request before any balance credit.
   */
  @PostMapping("/admin/wallet/topups/{id}/review")
  public void reviewTopup(
      @AuthenticationPrincipal StudentPrincipal p,
      @PathVariable long id,
      @Valid @RequestBody WalletDtos.TopupReview r) {
    wallet.reviewTopup(p, id, r);
  }

  /**
   * POST /api/admin/wallet/withdrawals/{id}/review: validates PAID/REJECTED and optional payout
   * reference; the service enforces required UTR and reservation/refund rules.
   */
  @PostMapping("/admin/wallet/withdrawals/{id}/review")
  public void reviewWithdrawal(
      @AuthenticationPrincipal StudentPrincipal p,
      @PathVariable long id,
      @Valid @RequestBody WalletDtos.WithdrawalReview r) {
    wallet.reviewWithdrawal(p, id, r);
  }

  // Offline reward collection belongs to job earnings and is separate from the wallet ledger.
  /**
   * POST /api/jobs/{id}/collection: records a helper-reported external reward collection
   * method. This call does not debit/credit wallet accounts or contact a payment provider.
   */
  @PostMapping("/jobs/{id}/collection")
  public void collect(
      @AuthenticationPrincipal StudentPrincipal p,
      @PathVariable long id,
      @Valid @RequestBody MarketDtos.Collection r) {
    market.collect(p, id, r.method());
  }

  /**
   * GET /api/jobs/{id}/reviews: returns visible job feedback through the marketplace service.
   */
  @GetMapping("/jobs/{id}/reviews")
  public Object reviews(@AuthenticationPrincipal StudentPrincipal p, @PathVariable long id) {
    return market.reviews(p, id);
  }

  /**
   * POST /api/jobs/{id}/reviews: validates rating and comment; the service requires completed-
   * job participation and one review per reviewer.
   */
  @PostMapping("/jobs/{id}/reviews")
  public void review(
      @AuthenticationPrincipal StudentPrincipal p,
      @PathVariable long id,
      @Valid @RequestBody MarketDtos.Review r) {
    market.review(p, id, r);
  }

  /**
   * POST /api/jobs/{id}/reports: validates a moderation reason and records the authenticated
   * reporter.
   */
  @PostMapping("/jobs/{id}/reports")
  public void report(
      @AuthenticationPrincipal StudentPrincipal p,
      @PathVariable long id,
      @Valid @RequestBody MarketDtos.Report r) {
    market.report(p, id, r.reason());
  }

  /**
   * GET /api/students/{id}: returns selected public details and received feedback for a
   * verified active student.
   */
  @GetMapping("/students/{id}")
  public Object student(@AuthenticationPrincipal StudentPrincipal p, @PathVariable long id) {
    return market.student(p, id);
  }

  /**
   * GET /api/admin: returns moderation data after the service independently checks
   * administrator access.
   */
  @GetMapping("/admin")
  public Object admin(@AuthenticationPrincipal StudentPrincipal p) {
    return market.adminOverview(p);
  }

  /**
   * PATCH /api/admin/users/{id}: validates ACTIVE/SUSPENDED and delegates account-state changes
   * and session revocation safeguards.
   */
  @PatchMapping("/admin/users/{id}")
  public void account(
      @AuthenticationPrincipal StudentPrincipal p,
      @PathVariable long id,
      @Valid @RequestBody MarketDtos.Account r) {
    market.account(p, id, r.status());
  }

  /**
   * POST /api/admin/jobs/{id}/hide: asks the service to hide a listing, cancel active work if
   * applicable, and notify its participants.
   */
  @PostMapping("/admin/jobs/{id}/hide")
  public void hide(@AuthenticationPrincipal StudentPrincipal p, @PathVariable long id) {
    market.hide(p, id);
  }

  /**
   * POST /api/admin/reports/{id}/resolve: asks the service to mark a moderation report
   * resolved; administrator permission is checked there.
   */
  @PostMapping("/admin/reports/{id}/resolve")
  public void resolve(@AuthenticationPrincipal StudentPrincipal p, @PathVariable long id) {
    market.resolve(p, id);
  }
}
