import { useState } from "react";
import { Link, useParams } from "react-router-dom";
import { api } from "../services/api";
import { useAuth } from "../contexts/AuthContext";
import { Feedback } from "../components/ui/Feedback";
import { useRemote } from "./useRemote";
import { deliverySteps, money, statusLabel } from "./types";
import type { Job, Message, Review } from "./types";
export default function JobDetail() {
  const { id } = useParams();
  const { student } = useAuth();
  const { data: j, error, reload } = useRemote<Job>(`/api/jobs/${id}`, 5000);
  const [busy, setBusy] = useState(false),
    [actionError, setError] = useState(""),
    [success, setSuccess] = useState(""),
    [otp, setOtp] = useState("");
  const owner = j?.owner_id === student?.id,
    helper = j?.helper_id === student?.id,
    participant = owner || helper;
  const steps = j ? deliverySteps(j.category) : [];
  const progressIndex = j ? steps.findIndex(([key]) => key === j.progress_step) : -1;
  async function act(action: string, details: Record<string, string> = {}) {
    setBusy(true);
    setError("");
    try {
      const result = await api<Job & { otp_error?: string }>(`/api/jobs/${id}/actions`, "POST", { action, ...details });
      if (result.otp_error) setError(result.otp_error);
      else if (action === "complete") setSuccess("The job is complete. Thanks for helping on campus!");
      if (action === "complete") setOtp("");
      await reload();
    } catch (e) {
      setError((e as Error).message);
    } finally {
      setBusy(false);
    }
  }
  async function report(e: React.FormEvent<HTMLFormElement>) {
    e.preventDefault();
    const form = e.currentTarget;
    setBusy(true);
    setError("");
    try {
      await api(`/api/jobs/${id}/reports`, "POST", {
        reason: new FormData(form).get("reason"),
      });
      form.reset();
      setSuccess("Your report was sent to the administrator.");
    } catch (e) {
      setError((e as Error).message);
    } finally {
      setBusy(false);
    }
  }
  if (!j)
    return (
      <>
        <Feedback message={error} />
        {!error ? <p>Loading job…</p> : null}
      </>
    );
  return (
    <section className="market-page">
      <Link to="/jobs" className="text-link">
        ← Marketplace
      </Link>
      <div className="market-heading">
        <div>
          <span className="eyebrow">{j.category}</span>
          <h1>{j.title}</h1>
          <span className="job-status">{statusLabel(j.status)}</span>
        </div>
        <strong className="reward-large">{money(j.reward)}</strong>
      </div>
      <Feedback message={error || actionError} />
      <Feedback message={success} success />
      <div className="market-panel">
        <p className="job-description">{j.description}</p>
        <dl className="job-facts">
          <div>
            <dt>Pickup / meeting</dt>
            <dd>{j.pickup}</dd>
          </div>
          <div>
            <dt>Destination</dt>
            <dd>{j.destination}</dd>
          </div>
          <div>
            <dt>Deadline</dt>
            <dd>{new Date(j.deadline).toLocaleString()}</dd>
          </div>
          <div>
            <dt>Posted by</dt>
            <dd>
              <Link to={`/students/${j.owner_id}`}>{j.owner_name}</Link>
            </dd>
          </div>
          <div>
            <dt>Helper</dt>
            <dd>
              {j.helper_id ? (
                <Link to={`/students/${j.helper_id}`}>{j.helper_name}</Link>
              ) : (
                "Waiting for a helper"
              )}
            </dd>
          </div>
        </dl>
        {j.category === "Xerox" ? (
          <div className="xerox-summary">
            <div><span>Print type</span><strong>{j.xerox_mode === "COLOR" ? "Color" : "Black and white"}</strong></div>
            <div><span>Copies</span><strong>{j.copies ?? "—"}</strong></div>
            {j.original_name ? <a className="button button-outline small" href={`/api/jobs/${j.id}/file`}>Download {j.original_name}</a> : null}
          </div>
        ) : null}
        <p className="subtle-note">
          Pay directly through cash, PhonePe or Paytm. JAVAC records rewards and
          self-reported collection; it does not transfer or verify payments.
        </p>
        <div className="market-actions">
          {j.status === "OPEN" && !owner ? (
            <button
              className="button"
              disabled={busy}
              onClick={() => void act("accept")}
            >
              Accept job
            </button>
          ) : null}
          {participant &&
          ["OPEN", "ACCEPTED", "IN_PROGRESS"].includes(j.status) ? (
            <button
              className="button button-outline"
              disabled={busy}
              onClick={() => {
                if (
                  window.confirm(
                    "Cancel this job? Both participants will be notified.",
                  )
                )
                  void act("cancel");
              }}
            >
              Cancel job
            </button>
          ) : null}
        </div>
      </div>
      {participant && j.helper_id && !["CANCELLED", "COMPLETED"].includes(j.status) ? (
        <section className="market-panel delivery-panel" aria-label="Delivery progress">
          <div className="panel-heading"><div><span className="eyebrow">JOB PROGRESS</span><h2>Delivery checklist</h2></div><span className="job-status">{j.status === "DELIVERED" ? "Ready for handoff" : statusLabel(j.status)}</span></div>
          <div className="delivery-checklist">
            {steps.map(([key, label], index) => {
              const checked = index <= progressIndex;
              const enabled = helper && j.status === "IN_PROGRESS" && index === progressIndex + 1;
              return <label className={`delivery-step ${checked ? "step-done" : ""}`} key={key}>
                <input type="checkbox" checked={checked} disabled={!enabled || busy} onChange={() => void act("progress", { step: key })} />
                <span>{label}</span>
                {checked ? <small>Updated</small> : null}
              </label>;
            })}
          </div>
          {helper && j.status === "ACCEPTED" ? <button className="button" disabled={busy} onClick={() => void act("start")}>Start delivery</button> : null}
          {owner && ["ACCEPTED", "IN_PROGRESS", "DELIVERED"].includes(j.status) ? <p className="subtle-note otp-owner-note">A delivery code was sent to your university email. Share it with the helper only after you receive your order.</p> : null}
          {helper && j.status === "DELIVERED" ? <form className="handoff-code" onSubmit={(e) => { e.preventDefault(); void act("complete", { otp }); }}>
            <label>Enter the requester’s 6-digit handoff code<input aria-label="Handoff code" inputMode="numeric" autoComplete="one-time-code" pattern="[0-9]{6}" maxLength={6} value={otp} onChange={(e) => setOtp(e.target.value.replace(/\D/g, "").slice(0, 6))} required /></label>
            <button className="button" disabled={busy || otp.length !== 6}>{busy ? "Checking…" : "Confirm handoff"}</button>
          </form> : null}
        </section>
      ) : null}
      {participant && j.helper_id ? (
        <Conversation
          id={id!}
          closed={["CANCELLED", "COMPLETED"].includes(j.status)}
        />
      ) : null}
      <Reviews id={id!} canReview={participant && j.status === "COMPLETED"} />
      <details className="market-panel">
        <summary>Report this listing</summary>
        <form className="market-form" onSubmit={report}>
          <label>
            Reason
            <textarea name="reason" required maxLength={500} />
          </label>
          <button className="button button-outline" disabled={busy}>
            Send report
          </button>
        </form>
      </details>
    </section>
  );
}
function Conversation({ id, closed }: { id: string; closed: boolean }) {
  const { data, error, reload } = useRemote<Message[]>(
    `/api/jobs/${id}/messages`,
  );
  const [body, setBody] = useState(""),
    [busy, setBusy] = useState(false),
    [sendError, setError] = useState("");
  async function send(e: React.FormEvent) {
    e.preventDefault();
    setBusy(true);
    setError("");
    try {
      await api(`/api/jobs/${id}/messages`, "POST", { body: body.trim() });
      setBody("");
      await reload();
    } catch (e) {
      setError((e as Error).message);
    } finally {
      setBusy(false);
    }
  }
  return (
    <section className="market-panel">
      <h2>Job conversation</h2>
      <p>Only you and the other participant can read these messages.</p>
      <Feedback message={error || sendError} />
      <div className="job-messages">
        {data
          ?.slice()
          .reverse()
          .map((m) => (
            <article key={m.id}>
              <strong>{m.sender_name}</strong>
              <p>{m.body}</p>
              <small>{new Date(m.created_at).toLocaleString()}</small>
            </article>
          ))}
        {data?.length === 0 ? <p>No messages yet.</p> : null}
      </div>
      {closed ? (
        <p>
          This job is closed. Messages remain available to its participants.
        </p>
      ) : (
        <form className="market-form" onSubmit={send}>
          <label>
            Message
            <textarea
              value={body}
              onChange={(e) => setBody(e.target.value)}
              maxLength={1000}
              required
            />
          </label>
          <button className="button" disabled={busy || !body.trim()}>
            Send message
          </button>
        </form>
      )}
    </section>
  );
}
function Reviews({ id, canReview }: { id: string; canReview: boolean }) {
  const { data, error, reload } = useRemote<Review[]>(
    `/api/jobs/${id}/reviews`,
  );
  const [busy, setBusy] = useState(false),
    [sendError, setError] = useState("");
  async function send(e: React.FormEvent<HTMLFormElement>) {
    e.preventDefault();
    const form = e.currentTarget;
    const values = new FormData(form);
    setBusy(true);
    setError("");
    try {
      await api(`/api/jobs/${id}/reviews`, "POST", {
        rating: Number(values.get("rating")),
        comment: values.get("comment"),
      });
      form.reset();
      await reload();
    } catch (e) {
      setError((e as Error).message);
    } finally {
      setBusy(false);
    }
  }
  return (
    <section className="market-panel">
      <h2>Reviews</h2>
      <Feedback message={error || sendError} />
      {data?.map((r, i) => (
        <article className="review-line" key={i}>
          <strong>
            {r.rating}/5 · {r.reviewer_name}
          </strong>
          <p>{r.comment}</p>
        </article>
      ))}
      {data?.length === 0 ? <p>No reviews yet.</p> : null}
      {canReview ? (
        <form onSubmit={send} className="market-form">
          <label>
            Rating
            <select name="rating">
              {[5, 4, 3, 2, 1].map((n) => (
                <option key={n} value={n}>
                  {n} / 5
                </option>
              ))}
            </select>
          </label>
          <label>
            Your feedback
            <textarea name="comment" maxLength={500} />
          </label>
          <button className="button button-outline" disabled={busy}>
            Leave review
          </button>
        </form>
      ) : null}
    </section>
  );
}
