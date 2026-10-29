import type { ReactNode } from "react";
import { ArrowLeft, ArrowUpRight, ShieldCheck } from "lucide-react";
import { Link } from "react-router-dom";
import Brand from "../common/Brand";
export default function AuthLayout({
  children,
  wide = false,
}: {
  children: ReactNode;
  wide?: boolean;
}) {
  return (
    <div className="auth-layout">
      <aside className="auth-story">
        <Brand light />
        <div className="auth-story-content">
          <span className="eyebrow light">BUILT FOR YOUR CAMPUS</span>
          <h2>
            A little help.
            <br />A new connection.
            <br />
            <span>A bigger possibility.</span>
          </h2>
          <p>
            Your next opportunity could be someone
            <br />
            you pass on the way to class.
          </p>
          <div className="story-ticket">
            <div className="ticket-icon">
              <ArrowUpRight size={27} />
            </div>
            <div>
              <strong>Good things start with a connection.</strong>
              <span>Your skills belong here.</span>
            </div>
          </div>
        </div>
        <div className="auth-story-foot">
          <ShieldCheck size={17} /> University email. A community you can trust.
        </div>
      </aside>
      <main className="auth-main">
        <Link className="back-link" to="/">
          <ArrowLeft size={16} /> Back to campus
        </Link>
        <div className="auth-mobile-brand">
          <Brand />
        </div>
        <div className={`auth-form-wrap ${wide ? "wide" : ""}`}>{children}</div>
        <div className="auth-footer">
          JAVAC <span>Job and Value Across Campus</span>
        </div>
      </main>
    </div>
  );
}
