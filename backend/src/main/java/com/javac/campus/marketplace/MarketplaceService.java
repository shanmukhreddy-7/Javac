package com.javac.campus.marketplace;

import com.javac.campus.common.ApiException;
import com.javac.campus.security.StudentPrincipal;
import java.time.Instant;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** All participant, state and payment rules are enforced here, never trusted to the browser. */
@Service
@Transactional
public class MarketplaceService {
  private final JdbcTemplate db;
  private final String adminEmail;
  private static final String VIEW =
      "SELECT j.*, o.full_name AS owner_name, h.full_name AS helper_name FROM campus_jobs j JOIN student_profiles o ON o.user_id=j.owner_id LEFT JOIN student_profiles h ON h.user_id=j.helper_id ";

  public MarketplaceService(JdbcTemplate db, @Value("${app.admin-email}") String adminEmail) {
    this.db = db;
    this.adminEmail = adminEmail;
  }

  private String now() {
    return Instant.now().toString();
  }

  private long number(Map<String, Object> row, String key) {
    return ((Number) row.get(key)).longValue();
  }

  private boolean isOwner(Map<String, Object> j, long u) {
    return number(j, "owner_id") == u;
  }

  private boolean isHelper(Map<String, Object> j, long u) {
    return j.get("helper_id") != null && number(j, "helper_id") == u;
  }

  private boolean admin(StudentPrincipal p) {
    return p.email().equalsIgnoreCase(adminEmail);
  }

  private void requireAdmin(StudentPrincipal p) {
    if (!admin(p)) throw new ApiException(403, "Administrator access required.");
  }

  private Map<String, Object> row(long id, boolean lock) {
    if (lock) db.queryForList("SELECT id FROM campus_jobs WHERE id=? FOR UPDATE", id);
    var rows = db.queryForList(VIEW + "WHERE j.id=?", id);
    if (rows.isEmpty()) throw new ApiException(404, "Job not found.");
    return rows.getFirst();
  }

  public Map<String, Object> detail(StudentPrincipal p, long id) {
    var j = row(id, false);
    if (Boolean.TRUE.equals(j.get("hidden")) && !admin(p))
      throw new ApiException(404, "Job not found.");
    return j;
  }

  private Map<String, Object> participant(StudentPrincipal p, long id, boolean lock) {
    var j = row(id, lock);
    if (!isOwner(j, p.id()) && !isHelper(j, p.id()))
      throw new ApiException(403, "Only this job's participants can use this feature.");
    return j;
  }

  private void notify(long u, long job, String text) {
    db.update(
        "INSERT INTO notifications(user_id,job_id,message,created_at) VALUES(?,?,?,?)",
        u,
        job,
        text,
        now());
  }

  private void both(Map<String, Object> j, String text) {
    notify(number(j, "owner_id"), number(j, "id"), text);
    if (j.get("helper_id") != null) notify(number(j, "helper_id"), number(j, "id"), text);
  }

  public List<Map<String, Object>> browse(
      StudentPrincipal p, String q, String category, String scope, int page) {
    if (page < 0 || page > 1000) throw new ApiException(400, "Invalid page.");
    var args = new ArrayList<Object>();
    String where = "WHERE j.hidden=FALSE ";
    if ("mine".equals(scope)) {
      where += "AND (j.owner_id=? OR j.helper_id=?) ";
      args.add(p.id());
      args.add(p.id());
    } else {
      where += "AND j.status='OPEN' AND j.deadline>? ";
      args.add(now());
    }
    if (q != null && !q.isBlank()) {
      if (q.length() > 100) throw new ApiException(400, "Search is too long.");
      where += "AND (LOWER(j.title) LIKE ? OR LOWER(j.description) LIKE ?) ";
      args.add("%" + q.toLowerCase(Locale.ROOT) + "%");
      args.add("%" + q.toLowerCase(Locale.ROOT) + "%");
    }
    if (category != null && !category.isBlank()) {
      where += "AND j.category=? ";
      args.add(category);
    }
    args.add(page * 30);
    return db.queryForList(VIEW + where + "ORDER BY j.id DESC LIMIT 30 OFFSET ?", args.toArray());
  }

  public Map<String, Object> post(StudentPrincipal p, MarketDtos.Post r) {
    String deadline;
    try {
      var parsed = Instant.parse(r.deadline());
      if (!parsed.isAfter(Instant.now()) || parsed.isAfter(Instant.now().plusSeconds(90 * 86400L)))
        throw new IllegalArgumentException();
      deadline = parsed.toString();
    } catch (Exception e) {
      throw new ApiException(400, "Choose a deadline in the next 90 days.");
    }
    var key = new org.springframework.jdbc.support.GeneratedKeyHolder();
    db.update(
        connection -> {
          var ps =
              connection.prepareStatement(
                  "INSERT INTO campus_jobs(owner_id,title,description,category,reward,pickup,destination,deadline,created_at,updated_at) VALUES(?,?,?,?,?,?,?,?,?,?)",
                  new String[] {"id"});
          Object[] values = {
            p.id(),
            r.title().trim(),
            r.description().trim(),
            r.category(),
            r.reward(),
            r.pickup().trim(),
            r.destination().trim(),
            deadline,
            now(),
            now()
          };
          for (int i = 0; i < values.length; i++) ps.setObject(i + 1, values[i]);
          return ps;
        },
        key);
    return detail(p, Objects.requireNonNull(key.getKey()).longValue());
  }

  public Map<String, Object> act(StudentPrincipal p, long id, String action) {
    var j = participantOrAccept(p, id, action);
    String status = String.valueOf(j.get("status")), next;
    if (Boolean.TRUE.equals(j.get("hidden")))
      throw new ApiException(409, "This listing was removed.");
    switch (action) {
      case "accept" -> {
        if (isOwner(j, p.id())) throw new ApiException(403, "You cannot accept your own job.");
        if (!"OPEN".equals(status)
            || !Instant.parse(String.valueOf(j.get("deadline"))).isAfter(Instant.now()))
          throw new ApiException(409, "This job is no longer available.");
        db.update("UPDATE campus_jobs SET helper_id=? WHERE id=?", p.id(), id);
        next = "ACCEPTED";
      }
      case "start" -> {
        if (!isHelper(j, p.id()) || !"ACCEPTED".equals(status))
          throw new ApiException(409, "Only the accepted helper can start this job.");
        next = "IN_PROGRESS";
      }
      case "deliver" -> {
        if (!isHelper(j, p.id()) || !"IN_PROGRESS".equals(status))
          throw new ApiException(409, "Start the job before marking it delivered.");
        next = "DELIVERED";
      }
      case "complete" -> {
        if (!isOwner(j, p.id()) || !"DELIVERED".equals(status))
          throw new ApiException(409, "Only the requester can confirm a delivered job.");
        next = "COMPLETED";
      }
      case "cancel" -> {
        if (!Set.of("OPEN", "ACCEPTED", "IN_PROGRESS").contains(status))
          throw new ApiException(409, "This job can no longer be cancelled.");
        next = "CANCELLED";
      }
      default -> throw new ApiException(400, "Unknown action.");
    }
    db.update("UPDATE campus_jobs SET status=?,updated_at=? WHERE id=?", next, now(), id);
    var result = detail(p, id);
    both(result, "Job " + next.toLowerCase(Locale.ROOT).replace('_', ' ') + ": " + j.get("title"));
    return result;
  }

  private Map<String, Object> participantOrAccept(StudentPrincipal p, long id, String action) {
    return "accept".equals(action) ? row(id, true) : participant(p, id, true);
  }

  public List<Map<String, Object>> messages(StudentPrincipal p, long id) {
    participant(p, id, false);
    return db.queryForList(
        "SELECT m.id,m.sender_id,m.body,m.created_at,p.full_name AS sender_name FROM job_messages m JOIN student_profiles p ON p.user_id=m.sender_id WHERE m.job_id=? ORDER BY m.id DESC LIMIT 100",
        id);
  }

  public void message(StudentPrincipal p, long id, String text) {
    var j = participant(p, id, true);
    if (j.get("helper_id") == null)
      throw new ApiException(409, "A helper must accept this job before messaging.");
    if (Set.of("CANCELLED", "COMPLETED").contains(j.get("status")))
      throw new ApiException(409, "This conversation is closed.");
    db.update(
        "INSERT INTO job_messages(job_id,sender_id,body,created_at) VALUES(?,?,?,?)",
        id,
        p.id(),
        text.trim(),
        now());
    notify(
        isOwner(j, p.id()) ? number(j, "helper_id") : number(j, "owner_id"),
        id,
        "New message about " + j.get("title"));
  }

  public List<Map<String, Object>> notifications(StudentPrincipal p) {
    return db.queryForList(
        "SELECT * FROM notifications WHERE user_id=? ORDER BY id DESC LIMIT 100", p.id());
  }

  public void read(StudentPrincipal p, long id) {
    if (db.update("UPDATE notifications SET is_read=TRUE WHERE id=? AND user_id=?", id, p.id())
        != 1) throw new ApiException(404, "Notification not found.");
  }

  public Map<String, Object> earnings(StudentPrincipal p) {
    var jobs =
        db.queryForList(
            VIEW + "WHERE j.helper_id=? AND j.status='COMPLETED' ORDER BY j.id DESC", p.id());
    var earned = java.math.BigDecimal.ZERO;
    var collected = java.math.BigDecimal.ZERO;
    for (var j : jobs) {
      var value = (java.math.BigDecimal) j.get("reward");
      earned = earned.add(value);
      if ("COLLECTED".equals(j.get("payment_status"))) collected = collected.add(value);
    }
    return Map.of(
        "earned",
        earned,
        "collected",
        collected,
        "pending",
        earned.subtract(collected),
        "jobs",
        jobs);
  }

  public void collect(StudentPrincipal p, long id, String method) {
    var j = participant(p, id, true);
    if (!isHelper(j, p.id())
        || !"COMPLETED".equals(j.get("status"))
        || !"UNPAID".equals(j.get("payment_status")))
      throw new ApiException(409, "Only the helper can confirm an uncollected completed job.");
    db.update(
        "UPDATE campus_jobs SET payment_status='COLLECTED',payment_method=?,collected_at=?,updated_at=? WHERE id=?",
        method,
        now(),
        now(),
        id);
    both(j, "Helper confirmed offline payment received: " + j.get("title"));
  }

  public List<Map<String, Object>> reviews(StudentPrincipal p, long id) {
    detail(p, id);
    return db.queryForList(
        "SELECT r.rating,r.comment,r.recipient_id,p.full_name AS reviewer_name,r.created_at FROM job_reviews r JOIN student_profiles p ON p.user_id=r.reviewer_id WHERE job_id=? ORDER BY r.id DESC",
        id);
  }

  public void review(StudentPrincipal p, long id, MarketDtos.Review r) {
    var j = participant(p, id, true);
    if (!"COMPLETED".equals(j.get("status")))
      throw new ApiException(409, "Complete the job before leaving feedback.");
    if (db.queryForObject(
            "SELECT COUNT(*) FROM job_reviews WHERE job_id=? AND reviewer_id=?",
            Integer.class,
            id,
            p.id())
        > 0) throw new ApiException(409, "You already reviewed this job.");
    db.update(
        "INSERT INTO job_reviews(job_id,reviewer_id,recipient_id,rating,comment,created_at) VALUES(?,?,?,?,?,?)",
        id,
        p.id(),
        isOwner(j, p.id()) ? number(j, "helper_id") : number(j, "owner_id"),
        r.rating(),
        r.comment().trim(),
        now());
  }

  public void report(StudentPrincipal p, long id, String reason) {
    detail(p, id);
    db.update(
        "INSERT INTO job_reports(job_id,reporter_id,reason,created_at) VALUES(?,?,?,?)",
        id,
        p.id(),
        reason.trim(),
        now());
  }

  public Map<String, Object> student(StudentPrincipal p, long id) {
    var rows =
        db.queryForList(
            "SELECT p.user_id AS id,p.full_name,p.department,p.study_year,p.bio,p.preferred_location FROM student_profiles p JOIN users u ON u.id=p.user_id WHERE p.user_id=? AND u.email_verified=TRUE AND u.account_status='ACTIVE'",
            id);
    if (rows.isEmpty()) throw new ApiException(404, "Student not found.");
    var result = rows.getFirst();
    result.put(
        "reviews",
        db.queryForList(
            "SELECT r.rating,r.comment,p.full_name AS reviewer_name FROM job_reviews r JOIN student_profiles p ON p.user_id=r.reviewer_id WHERE recipient_id=? ORDER BY r.id DESC LIMIT 30",
            id));
    return result;
  }

  public Map<String, Object> adminOverview(StudentPrincipal p) {
    requireAdmin(p);
    return Map.of(
        "users",
        db.queryForList(
            "SELECT u.id,u.email,u.email_verified,u.account_status,p.full_name FROM users u JOIN student_profiles p ON p.user_id=u.id ORDER BY u.id DESC LIMIT 100"),
        "jobs",
        db.queryForList(VIEW + "ORDER BY j.id DESC LIMIT 100"),
        "reports",
        db.queryForList(
            "SELECT r.*,j.title FROM job_reports r JOIN campus_jobs j ON j.id=r.job_id ORDER BY r.id DESC LIMIT 100"));
  }

  public void account(StudentPrincipal p, long id, String status) {
    requireAdmin(p);
    if (id == p.id()) throw new ApiException(400, "You cannot suspend your own admin account.");
    if (db.update(
            "UPDATE users SET account_status=?,credential_version=credential_version+1 WHERE id=? AND email_verified=TRUE AND email<>?",
            status,
            id,
            adminEmail)
        != 1) throw new ApiException(409, "Only verified non-admin accounts can be changed.");
  }

  public void hide(StudentPrincipal p, long id) {
    requireAdmin(p);
    var j = row(id, true);
    db.update(
        "UPDATE campus_jobs SET hidden=TRUE,status=CASE WHEN status IN ('OPEN','ACCEPTED','IN_PROGRESS','DELIVERED') THEN 'CANCELLED' ELSE status END,updated_at=? WHERE id=?",
        now(),
        id);
    both(j, "An administrator removed this listing: " + j.get("title"));
  }

  public void resolve(StudentPrincipal p, long id) {
    requireAdmin(p);
    if (db.update("UPDATE job_reports SET resolved=TRUE WHERE id=?", id) != 1)
      throw new ApiException(404, "Report not found.");
  }
}
