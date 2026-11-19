/**
 * Create a job with route, deadline, reward, and optional Xerox print details.
 * Ordinary jobs use JSON; Xerox jobs upload a JSON request part and private document as multipart data.
 */
import { useState } from "react";
import { useNavigate } from "react-router-dom";
import { api } from "../services/api";
import { Feedback } from "../components/ui/Feedback";
import { categories } from "./types";
import type { Job } from "./types";
export default function PostJob() {
  const navigate = useNavigate();
  const [error, setError] = useState(""),
    [busy, setBusy] = useState(false),
    [category, setCategory] = useState("Food pickup"),
    [fileName, setFileName] = useState("");
  async function submit(e: React.FormEvent<HTMLFormElement>) {
    e.preventDefault();
    const form = new FormData(e.currentTarget);
    setBusy(true);
    setError("");
    try {
      const request = {
        title: String(form.get("title")).trim(),
        description: String(form.get("description")).trim(),
        category: form.get("category"),
        reward: Number(form.get("reward")),
        pickup: String(form.get("pickup")).trim(),
        destination: String(form.get("destination")).trim(),
        // Convert the local datetime input to an ISO instant before Java validates the deadline.
        deadline: new Date(String(form.get("deadline"))).toISOString(),
        xeroxMode: category === "Xerox" ? form.get("xeroxMode") : null,
        xeroxCopies: category === "Xerox" ? Number(form.get("xeroxCopies")) : null,
      };
      let job: Job;
      if (category === "Xerox") {
        const file = form.get("file");
        if (!(file instanceof File) || file.size === 0) throw new Error("Choose the file to print.");
        if (file.size > 2 * 1024 * 1024) throw new Error("The file must be 2 MB or smaller.");
        const payload = new FormData();
        // Spring reads this JSON part as the validated Post DTO; the file is a separate part.
        payload.append("request", new Blob([JSON.stringify(request)], { type: "application/json" }));
        payload.append("file", file);
        job = await api<Job>("/api/jobs", "POST", payload);
      } else {
        job = await api<Job>("/api/jobs", "POST", request);
      }
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
            <select name="category" value={category} onChange={(e) => setCategory(e.target.value)}>
              {categories.map((c) => (
                <option key={c} value={c}>{c === "Xerox" ? "Xerox pickup" : c}</option>
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
        {category === "Xerox" ? (
          <div className="xerox-request-fields">
            <div className="market-two">
              <label>
                Print type
                <select name="xeroxMode" required defaultValue="BLACK_WHITE">
                  <option value="BLACK_WHITE">Black and white</option>
                  <option value="COLOR">Color</option>
                </select>
              </label>
              <label>
                Number of copies
                <input name="xeroxCopies" type="number" min="1" max="100" required defaultValue="1" />
              </label>
            </div>
            <label>
              File to print (PDF, PNG, or JPG; up to 2 MB)
              <input name="file" type="file" accept=".pdf,.png,.jpg,.jpeg,application/pdf,image/png,image/jpeg" required onChange={(e) => setFileName(e.target.files?.[0]?.name ?? "")} />
              {fileName ? <small className="subtle-note">Selected: {fileName}</small> : null}
            </label>
            <p className="subtle-note">The file is private to this request and can only be opened by you and the accepted helper.</p>
          </div>
        ) : null}
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
