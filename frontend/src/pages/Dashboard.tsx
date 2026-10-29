import { presentationMode } from "../services/mode";
import { Link } from "react-router-dom";
import {
  ArrowUpRight,
  ArrowRight,
  CheckCircle2,
  ShieldCheck,
  UserRound,
  Plus,
  BriefcaseBusiness,
  PenLine,
  Wallet,
  Bell,
  MapPin,
  GraduationCap,
} from "lucide-react";
import { useAuth } from "../contexts/AuthContext";
import { initials } from "../components/layout/AppLayout";
export default function Dashboard() {
  const { student: s, isDemo } = useAuth();
  if (!s) return null;
  const checklist = [
    { label: "Add your student details", done: true },
    { label: "Tell us a little about yourself", done: !!s.bio.trim() },
    {
      label: "Choose your campus location",
      done: !!s.preferredLocation.trim(),
    },
    { label: "Add the skills you can share", done: s.skills.length > 0 },
  ];
  return (
    <>
      <div className="page-heading">
        <div>
          <span className="eyebrow">A GOOD PLACE TO BEGIN</span>
          <h1>
            Hey, {s.fullName.split(" ")[0]}. <span className="greeting-dot" />
          </h1>
          <p>Your campus, your skills, your next chapter.</p>
        </div>
        <Link className="button secondary" to="/profile">
          Edit my profile <ArrowUpRight size={17} />
        </Link>
      </div>
      <div className="dashboard-grid">
        <section className="welcome-panel">
          <div className="welcome-top">
            <span className="eyebrow light">YOUR CAMPUS STORY STARTS HERE</span>
            <ArrowUpRight size={26} />
          </div>
          <h2>
            You’ve got something
            <br />
            worth sharing.
          </h2>
          <p>
            A skill, a fresh perspective, a helping hand.
            <br />
            Let your profile do the introductions.
          </p>
          <Link className="button white" to="/profile">
            Make your profile yours <ArrowRight size={17} />
          </Link>
          <span className="welcome-decoration" aria-hidden="true">
            ↗
          </span>
        </section>
        <section className="panel completion-panel">
          <div className="panel-heading">
            <h2>Ready for what’s next?</h2>
            <span className="eyebrow">YOUR PROFILE</span>
          </div>
          <div className="completion-value">
            {s.completion}
            <span>%</span>
            <small>complete</small>
          </div>
          <div
            className="progress-track"
            role="progressbar"
            aria-label="Profile completion"
            aria-valuenow={s.completion}
            aria-valuemin={0}
            aria-valuemax={100}
          >
            <span style={{ width: s.completion + "%" }} />
          </div>
          <ul className="checklist">
            {checklist.map((c) => (
              <li key={c.label}>
                {c.done ? (
                  <CheckCircle2 size={16} />
                ) : (
                  <span className="empty-check" />
                )}
                <span>{c.label}</span>
              </li>
            ))}
          </ul>
        </section>
        <section className="panel profile-summary">
          <div className="panel-heading">
            <h2>Your campus identity</h2>
            <Link to="/profile" aria-label="View profile">
              <ArrowUpRight size={20} />
            </Link>
          </div>
          <div className="identity-row">
            <div className="avatar large">{initials(s.fullName)}</div>
            <div>
              <h3>{s.fullName}</h3>
              <p>{s.registrationNumber}</p>
            </div>
          </div>
          <div className="identity-detail">
            <GraduationCap size={17} />
            <span>
              {s.department} · Year {s.studyYear}
            </span>
          </div>
          <div className="identity-detail">
            <MapPin size={17} />
            <span>
              {s.preferredLocation || "Add your preferred campus location"}
            </span>
          </div>
          <div
            className={`verification-status ${isDemo || presentationMode ? "sample-status" : ""}`}
          >
            <ShieldCheck size={18} />
            <div>
              <strong>
                {isDemo
                  ? "Sample account · not verified"
                  : presentationMode
                    ? "Presentation account · no email check"
                    : "University email verified"}
              </strong>
              <span>{s.email}</span>
            </div>
          </div>
        </section>
        <section className="panel skills-overview">
          <div className="panel-heading">
            <h2>Your skills, your edge.</h2>
            <span className="count-label">{s.skills.length} selected</span>
          </div>
          <p>The things you know could be just what someone needs.</p>
          <div className="skill-chips">
            {s.skills.length ? (
              s.skills.map((skill) => (
                <span className="skill-chip" key={skill.id}>
                  {skill.name}
                </span>
              ))
            ) : (
              <div className="empty-state">
                <UserRound size={21} />
                <span>Your skills belong here. Add your first one.</span>
              </div>
            )}
          </div>
          <Link to="/profile#skills" className="text-button">
            <Plus size={16} /> Manage my skills <ArrowUpRight size={16} />
          </Link>
        </section>
      </div>
      {presentationMode && (
        <section className="requests-dashboard-link panel">
          <div>
            <h2>Connect with another student.</h2>
            <p>
              Send a campus request, check your inbox, and respond to the people
              who need your skills.
            </p>
          </div>
          <Link className="button" to="/requests">
            Open requests <ArrowUpRight size={17} />
          </Link>
        </section>
      )}
      <section className="horizon-section">
        <div className="horizon-heading">
          <div>
            <span className="eyebrow">THIS IS JUST THE BEGINNING</span>
            <h2>A little further down the road.</h2>
          </div>
          <span className="coming-soon">COMING IN FUTURE MODULES</span>
        </div>
        <div className="horizon-grid">
          {[
            {
              icon: BriefcaseBusiness,
              name: "Browse jobs",
              copy: "Find a place to put your skills to work.",
            },
            {
              icon: PenLine,
              name: "Post a job",
              copy: "Find the right person for a helping hand.",
            },
            {
              icon: Wallet,
              name: "My jobs & earnings",
              copy: "Keep your opportunities in one place.",
            },
            {
              icon: Bell,
              name: "Notifications",
              copy: "Stay connected to what matters.",
            },
          ].map(({ icon: Icon, name, copy }) => (
            <article key={name}>
              <Icon size={22} />
              <h3>{name}</h3>
              <p>{copy}</p>
              <span>Coming soon</span>
            </article>
          ))}
        </div>
      </section>
    </>
  );
}
