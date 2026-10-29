import { useEffect, useState } from "react";
import { Link, useNavigate } from "react-router-dom";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import type { z } from "zod";
import { ArrowUpRight } from "lucide-react";
import AuthLayout from "../components/layout/AuthLayout";
import Field from "../components/forms/Field";
import TermsDialog from "../components/common/TermsDialog";
import { Feedback, Spinner } from "../components/ui/Feedback";
import { api, RequestError } from "../services/api";
import {
  presentationRegistrationSchema,
  departments,
} from "../services/validation";
type Registration = z.infer<typeof presentationRegistrationSchema>;
export default function PresentationRegister() {
  const [error, setError] = useState("");
  const [terms, setTerms] = useState(false);
  const [domains, setDomains] = useState<string[]>([]);
  const navigate = useNavigate();
  useEffect(() => {
    api<{ approvedDomains: string[] }>("/api/auth/config")
      .then((r) => setDomains(r.approvedDomains))
      .catch(() => {});
  }, []);
  const {
    register,
    handleSubmit,
    setError: setFieldError,
    formState: { errors, isSubmitting },
  } = useForm<Registration>({
    resolver: zodResolver(presentationRegistrationSchema),
    defaultValues: { studyYear: 2, termsAccepted: false },
  });
  const submit = handleSubmit(async (data) => {
    setError("");
    try {
      if (
        domains.length &&
        !domains.includes(data.email.split("@")[1].toLowerCase())
      ) {
        setFieldError("email", {
          message: "Use your approved university domain: " + domains.join(", "),
        });
        return;
      }
      await api("/api/auth/register", "POST", data);
      navigate("/login", { state: { registered: true, email: data.email } });
    } catch (e) {
      setError((e as Error).message);
      if (e instanceof RequestError)
        Object.entries(e.fields).forEach(([key, message]) =>
          setFieldError(key as keyof Registration, { message }),
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
        Create your presentation account and start right away.
      </p>
      <div className="presentation-note">
        <strong>Classroom presentation</strong>
        <p>
          No password, OTP, or email verification. Use fictional details: these
          accounts demonstrate the product and do not verify your identity.
        </p>
      </div>
      <Feedback message={error} />
      <form noValidate onSubmit={submit}>
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
          placeholder="student@srmap.edu.in"
          hint={
            domains.length
              ? "Approved domain: " + domains.join(", ")
              : "Use the configured university domain."
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
            <label htmlFor="presentation-year">Year of study</label>
            <select
              id="presentation-year"
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
          <label htmlFor="presentation-department">Department</label>
          <select
            id="presentation-department"
            aria-invalid={!!errors.department}
            {...register("department")}
          >
            <option value="">Select your department</option>
            {departments.map((d) => (
              <option key={d}>{d}</option>
            ))}
          </select>
          {errors.department && (
            <span role="alert" className="field-error">
              {errors.department.message}
            </span>
          )}
        </div>
        <label className="checkbox-label">
          <input type="checkbox" {...register("termsAccepted")} />
          <span>
            I agree to the{" "}
            <button
              className="inline-link"
              type="button"
              onClick={() => setTerms(true)}
            >
              community terms
            </button>{" "}
            and understand this is a presentation account.
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
      </form>
      <p className="form-switch">
        Already registered? <Link to="/login">Log in</Link>
      </p>
      {terms && <TermsDialog onClose={() => setTerms(false)} />}
    </AuthLayout>
  );
}
