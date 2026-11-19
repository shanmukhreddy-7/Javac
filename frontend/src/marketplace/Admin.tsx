/**
 * Account/listing moderation and wallet review screens for administrators.
 * The Java services enforce administrator access even if someone bypasses the UI role check.
 */
import { useState } from "react";
import { Link } from "react-router-dom";
import { api } from "../services/api";
import { useAuth } from "../contexts/AuthContext";
import { Feedback } from "../components/ui/Feedback";
import { useRemote } from "./useRemote";
import type { Job } from "./types";
interface Account {
  id: number;
  email: string;
  full_name: string;
  email_verified: boolean;
  account_status: string;
}
interface Report {
  id: number;
  job_id: number;
  title: string;
  reason: string;
  resolved: boolean;
}
export default function Admin() {
  const { student } = useAuth();
  const { data, error, reload } = useRemote<{
    users: Account[];
    jobs: Job[];
    reports: Report[];
  }>("/api/admin");
  // The wallet queue is separate from account moderation and refreshes periodically.
  const { data: walletQueue, error: walletError, reload: reloadWallet } = useRemote<{
    topups: { id:number; user_id:number; full_name:string; email:string; amount:number; utr:string; proof_type:string|null; status:string; created_at:string }[];
    withdrawals: { id:number; user_id:number; full_name:string; email:string; amount:number; upi_id:string; status:string; payment_utr:string|null; created_at:string }[];
  }>("/api/admin/wallet",30000);
  const [busy, setBusy] = useState(false),
    [actionError, setError] = useState("");
  // Refresh both queues after a confirmed admin action so displayed decisions come from the server.
  async function act(path: string, method = "POST", body?: unknown) {
    if (!window.confirm("Apply this moderation action?")) return;
    setBusy(true);
    setError("");
    try {
      await api(path, method, body);
      await reload();
      await reloadWallet();
    } catch (e) {
      setError((e as Error).message);
    } finally {
      setBusy(false);
    }
  }
  if (student?.role !== "ADMIN")
    return <p>Administrator access is required.</p>;
  return (
    <section className="market-page">
      <span className="eyebrow">A HEALTHIER CAMPUS COMMUNITY</span>
      <h1>Admin dashboard.</h1>
      <Feedback message={error || walletError || actionError} />
      <section className="market-panel">
        <h2>Student accounts</h2>
        <p>
          Accounts must verify their own email. Admin actions cannot bypass OTP
          verification.
        </p>
        {data?.users.map((u) => (
          <article key={u.id} className="admin-row">
            <div>
              <strong>{u.full_name}</strong>
              <small>
                {u.email} · {u.account_status}
              </small>
            </div>
            {u.id !== student.id && u.email_verified ? (
              <button
                className="button button-outline"
                disabled={busy}
                onClick={() =>
                  void act(`/api/admin/users/${u.id}`, "PATCH", {
                    status:
                      u.account_status === "SUSPENDED" ? "ACTIVE" : "SUSPENDED",
                  })
                }
              >
                {u.account_status === "SUSPENDED" ? "Restore" : "Suspend"}
              </button>
            ) : null}
          </article>
        ))}
      </section>
      <section className="market-panel">
        <h2>Wallet top-ups</h2>
        <p>Verify each payment in the receiving account before approving a request.</p>
        {walletQueue?.topups.length === 0 ? <p>No top-ups to review.</p> : null}
        {walletQueue?.topups.map((t) => <article className="admin-row wallet-admin-row" key={t.id}>
          <div><strong>{t.full_name} · {new Intl.NumberFormat("en-IN",{style:"currency",currency:"INR"}).format(Number(t.amount))}</strong><small>{t.email} · UTR {t.utr} · {new Date(t.created_at).toLocaleString()}</small>
            {t.proof_type ? <a className="text-link" href={`/api/admin/wallet/topups/${t.id}/proof`} target="_blank" rel="noreferrer">View screenshot</a> : <small>No screenshot attached</small>}</div>
          {t.status === "PENDING" ? <div className="market-actions"><button className="button" disabled={busy} onClick={() => void act(`/api/admin/wallet/topups/${t.id}/review`,"POST",{status:"APPROVED",note:"Payment verified by admin"})}>Verify & add funds</button><button className="button button-outline" disabled={busy} onClick={() => void act(`/api/admin/wallet/topups/${t.id}/review`,"POST",{status:"REJECTED",note:"Payment could not be verified"})}>Reject</button></div> : <span className={`job-status status-${t.status.toLowerCase()}`}>{t.status}</span>}
        </article>)}
      </section>
      <section className="market-panel">
        <h2>Withdrawal requests</h2>
        <p>Send the payout to the listed UPI ID, then record its payment UTR here.</p>
        {walletQueue?.withdrawals.length === 0 ? <p>No withdrawals to review.</p> : null}
        {walletQueue?.withdrawals.map((w) => <article className="admin-row wallet-admin-row" key={w.id}>
          <div><strong>{w.full_name} · {new Intl.NumberFormat("en-IN",{style:"currency",currency:"INR"}).format(Number(w.amount))}</strong><small>{w.email} · {w.upi_id} · {new Date(w.created_at).toLocaleString()}</small></div>
          {w.status === "PENDING" ? <div className="market-actions"><button className="button" disabled={busy} onClick={() => { const paymentUtr=window.prompt("Enter the UTR for the payout you have sent:")?.trim(); if(paymentUtr) void act(`/api/admin/wallet/withdrawals/${w.id}/review`,"POST",{status:"PAID",paymentUtr,note:"Paid by admin"}); }}>Mark paid</button><button className="button button-outline" disabled={busy} onClick={() => void act(`/api/admin/wallet/withdrawals/${w.id}/review`,"POST",{status:"REJECTED",note:"Withdrawal request declined"})}>Reject & return balance</button></div> : <span className={`job-status status-${w.status.toLowerCase()}`}>{w.status}{w.payment_utr ? ` · ${w.payment_utr}` : ""}</span>}
        </article>)}
      </section>
      <section className="market-panel">
        <h2>Reports</h2>
        {data?.reports.length === 0 ? <p>No reports.</p> : null}
        {data?.reports.map((r) => (
          <article key={r.id} className="admin-row">
            <div>
              <Link to={`/jobs/${r.job_id}`}>{r.title}</Link>
              <p>{r.reason}</p>
            </div>
            {r.resolved ? (
              <span>Resolved</span>
            ) : (
              <button
                className="button button-outline"
                disabled={busy}
                onClick={() => void act(`/api/admin/reports/${r.id}/resolve`)}
              >
                Resolve
              </button>
            )}
          </article>
        ))}
      </section>
      <section className="market-panel">
        <h2>Listings</h2>
        {data?.jobs.map((j) => (
          <article key={j.id} className="admin-row">
            <Link to={`/jobs/${j.id}`}>{j.title}</Link>
            {j.hidden ? (
              <span>Removed</span>
            ) : (
              <button
                className="button button-outline"
                disabled={busy}
                onClick={() => void act(`/api/admin/jobs/${j.id}/hide`)}
              >
                Remove listing
              </button>
            )}
          </article>
        ))}
      </section>
    </section>
  );
}
