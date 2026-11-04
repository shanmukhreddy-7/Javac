import { useState } from "react";
import { Link } from "react-router-dom";
import { api } from "../services/api";
import { Feedback } from "../components/ui/Feedback";
import { useRemote } from "./useRemote";
import { money } from "./types";
import type { Job } from "./types";
export default function Earnings() {
  const { data, error, reload } = useRemote<{
    earned: number;
    collected: number;
    pending: number;
    jobs: Job[];
  }>("/api/earnings");
  const [busy, setBusy] = useState(false),
    [actionError, setError] = useState("");
  async function collect(e: React.FormEvent<HTMLFormElement>, id: number) {
    e.preventDefault();
    if (
      !window.confirm(
        "Confirm you have actually received this payment outside JAVAC?",
      )
    )
      return;
    setBusy(true);
    setError("");
    try {
      await api(`/api/jobs/${id}/collection`, "POST", {
        method: new FormData(e.currentTarget).get("method"),
      });
      await reload();
    } catch (e) {
      setError((e as Error).message);
    } finally {
      setBusy(false);
    }
  }
  return (
    <section className="market-page">
      <span className="eyebrow">THE VALUE YOU BRING</span>
      <h1>Your earnings.</h1>
      <p>
        Agreed rewards for completed jobs. Payments happen directly between
        students.
      </p>
      <Feedback message={error || actionError} />
      {data ? (
        <>
          <div className="earnings-summary">
            {[
              ["Total earned", data.earned],
              ["Awaiting collection", data.pending],
              ["Collected", data.collected],
            ].map(([label, value]) => (
              <div className="market-panel" key={label}>
                <span>{label}</span>
                <strong>{money(Number(value))}</strong>
              </div>
            ))}
          </div>
          <p className="subtle-note">
            “Collected” is your own confirmation. JAVAC has no wallet, cash-out
            or automatic UPI verification.
          </p>
          {data.jobs.length === 0 ? (
            <div className="market-empty">
              <h2>Your first earning starts with a little help.</h2>
              <Link to="/jobs">Browse jobs</Link>
            </div>
          ) : (
            data.jobs.map((j) => (
              <article className="market-panel earning-row" key={j.id}>
                <div>
                  <Link to={`/jobs/${j.id}`}>
                    <h2>{j.title}</h2>
                  </Link>
                  <p>
                    {j.owner_name} · {money(j.reward)}
                  </p>
                </div>
                {j.payment_status === "COLLECTED" ? (
                  <span className="job-status">
                    Collected · {j.payment_method}
                  </span>
                ) : (
                  <form
                    className="market-actions"
                    onSubmit={(e) => void collect(e, j.id)}
                  >
                    <label>
                      Received through
                      <select name="method">
                        <option value="CASH">Cash</option>
                        <option value="PHONEPE">PhonePe</option>
                        <option value="PAYTM">Paytm</option>
                      </select>
                    </label>
                    <button className="button button-outline" disabled={busy}>
                      Confirm received
                    </button>
                  </form>
                )}
              </article>
            ))
          )}
        </>
      ) : !error ? (
        <p>Loading earnings…</p>
      ) : null}
    </section>
  );
}
