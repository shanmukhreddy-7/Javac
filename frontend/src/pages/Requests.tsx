import { useCallback, useEffect, useState } from "react";
import { ArrowUpRight, Inbox, Send, Check, X, RefreshCw } from "lucide-react";
import { useAuth } from "../contexts/AuthContext";
import { api } from "../services/api";
import type { CampusPerson, CampusRequest } from "../types";
import { Feedback, Spinner } from "../components/ui/Feedback";
import Field from "../components/forms/Field";
export default function Requests() {
  const { student, isDemo } = useAuth();
  const [people, setPeople] = useState<CampusPerson[]>([]);
  const [requests, setRequests] = useState<CampusRequest[]>([]);
  const [recipient, setRecipient] = useState("");
  const [title, setTitle] = useState("");
  const [description, setDescription] = useState("");
  const [tab, setTab] = useState<"inbox" | "sent">("inbox");
  const [error, setError] = useState("");
  const [message, setMessage] = useState("");
  const [busy, setBusy] = useState(false);
  const [loading, setLoading] = useState(true);
  const [responding, setResponding] = useState<number | null>(null);
  const load = useCallback(async () => {
    if (isDemo) {
      setLoading(false);
      return;
    }
    try {
      const [students, items] = await Promise.all([
        api<CampusPerson[]>("/api/students"),
        api<CampusRequest[]>("/api/requests"),
      ]);
      setPeople(students);
      setRequests(items);
      setError("");
    } catch (e) {
      setError((e as Error).message);
    } finally {
      setLoading(false);
    }
  }, [isDemo]);
  useEffect(() => {
    void load();
    const interval = setInterval(() => {
      if (document.visibilityState === "visible") void load();
    }, 5000);
    const focus = () => void load();
    window.addEventListener("focus", focus);
    return () => {
      clearInterval(interval);
      window.removeEventListener("focus", focus);
    };
  }, [load]);
  if (!student) return null;
  const incoming = requests.filter((r) => r.recipientId === student.id);
  const outgoing = requests.filter((r) => r.senderId === student.id);
  const items = tab === "inbox" ? incoming : outgoing;
  const send = async (e: React.FormEvent) => {
    e.preventDefault();
    setError("");
    setMessage("");
    if (title.trim().length < 3 || description.trim().length < 5) {
      setError(
        "Add a title of at least 3 characters and a description of at least 5 characters.",
      );
      return;
    }
    setBusy(true);
    try {
      await api("/api/requests", "POST", {
        recipientId: Number(recipient),
        title: title.trim(),
        description: description.trim(),
      });
      setTitle("");
      setDescription("");
      setRecipient("");
      setTab("sent");
      setMessage("Request sent. The other account will see it in its inbox.");
      await load();
    } catch (e) {
      setError((e as Error).message);
    } finally {
      setBusy(false);
    }
  };
  const respond = async (id: number, status: "ACCEPTED" | "DECLINED") => {
    setResponding(id);
    setMessage("");
    try {
      await api("/api/requests/" + id, "PATCH", { status });
      await load();
      setMessage(
        status === "ACCEPTED"
          ? "Request accepted. The sender can see your response."
          : "Request declined. The sender can see your response.",
      );
    } catch (e) {
      setError((e as Error).message);
    } finally {
      setResponding(null);
    }
  };
  return (
    <>
      <div className="page-heading">
        <div>
          <span className="eyebrow">GOOD THINGS START WITH A REQUEST</span>
          <h1>A little help, connected.</h1>
          <p>
            Send a request to another student and keep the conversation moving.
          </p>
        </div>
        <button
          className="button secondary"
          onClick={() => void load()}
          disabled={loading || isDemo}
        >
          <RefreshCw size={16} />
          Refresh
        </button>
      </div>
      {isDemo ? (
        <section className="panel">
          <h2>Try requests with two presentation accounts.</h2>
          <p className="panel-description">
            The quick sample is offline. Exit the sample, create two accounts
            with different emails, and sign in from two browsers to send
            requests between them.
          </p>
        </section>
      ) : (
        <>
          <Feedback message={error} />
          <Feedback message={message} success />
          <div className="requests-layout">
            <section className="panel form-panel">
              <div className="panel-heading">
                <h2>
                  <Send size={18} /> Send a campus request
                </h2>
              </div>
              {loading ? (
                <div className="loading-inline">
                  <Spinner />
                  Loading students…
                </div>
              ) : people.length ? (
                <form onSubmit={send}>
                  <div className="field">
                    <label htmlFor="recipient">Student</label>
                    <select
                      id="recipient"
                      required
                      value={recipient}
                      onChange={(e) => setRecipient(e.target.value)}
                    >
                      <option value="">Choose a student</option>
                      {people.map((p) => (
                        <option value={p.id} key={p.id}>
                          {p.fullName} · {p.department}
                        </option>
                      ))}
                    </select>
                  </div>
                  <Field
                    label="What do you need help with?"
                    required
                    minLength={3}
                    maxLength={100}
                    value={title}
                    onChange={(e) => setTitle(e.target.value)}
                    placeholder="e.g. Help with a Java assignment"
                  />
                  <div className="field">
                    <label htmlFor="request-description">A few details</label>
                    <textarea
                      id="request-description"
                      required
                      minLength={5}
                      maxLength={1000}
                      rows={4}
                      value={description}
                      onChange={(e) => setDescription(e.target.value)}
                      placeholder="Share what you need and when you need it."
                    />
                  </div>
                  <button className="button full" disabled={busy}>
                    {busy ? (
                      <Spinner />
                    ) : (
                      <>
                        Send request <ArrowUpRight size={17} />
                      </>
                    )}
                  </button>
                </form>
              ) : (
                <div className="request-empty">
                  <Send size={25} />
                  <h3>Your first connection is next.</h3>
                  <p>
                    Create a second account in another browser. It will appear
                    here automatically.
                  </p>
                </div>
              )}
              <p className="subtle-note">
                Requests refresh every 5 seconds while this page is visible.
              </p>
            </section>
            <section className="panel request-inbox">
              <div
                className="request-tabs"
                role="tablist"
                aria-label="Request folders"
              >
                <button
                  role="tab"
                  aria-selected={tab === "inbox"}
                  onClick={() => setTab("inbox")}
                >
                  <Inbox size={16} />
                  Inbox <span>{incoming.length}</span>
                </button>
                <button
                  role="tab"
                  aria-selected={tab === "sent"}
                  onClick={() => setTab("sent")}
                >
                  <Send size={16} />
                  Sent <span>{outgoing.length}</span>
                </button>
              </div>
              <div
                role="tabpanel"
                aria-label={
                  tab === "inbox" ? "Received requests" : "Sent requests"
                }
              >
                {items.length ? (
                  items.map((r) => (
                    <article className="request-item" key={r.id}>
                      <div className="request-item-top">
                        <span>
                          {tab === "inbox"
                            ? "From " + r.senderName
                            : "To " + r.recipientName}
                        </span>
                        <span
                          className={"request-status " + r.status.toLowerCase()}
                        >
                          {r.status === "PENDING"
                            ? "Pending"
                            : r.status === "ACCEPTED"
                              ? "Accepted"
                              : "Declined"}
                        </span>
                      </div>
                      <h3>{r.title}</h3>
                      <p>{r.description}</p>
                      <time dateTime={r.createdAt}>
                        {new Date(r.createdAt).toLocaleString("en-IN", {
                          day: "numeric",
                          month: "short",
                          hour: "2-digit",
                          minute: "2-digit",
                        })}
                      </time>
                      {tab === "inbox" && r.status === "PENDING" && (
                        <div className="request-actions">
                          <button
                            className="button small"
                            disabled={responding === r.id}
                            onClick={() => void respond(r.id, "ACCEPTED")}
                          >
                            <Check size={14} />
                            Accept
                          </button>
                          <button
                            className="button secondary small"
                            disabled={responding === r.id}
                            onClick={() => void respond(r.id, "DECLINED")}
                          >
                            <X size={14} />
                            Decline
                          </button>
                        </div>
                      )}
                    </article>
                  ))
                ) : (
                  <div className="request-empty">
                    <Inbox size={28} />
                    <h3>
                      {tab === "inbox"
                        ? "A quiet inbox, for now."
                        : "Your next connection starts here."}
                    </h3>
                    <p>
                      {tab === "inbox"
                        ? "Requests sent to your account will appear here automatically."
                        : "Send your first request using the form."}
                    </p>
                  </div>
                )}
              </div>
            </section>
          </div>
        </>
      )}
    </>
  );
}
