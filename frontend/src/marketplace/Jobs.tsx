import { useState } from "react";
import { Link, useLocation } from "react-router-dom";
import { Feedback } from "../components/ui/Feedback";
import { useAuth } from "../contexts/AuthContext";
import { useRemote } from "./useRemote";
import { categories, money, statusLabel } from "./types";
import type { Job } from "./types";
export default function Jobs() {
  const mine = useLocation().pathname === "/my-jobs";
  const { isDemo } = useAuth();
  const [query, setQuery] = useState(""),
    [search, setSearch] = useState(""),
    [category, setCategory] = useState(""),
    [page, setPage] = useState(0);
  const { data, error, reload } = useRemote<Job[]>(
    `/api/jobs?scope=${mine ? "mine" : "all"}&q=${encodeURIComponent(search)}&category=${encodeURIComponent(category)}&page=${page}`,
  );
  if (isDemo)
    return (
      <section>
        <h1>Marketplace</h1>
        <p>Exit the offline sample and verify an account to use shared jobs.</p>
      </section>
    );
  return (
    <section className="market-page">
      <div className="market-heading">
        <div>
          <span className="eyebrow">SMALL TASKS. REAL CONNECTIONS.</span>
          <h1>{mine ? "Your campus jobs." : "A little help, nearby."}</h1>
          <p>
            {mine
              ? "Jobs you posted and jobs you’re helping with."
              : "Find a task that fits your route, time or skills."}
          </p>
        </div>
        <Link className="button" to="/jobs/new">
          Post a job
        </Link>
      </div>
      <form
        className="market-filters"
        onSubmit={(e) => {
          e.preventDefault();
          setSearch(query);
          setPage(0);
        }}
      >
        <label>
          Search jobs
          <input
            value={query}
            onChange={(e) => setQuery(e.target.value)}
            maxLength={100}
            placeholder="Food pickup, Java help…"
          />
        </label>
        <label>
          Category
          <select
            value={category}
            onChange={(e) => {
              setCategory(e.target.value);
              setPage(0);
            }}
          >
            <option value="">All categories</option>
            {categories.map((c) => (
              <option key={c}>{c}</option>
            ))}
          </select>
        </label>
        <button className="button">Search</button>
        <button
          type="button"
          className="button button-outline"
          onClick={() => void reload()}
        >
          Refresh
        </button>
      </form>
      <Feedback message={error} />
      {!data && !error ? <p role="status">Loading campus jobs…</p> : null}
      {data?.length === 0 ? (
        <div className="market-empty">
          <h2>No jobs here yet.</h2>
          <p>
            {mine
              ? "Post a request or accept a job to get started."
              : "Try another search or post the first campus request."}
          </p>
        </div>
      ) : null}
      <div className="job-grid">
        {data?.map((j) => (
          <Link className="job-card" key={j.id} to={`/jobs/${j.id}`}>
            <div className="job-card-top">
              <span className="eyebrow">{j.category}</span>
              <span className={`job-status status-${j.status.toLowerCase()}`}>
                {statusLabel(j.status)}
              </span>
            </div>
            <h2>{j.title}</h2>
            <p>{j.description.slice(0, 160)}</p>
            <div className="job-route">
              {j.pickup} → {j.destination}
            </div>
            <div className="job-card-footer">
              <strong>{money(j.reward)}</strong>
              <span>By {j.owner_name}</span>
            </div>
            <small>Due {new Date(j.deadline).toLocaleString()}</small>
          </Link>
        ))}
      </div>
      <div className="market-actions">
        <button
          className="button button-outline"
          disabled={!page}
          onClick={() => setPage((p) => p - 1)}
        >
          Previous
        </button>
        <span>Page {page + 1}</span>
        <button
          className="button button-outline"
          disabled={!data || data.length < 30}
          onClick={() => setPage((p) => p + 1)}
        >
          Next
        </button>
      </div>
    </section>
  );
}
