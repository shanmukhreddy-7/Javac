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

/** Manual, auditable wallet requests. Credits happen only after an administrator verifies receipt. */
@Service
@Transactional
public class WalletService {
  private final JdbcTemplate db;
  private final String adminEmail;

  public WalletService(JdbcTemplate db, @Value("${app.admin-email}") String adminEmail) {
    this.db = db;
    this.adminEmail = adminEmail;
  }

  private String now() { return Instant.now().toString(); }
  private void requireAdmin(StudentPrincipal p) {
    if (!p.email().equalsIgnoreCase(adminEmail)) throw new ApiException(403, "Administrator access required.");
  }
  private BigDecimal amount(BigDecimal value) {
    if (value == null || value.scale() > 2 || value.compareTo(BigDecimal.ONE) < 0
        || value.compareTo(new BigDecimal("50000.00")) > 0)
      throw new ApiException(400, "Enter an amount between ₹1 and ₹50,000, with up to two decimals.");
    return value.setScale(2);
  }
  private Map<String,Object> one(String sql, Object... args) {
    var rows=db.queryForList(sql,args);
    if(rows.isEmpty()) throw new ApiException(404,"Wallet request not found.");
    return rows.getFirst();
  }

  public void createWelcomeCredit(long userId) {
    String at=now();
    db.update("INSERT INTO wallet_accounts(user_id,balance,updated_at) VALUES(?,?,?)",userId,new BigDecimal("100.00"),at);
    db.update("INSERT INTO wallet_transactions(user_id,kind,amount,status,note,created_at) VALUES(?,?,?,?,?,?)",
        userId,"WELCOME_CREDIT",new BigDecimal("100.00"),"COMPLETED","One-time JAVAC welcome credit",at);
  }

  public Map<String,Object> view(StudentPrincipal p) {
    var account=one("SELECT balance FROM wallet_accounts WHERE user_id=?",p.id());
    return Map.of("balance",account.get("balance"),
        "transactions",db.queryForList("SELECT id,kind,amount,status,reference_id,note,created_at FROM wallet_transactions WHERE user_id=? ORDER BY id DESC LIMIT 100",p.id()),
        "topups",db.queryForList("SELECT id,amount,utr,status,review_note,created_at,reviewed_at FROM wallet_topup_requests WHERE user_id=? ORDER BY id DESC LIMIT 50",p.id()),
        "withdrawals",db.queryForList("SELECT id,amount,upi_id,status,payment_utr,review_note,created_at,reviewed_at FROM wallet_withdrawal_requests WHERE user_id=? ORDER BY id DESC LIMIT 50",p.id()));
  }

  public Map<String,Object> topup(StudentPrincipal p, BigDecimal rawAmount, String rawUtr, MultipartFile proof) {
    BigDecimal value=amount(rawAmount);
    String utr=rawUtr==null?"":rawUtr.trim().replaceAll("\\s+","").toUpperCase(Locale.ROOT);
    if(!utr.matches("[A-Z0-9-]{6,40}")) throw new ApiException(400,"Enter the UTR/reference from your payment receipt.");
    byte[] bytes=null; String contentType=null;
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

  private boolean isPng(byte[] b){return b.length>8&&(b[0]&255)==137&&b[1]==80&&b[2]==78&&b[3]==71;}
  private boolean isJpeg(byte[] b){return b.length>3&&(b[0]&255)==255&&(b[1]&255)==216&&(b[2]&255)==255;}

  public Map<String,Object> withdraw(StudentPrincipal p, WalletDtos.Withdrawal r) {
    BigDecimal value=amount(r.amount()); String upi=r.upiId().trim();
    var rows=db.queryForList("SELECT balance FROM wallet_accounts WHERE user_id=? FOR UPDATE",p.id());
    if(rows.isEmpty()) throw new ApiException(404,"Wallet not found.");
    BigDecimal balance=(BigDecimal)rows.getFirst().get("balance");
    if(balance.compareTo(value)<0) throw new ApiException(409,"Your available wallet balance is too low for this withdrawal.");
    String at=now();
    db.update("UPDATE wallet_accounts SET balance=balance-?,updated_at=? WHERE user_id=?",value,at,p.id());
    var key=new org.springframework.jdbc.support.GeneratedKeyHolder();
    db.update(c->{var ps=c.prepareStatement("INSERT INTO wallet_withdrawal_requests(user_id,amount,upi_id,status,created_at) VALUES(?,?,?,'PENDING',?)",new String[]{"id"});ps.setLong(1,p.id());ps.setBigDecimal(2,value);ps.setString(3,upi);ps.setString(4,at);return ps;},key);
    long id=Objects.requireNonNull(key.getKey()).longValue();
    db.update("INSERT INTO wallet_transactions(user_id,kind,amount,status,reference_id,note,created_at) VALUES(?,?,?,?,?,?,?)",p.id(),"WITHDRAWAL",value.negate(),"PENDING",id,"Withdrawal request pending admin payment",at);
    return one("SELECT id,amount,upi_id,status,created_at FROM wallet_withdrawal_requests WHERE id=?",id);
  }

  public Map<String,Object> adminQueue(StudentPrincipal p) {
    requireAdmin(p);
    return Map.of("topups",db.queryForList("SELECT t.id,t.user_id,t.amount,t.utr,t.proof_type,t.status,t.created_at,p.full_name,u.email FROM wallet_topup_requests t JOIN users u ON u.id=t.user_id JOIN student_profiles p ON p.user_id=u.id ORDER BY CASE WHEN t.status='PENDING' THEN 0 ELSE 1 END,t.id DESC LIMIT 100"),
        "withdrawals",db.queryForList("SELECT w.id,w.user_id,w.amount,w.upi_id,w.status,w.payment_utr,w.created_at,p.full_name,u.email FROM wallet_withdrawal_requests w JOIN users u ON u.id=w.user_id JOIN student_profiles p ON p.user_id=u.id ORDER BY CASE WHEN w.status='PENDING' THEN 0 ELSE 1 END,w.id DESC LIMIT 100"));
  }

  public Map<String,Object> topupProof(StudentPrincipal p,long id) {
    requireAdmin(p);
    return one("SELECT proof_data,proof_type FROM wallet_topup_requests WHERE id=? AND proof_data IS NOT NULL",id);
  }

  public void reviewTopup(StudentPrincipal p,long id,WalletDtos.TopupReview r) {
    requireAdmin(p);
    db.queryForList("SELECT id FROM wallet_topup_requests WHERE id=? FOR UPDATE",id);
    var request=one("SELECT * FROM wallet_topup_requests WHERE id=?",id);
    if(!"PENDING".equals(request.get("status"))) throw new ApiException(409,"This top-up has already been reviewed.");
    String at=now(); BigDecimal value=(BigDecimal)request.get("amount"); long user=((Number)request.get("user_id")).longValue();
    if("APPROVED".equals(r.status())){
      db.update("UPDATE wallet_accounts SET balance=balance+?,updated_at=? WHERE user_id=?",value,at,user);
      db.update("INSERT INTO wallet_transactions(user_id,kind,amount,status,reference_id,note,created_at) VALUES(?,?,?,?,?,?,?)",user,"TOPUP",value,"COMPLETED",id,"Admin verified UTR "+request.get("utr"),at);
    }
    db.update("UPDATE wallet_topup_requests SET status=?,reviewed_by=?,review_note=?,reviewed_at=? WHERE id=?",r.status(),p.id(),trim(r.note()),at,id);
  }

  public void reviewWithdrawal(StudentPrincipal p,long id,WalletDtos.WithdrawalReview r) {
    requireAdmin(p);
    db.queryForList("SELECT id FROM wallet_withdrawal_requests WHERE id=? FOR UPDATE",id);
    var request=one("SELECT * FROM wallet_withdrawal_requests WHERE id=?",id);
    if(!"PENDING".equals(request.get("status"))) throw new ApiException(409,"This withdrawal has already been reviewed.");
    String utr=r.paymentUtr()==null?"":r.paymentUtr().trim().replaceAll("\\s+","").toUpperCase(Locale.ROOT);
    if("PAID".equals(r.status())&&!utr.matches("[A-Z0-9-]{6,40}")) throw new ApiException(400,"Enter the outgoing payment UTR before marking this withdrawal paid.");
    String at=now(); long user=((Number)request.get("user_id")).longValue(); BigDecimal value=(BigDecimal)request.get("amount");
    if("REJECTED".equals(r.status())){
      db.update("UPDATE wallet_accounts SET balance=balance+?,updated_at=? WHERE user_id=?",value,at,user);
      db.update("INSERT INTO wallet_transactions(user_id,kind,amount,status,reference_id,note,created_at) VALUES(?,?,?,?,?,?,?)",user,"WITHDRAWAL_REFUND",value,"COMPLETED",id,"Rejected withdrawal returned to wallet",at);
      db.update("UPDATE wallet_transactions SET status='REJECTED' WHERE user_id=? AND kind='WITHDRAWAL' AND reference_id=?",user,id);
    }else{
      db.update("UPDATE wallet_transactions SET status='COMPLETED',note=? WHERE user_id=? AND kind='WITHDRAWAL' AND reference_id=?", "Admin marked payout paid, UTR "+utr,user,id);
    }
    db.update("UPDATE wallet_withdrawal_requests SET status=?,payment_utr=?,reviewed_by=?,review_note=?,reviewed_at=? WHERE id=?",r.status(),utr.isBlank()?null:utr,p.id(),trim(r.note()),at,id);
  }

  private String trim(String s){return s==null?"":s.trim();}
}
