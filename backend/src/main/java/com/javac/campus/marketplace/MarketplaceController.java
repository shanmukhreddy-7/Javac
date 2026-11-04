package com.javac.campus.marketplace;

import com.javac.campus.security.StudentPrincipal;
import jakarta.validation.Valid;
import java.util.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
public class MarketplaceController {
  private final MarketplaceService market;

  public MarketplaceController(MarketplaceService market) {
    this.market = market;
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

  @PostMapping("/jobs")
  @ResponseStatus(org.springframework.http.HttpStatus.CREATED)
  public Object post(
      @AuthenticationPrincipal StudentPrincipal p, @Valid @RequestBody MarketDtos.Post r) {
    return market.post(p, r);
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
    return market.act(p, id, r.action());
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
