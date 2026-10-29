import { presentationMode } from "../services/mode";
import { useState, useEffect } from "react";
import { useLocation } from "react-router-dom";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import {
  ShieldCheck,
  UserRound,
  MapPin,
  Check,
  LockKeyhole,
} from "lucide-react";
import { useAuth } from "../contexts/AuthContext";
import { initials } from "../components/layout/AppLayout";
import Field from "../components/forms/Field";
import SkillPicker from "../components/forms/SkillPicker";
import { Feedback, Spinner } from "../components/ui/Feedback";
import { profileSchema, departments } from "../services/validation";
import type { ProfileInput } from "../types";
export default function Profile() {
  const { student: s, isDemo, saveProfile, saveSkills } = useAuth();
  const [message, setMessage] = useState("");
  const [error, setError] = useState("");
  const location = useLocation();
  const {
    register,
    handleSubmit,
    reset,
    formState: { errors, isSubmitting, isDirty },
  } = useForm<ProfileInput>({
    resolver: zodResolver(profileSchema),
    defaultValues: s ?? {},
  });
  useEffect(() => {
    if (s)
      reset({
        fullName: s.fullName,
        department: s.department,
        studyYear: s.studyYear,
        bio: s.bio,
        preferredLocation: s.preferredLocation,
      });
  }, [s, reset]);
  useEffect(() => {
    if (location.hash === "#skills")
      document
        .getElementById("skills")
        ?.scrollIntoView({ behavior: "instant" });
  }, [location.hash]);
  if (!s) return null;
  const submit = handleSubmit(async (input) => {
    setError("");
    setMessage("");
    try {
      await saveProfile(input);
      setMessage(
        isDemo
          ? "Demo profile updated for this visit."
          : "Your profile has been saved.",
      );
    } catch (e) {
      setError((e as Error).message);
    }
  });
  return (
    <>
      <div className="page-heading">
        <div>
          <span className="eyebrow">A LITTLE MORE YOU</span>
          <h1>Make your introduction.</h1>
          <p>Your campus story, in your own words.</p>
        </div>
        <span className="account-badge">
          <ShieldCheck size={16} />
          {isDemo
            ? "Fictional sample profile"
            : presentationMode
              ? "Presentation profile"
              : "Verified student"}
        </span>
      </div>
      <div className="profile-layout">
        <aside className="profile-card panel">
          <div className="avatar extra-large">{initials(s.fullName)}</div>
          <h2>{s.fullName}</h2>
          <p>{s.department}</p>
          <span className="count-label">
            Year {s.studyYear} · {s.registrationNumber}
          </span>
          <div className="profile-card-divider" />
          <div className="identity-detail">
            <MapPin size={17} />
            <span>{s.preferredLocation || "Your campus location"}</span>
          </div>
          <p className="profile-bio">
            {s.bio || "Your introduction starts with a short bio."}
          </p>
          <div className="progress-label">
            <span>Profile completion</span>
            <strong>{s.completion}%</strong>
          </div>
          <div className="progress-track">
            <span style={{ width: s.completion + "%" }} />
          </div>
          <p className="hint">
            A few details help your campus get to know you.
          </p>
        </aside>
        <div className="profile-edit-column">
          <section className="panel form-panel">
            <div className="panel-heading">
              <h2>
                <UserRound size={19} /> The person behind the profile
              </h2>
              <span className="eyebrow">01 / ABOUT YOU</span>
            </div>
            <Feedback message={error} />
            <Feedback message={message} success />
            <form onSubmit={submit} noValidate>
              <Field
                label="Full name"
                autoComplete="name"
                error={errors.fullName?.message}
                {...register("fullName")}
              />
              <div className="form-grid">
                <Field
                  label="University email"
                  value={s.email}
                  readOnly
                  hint="Linked to your university identity."
                />
                <Field
                  label="Registration number"
                  value={s.registrationNumber}
                  readOnly
                  hint="Linked to this presentation account."
                />
              </div>
              <div className="locked-note">
                <LockKeyhole size={13} /> Identity details are protected.
                Contact your administrator for corrections.
              </div>
              <div className="form-grid">
                <div className="field">
                  <label htmlFor="profile-department">Department</label>
                  <select id="profile-department" {...register("department")}>
                    {[...new Set([...departments, s.department])].map((d) => (
                      <option key={d}>{d}</option>
                    ))}
                  </select>
                  <span className="field-error">
                    {errors.department?.message}
                  </span>
                </div>
                <div className="field">
                  <label htmlFor="profile-year">Year of study</label>
                  <select
                    id="profile-year"
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
                <label htmlFor="bio">A little about you</label>
                <textarea
                  id="bio"
                  rows={4}
                  maxLength={500}
                  placeholder="What do you enjoy? What would you love to help with?"
                  {...register("bio")}
                />
                <span className="hint">
                  A short introduction. Up to 500 characters.
                </span>
                <span className="field-error">{errors.bio?.message}</span>
              </div>
              <Field
                label="Preferred campus location"
                maxLength={100}
                placeholder="e.g. Central library, student centre"
                error={errors.preferredLocation?.message}
                {...register("preferredLocation")}
              />
              <div className="panel-actions">
                <button
                  type="button"
                  className="text-button"
                  onClick={() => {
                    reset();
                    setError("");
                    setMessage("");
                  }}
                  disabled={!isDirty || isSubmitting}
                >
                  Discard changes
                </button>
                <button className="button" disabled={isSubmitting}>
                  {isSubmitting ? (
                    <Spinner />
                  ) : (
                    <>
                      <Check size={16} /> Save profile
                    </>
                  )}
                </button>
              </div>
            </form>
          </section>
          <section className="panel form-panel" id="skills">
            <div className="panel-heading">
              <h2>Your skills, your superpowers.</h2>
              <span className="eyebrow">02 / WHAT YOU BRING</span>
            </div>
            <p className="panel-description">
              Big or small, practical or creative. Choose the things you can
              help with.
            </p>
            <SkillPicker
              selected={s.skills}
              isDemo={isDemo}
              onSave={saveSkills}
            />
          </section>
        </div>
      </div>
    </>
  );
}
