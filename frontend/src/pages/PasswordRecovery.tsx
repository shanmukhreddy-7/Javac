import { useState } from "react";
import { Link } from "react-router-dom";
import AuthLayout from "../components/layout/AuthLayout";
import Field from "../components/forms/Field";
import { Feedback } from "../components/ui/Feedback";
import { api } from "../services/api";
import { passwordSchema } from "../services/validation";
export default function PasswordRecovery() {
  const [email, setEmail] = useState(""),
    [code, setCode] = useState(""),
    [password, setPassword] = useState(""),
    [confirm, setConfirm] = useState(""),
    [stage, setStage] = useState(0),
    [busy, setBusy] = useState(false),
    [error, setError] = useState("");
  async function submit(e: React.FormEvent) {
    e.preventDefault();
    setError("");
    if (stage === 1) {
      const result = passwordSchema.safeParse(password);
      if (!result.success) {
        setError(result.error.issues[0].message);
        return;
      }
      if (password !== confirm) {
        setError("Passwords do not match.");
        return;
      }
    }
    setBusy(true);
    try {
      await api(
        stage === 0 ? "/api/auth/forgot-password" : "/api/auth/reset-otp",
        "POST",
        stage === 0 ? { email } : { email, code, password },
      );
      setStage(stage === 0 ? 1 : 2);
      setPassword("");
      setConfirm("");
    } catch (e) {
      setError((e as Error).message);
    } finally {
      setBusy(false);
    }
  }
  return (
    <AuthLayout>
      <span className="eyebrow">LET’S GET YOU BACK IN</span>
      <h1>{stage === 2 ? "A fresh start." : "Reset your password."}</h1>
      <p className="form-intro">
        {stage === 0
          ? "We’ll send a reset code if your account is eligible."
          : stage === 1
            ? "Enter the six-digit email code and a strong new password. Codes expire after five minutes."
            : "Your password was changed. Previous sessions have been revoked."}
      </p>
      <Feedback message={error} />
      {stage < 2 ? (
        <form onSubmit={submit}>
          <Field
            label="University email"
            type="email"
            required
            autoComplete="email"
            value={email}
            onChange={(e) => setEmail(e.target.value)}
            readOnly={stage === 1}
          />
          {stage === 1 ? (
            <>
              <Field
                label="Reset code"
                inputMode="numeric"
                required
                pattern="[0-9]{6}"
                maxLength={6}
                autoComplete="one-time-code"
                value={code}
                onChange={(e) => setCode(e.target.value.replace(/\D/g, ""))}
              />
              <Field
                label="New password"
                type="password"
                required
                autoComplete="new-password"
                value={password}
                onChange={(e) => setPassword(e.target.value)}
                hint="12–64 characters with uppercase, lowercase, number and symbol."
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
          ) : null}
          <button className="button full" disabled={busy}>
            {busy
              ? "Working…"
              : stage === 0
                ? "Send reset code"
                : "Reset password"}
          </button>
          {stage === 1 ? (
            <button
              type="button"
              className="text-button"
              onClick={() => {
                setStage(0);
                setCode("");
              }}
            >
              Request another code
            </button>
          ) : null}
        </form>
      ) : (
        <Link className="button full" to="/login">
          Continue to login
        </Link>
      )}
      <p className="form-switch">
        <Link to="/login">Back to login</Link>
      </p>
    </AuthLayout>
  );
}
