import { useEffect, useState } from "react";
import { Search, Plus, X, Check } from "lucide-react";
import type { Skill } from "../../types";
import { api } from "../../services/api";
import { demoSkills } from "../../services/demo";
import { Feedback, Spinner } from "../ui/Feedback";
export default function SkillPicker({
  selected,
  onSave,
  isDemo,
}: {
  selected: Skill[];
  onSave: (skills: Skill[]) => Promise<void>;
  isDemo: boolean;
}) {
  const [directory, setDirectory] = useState<Skill[]>([]);
  const [choice, setChoice] = useState(selected);
  const [query, setQuery] = useState("");
  const [category, setCategory] = useState("All skills");
  const [busy, setBusy] = useState(false);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [message, setMessage] = useState("");
  const load = () => {
    setLoading(true);
    setError("");
    if (isDemo) {
      setDirectory(demoSkills);
      setLoading(false);
      return;
    }
    api<Skill[]>("/api/skills")
      .then(setDirectory)
      .catch((e) => setError(e.message))
      .finally(() => setLoading(false));
  };
  useEffect(load, [isDemo]);
  useEffect(() => setChoice(selected), [selected]);
  const toggle = (skill: Skill) => {
    setMessage("");
    if (choice.some((s) => s.id === skill.id)) {
      setChoice(choice.filter((s) => s.id !== skill.id));
      return;
    }
    if (choice.length >= 20) {
      setError("Choose up to 20 skills.");
      return;
    }
    setError("");
    setChoice([...choice, skill]);
  };
  const save = async () => {
    setBusy(true);
    setError("");
    setMessage("");
    try {
      await onSave(choice);
      setMessage(
        isDemo
          ? "Demo skills updated for this visit."
          : "Your skills have been saved.",
      );
    } catch (e) {
      setError((e as Error).message);
    } finally {
      setBusy(false);
    }
  };
  const filtered = directory.filter(
    (s) =>
      (category === "All skills" || s.category === category) &&
      s.name.toLowerCase().includes(query.toLowerCase()),
  );
  return (
    <>
      <div className="skill-picker-selected">
        <span className="field-label">
          YOUR SELECTED SKILLS <small>{choice.length} / 20</small>
        </span>
        <div className="skill-chips">
          {choice.length ? (
            choice.map((s) => (
              <button
                type="button"
                className="skill-chip selected"
                key={s.id}
                aria-label={"Remove " + s.name}
                onClick={() => toggle(s)}
              >
                {s.name}
                <X size={14} />
              </button>
            ))
          ) : (
            <p className="muted">
              Choose a few things you’d love to help with.
            </p>
          )}
        </div>
      </div>
      <div className="skill-search-row">
        <div className="search-input">
          <Search size={17} />
          <input
            type="search"
            aria-label="Search skills"
            placeholder="Find your skill…"
            value={query}
            onChange={(e) => setQuery(e.target.value)}
          />
        </div>
        <select
          aria-label="Skill category"
          value={category}
          onChange={(e) => setCategory(e.target.value)}
        >
          {["All skills", ...new Set(directory.map((s) => s.category))].map(
            (c) => (
              <option key={c}>{c}</option>
            ),
          )}
        </select>
      </div>
      <Feedback message={error} />
      <Feedback message={message} success />
      {loading ? (
        <div className="loading-inline">
          <Spinner /> Loading the skill directory…
        </div>
      ) : (
        <div className="skill-directory">
          {filtered.map((s) => {
            const active = choice.some((x) => x.id === s.id);
            return (
              <button
                type="button"
                aria-pressed={active}
                className={`skill-option ${active ? "active" : ""}`}
                key={s.id}
                onClick={() => toggle(s)}
              >
                <span>
                  <strong>{s.name}</strong>
                  <small>{s.category}</small>
                </span>
                {active ? <Check size={17} /> : <Plus size={17} />}
              </button>
            );
          })}
          {!filtered.length && (
            <p className="muted">
              No skills match your search. Try another name or category.
            </p>
          )}
        </div>
      )}
      <div className="panel-actions">
        {!directory.length && !loading && (
          <button className="button secondary" type="button" onClick={load}>
            Retry directory
          </button>
        )}
        <button
          className="button"
          type="button"
          disabled={busy || loading || !directory.length}
          onClick={save}
        >
          {busy ? <Spinner /> : "Save my skills"}
        </button>
      </div>
    </>
  );
}
