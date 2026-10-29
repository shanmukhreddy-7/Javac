import { useState } from "react";
import { Link, useNavigate, useLocation } from "react-router-dom";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import type { z } from "zod";
import { ArrowUpRight, Eye, EyeOff } from "lucide-react";
import AuthLayout from "../components/layout/AuthLayout";
import Field from "../components/forms/Field";
import { Feedback, Spinner } from "../components/ui/Feedback";
import { loginSchema } from "../services/validation";
import { useAuth } from "../contexts/AuthContext";
export default function Login() {
  const { login } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();
  const [error, setError] = useState("");
  const [show, setShow] = useState(false);
  const {
    register,
    handleSubmit,
    formState: { errors, isSubmitting },
  } = useForm<z.infer<typeof loginSchema>>({
    resolver: zodResolver(loginSchema),
  });
  const submit = handleSubmit(async (data) => {
    setError("");
    try {
      await login(data.email, data.password);
      navigate("/dashboard");
    } catch (e) {
      setError((e as Error).message);
    }
  });
  return (
    <AuthLayout>
      <span className="eyebrow">YOUR CAMPUS IS CALLING</span>
      <h1>Good to see you.</h1>
      <p className="form-intro">Log in and pick up where you left off.</p>
      <Feedback message={error} />
      <Feedback
        success
        message={
          (location.state as { passwordChanged?: boolean } | null)
            ?.passwordChanged
            ? "Password changed. Log in with your new password."
            : ""
        }
      />
      <form onSubmit={submit} noValidate>
        <Field
          label="University email"
          type="email"
          autoComplete="email"
          placeholder="you@university.edu"
          error={errors.email?.message}
          {...register("email")}
        />
        <div className="password-field">
          <Field
            label="Password"
            type={show ? "text" : "password"}
            autoComplete="current-password"
            placeholder="Enter your password"
            error={errors.password?.message}
            {...register("password")}
          />
          <button
            type="button"
            className="password-toggle"
            aria-label={show ? "Hide password" : "Show password"}
            onClick={() => setShow(!show)}
          >
            {show ? <EyeOff size={18} /> : <Eye size={18} />}
          </button>
        </div>
        <div className="form-utility">
          <span>Secure, cookie-based sign in</span>
          <Link to="/forgot-password">Forgot password?</Link>
        </div>
        <button className="button full" disabled={isSubmitting}>
          {isSubmitting ? (
            <Spinner />
          ) : (
            <>
              Log in <ArrowUpRight size={18} />
            </>
          )}
        </button>
      </form>
      <p className="form-switch">
        New around here?{" "}
        <Link to="/register">
          Create an account <ArrowUpRight size={14} />
        </Link>
      </p>
      <div className="subtle-note">
        Waiting for verification?{" "}
        <Link to="/verify-email">Resend your email</Link>
      </div>
    </AuthLayout>
  );
}
