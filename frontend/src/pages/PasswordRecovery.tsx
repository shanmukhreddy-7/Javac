import { useState } from "react";
import { Link, useLocation } from "react-router-dom";
import AuthLayout from "../components/layout/AuthLayout";
import Field from "../components/forms/Field";
import { Feedback, Spinner } from "../components/ui/Feedback";
import { api } from "../services/api";
import { passwordSchema } from "../services/validation";
export default function PasswordRecovery() {
  const location = useLocation();
  const reset = location.pathname === "/reset-password";
  const [token] = useState(() => {
    const value =
      new URLSearchParams(location.hash.slice(1)).get("token") ??
      new URLSearchParams(location.search).get("token") ??
      "";
    if (reset) window.history.replaceState(null, "", "/reset-password");
    return value;
  });
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [confirm, setConfirm] = useState("");
  const [message, setMessage] = useState("");
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);
  const [done, setDone] = useState(false);
  const submit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError("");
    setMessage("");
    if (reset) {
      const parsed = passwordSchema.safeParse(password);
      if (!parsed.success) {
        setError(parsed.error.issues[0].message);
        return;
      }
      if (password !== confirm) {
        setError("Passwords do not match.");
        return;
      }
      if (!token) {
        setError("This reset link is missing its token. Request a new link.");
        return;
      }
    }
    setBusy(true);
    try {
      const r = await api<{ message: string }>(
        reset ? "/api/auth/reset-password" : "/api/auth/forgot-password",
        "POST",
        reset ? { token, password } : { email },
      );
      setMessage(r.message);
      setDone(true);
      setPassword("");
      setConfirm("");
    } catch (e) {
      setError((e as Error).message);
    } finally {
      setBusy(false);
    }
  };
  return (
    <AuthLayout>
      <span className="eyebrow">LET’S GET YOU BACK IN</span>
      <h1>{reset ? "A fresh start." : "Forgot your password?"}</h1>
      <p className="form-intro">
        {reset
          ? "Choose a strong new password. Your previous sessions will be signed out."
          : "It happens. We’ll send a reset link if your account is eligible."}
      </p>
      <Feedback message={error} />
      <Feedback message={message} success />
      {!done && (
        <form onSubmit={submit}>
          {reset ? (
            <>
              <Field
                label="New password"
                type="password"
                required
                autoComplete="new-password"
                value={password}
                onChange={(e) => setPassword(e.target.value)}
                hint="12–64 characters: upper/lowercase, a number and a symbol."
              />
              <Field
                label="Confirm new password"
                type="password"
                required
                autoComplete="new-password"
                value={confirm}
                onChange={(e) => setConfirm(e.target.value)}
              />
            </>
          ) : (
            <Field
              label="University email"
              type="email"
              required
              autoComplete="email"
              value={email}
              onChange={(e) => setEmail(e.target.value)}
            />
          )}
          <button className="button full" disabled={busy}>
            {busy ? <Spinner /> : reset ? "Reset password" : "Send reset link"}
          </button>
        </form>
      )}
      <p className="form-switch">
        <Link to="/login">Back to login</Link>
        {reset && (
          <>
            {" "}
            · <Link to="/forgot-password">Request a new link</Link>
          </>
        )}
      </p>
    </AuthLayout>
  );
}
