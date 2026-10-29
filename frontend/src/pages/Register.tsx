import { useState, useEffect } from "react";
import { Link, useNavigate } from "react-router-dom";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import type { z } from "zod";
import { ArrowUpRight, ShieldCheck } from "lucide-react";
import AuthLayout from "../components/layout/AuthLayout";
import TermsDialog from "../components/common/TermsDialog";
import Field from "../components/forms/Field";
import { Feedback, Spinner } from "../components/ui/Feedback";
import { registerSchema, departments } from "../services/validation";
import { api, RequestError } from "../services/api";
export default function Register() {
  const navigate = useNavigate();
  const [error, setError] = useState("");
  const [domains, setDomains] = useState<string[]>([]);
  const [terms, setTerms] = useState(false);
  useEffect(() => {
    api<{ approvedDomains: string[] }>("/api/auth/config")
      .then((r) => setDomains(r.approvedDomains))
      .catch(() => {});
  }, []);
  const {
    register,
    handleSubmit,
    setError: fieldError,
    formState: { errors, isSubmitting },
  } = useForm<z.infer<typeof registerSchema>>({
    resolver: zodResolver(registerSchema),
    defaultValues: { studyYear: 1, termsAccepted: false },
  });
  const submit = handleSubmit(async (data) => {
    setError("");
    try {
      if (
        domains.length &&
        !domains.includes(data.email.split("@")[1].toLowerCase())
      ) {
        fieldError("email", {
          message: "Use your approved university email: " + domains.join(", "),
        });
        return;
      }
      await api("/api/auth/register", "POST", data);
      navigate("/verify-email", {
        state: { email: data.email, registered: true },
      });
    } catch (e) {
      setError((e as Error).message);
      if (e instanceof RequestError)
        Object.entries(e.fields).forEach(([key, message]) =>
          fieldError(key as keyof z.infer<typeof registerSchema>, { message }),
        );
    }
  });
  return (
    <AuthLayout wide>
      <span className="eyebrow">YOUR NEXT CHAPTER STARTS HERE</span>
      <h1>
        Find your people.
        <br />
        Bring your potential.
      </h1>
      <p className="form-intro">
        Create your student account. Make campus yours.
      </p>
      <Feedback message={error} />
      <form noValidate onSubmit={submit}>
        <div className="form-section-label">
          01 <span>THE BASICS</span>
        </div>
        <Field
          label="Full name"
          autoComplete="name"
          placeholder="Your first and last name"
          error={errors.fullName?.message}
          {...register("fullName")}
        />
        <Field
          label="University email"
          type="email"
          autoComplete="email"
          placeholder="you@university.edu"
          hint={
            domains.length
              ? "Approved domains: " + domains.join(", ")
              : "Use your institution’s approved student email address."
          }
          error={errors.email?.message}
          {...register("email")}
        />
        <div className="form-grid">
          <Field
            label="Registration number"
            placeholder="Your student ID"
            error={errors.registrationNumber?.message}
            {...register("registrationNumber")}
          />
          <div className="field">
            <label htmlFor="year">Year of study</label>
            <select
              id="year"
              {...register("studyYear", { valueAsNumber: true })}
            >
              {[1, 2, 3, 4, 5, 6].map((n) => (
                <option key={n} value={n}>
                  Year {n}
                </option>
              ))}
            </select>
          </div>
        </div>
        <div className="field">
          <label htmlFor="department">Department</label>
          <select
            id="department"
            aria-invalid={!!errors.department}
            {...register("department")}
          >
            <option value="">Select your department</option>
            {departments.map((d) => (
              <option key={d}>{d}</option>
            ))}
          </select>
          {errors.department && (
            <span className="field-error" role="alert">
              {errors.department.message}
            </span>
          )}
        </div>
        <div className="form-section-label">
          02 <span>KEEP IT SECURE</span>
        </div>
        <div className="form-grid">
          <Field
            label="Password"
            type="password"
            autoComplete="new-password"
            placeholder="Create a password"
            error={errors.password?.message}
            {...register("password")}
          />
          <Field
            label="Confirm password"
            type="password"
            autoComplete="new-password"
            placeholder="Repeat your password"
            error={errors.confirmPassword?.message}
            {...register("confirmPassword")}
          />
        </div>
        <p className="password-hint">
          12–64 characters, with upper/lowercase letters, a number, and a
          symbol.
        </p>
        <label className="checkbox-label">
          <input type="checkbox" {...register("termsAccepted")} />
          <span>
            I agree to the{" "}
            <button
              type="button"
              className="inline-link"
              onClick={() => setTerms(true)}
            >
              community terms
            </button>{" "}
            and to using JAVAC responsibly.
          </span>
        </label>
        {errors.termsAccepted && (
          <span className="field-error" role="alert">
            {errors.termsAccepted.message}
          </span>
        )}
        <button className="button full" disabled={isSubmitting}>
          {isSubmitting ? (
            <Spinner />
          ) : (
            <>
              Create my account <ArrowUpRight size={18} />
            </>
          )}
        </button>
        <p className="form-security">
          <ShieldCheck size={15} /> Your university email keeps this community
          student-only.
        </p>
      </form>
      <p className="form-switch">
        Already part of the campus? <Link to="/login">Log in</Link>
      </p>
      {terms && <TermsDialog onClose={() => setTerms(false)} />}
    </AuthLayout>
  );
}
