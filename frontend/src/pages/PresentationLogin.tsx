import { useState } from "react";
import { Link, useLocation, useNavigate } from "react-router-dom";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { z } from "zod";
import { ArrowUpRight } from "lucide-react";
import AuthLayout from "../components/layout/AuthLayout";
import Field from "../components/forms/Field";
import { Feedback, Spinner } from "../components/ui/Feedback";
import { useAuth } from "../contexts/AuthContext";
const schema = z.object({
  email: z.email("Enter a valid university email.").max(254),
});
export default function PresentationLogin() {
  const { login } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();
  const state = location.state as {
    email?: string;
    registered?: boolean;
  } | null;
  const [error, setError] = useState("");
  const {
    register,
    handleSubmit,
    formState: { errors, isSubmitting },
  } = useForm<z.infer<typeof schema>>({
    resolver: zodResolver(schema),
    defaultValues: { email: state?.email ?? "" },
  });
  const submit = handleSubmit(async (data) => {
    setError("");
    try {
      await login(data.email, "");
      navigate("/dashboard");
    } catch (e) {
      setError((e as Error).message);
    }
  });
  return (
    <AuthLayout>
      <span className="eyebrow">YOUR CAMPUS IS CALLING</span>
      <h1>Good to see you.</h1>
      <p className="form-intro">
        Enter the email used for your presentation account.
      </p>
      <div className="presentation-note">
        <strong>No codes. No waiting.</strong>
        <p>
          This presentation signs in by email only. It does not verify identity,
          so use fictional details and keep private information out.
        </p>
      </div>
      <Feedback message={error} />
      {state?.registered && (
        <Feedback
          success
          message="Account created. Sign in now — no OTP or email verification is needed."
        />
      )}
      <form onSubmit={submit} noValidate>
        <Field
          label="University email"
          type="email"
          autoComplete="email"
          placeholder="student@srmap.edu.in"
          error={errors.email?.message}
          {...register("email")}
        />
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
    </AuthLayout>
  );
}
