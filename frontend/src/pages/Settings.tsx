import { presentationMode } from "../services/mode";
import { useState } from "react";
import { useNavigate } from "react-router-dom";
import { LockKeyhole, LogOut, ShieldCheck } from "lucide-react";
import { useAuth } from "../contexts/AuthContext";
import Field from "../components/forms/Field";
import { Feedback, Spinner } from "../components/ui/Feedback";
import { api } from "../services/api";
import { passwordSchema } from "../services/validation";
export default function Settings() {
  const { student: s, isDemo, logout, clearSession } = useAuth();
  const navigate = useNavigate();
  const [current, setCurrent] = useState("");
  const [password, setPassword] = useState("");
  const [confirm, setConfirm] = useState("");
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);
  if (!s) return null;
  const change = async (e: React.FormEvent) => {
    e.preventDefault();
    setError("");
    const parsed = passwordSchema.safeParse(password);
    if (!parsed.success) {
      setError(parsed.error.issues[0].message);
      return;
    }
    if (password !== confirm) {
      setError("Passwords do not match.");
      return;
    }
    setBusy(true);
    try {
      await api("/api/auth/change-password", "POST", {
        currentPassword: current,
        password,
      });
      setCurrent("");
      setPassword("");
      setConfirm("");
      clearSession();
      navigate("/login", { state: { passwordChanged: true } });
    } catch (e) {
      setError((e as Error).message);
    } finally {
      setBusy(false);
    }
  };
  const leave = async () => {
    setBusy(true);
    setError("");
    try {
      await logout();
      navigate("/");
    } catch (e) {
      setError((e as Error).message);
    } finally {
      setBusy(false);
    }
  };
  return (
    <>
      <div className="page-heading">
        <div>
          <span className="eyebrow">THE DETAILS THAT MATTER</span>
          <h1>Your account, in order.</h1>
          <p>A few essentials. All in one place.</p>
        </div>
      </div>
      <div className="settings-column">
        <section className="panel form-panel">
          <div className="panel-heading">
            <h2>
              <ShieldCheck size={20} /> Account information
            </h2>
            <span className="count-label">
              {isDemo
                ? "SAMPLE"
                : presentationMode
                  ? "PRESENTATION"
                  : "STUDENT"}
            </span>
          </div>
          <dl className="account-details">
            <div>
              <dt>University email</dt>
              <dd>{s.email}</dd>
            </div>
            <div>
              <dt>Account status</dt>
              <dd>
                {isDemo
                  ? "Fictional sample · not university verified"
                  : presentationMode
                    ? "Presentation account · email not checked"
                    : s.accountStatus === "ACTIVE"
                      ? "Active · university email verified"
                      : s.accountStatus}
              </dd>
            </div>
            <div>
              <dt>Registration number</dt>
              <dd>{s.registrationNumber}</dd>
            </div>
            <div>
              <dt>{isDemo ? "Sample date" : "Joined JAVAC"}</dt>
              <dd>
                {new Date(s.createdAt).toLocaleDateString("en-IN", {
                  day: "numeric",
                  month: "long",
                  year: "numeric",
                })}
              </dd>
            </div>
          </dl>
        </section>
        <section className="panel form-panel">
          <div className="panel-heading">
            <h2>
              <LockKeyhole size={19} /> Your sign-in method
            </h2>
          </div>
          {isDemo || presentationMode ? (
            <div className="demo-settings-note">
              <strong>No passwords or verification codes.</strong>
              <p>
                This classroom presentation signs in by email only. It does not
                establish who owns an email address. Use fictional details and
                do not include private information in your profile or requests.
              </p>
            </div>
          ) : (
            <>
              <p className="panel-description">
                Changing your password signs you out on every device.
              </p>
              <Feedback message={error} />
              <form onSubmit={change}>
                <Field
                  label="Current password"
                  type="password"
                  required
                  autoComplete="current-password"
                  value={current}
                  onChange={(e) => setCurrent(e.target.value)}
                />
                <div className="form-grid">
                  <Field
                    label="New password"
                    type="password"
                    required
                    autoComplete="new-password"
                    value={password}
                    onChange={(e) => setPassword(e.target.value)}
                  />
                  <Field
                    label="Confirm new password"
                    type="password"
                    required
                    autoComplete="new-password"
                    value={confirm}
                    onChange={(e) => setConfirm(e.target.value)}
                  />
                </div>
                <p className="password-hint">
                  12–64 characters, with upper/lowercase, a number and a symbol.
                </p>
                <div className="panel-actions">
                  <button className="button" disabled={busy}>
                    {busy ? <Spinner /> : "Update password & log out"}
                  </button>
                </div>
              </form>
            </>
          )}
        </section>
        <section className="panel logout-panel">
          <div>
            <h2>See you around campus.</h2>
            <p>
              {isDemo
                ? "Leave the sample experience and return to the homepage."
                : "End this session securely. Your profile will be here when you return."}
            </p>
          </div>
          <button className="button secondary" disabled={busy} onClick={leave}>
            <LogOut size={16} />
            {isDemo ? "Exit demo" : "Log out"}
          </button>
        </section>
        {isDemo && <Feedback message={error} />}
      </div>
    </>
  );
}
