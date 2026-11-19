package com.javac.campus.marketplace;

import com.javac.campus.common.ApiException;
import com.javac.campus.security.StudentPrincipal;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/**
 * Transactional wallet balances, ledger entries, and administrator-reviewed requests.
 * External payments are performed manually; this service records approval or payout references.
 * A completed campus job does not automatically credit this wallet.
 *
 * Balances use BigDecimal in Java and DECIMAL in SQL. Positive ledger amounts credit funds and
 * negative amounts reserve/debit funds; reference_id links entries to a request by application
 * convention. @Transactional keeps balance, request, and ledger changes atomic.
 */
@Service
@Transactional
public class WalletService {
  // Shared connection source through JdbcTemplate; transaction participation spans all wallet
  // writes.
  private final JdbcTemplate db;
  // Trusted admin identity used for manual payment-request review.
  private final String adminEmail;

  /**
   * Constructor injection: Spring supplies the collaborators declared here, so this class can
   * delegate work without constructing its own repositories or services.
   */
  public WalletService(JdbcTemplate db, @Value("${app.admin-email}") String adminEmail) {
    this.db = db;
    this.adminEmail = adminEmail;
  }

  /**
   * Returns a UTC ISO-8601 string for wallet VARCHAR timestamp columns. A method may reuse one
   * value so its related ledger/request changes share a timestamp.
   */
  private String now() { return Instant.now().toString(); }
  /**
   * Uses the configured administrator email to protect review operations on the server. Access
   * to an admin route or screen alone does not grant this permission.
   */
  private void requireAdmin(StudentPrincipal p) {
    if (!p.email().equalsIgnoreCase(adminEmail)) throw new ApiException(403, "Administrator access required.");
  }
  /**
   * Requires an exact decimal value from INR 1 through INR 50,000 with at most two fractional
   * digits, then normalizes its scale to two. BigDecimal avoids floating-point rounding in
   * balances.
   */
  private BigDecimal amount(BigDecimal value) {
    if (value == null || value.scale() > 2 || value.compareTo(BigDecimal.ONE) < 0
        || value.compareTo(new BigDecimal("50000.00")) > 0)
      throw new ApiException(400, "Enter an amount between ₹1 and ₹50,000, with up to two decimals.");
    return value.setScale(2);
  }
  /**
   * Executes a parameterized SELECT and returns its first row, or a safe HTTP 404. The SQL text
   * comes from fixed service statements; supplied arguments are bound separately.
   */
  private Map<String,Object> one(String sql, Object... args) {
    var rows=db.queryForList(sql,args);
    if(rows.isEmpty()) throw new ApiException(404,"Wallet request not found.");
    return rows.getFirst();
  }

  /**
   * Inserts a wallet account with INR 100 and a completed positive WELCOME_CREDIT ledger entry
   * during registration. The primary key user_id prevents a second wallet for the same account;
   * the surrounding transaction makes both writes succeed or fail together.
   */
  public void createWelcomeCredit(long userId) {
    String at=now();
    db.update("INSERT INTO wallet_accounts(user_id,balance,updated_at) VALUES(?,?,?)",userId,new BigDecimal("100.00"),at);
    db.update("INSERT INTO wallet_transactions(user_id,kind,amount,status,note,created_at) VALUES(?,?,?,?,?,?)",
        userId,"WELCOME_CREDIT",new BigDecimal("100.00"),"COMPLETED","One-time JAVAC welcome credit",at);
  }

  /**
   * Loads only the principal's available balance and bounded histories of ledger entries, top-
   * ups, and withdrawals. Payment-proof bytes are omitted from this student view.
   */
  public Map<String,Object> view(StudentPrincipal p) {
    var account=one("SELECT balance FROM wallet_accounts WHERE user_id=?",p.id());
    return Map.of("balance",account.get("balance"),
        "transactions",db.queryForList("SELECT id,kind,amount,status,reference_id,note,created_at FROM wallet_transactions WHERE user_id=? ORDER BY id DESC LIMIT 100",p.id()),
        "topups",db.queryForList("SELECT id,amount,utr,status,review_note,created_at,reviewed_at FROM wallet_topup_requests WHERE user_id=? ORDER BY id DESC LIMIT 50",p.id()),
        "withdrawals",db.queryForList("SELECT id,amount,upi_id,status,payment_utr,review_note,created_at,reviewed_at FROM wallet_withdrawal_requests WHERE user_id=? ORDER BY id DESC LIMIT 50",p.id()));
  }

  /**
   * Normalizes and validates the external-payment UTR and amount, validates optional PNG/JPEG
   * proof, and stores a PENDING request with its generated ID. It does not change available
   * balance; an administrator must verify and approve the payment first.
   */
  public Map<String,Object> topup(StudentPrincipal p, BigDecimal rawAmount, String rawUtr, MultipartFile proof) {
    BigDecimal value=amount(rawAmount);
    String utr=rawUtr==null?"":rawUtr.trim().replaceAll("\\s+","").toUpperCase(Locale.ROOT);
    // Normalize reference spelling before validation; the database also enforces unique top-up
    // UTRs.
    if(!utr.matches("[A-Z0-9-]{6,40}")) throw new ApiException(400,"Enter the UTR/reference from your payment receipt.");
    byte[] bytes=null; String contentType=null;
    // Optional proof is limited to 1 MB and checked for PNG/JPEG signatures before private storage.
    if(proof!=null&&!proof.isEmpty()){
      if(proof.getSize()>1_048_576) throw new ApiException(413,"Payment proof must be smaller than 1 MB.");
      try { bytes=proof.getBytes(); } catch(Exception e){ throw new ApiException(400,"Could not read the payment proof."); }
      if(isPng(bytes)) contentType="image/png";
      else if(isJpeg(bytes)) contentType="image/jpeg";
      else throw new ApiException(400,"Upload a PNG or JPEG payment screenshot.");
    }
    var key=new org.springframework.jdbc.support.GeneratedKeyHolder();
    byte[] payload=bytes; String mime=contentType;
    db.update(c->{var ps=c.prepareStatement("INSERT INTO wallet_topup_requests(user_id,amount,utr,proof_data,proof_type,status,created_at) VALUES(?,?,?,?,?,'PENDING',?)",new String[]{"id"});
      ps.setLong(1,p.id()); ps.setBigDecimal(2,value); ps.setString(3,utr); ps.setBytes(4,payload); ps.setString(5,mime); ps.setString(6,now()); return ps;},key);
    long id=Objects.requireNonNull(key.getKey()).longValue();
    return one("SELECT id,amount,utr,status,created_at FROM wallet_topup_requests WHERE id=? AND user_id=?",id,p.id());
  }

  /**
   * Recognizes the PNG byte prefix for an optional proof upload; the size limit is checked
   * separately before reading/storing bytes.
   */
  private boolean isPng(byte[] b){return b.length>8&&(b[0]&255)==137&&b[1]==80&&b[2]==78&&b[3]==71;}
  /**
   * Recognizes the JPEG byte prefix for an optional proof upload. A matching signature is a
   * basic type check, not full image decoding.
   */
  private boolean isJpeg(byte[] b){return b.length>3&&(b[0]&255)==255&&(b[1]&255)==216&&(b[2]&255)==255;}

  /**
   * Locks the student's balance row before checking funds. Reserves the requested amount by
   * subtracting it immediately, inserts a PENDING withdrawal, and records a negative PENDING
   * ledger entry linked by reference_id. These writes commit together.
   */
  public Map<String,Object> withdraw(StudentPrincipal p, WalletDtos.Withdrawal r) {
    BigDecimal value=amount(r.amount()); String upi=r.upiId().trim();
    var rows=db.queryForList("SELECT balance FROM wallet_accounts WHERE user_id=? FOR UPDATE",p.id());
    if(rows.isEmpty()) throw new ApiException(404,"Wallet not found.");
    BigDecimal balance=(BigDecimal)rows.getFirst().get("balance");
    if(balance.compareTo(value)<0) throw new ApiException(409,"Your available wallet balance is too low for this withdrawal.");
    String at=now();
    // Reservation and the pending withdrawal/ledger writes belong to the same service transaction.
    db.update("UPDATE wallet_accounts SET balance=balance-?,updated_at=? WHERE user_id=?",value,at,p.id());
    var key=new org.springframework.jdbc.support.GeneratedKeyHolder();
    db.update(c->{var ps=c.prepareStatement("INSERT INTO wallet_withdrawal_requests(user_id,amount,upi_id,status,created_at) VALUES(?,?,?,'PENDING',?)",new String[]{"id"});ps.setLong(1,p.id());ps.setBigDecimal(2,value);ps.setString(3,upi);ps.setString(4,at);return ps;},key);
    long id=Objects.requireNonNull(key.getKey()).longValue();
    db.update("INSERT INTO wallet_transactions(user_id,kind,amount,status,reference_id,note,created_at) VALUES(?,?,?,?,?,?,?)",p.id(),"WITHDRAWAL",value.negate(),"PENDING",id,"Withdrawal request pending admin payment",at);
    return one("SELECT id,amount,upi_id,status,created_at FROM wallet_withdrawal_requests WHERE id=?",id);
  }

  /**
   * Checks administrator permission, joins request records to student/account identities, and
   * returns bounded top-up/withdrawal queues with PENDING items first. Proof bytes are
   * available through a separate protected endpoint.
   */
  public Map<String,Object> adminQueue(StudentPrincipal p) {
  /**
   * Uses the configured administrator email to protect review operations on the server. Access
   * to an admin route or screen alone does not grant this permission.
   */
    requireAdmin(p);
    return Map.of("topups",db.queryForList("SELECT t.id,t.user_id,t.amount,t.utr,t.proof_type,t.status,t.created_at,p.full_name,u.email FROM wallet_topup_requests t JOIN users u ON u.id=t.user_id JOIN student_profiles p ON p.user_id=u.id ORDER BY CASE WHEN t.status='PENDING' THEN 0 ELSE 1 END,t.id DESC LIMIT 100"),
        "withdrawals",db.queryForList("SELECT w.id,w.user_id,w.amount,w.upi_id,w.status,w.payment_utr,w.created_at,p.full_name,u.email FROM wallet_withdrawal_requests w JOIN users u ON u.id=w.user_id JOIN student_profiles p ON p.user_id=u.id ORDER BY CASE WHEN w.status='PENDING' THEN 0 ELSE 1 END,w.id DESC LIMIT 100"));
  }

  /**
   * Authorizes the administrator, then returns stored screenshot bytes and MIME type only when
   * proof exists. Student request history does not expose these private bytes.
   */
  public Map<String,Object> topupProof(StudentPrincipal p,long id) {
  /**
   * Uses the configured administrator email to protect review operations on the server. Access
   * to an admin route or screen alone does not grant this permission.
   */
    requireAdmin(p);
    return one("SELECT proof_data,proof_type FROM wallet_topup_requests WHERE id=? AND proof_data IS NOT NULL",id);
  }

  /**
   * Locks the request and requires PENDING status to prevent duplicate credit. APPROVED adds
   * the amount to balance and creates a completed positive TOPUP ledger entry; REJECTED adds no
   * balance. Both paths record reviewer, note, status, and review time.
   */
  public void reviewTopup(StudentPrincipal p,long id,WalletDtos.TopupReview r) {
  /**
   * Uses the configured administrator email to protect review operations on the server. Access
   * to an admin route or screen alone does not grant this permission.
   */
    requireAdmin(p);
    db.queryForList("SELECT id FROM wallet_topup_requests WHERE id=? FOR UPDATE",id);
    var request=one("SELECT * FROM wallet_topup_requests WHERE id=?",id);
    if(!"PENDING".equals(request.get("status"))) throw new ApiException(409,"This top-up has already been reviewed.");
    String at=now(); BigDecimal value=(BigDecimal)request.get("amount"); long user=((Number)request.get("user_id")).longValue();
    // Only verified approval credits the wallet and adds a completed TOPUP ledger entry.
    if("APPROVED".equals(r.status())){
      db.update("UPDATE wallet_accounts SET balance=balance+?,updated_at=? WHERE user_id=?",value,at,user);
      db.update("INSERT INTO wallet_transactions(user_id,kind,amount,status,reference_id,note,created_at) VALUES(?,?,?,?,?,?,?)",user,"TOPUP",value,"COMPLETED",id,"Admin verified UTR "+request.get("utr"),at);
    }
    db.update("UPDATE wallet_topup_requests SET status=?,reviewed_by=?,review_note=?,reviewed_at=? WHERE id=?",r.status(),p.id(),trim(r.note()),at,id);
  }

  /**
   * Locks a PENDING withdrawal for exactly-once review. PAID requires an outgoing UTR and
   * completes its existing reserved debit without subtracting again. REJECTED restores funds,
   * marks the debit rejected, and adds a positive refund entry. External payment itself happens
   * outside this application.
   */
  public void reviewWithdrawal(StudentPrincipal p,long id,WalletDtos.WithdrawalReview r) {
  /**
   * Uses the configured administrator email to protect review operations on the server. Access
   * to an admin route or screen alone does not grant this permission.
   */
    requireAdmin(p);
    db.queryForList("SELECT id FROM wallet_withdrawal_requests WHERE id=? FOR UPDATE",id);
    var request=one("SELECT * FROM wallet_withdrawal_requests WHERE id=?",id);
    if(!"PENDING".equals(request.get("status"))) throw new ApiException(409,"This withdrawal has already been reviewed.");
    String utr=r.paymentUtr()==null?"":r.paymentUtr().trim().replaceAll("\\s+","").toUpperCase(Locale.ROOT);
    // A paid decision must include the outgoing bank reference supplied by the administrator.
    if("PAID".equals(r.status())&&!utr.matches("[A-Z0-9-]{6,40}")) throw new ApiException(400,"Enter the outgoing payment UTR before marking this withdrawal paid.");
    String at=now(); long user=((Number)request.get("user_id")).longValue(); BigDecimal value=(BigDecimal)request.get("amount");
    // Restore reserved funds and preserve both the rejected debit and its refund in the ledger.
    if("REJECTED".equals(r.status())){
      db.update("UPDATE wallet_accounts SET balance=balance+?,updated_at=? WHERE user_id=?",value,at,user);
      db.update("INSERT INTO wallet_transactions(user_id,kind,amount,status,reference_id,note,created_at) VALUES(?,?,?,?,?,?,?)",user,"WITHDRAWAL_REFUND",value,"COMPLETED",id,"Rejected withdrawal returned to wallet",at);
      db.update("UPDATE wallet_transactions SET status='REJECTED' WHERE user_id=? AND kind='WITHDRAWAL' AND reference_id=?",user,id);
    }else{
      db.update("UPDATE wallet_transactions SET status='COMPLETED',note=? WHERE user_id=? AND kind='WITHDRAWAL' AND reference_id=?", "Admin marked payout paid, UTR "+utr,user,id);
    }
    db.update("UPDATE wallet_withdrawal_requests SET status=?,payment_utr=?,reviewed_by=?,review_note=?,reviewed_at=? WHERE id=?",r.status(),utr.isBlank()?null:utr,p.id(),trim(r.note()),at,id);
  }

  /**
   * Normalizes an optional administrator note to a non-null trimmed string before persistence.
   */
  private String trim(String s){return s==null?"":s.trim();}
}
