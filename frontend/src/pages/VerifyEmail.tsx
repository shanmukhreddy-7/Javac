import { useState, useEffect, useRef } from "react";
import { useLocation, Link } from "react-router-dom";
import { Mail, ArrowUpRight, ShieldCheck } from "lucide-react";
import AuthLayout from "../components/layout/AuthLayout";
import { Feedback, Spinner } from "../components/ui/Feedback";
import Field from "../components/forms/Field";
import { api } from "../services/api";
export default function VerifyEmail() {
  const location = useLocation();
  const state = location.state as {
    email?: string;
    registered?: boolean;
  } | null;
  const [token] = useState(
    () =>
      new URLSearchParams(location.hash.slice(1)).get("token") ??
      new URLSearchParams(location.search).get("token"),
  );
  const [email, setEmail] = useState(state?.email ?? "");
  const [message, setMessage] = useState("");
  const [error, setError] = useState("");
  const [verified, setVerified] = useState(false);
  const [busy, setBusy] = useState(false);
  const [cooldown, setCooldown] = useState(0);
  const started = useRef(false);
  useEffect(() => {
    if (!token || started.current) return;
    started.current = true;
    window.history.replaceState(null, "", "/verify-email");
    setBusy(true);
    api<{ message: string }>("/api/auth/verify-email", "POST", { token })
      .then((r) => {
        setMessage(r.message);
        setVerified(true);
      })
      .catch((e) => setError(e.message))
      .finally(() => setBusy(false));
  }, [token]);
  useEffect(() => {
    if (cooldown <= 0) return;
    const timer = setTimeout(() => setCooldown((c) => c - 1), 1000);
    return () => clearTimeout(timer);
  }, [cooldown]);
  const resend = async (e: React.FormEvent) => {
    e.preventDefault();
    setBusy(true);
    setError("");
    setMessage("");
    try {
      const r = await api<{ message: string }>(
        "/api/auth/resend-verification",
        "POST",
        { email },
      );
      setMessage(r.message);
      setCooldown(60);
    } catch (e) {
      setError((e as Error).message);
    } finally {
      setBusy(false);
    }
  };
  return (
    <AuthLayout>
      <div className="page-symbol">{verified ? <ShieldCheck /> : <Mail />}</div>
      <span className="eyebrow">ONE LAST CONNECTION</span>
      <h1>{verified ? "You’re officially in." : "Check your inbox."}</h1>
      <p className="form-intro">
        {verified
          ? "Your university email is verified. Your campus is ready for you."
          : state?.registered
            ? "We’ve sent a verification link to your university email. Open it to activate your account."
            : "Open your verification link, or request a new one below. Links expire after 30 minutes."}
      </p>
      <Feedback message={error} />
      <Feedback message={message} success />
      {busy && (
        <div className="loading-inline">
          <Spinner /> Working on it…
        </div>
      )}
      {verified ? (
        <Link className="button full" to="/login">
          Continue to login <ArrowUpRight size={18} />
        </Link>
      ) : (
        <form onSubmit={resend}>
          <Field
            label="University email"
            type="email"
            required
            autoComplete="email"
            value={email}
            onChange={(e) => setEmail(e.target.value)}
            placeholder="you@university.edu"
          />
          <button className="button full" disabled={busy || cooldown > 0}>
            {cooldown > 0
              ? `Resend in ${cooldown}s`
              : "Resend verification email"}
          </button>
          <p className="subtle-note">
            In local development, your email arrives in the development mailbox.
            Production uses your university inbox.
          </p>
        </form>
      )}
      <p className="form-switch">
        <Link to="/login">Back to login</Link>
      </p>
    </AuthLayout>
  );
}
