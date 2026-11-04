import { useState, useEffect } from "react";
import { Link, useLocation } from "react-router-dom";
import AuthLayout from "../components/layout/AuthLayout";
import Field from "../components/forms/Field";
import { Feedback } from "../components/ui/Feedback";
import { api } from "../services/api";
export default function VerifyEmail() {
  const location = useLocation();
  const state = location.state as { email?: string } | null;
  const [email, setEmail] = useState(state?.email ?? ""),
    [code, setCode] = useState(""),
    [busy, setBusy] = useState(false),
    [done, setDone] = useState(false),
    [error, setError] = useState(""),
    [message, setMessage] = useState(""),
    [cooldown, setCooldown] = useState(state?.email ? 60 : 0);
  useEffect(() => {
    if (cooldown <= 0) return;
    const timer = setTimeout(() => setCooldown((c) => c - 1), 1000);
    return () => clearTimeout(timer);
  }, [cooldown]);
  async function verify(e: React.FormEvent) {
    e.preventDefault();
    setBusy(true);
    setError("");
    try {
      await api("/api/auth/verify-otp", "POST", { email, code });
      setDone(true);
      setCode("");
    } catch (e) {
      setError((e as Error).message);
    } finally {
      setBusy(false);
    }
  }
  async function resend() {
    setBusy(true);
    setError("");
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
  }
  return (
    <AuthLayout>
      <span className="eyebrow">YOUR CAMPUS CONNECTION</span>
      <h1>{done ? "You’re verified." : "Check your email."}</h1>
      <p className="form-intro">
        {done
          ? "Sign in with your password to get started."
          : "Enter the six-digit code sent to your university email. Codes expire after five minutes."}
      </p>
      <Feedback message={error} />
      <Feedback message={message} success />
      {done ? (
        <Link className="button full" to="/login">
          Continue to login
        </Link>
      ) : (
        <form onSubmit={verify}>
          <Field
            label="University email"
            type="email"
            required
            autoComplete="email"
            value={email}
            onChange={(e) => setEmail(e.target.value)}
          />
          <Field
            label="Verification code"
            required
            inputMode="numeric"
            autoComplete="one-time-code"
            pattern="[0-9]{6}"
            maxLength={6}
            value={code}
            onChange={(e) => setCode(e.target.value.replace(/\D/g, ""))}
          />
          <button className="button full" disabled={busy}>
            Verify email
          </button>
          <button
            type="button"
            className="text-button"
            disabled={busy || cooldown > 0 || !email}
            onClick={() => void resend()}
          >
            {cooldown ? `Resend in ${cooldown}s` : "Resend code"}
          </button>
        </form>
      )}
      <p className="form-switch">
        <Link to="/login">Back to login</Link>
      </p>
    </AuthLayout>
  );
}
