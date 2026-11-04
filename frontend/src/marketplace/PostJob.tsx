import { useState } from "react";
import { useNavigate } from "react-router-dom";
import { api } from "../services/api";
import { Feedback } from "../components/ui/Feedback";
import { categories } from "./types";
import type { Job } from "./types";
export default function PostJob() {
  const navigate = useNavigate();
  const [error, setError] = useState(""),
    [busy, setBusy] = useState(false);
  async function submit(e: React.FormEvent<HTMLFormElement>) {
    e.preventDefault();
    const form = new FormData(e.currentTarget);
    setBusy(true);
    setError("");
    try {
      const job = await api<Job>("/api/jobs", "POST", {
        title: String(form.get("title")).trim(),
        description: String(form.get("description")).trim(),
        category: form.get("category"),
        reward: Number(form.get("reward")),
        pickup: String(form.get("pickup")).trim(),
        destination: String(form.get("destination")).trim(),
        deadline: new Date(String(form.get("deadline"))).toISOString(),
      });
      navigate(`/jobs/${job.id}`);
    } catch (e) {
      setError((e as Error).message);
    } finally {
      setBusy(false);
    }
  }
  return (
    <section className="market-page">
      <span className="eyebrow">TURN A NEED INTO A CONNECTION</span>
      <h1>Post a campus job.</h1>
      <p>
        Be clear about the task, route and reward. Payment is arranged directly
        with your helper.
      </p>
      <Feedback message={error} />
      <form className="market-panel market-form" onSubmit={submit}>
        <label>
          Job title
          <input
            name="title"
            required
            maxLength={120}
            placeholder="Bring my food from Gate 1"
          />
        </label>
        <label>
          Description
          <textarea
            name="description"
            required
            maxLength={2000}
            rows={4}
            placeholder="What should your helper know? Keep private details out of public listings."
          />
        </label>
        <div className="market-two">
          <label>
            Category
            <select name="category">
              {categories.map((c) => (
                <option key={c}>{c}</option>
              ))}
            </select>
          </label>
          <label>
            Agreed reward (₹)
            <input
              name="reward"
              type="number"
              min="0"
              max="10000"
              step="0.01"
              required
              defaultValue="30"
            />
          </label>
          <label>
            Pickup / meeting location
            <input
              name="pickup"
              required
              maxLength={120}
              placeholder="Gate 1"
            />
          </label>
          <label>
            Delivery / task location
            <input
              name="destination"
              required
              maxLength={120}
              placeholder="Academic Block"
            />
          </label>
        </div>
        <label>
          Deadline
          <input
            type="datetime-local"
            name="deadline"
            required
            min={new Date(Date.now() + 60000)
              .toLocaleString("sv-SE")
              .slice(0, 16)
              .replace(" ", "T")}
          />
        </label>
        <button className="button" disabled={busy}>
          {busy ? "Posting…" : "Post job"}
        </button>
      </form>
    </section>
  );
}
