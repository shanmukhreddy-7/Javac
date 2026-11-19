package com.javac.campus.marketplace;

import com.javac.campus.common.ApiException;
import com.javac.campus.auth.EmailDelivery;
import com.javac.campus.security.StudentPrincipal;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.MailException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/**
 * Transactional job rules and JDBC persistence for the campus marketplace.
 * Controllers delegate here so participant checks, delivery states, and row locks also protect
 * callers that bypass the browser. Job earnings record offline collection, not wallet settlement.
 *
 * JdbcTemplate returns rows as maps keyed by SQL column labels. The p parameter is trusted
 * session identity, id selects a job, and request records hold validated input. A class-level
 * transaction spans updates and notifications; FOR UPDATE locks remain held until that
 * transaction finishes.
 */
@Service
@Transactional
public class MarketplaceService {
  private static final Logger log = LoggerFactory.getLogger(MarketplaceService.class);
  // SQL access for jobs, attachment bytes, conversations, feedback, and notifications.
  private final JdbcTemplate db;
  // Server configuration used to authorize moderation operations.
  private final String adminEmail;
  // Sends the requester a delivery code at helper acceptance.
  private final EmailDelivery email;
  // Encodes and matches delivery handoff codes using the configured password encoder.
  private final PasswordEncoder passwords;
  // Secure random source for six-digit delivery handoff codes.
  private final SecureRandom random = new SecureRandom();
  // Shared listing projection excludes delivery-code secrets; detailed reads redact them
  // separately.
  private static final String VIEW =
      "SELECT j.id,j.owner_id,j.helper_id,j.title,j.description,j.category,j.reward,j.pickup,j.destination,j.deadline,j.status,j.payment_status,j.payment_method,j.collected_at,j.hidden,j.progress_step,j.created_at,j.updated_at,o.full_name AS owner_name,h.full_name AS helper_name FROM campus_jobs j JOIN student_profiles o ON o.user_id=j.owner_id LEFT JOIN student_profiles h ON h.user_id=j.helper_id ";

  /**
   * Constructor injection: Spring supplies the collaborators declared here, so this class can
   * delegate work without constructing its own repositories or services.
   */
  public MarketplaceService(
      JdbcTemplate db,
      @Value("${app.admin-email}") String adminEmail,
      EmailDelivery email,
      PasswordEncoder passwords) {
    this.db = db;
    this.adminEmail = adminEmail;
    this.email = email;
    this.passwords = passwords;
  }

  /**
   * Returns the current UTC Instant as ISO-8601 text, matching the marketplace tables' VARCHAR
   * timestamp columns. This is distinct from JPA account entities' TIMESTAMP columns.
   */
  private String now() {
    return Instant.now().toString();
  }

  /**
   * Converts a JDBC numeric column to long without depending on which Number subtype the
   * database driver returned. Callers use this only for non-null identity columns.
   */
  private long number(Map<String, Object> row, String key) {
    return ((Number) row.get(key)).longValue();
  }

  /**
   * Compares the authenticated account ID with owner_id, the student who posted this job. It is
   * a role check for this specific job, not a global account role.
   */
  private boolean isOwner(Map<String, Object> j, long u) {
    return number(j, "owner_id") == u;
  }

  /**
   * Checks that a helper has been assigned before comparing helper_id with the account ID. Open
   * jobs have a null helper_id.
   */
  private boolean isHelper(Map<String, Object> j, long u) {
    return j.get("helper_id") != null && number(j, "helper_id") == u;
  }

  /**
   * Checks the principal email against the configured admin email, ignoring letter case.
   * Administrative service operations call requireAdmin before exposing private review data.
   */
  private boolean admin(StudentPrincipal p) {
    return p.email().equalsIgnoreCase(adminEmail);
  }

  /**
   * Rejects non-administrator callers with HTTP 403. This server-side guard applies even if
   * someone bypasses the React admin screen.
   */
  private void requireAdmin(StudentPrincipal p) {
    if (!admin(p)) throw new ApiException(403, "Administrator access required.");
  }

  /**
   * Optionally locks the job by ID using SELECT FOR UPDATE, then reads its complete row with
   * requester/helper names. JOIN requires the requester profile; LEFT JOIN allows unaccepted
   * jobs with no helper. Missing records become HTTP 404.
   */
  private Map<String, Object> row(long id, boolean lock) {
    if (lock) db.queryForList("SELECT id FROM campus_jobs WHERE id=? FOR UPDATE", id);
    var rows = db.queryForList("SELECT j.*, o.full_name AS owner_name, h.full_name AS helper_name FROM campus_jobs j JOIN student_profiles o ON o.user_id=j.owner_id LEFT JOIN student_profiles h ON h.user_id=j.helper_id WHERE j.id=?", id);
    if (rows.isEmpty()) throw new ApiException(404, "Job not found.");
    return rows.getFirst();
  }

  /**
   * Builds visible job details and optional Xerox metadata. Hidden jobs are unavailable to
   * ordinary students. Removes delivery hash, expiry, and attempt fields before returning;
   * document bytes are retrieved only through the separately authorized attachment method.
   */
  public Map<String, Object> detail(StudentPrincipal p, long id) {
    var j = row(id, false);
    if (Boolean.TRUE.equals(j.get("hidden")) && !admin(p))
      throw new ApiException(404, "Job not found.");
    j.remove("completion_otp_hash");
    j.remove("completion_otp_expires_at");
    j.remove("completion_otp_attempts");
    var files=db.queryForList("SELECT original_name,content_type,file_size,xerox_mode,copies FROM job_attachments WHERE job_id=?",id);
    if(!files.isEmpty()) j.putAll(files.getFirst());
    return j;
  }

  /**
   * Loads a job, optionally locking it, and requires the principal to be its requester or
   * assigned helper. This shared guard protects conversations, private files, reviews, and
   * changes.
   */
  private Map<String, Object> participant(StudentPrincipal p, long id, boolean lock) {
    var j = row(id, lock);
    if (!isOwner(j, p.id()) && !isHelper(j, p.id()))
      throw new ApiException(403, "Only this job's participants can use this feature.");
    return j;
  }

  /**
   * Inserts one persistent notification for a user and job. This writes an in-app record; it
   * does not send email or a mobile push notification.
   */
  private void notify(long u, long job, String text) {
    db.update(
        "INSERT INTO notifications(user_id,job_id,message,created_at) VALUES(?,?,?,?)",
        u,
        job,
        text,
  /**
   * Returns the current UTC Instant as ISO-8601 text, matching the marketplace tables' VARCHAR
   * timestamp columns. This is distinct from JPA account entities' TIMESTAMP columns.
   */
        now());
  }

  /**
   * Creates a notification for the requester and, if assigned, the helper. Uses the job's
   * identity fields so callers do not need to duplicate recipient selection.
   */
  private void both(Map<String, Object> j, String text) {
  /**
   * Inserts one persistent notification for a user and job. This writes an in-app record; it
   * does not send email or a mobile push notification.
   */
    notify(number(j, "owner_id"), number(j, "id"), text);
    if (j.get("helper_id") != null) notify(number(j, "helper_id"), number(j, "id"), text);
  }

  /**
   * Builds a paginated listing using fixed SQL clauses and separately bound values. Mine
   * includes the student's owned/helped jobs; the general market includes only visible,
   * unexpired OPEN jobs. Optional text/category filters apply before newest-first ordering and
   * a 30-row page.
   */
  public List<Map<String, Object>> browse(
      StudentPrincipal p, String q, String category, String scope, int page) {
    if (page < 0 || page > 1000) throw new ApiException(400, "Invalid page.");
    // Keep SQL values in a separate argument list; only fixed application clauses are concatenated.
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

  /**
   * Validates category-specific fields and optional private Xerox bytes, normalizes the
   * deadline, inserts a job with bound parameters, obtains its generated ID, and stores an
   * optional attachment under that ID. Both inserts share this service transaction; the
   * returned detail excludes private bytes and code state.
   */
  public Map<String, Object> post(StudentPrincipal p, MarketDtos.Post r, MultipartFile file) {
    boolean xerox="Xerox".equals(r.category());
    if(xerox&&(file==null||file.isEmpty())) throw new ApiException(400,"Attach the document to your Xerox request.");
    if(xerox&&(r.xeroxMode()==null||r.xeroxMode().isBlank()||r.xeroxCopies()==null)) throw new ApiException(400,"Choose black-and-white or color and enter the copy count.");
    if(!xerox&&(file!=null&&!file.isEmpty()||r.xeroxMode()!=null&&!r.xeroxMode().isBlank()||r.xeroxCopies()!=null)) throw new ApiException(400,"File uploads and print options are only for Xerox requests.");
    byte[] fileBytes=null; String fileType=null; String fileName=null;
    // Inspect byte signatures as well as size; a filename or browser MIME type alone is not
    // trusted.
    if(xerox){
      if(file.getSize()>2_097_152) throw new ApiException(413,"Xerox files must be 2 MB or smaller.");
      try{fileBytes=file.getBytes();}catch(Exception e){throw new ApiException(400,"The Xerox file could not be read.");}
      if(isPdf(fileBytes)) fileType="application/pdf";
      else if(isPng(fileBytes)) fileType="image/png";
      else if(isJpeg(fileBytes)) fileType="image/jpeg";
      else throw new ApiException(400,"Upload a PDF, PNG, or JPEG file.");
      String source=Objects.toString(file.getOriginalFilename(),"xerox-file").replace('\\','/');
      fileName=source.substring(source.lastIndexOf('/')+1).replaceAll("[^A-Za-z0-9._ -]","_");
      if(fileName.isBlank()) fileName="xerox-file";
      if(fileName.length()>255) fileName=fileName.substring(0,255);
    }
    // Normalize deadlines to UTC instants and reject expired or excessively distant requests.
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
  /**
   * Returns the current UTC Instant as ISO-8601 text, matching the marketplace tables' VARCHAR
   * timestamp columns. This is distinct from JPA account entities' TIMESTAMP columns.
   */
            now(),
  /**
   * Returns the current UTC Instant as ISO-8601 text, matching the marketplace tables' VARCHAR
   * timestamp columns. This is distinct from JPA account entities' TIMESTAMP columns.
   */
            now()
          };
          for (int i = 0; i < values.length; i++) ps.setObject(i + 1, values[i]);
          return ps;
        },
        key);
    // Use the generated job key for its optional one-to-one attachment in the same transaction.
    long jobId=Objects.requireNonNull(key.getKey()).longValue();
    if(xerox) db.update("INSERT INTO job_attachments(job_id,original_name,content_type,file_size,xerox_mode,copies,file_data) VALUES(?,?,?,?,?,?,?)",
        jobId,fileName,fileType,fileBytes.length,r.xeroxMode(),r.xeroxCopies(),fileBytes);
    return detail(p, jobId);
  }

  /**
   * Checks the leading %PDF- signature before accepting a document as PDF. This is a basic
   * file-type check, not full PDF parsing or malware scanning.
   */
  private boolean isPdf(byte[] b){return b.length>=5&&b[0]=='%'&&b[1]=='P'&&b[2]=='D'&&b[3]=='F'&&b[4]=='-';}
  /**
   * Checks enough leading bytes to recognize the PNG prefix used by this implementation.
   * Unsigned byte conversion avoids Java's signed-byte representation changing the comparison.
   */
  private boolean isPng(byte[] b){return b.length>8&&(b[0]&255)==137&&b[1]==80&&b[2]==78&&b[3]==71;}
  /**
   * Checks the JPEG start-of-image marker and following marker prefix. Like the other signature
   * helpers, this identifies a basic type rather than decoding the full file.
   */
  private boolean isJpeg(byte[] b){return b.length>3&&(b[0]&255)==255&&(b[1]&255)==216&&(b[2]&255)==255;}

  /**
   * Requires a requester/helper relationship and a visible job before returning private file
   * data. Publicly displayed filename/print metadata is not permission to download the
   * document.
   */
  public Map<String,Object> attachment(StudentPrincipal p,long id){
    var j=participant(p,id,false);
    if(Boolean.TRUE.equals(j.get("hidden"))) throw new ApiException(404,"Job not found.");
    return oneAttachment(id);
  }
  /**
   * Selects filename, content type, and bytes for a job's one-to-one attachment. This helper
   * only performs retrieval: its caller must already have authorized access. Missing files
   * produce HTTP 404.
   */
  private Map<String,Object> oneAttachment(long id){
    var rows=db.queryForList("SELECT original_name,content_type,file_data FROM job_attachments WHERE job_id=?",id);
    if(rows.isEmpty()) throw new ApiException(404,"This job has no uploaded file.");
    return rows.getFirst();
  }

  /**
   * Central job state machine. Locks the job, checks actor/current state, and handles
   * acceptance, starting, ordered progress, handoff-code completion, or cancellation.
   * Acceptance generates the requester's delivery code; completion clears its saved secret.
   * Notifications and state updates share the transaction.
   */
  public Map<String, Object> act(StudentPrincipal p, long id, MarketDtos.Action request) {
    String action=request.action(), actionStep=request.step(), actionOtp=request.otp();
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
        // Create a fresh handoff code at acceptance; only its hash and expiry are persisted.
        String code=String.format(Locale.ROOT,"%06d",random.nextInt(1_000_000));
        String hash=passwords.encode(code);
        String expiry=Instant.now().plusSeconds(8*60*60).toString();
        db.update("UPDATE campus_jobs SET helper_id=?,completion_otp_hash=?,completion_otp_expires_at=?,completion_otp_attempts=0 WHERE id=?",p.id(),hash,expiry,id);
        String ownerEmail=db.queryForObject("SELECT email FROM users WHERE id=?",String.class,number(j,"owner_id"));
        // Send the raw code to the requester, who shares it only after receiving the physical item.
        try {
          email.send(ownerEmail,"JAVAC delivery confirmation code",
              "Your delivery code for \""+j.get("title")+"\" is "+code+". Share it with the helper only after you receive your order. It expires in 8 hours.");
        } catch (MailException unavailable) {
          // Email is an optional notification, not the authority for acceptance. The requester
          // can generate a fresh code in-app after delivery; never log codes or mail contents.
          log.warn("Delivery email unavailable for job {}; requester can generate a handoff code in-app", id);
        }
        next = "ACCEPTED";
      }
      case "start" -> {
        if (!isHelper(j, p.id()) || !"ACCEPTED".equals(status))
          throw new ApiException(409, "Only the accepted helper can start this job.");
        next = "IN_PROGRESS";
      }
      case "deliver" -> {
        throw new ApiException(400,"Use the delivery checklist to update progress.");
      }
      case "progress" -> {
        if(!isHelper(j,p.id())||!"IN_PROGRESS".equals(status)) throw new ApiException(409,"Only the accepted helper can update an active job.");
        // Each category has an ordered manual checklist; these entries are not GPS updates.
        var steps="Xerox".equals(j.get("category"))
            ? List.of("FILE_RECEIVED","XEROX_COMPLETE","ON_THE_WAY","AT_BLOCK")
            : List.of("PICKED_UP","ON_THE_WAY","AT_BLOCK");
        String current=Objects.toString(j.get("progress_step"),"NOT_STARTED");
        int index="NOT_STARTED".equals(current)?-1:steps.indexOf(current);
        // Accept only the immediately following step, preventing skipped or repeated progress
        // updates.
        if(index< -1||index+1>=steps.size()||!Objects.equals(steps.get(index+1),actionStep)) throw new ApiException(409,"Complete the next checklist step in order.");
        next=actionStep.equals("AT_BLOCK")?"DELIVERED":"IN_PROGRESS";
        db.update("UPDATE campus_jobs SET progress_step=?,status=?,updated_at=? WHERE id=?",actionStep,next,now(),id);
        var updated=detail(p,id); both(updated,"Delivery update: "+progressLabel(actionStep)+" — "+j.get("title"));
        return updated;
      }
      case "complete" -> {
        // The helper enters the requester-provided code only after the final checklist step.
        if (!isHelper(j, p.id()) || !"DELIVERED".equals(status))
          throw new ApiException(409, "Only the accepted helper can enter the requester's code at handoff.");
        String saved=Objects.toString(j.get("completion_otp_hash"),"");
        Object expires=j.get("completion_otp_expires_at");
        int attempts=((Number)j.get("completion_otp_attempts")).intValue();
        if(saved.isBlank()||attempts>=5||expires==null||!Instant.parse(expires.toString()).isAfter(Instant.now()))
          // Return a normal result with otp_error so failure-state writes are not rolled back by an
          // exception.
          return otpFailure(p,id,"The delivery code has expired or is locked. Ask the requester to contact support.");
        String supplied=actionOtp==null?"":actionOtp;
        if(!passwords.matches(supplied,saved)){
          // Persist the failed attempt before returning the error; repeated wrong guesses
          // eventually lock the code.
          db.update("UPDATE campus_jobs SET completion_otp_attempts=completion_otp_attempts+1 WHERE id=?",id);
          return otpFailure(p,id,"That code is incorrect. Check with the requester and try again.");
        }
        next = "COMPLETED";
      }
      case "cancel" -> {
        if (!Set.of("OPEN", "ACCEPTED", "IN_PROGRESS").contains(status))
          throw new ApiException(409, "This job can no longer be cancelled.");
        next = "CANCELLED";
      }
      default -> throw new ApiException(400, "Unknown action.");
    }
    // Successful handoff removes the stored code hash and expiry so completion cannot reuse them.
    if("complete".equals(action)) db.update("UPDATE campus_jobs SET status=?,completion_otp_hash=NULL,completion_otp_expires_at=NULL,updated_at=? WHERE id=?",next,now(),id);
    else db.update("UPDATE campus_jobs SET status=?,updated_at=? WHERE id=?", next, now(), id);
    var result = detail(p, id);
  /**
   * Creates a notification for the requester and, if assigned, the helper. Uses the job's
   * identity fields so callers do not need to duplicate recipient selection.
   */
    both(result, "Job " + next.toLowerCase(Locale.ROOT).replace('_', ' ') + ": " + j.get("title"));
    return result;
  }

  /**
   * Issues a replacement code only to the signed-in requester of a visible DELIVERED job.
   * Locking the job serializes reissue with completion. Only the encoded verifier is stored;
   * the raw code is returned once through a private no-store response, never job detail/chat.
   * This also recovers expired/locked codes without relying on an SMTP connection.
   */
  public Map<String, String> handoffCode(StudentPrincipal p, long id) {
    var j = row(id, true);
    if (!isOwner(j, p.id()))
      throw new ApiException(403, "Only the requester can generate the delivery code.");
    if (Boolean.TRUE.equals(j.get("hidden")) || !"DELIVERED".equals(j.get("status")))
      throw new ApiException(409, "Generate the code after the helper reaches your block and you receive the order.");
    String code = String.format(Locale.ROOT, "%06d", random.nextInt(1_000_000));
    String expiry = Instant.now().plusSeconds(8 * 60 * 60).toString();
    db.update(
        "UPDATE campus_jobs SET completion_otp_hash=?,completion_otp_expires_at=?,completion_otp_attempts=0 WHERE id=?",
        passwords.encode(code), expiry, id);
    return Map.of("code", code, "expiresAt", expiry);
  }

  /**
   * Adds an otp_error field to a normal job detail result. Returning normally is intentional:
   * throwing a runtime exception here would roll back the failed-attempt increment in the
   * service transaction.
   */
  private Map<String,Object> otpFailure(StudentPrincipal p,long id,String message){var result=detail(p,id);result.put("otp_error",message);return result;}
  /**
   * Converts internal manual-checklist constants into readable notification text.
   * Category/state validation occurs in act before any update is written.
   */
  private String progressLabel(String step){return switch(step){case "FILE_RECEIVED"->"File received";case "XEROX_COMPLETE"->"Xerox complete";case "PICKED_UP"->"Order picked up";case "ON_THE_WAY"->"On the way";case "AT_BLOCK"->"At your block";default->"Updated";};}

  /**
   * Acceptance must be available to an eligible nonparticipant, so it locks the job directly.
   * Every other action also requires requester/helper membership. act then applies action-
   * specific actor restrictions.
   */
  private Map<String, Object> participantOrAccept(StudentPrincipal p, long id, String action) {
    return "accept".equals(action) ? row(id, true) : participant(p, id, true);
  }

  /**
   * Authorizes participant access, joins each message sender to a profile name, and returns at
   * most 100 newest messages. The client handles display order; this query orders IDs
   * descending.
   */
  public List<Map<String, Object>> messages(StudentPrincipal p, long id) {
  /**
   * Loads a job, optionally locking it, and requires the principal to be its requester or
   * assigned helper. This shared guard protects conversations, private files, reviews, and
   * changes.
   */
    participant(p, id, false);
    return db.queryForList(
        "SELECT m.id,m.sender_id,m.body,m.created_at,p.full_name AS sender_name FROM job_messages m JOIN student_profiles p ON p.user_id=m.sender_id WHERE m.job_id=? ORDER BY m.id DESC LIMIT 100",
        id);
  }

  /**
   * Locks and authorizes the conversation, requires an assigned helper, and rejects closed
   * jobs. Inserts trimmed text and notifies the other participant in the same transaction.
   */
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
  /**
   * Returns the current UTC Instant as ISO-8601 text, matching the marketplace tables' VARCHAR
   * timestamp columns. This is distinct from JPA account entities' TIMESTAMP columns.
   */
        now());
  /**
   * Inserts one persistent notification for a user and job. This writes an in-app record; it
   * does not send email or a mobile push notification.
   */
    notify(
  /**
   * Compares the authenticated account ID with owner_id, the student who posted this job. It is
   * a role check for this specific job, not a global account role.
   */
        isOwner(j, p.id()) ? number(j, "helper_id") : number(j, "owner_id"),
        id,
        "New message about " + j.get("title"));
  }

  /**
   * Returns only the signed-in account's latest 100 notification rows. The user_id predicate
   * prevents reading another student's notifications.
   */
  public List<Map<String, Object>> notifications(StudentPrincipal p) {
    return db.queryForList(
        "SELECT * FROM notifications WHERE user_id=? ORDER BY id DESC LIMIT 100", p.id());
  }

  /**
   * Marks a notification read only when both its ID and owning user match. A zero-row update
   * becomes HTTP 404 rather than disclosing another user's record.
   */
  public void read(StudentPrincipal p, long id) {
    if (db.update("UPDATE notifications SET is_read=TRUE WHERE id=? AND user_id=?", id, p.id())
        != 1) throw new ApiException(404, "Notification not found.");
  }

  /**
   * Reads completed jobs performed by this helper and sums rewards with exact BigDecimal
   * arithmetic. Earned minus recorded collected is pending offline payment; none of these
   * totals initiate transfers or update wallet_accounts.
   */
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

  /**
   * Locks a completed job and permits only its helper to mark an UNPAID reward COLLECTED with a
   * reported external method/time. This is an offline collection record, not payment-provider
   * verification or wallet settlement.
   */
  public void collect(StudentPrincipal p, long id, String method) {
    var j = participant(p, id, true);
    if (!isHelper(j, p.id())
        || !"COMPLETED".equals(j.get("status"))
        || !"UNPAID".equals(j.get("payment_status")))
      throw new ApiException(409, "Only the helper can confirm an uncollected completed job.");
    db.update(
        "UPDATE campus_jobs SET payment_status='COLLECTED',payment_method=?,collected_at=?,updated_at=? WHERE id=?",
        method,
  /**
   * Returns the current UTC Instant as ISO-8601 text, matching the marketplace tables' VARCHAR
   * timestamp columns. This is distinct from JPA account entities' TIMESTAMP columns.
   */
        now(),
  /**
   * Returns the current UTC Instant as ISO-8601 text, matching the marketplace tables' VARCHAR
   * timestamp columns. This is distinct from JPA account entities' TIMESTAMP columns.
   */
        now(),
        id);
  /**
   * Creates a notification for the requester and, if assigned, the helper. Uses the job's
   * identity fields so callers do not need to duplicate recipient selection.
   */
    both(j, "Helper confirmed offline payment received: " + j.get("title"));
  }

  /**
   * Checks job visibility through detail and returns feedback with reviewer names, ordered
   * newest first. This read does not expose email addresses or account credentials.
   */
  public List<Map<String, Object>> reviews(StudentPrincipal p, long id) {
  /**
   * Builds visible job details and optional Xerox metadata. Hidden jobs are unavailable to
   * ordinary students. Removes delivery hash, expiry, and attempt fields before returning;
   * document bytes are retrieved only through the separately authorized attachment method.
   */
    detail(p, id);
    return db.queryForList(
        "SELECT r.rating,r.comment,r.recipient_id,p.full_name AS reviewer_name,r.created_at FROM job_reviews r JOIN student_profiles p ON p.user_id=r.reviewer_id WHERE job_id=? ORDER BY r.id DESC",
        id);
  }

  /**
   * Locks a participant's completed job, checks for a previous review by this reviewer, and
   * inserts feedback addressed to the other participant. The UNIQUE(job_id,reviewer_id)
   * constraint supplies a final duplicate-write guard.
   */
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
  /**
   * Compares the authenticated account ID with owner_id, the student who posted this job. It is
   * a role check for this specific job, not a global account role.
   */
        isOwner(j, p.id()) ? number(j, "helper_id") : number(j, "owner_id"),
        r.rating(),
        r.comment().trim(),
  /**
   * Returns the current UTC Instant as ISO-8601 text, matching the marketplace tables' VARCHAR
   * timestamp columns. This is distinct from JPA account entities' TIMESTAMP columns.
   */
        now());
  }

  /**
   * Checks that the job is visible to the caller and inserts a trimmed moderation reason with
   * reporter and job IDs. Filing a report itself does not hide a listing.
   */
  public void report(StudentPrincipal p, long id, String reason) {
  /**
   * Builds visible job details and optional Xerox metadata. Hidden jobs are unavailable to
   * ordinary students. Removes delivery hash, expiry, and attempt fields before returning;
   * document bytes are retrieved only through the separately authorized attachment method.
   */
    detail(p, id);
    db.update(
        "INSERT INTO job_reports(job_id,reporter_id,reason,created_at) VALUES(?,?,?,?)",
        id,
        p.id(),
        reason.trim(),
  /**
   * Returns the current UTC Instant as ISO-8601 text, matching the marketplace tables' VARCHAR
   * timestamp columns. This is distinct from JPA account entities' TIMESTAMP columns.
   */
        now());
  }

  /**
   * Returns selected public profile fields only for a verified ACTIVE account, plus up to 30
   * received reviews. It deliberately excludes email, registration number, password, and
   * session details.
   */
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

  /**
   * Requires administrator access before loading the latest user, job, and report records.
   * Hidden jobs remain in this overview so moderation can inspect them.
   */
  public Map<String, Object> adminOverview(StudentPrincipal p) {
  /**
   * Rejects non-administrator callers with HTTP 403. This server-side guard applies even if
   * someone bypasses the React admin screen.
   */
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

  /**
   * Requires admin access and rejects self-administration. Changes only a verified non-admin
   * account's status and increments credential_version in the same SQL update, causing older
   * sessions to fail subsequent account checks.
   */
  public void account(StudentPrincipal p, long id, String status) {
  /**
   * Rejects non-administrator callers with HTTP 403. This server-side guard applies even if
   * someone bypasses the React admin screen.
   */
    requireAdmin(p);
    if (id == p.id()) throw new ApiException(400, "You cannot suspend your own admin account.");
    if (db.update(
            "UPDATE users SET account_status=?,credential_version=credential_version+1 WHERE id=? AND email_verified=TRUE AND email<>?",
            status,
            id,
            adminEmail)
        != 1) throw new ApiException(409, "Only verified non-admin accounts can be changed.");
  }

  /**
   * Locks an administrator-selected job and marks it hidden. Active workflow states become
   * CANCELLED; already completed/cancelled states are retained. Both participants receive an
   * in-app removal notification.
   */
  public void hide(StudentPrincipal p, long id) {
  /**
   * Rejects non-administrator callers with HTTP 403. This server-side guard applies even if
   * someone bypasses the React admin screen.
   */
    requireAdmin(p);
    var j = row(id, true);
    db.update(
        "UPDATE campus_jobs SET hidden=TRUE,status=CASE WHEN status IN ('OPEN','ACCEPTED','IN_PROGRESS','DELIVERED') THEN 'CANCELLED' ELSE status END,updated_at=? WHERE id=?",
  /**
   * Returns the current UTC Instant as ISO-8601 text, matching the marketplace tables' VARCHAR
   * timestamp columns. This is distinct from JPA account entities' TIMESTAMP columns.
   */
        now(),
        id);
  /**
   * Creates a notification for the requester and, if assigned, the helper. Uses the job's
   * identity fields so callers do not need to duplicate recipient selection.
   */
    both(j, "An administrator removed this listing: " + j.get("title"));
  }

  /**
   * Requires admin access and marks the selected report resolved. Checks the affected-row count
   * so nonexistent IDs return HTTP 404.
   */
  public void resolve(StudentPrincipal p, long id) {
  /**
   * Rejects non-administrator callers with HTTP 403. This server-side guard applies even if
   * someone bypasses the React admin screen.
   */
    requireAdmin(p);
    if (db.update("UPDATE job_reports SET resolved=TRUE WHERE id=?", id) != 1)
      throw new ApiException(404, "Report not found.");
  }
}
