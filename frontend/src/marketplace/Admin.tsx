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
  const [busy, setBusy] = useState(false),
    [actionError, setError] = useState("");
  async function act(path: string, method = "POST", body?: unknown) {
    if (!window.confirm("Apply this moderation action?")) return;
    setBusy(true);
    setError("");
    try {
      await api(path, method, body);
      await reload();
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
      <Feedback message={error || actionError} />
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
