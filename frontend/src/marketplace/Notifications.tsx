import { useState } from "react";
import { Link } from "react-router-dom";
import { api } from "../services/api";
import { Feedback } from "../components/ui/Feedback";
import { useRemote } from "./useRemote";
import type { Notice } from "./types";
export default function Notifications() {
  const { data, error, reload } = useRemote<Notice[]>("/api/notifications");
  const [actionError, setError] = useState("");
  async function read(id: number) {
    try {
      await api(`/api/notifications/${id}/read`, "POST");
      await reload();
    } catch (e) {
      setError((e as Error).message);
    }
  }
  return (
    <section className="market-page">
      <span className="eyebrow">STAY IN THE LOOP</span>
      <h1>Your notifications.</h1>
      <p>Job updates and messages refresh while this page is open.</p>
      <Feedback message={error || actionError} />
      {data?.length === 0 ? (
        <div className="market-empty">
          <h2>You’re all caught up.</h2>
          <p>Updates will appear when your campus jobs move forward.</p>
        </div>
      ) : null}
      {data?.map((n) => (
        <article
          className={`market-panel notice-row ${n.is_read ? "" : "notice-unread"}`}
          key={n.id}
        >
          <div>
            <Link to={`/jobs/${n.job_id}`}>{n.message}</Link>
            <small>{new Date(n.created_at).toLocaleString()}</small>
          </div>
          {!n.is_read ? (
            <button
              className="button button-outline"
              onClick={() => void read(n.id)}
            >
              Mark read
            </button>
          ) : (
            <span>Read</span>
          )}
        </article>
      ))}
    </section>
  );
}
