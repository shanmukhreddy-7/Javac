package com.javac.campus.marketplace;

import com.javac.campus.security.StudentPrincipal;
import jakarta.validation.Valid;
import java.math.BigDecimal;
import java.util.*;
import org.springframework.http.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
public class MarketplaceController {
  private final MarketplaceService market;
  private final WalletService wallet;

  public MarketplaceController(MarketplaceService market, WalletService wallet) {
    this.market = market;
    this.wallet = wallet;
  }

  @GetMapping("/jobs")
  public Object jobs(
      @AuthenticationPrincipal StudentPrincipal p,
      @RequestParam(required = false) String q,
      @RequestParam(required = false) String category,
      @RequestParam(defaultValue = "all") String scope,
      @RequestParam(defaultValue = "0") int page) {
    return market.browse(p, q, category, scope, page);
  }

  @PostMapping(value = "/jobs", consumes = MediaType.APPLICATION_JSON_VALUE)
  @ResponseStatus(org.springframework.http.HttpStatus.CREATED)
  public Object post(
      @AuthenticationPrincipal StudentPrincipal p, @Valid @RequestBody MarketDtos.Post r) {
    return market.post(p, r, null);
  }

  @PostMapping(value = "/jobs", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  @ResponseStatus(org.springframework.http.HttpStatus.CREATED)
  public Object postWithFile(
      @AuthenticationPrincipal StudentPrincipal p,
      @Valid @RequestPart("request") MarketDtos.Post r,
      @RequestPart(value = "file", required = false) MultipartFile file) {
    return market.post(p, r, file);
  }

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

  @GetMapping("/jobs/{id}")
  public Object detail(@AuthenticationPrincipal StudentPrincipal p, @PathVariable long id) {
    return market.detail(p, id);
  }

  @PostMapping("/jobs/{id}/actions")
  public Object act(
      @AuthenticationPrincipal StudentPrincipal p,
      @PathVariable long id,
      @Valid @RequestBody MarketDtos.Action r) {
    return market.act(p, id, r);
  }

  @GetMapping("/jobs/{id}/messages")
  public Object messages(@AuthenticationPrincipal StudentPrincipal p, @PathVariable long id) {
    return market.messages(p, id);
  }

  @PostMapping("/jobs/{id}/messages")
  public void message(
      @AuthenticationPrincipal StudentPrincipal p,
      @PathVariable long id,
      @Valid @RequestBody MarketDtos.Message r) {
    market.message(p, id, r.body());
  }

  @GetMapping("/notifications")
  public Object notifications(@AuthenticationPrincipal StudentPrincipal p) {
    return market.notifications(p);
  }

  @PostMapping("/notifications/{id}/read")
  public void read(@AuthenticationPrincipal StudentPrincipal p, @PathVariable long id) {
    market.read(p, id);
  }

  @GetMapping("/earnings")
  public Object earnings(@AuthenticationPrincipal StudentPrincipal p) {
    return market.earnings(p);
  }

  @GetMapping("/wallet")
  public Object wallet(@AuthenticationPrincipal StudentPrincipal p) {
    return wallet.view(p);
  }

  @PostMapping(value = "/wallet/topups", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  @ResponseStatus(org.springframework.http.HttpStatus.CREATED)
  public Object topup(
      @AuthenticationPrincipal StudentPrincipal p,
      @RequestParam BigDecimal amount,
      @RequestParam String utr,
      @RequestPart(value = "proof", required = false) MultipartFile proof) {
    return wallet.topup(p, amount, utr, proof);
  }

  @PostMapping("/wallet/withdrawals")
  @ResponseStatus(org.springframework.http.HttpStatus.CREATED)
  public Object withdrawal(
      @AuthenticationPrincipal StudentPrincipal p, @Valid @RequestBody WalletDtos.Withdrawal r) {
    return wallet.withdraw(p, r);
  }

  @GetMapping("/admin/wallet")
  public Object walletQueue(@AuthenticationPrincipal StudentPrincipal p) {
    return wallet.adminQueue(p);
  }

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

  @PostMapping("/admin/wallet/topups/{id}/review")
  public void reviewTopup(
      @AuthenticationPrincipal StudentPrincipal p,
      @PathVariable long id,
      @Valid @RequestBody WalletDtos.TopupReview r) {
    wallet.reviewTopup(p, id, r);
  }

  @PostMapping("/admin/wallet/withdrawals/{id}/review")
  public void reviewWithdrawal(
      @AuthenticationPrincipal StudentPrincipal p,
      @PathVariable long id,
      @Valid @RequestBody WalletDtos.WithdrawalReview r) {
    wallet.reviewWithdrawal(p, id, r);
  }

  @PostMapping("/jobs/{id}/collection")
  public void collect(
      @AuthenticationPrincipal StudentPrincipal p,
      @PathVariable long id,
      @Valid @RequestBody MarketDtos.Collection r) {
    market.collect(p, id, r.method());
  }

  @GetMapping("/jobs/{id}/reviews")
  public Object reviews(@AuthenticationPrincipal StudentPrincipal p, @PathVariable long id) {
    return market.reviews(p, id);
  }

  @PostMapping("/jobs/{id}/reviews")
  public void review(
      @AuthenticationPrincipal StudentPrincipal p,
      @PathVariable long id,
      @Valid @RequestBody MarketDtos.Review r) {
    market.review(p, id, r);
  }

  @PostMapping("/jobs/{id}/reports")
  public void report(
      @AuthenticationPrincipal StudentPrincipal p,
      @PathVariable long id,
      @Valid @RequestBody MarketDtos.Report r) {
    market.report(p, id, r.reason());
  }

  @GetMapping("/students/{id}")
  public Object student(@AuthenticationPrincipal StudentPrincipal p, @PathVariable long id) {
    return market.student(p, id);
  }

  @GetMapping("/admin")
  public Object admin(@AuthenticationPrincipal StudentPrincipal p) {
    return market.adminOverview(p);
  }

  @PatchMapping("/admin/users/{id}")
  public void account(
      @AuthenticationPrincipal StudentPrincipal p,
      @PathVariable long id,
      @Valid @RequestBody MarketDtos.Account r) {
    market.account(p, id, r.status());
  }

  @PostMapping("/admin/jobs/{id}/hide")
  public void hide(@AuthenticationPrincipal StudentPrincipal p, @PathVariable long id) {
    market.hide(p, id);
  }

  @PostMapping("/admin/reports/{id}/resolve")
  public void resolve(@AuthenticationPrincipal StudentPrincipal p, @PathVariable long id) {
    market.resolve(p, id);
  }
}
