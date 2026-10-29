import { useState, useEffect } from "react";
import { NavLink, Link, Outlet, useNavigate } from "react-router-dom";
import {
  LayoutDashboard,
  MessageSquare,
  UserRound,
  Settings,
  LogOut,
  ArrowUpRight,
  Menu,
  X,
  BriefcaseBusiness,
  Wallet,
  ShieldCheck,
} from "lucide-react";
import { presentationMode } from "../../services/mode";
import Brand from "../common/Brand";
import { Feedback } from "../ui/Feedback";
import { useAuth } from "../../contexts/AuthContext";
export function initials(name: string) {
  return name
    .trim()
    .split(/\s+/)
    .slice(0, 2)
    .map((n) => n[0])
    .join("")
    .toUpperCase();
}
export default function AppLayout() {
  const { student, isDemo, logout } = useAuth();
  const [open, setOpen] = useState(false);
  const [mobile, setMobile] = useState(
    () => window.matchMedia("(max-width: 760px)").matches,
  );
  useEffect(() => {
    const media = window.matchMedia("(max-width: 760px)");
    const update = () => setMobile(media.matches);
    media.addEventListener("change", update);
    return () => media.removeEventListener("change", update);
  }, []);
  useEffect(() => {
    if (!open) return;
    const escape = (event: KeyboardEvent) => {
      if (event.key === "Escape") setOpen(false);
    };
    window.addEventListener("keydown", escape);
    return () => window.removeEventListener("keydown", escape);
  }, [open]);
  const [error, setError] = useState("");
  const navigate = useNavigate();
  const leave = async () => {
    try {
      await logout();
      navigate("/");
    } catch (e) {
      setError((e as Error).message);
    }
  };
  return (
    <div className="app-layout">
      <aside
        className={`sidebar ${open ? "sidebar-open" : ""}`}
        inert={mobile && !open}
        aria-hidden={mobile && !open}
      >
        <div className="sidebar-logo">
          <Brand />
          <button
            className="icon-button sidebar-close"
            aria-label="Close navigation"
            onClick={() => setOpen(false)}
          >
            <X />
          </button>
        </div>
        <span className="nav-caption">YOUR CAMPUS</span>
        <nav aria-label="Student navigation">
          <NavLink to="/dashboard" onClick={() => setOpen(false)}>
            <LayoutDashboard size={19} />
            Overview
          </NavLink>
          <NavLink to="/profile" onClick={() => setOpen(false)}>
            <UserRound size={19} />
            My profile
          </NavLink>
          {presentationMode && (
            <NavLink to="/requests" onClick={() => setOpen(false)}>
              <MessageSquare size={19} />
              Requests
            </NavLink>
          )}
          <NavLink to="/settings" onClick={() => setOpen(false)}>
            <Settings size={19} />
            Settings
          </NavLink>
        </nav>
        <div className="sidebar-divider" />
        <span className="nav-caption">ON THE HORIZON</span>
        <div className="future-nav">
          <span>
            <BriefcaseBusiness size={18} />
            Marketplace <small>Soon</small>
          </span>
          <span>
            <Wallet size={18} />
            My earnings <small>Soon</small>
          </span>
        </div>
        <div className="sidebar-bottom">
          <div className="sidebar-note">
            <ShieldCheck size={23} />
            <strong>
              Your campus.
              <br />A little more connected.
            </strong>
            <p>Your profile is the first step to what’s next.</p>
            <Link to="/profile">
              Make it yours <ArrowUpRight size={15} />
            </Link>
          </div>
          <button className="logout-button" onClick={leave}>
            <LogOut size={17} />
            {isDemo ? "Exit sample" : "Log out"}
          </button>
        </div>
      </aside>
      {open && (
        <button
          className="sidebar-overlay"
          aria-label="Close navigation"
          onClick={() => setOpen(false)}
        />
      )}
      <div className="app-main" inert={mobile && open}>
        <header className="app-header">
          <div className="header-breadcrumb">
            <button
              className="icon-button mobile-toggle"
              aria-label="Open navigation"
              onClick={() => setOpen(true)}
            >
              <Menu />
            </button>
            <span>My campus</span>
            <span className="breadcrumb-slash">/</span>
            <strong>Student space</strong>
            <Link to="/" className="mobile-app-brand" aria-label="JAVAC home">
              <img src="/javac-logo.png" alt="JAVAC" />
            </Link>
          </div>
          <Link to="/profile" className="header-profile">
            <span>
              {student?.fullName}
              <small>
                {isDemo
                  ? "Sample student"
                  : presentationMode
                    ? "Presentation account"
                    : "Student account"}
              </small>
            </span>
            <div className="avatar">
              {initials(student?.fullName ?? "Student")}
            </div>
          </Link>
        </header>
        {(isDemo || presentationMode) && (
          <div className="demo-banner">
            <span className="demo-tag">
              {isDemo ? "OFFLINE SAMPLE" : "PRESENTATION"}
            </span>
            <span>
              {isDemo
                ? "Offline sample. Profile edits stay in this visit; shared requests are unavailable."
                : "No OTP or identity verification. Use fictional details. Requests are shared between accounts."}
            </span>
            <button onClick={leave}>
              {isDemo ? "Exit sample" : "Log out"} <ArrowUpRight size={14} />
            </button>
          </div>
        )}
        <div className="app-content">
          <Feedback message={error} />
          <Outlet />
        </div>
        <footer className="app-footer">
          <span>JAVAC — Job and Value Across Campus</span>
          <span>Made for your next chapter.</span>
        </footer>
      </div>
    </div>
  );
}
