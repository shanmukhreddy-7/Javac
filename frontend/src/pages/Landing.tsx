import { useState } from "react";
import { Link, useNavigate } from "react-router-dom";
import {
  ArrowUpRight,
  ArrowRight,
  Code2,
  PenTool,
  Camera,
  BookOpen,
  ShieldCheck,
  MoveUpRight,
  Menu,
  X,
  Check,
  Plus,
  Layers,
  HandHeart,
} from "lucide-react";
import Brand from "../components/common/Brand";
import { useAuth } from "../contexts/AuthContext";
import { demoEnabled } from "../services/demo";
export default function Landing() {
  const [menu, setMenu] = useState(false);
  const { startDemo, student } = useAuth();
  const navigate = useNavigate();
  const demo = () => {
    startDemo();
    navigate("/dashboard");
  };
  return (
    <div className="landing">
      <header className="site-header">
        <div className="container nav-row">
          <Brand />
          <button
            className="icon-button mobile-toggle"
            aria-label="Toggle navigation"
            aria-expanded={menu}
            onClick={() => setMenu(!menu)}
          >
            {menu ? <X /> : <Menu />}
          </button>
          <nav className={menu ? "open" : ""} aria-label="Main navigation">
            <a href="#how-it-works" onClick={() => setMenu(false)}>
              How it works
            </a>
            <a href="#about" onClick={() => setMenu(false)}>
              About JAVAC
            </a>
            <span className="nav-divider" />
            <Link to={student ? "/dashboard" : "/login"}>
              {student ? "My campus" : "Log in"}
            </Link>
            <Link className="button small" to="/register">
              Get started <ArrowUpRight size={16} />
            </Link>
          </nav>
        </div>
      </header>
      <main>
        <section className="hero container">
          <div className="hero-copy">
            <div className="eyebrow">
              <span className="mini-line" /> FOR STUDENTS. BY STUDENTS.
            </div>
            <h1>
              Your campus.
              <br />
              Your <span>opportunities.</span>
            </h1>
            <p className="hero-description">
              Find help, share your skills, and make the most of campus life —
              all in one place.
            </p>
            <div className="hero-actions">
              <Link to="/register" className="button">
                Get started <ArrowUpRight size={19} />
              </Link>
              <a href="#how-it-works" className="text-button">
                Explore how it works <ArrowRight size={18} />
              </a>
            </div>
            <div className="hero-trust">
              <ShieldCheck size={18} />
              <span>A campus community, connected by trust.</span>
            </div>
          </div>
          <div
            className="campus-composition"
            aria-label="An illustration of students connecting through their skills"
          >
            <div className="composition-top">
              <span>THE CAMPUS CONNECTION</span>
              <span>01 / JAVAC</span>
            </div>
            <div className="orbit orbit-one" />
            <div className="orbit orbit-two" />
            <div className="composition-word">
              a little
              <br />
              <i>campus</i>
              <br />
              chemistry.
            </div>
            <div className="skill-float skill-float-code">
              <Code2 size={21} />
              <span>A knack for code.</span>
              <span className="float-dot" />
            </div>
            <div className="skill-float skill-float-design">
              <PenTool size={21} />
              <span>An eye for design.</span>
            </div>
            <div className="connection-ticket">
              <div className="ticket-top">
                <div className="avatar-stack">
                  <span>A</span>
                  <span>S</span>
                </div>
                <span>THE RIGHT CONNECTION</span>
                <MoveUpRight size={22} />
              </div>
              <div className="ticket-rule" />
              <div className="ticket-main">
                <span>Your skills.</span>
                <Plus size={18} />
                <span>Their next big idea.</span>
              </div>
              <div className="ticket-bottom">
                <Check size={14} /> Better together. Right here on campus.
              </div>
            </div>
            <div className="composition-bottom">
              <span>SKILLS MEET POSSIBILITY</span>
              <span>↗</span>
            </div>
          </div>
        </section>
        <section className="skill-strip" aria-label="Campus skills">
          <div className="container skill-strip-inner">
            <span>
              A CAMPUS FULL
              <br />
              OF POSSIBILITIES
            </span>
            <div>
              <Code2 />
              Programming
            </div>
            <div>
              <PenTool />
              Design
            </div>
            <div>
              <BookOpen />
              Tutoring
            </div>
            <div>
              <Camera />
              Photography
            </div>
            <div>
              <Layers />
              And your thing.
            </div>
          </div>
        </section>
        <section id="how-it-works" className="how-section container">
          <div className="section-heading">
            <div>
              <span className="eyebrow">LESS SEARCHING. MORE CONNECTING.</span>
              <h2>
                Big potential.
                <br />
                Small campus.
              </h2>
            </div>
            <p>
              There’s talent all around you.
              <br />
              JAVAC helps you find it — and share yours.
            </p>
          </div>
          <div className="steps">
            <article>
              <span className="step-number">01</span>
              <div>
                <h3>Make it official.</h3>
                <p>
                  Create your presentation account with a university email. No
                  OTP or verification email — start straight away.
                </p>
                <Link to="/register">
                  Create your account <ArrowUpRight size={16} />
                </Link>
              </div>
            </article>
            <article>
              <span className="step-number">02</span>
              <div>
                <h3>Bring your own thing.</h3>
                <p>
                  Build a profile that feels like you. Add the skills you’re
                  good at and the places you’re around.
                </p>
                <Link to="/register">
                  Find your place <ArrowUpRight size={16} />
                </Link>
              </div>
            </article>
            <article>
              <span className="step-number">03</span>
              <div>
                <h3>Make things happen.</h3>
                <p>
                  Student-to-student jobs and services are next. Get your
                  profile ready for what’s coming.
                </p>
                <span className="coming-soon">MARKETPLACE COMING SOON</span>
              </div>
            </article>
          </div>
        </section>
        <section id="about" className="about-section">
          <div className="container about-inner">
            <div className="about-mark">
              <HandHeart size={42} strokeWidth={1.3} />
              <span>
                YOUR CAMPUS.
                <br />
                YOUR COMMUNITY.
              </span>
            </div>
            <div>
              <span className="eyebrow">THE IDEA BEHIND JAVAC</span>
              <h2>
                Opportunity doesn’t always
                <br />
                have to be far from home.
              </h2>
              <p>
                Job and Value Across Campus brings student talent closer to the
                people who need it. A place to ask for help, put your skills to
                work, and build something valuable together.
              </p>
            </div>
          </div>
        </section>
        <section className="container final-cta">
          <div>
            <span className="eyebrow">THERE’S A PLACE FOR YOUR TALENT.</span>
            <h2>Let’s start on campus.</h2>
          </div>
          <div>
            <Link className="button" to="/register">
              Join your community <ArrowUpRight size={19} />
            </Link>
            {demoEnabled && (
              <button className="demo-link" onClick={demo}>
                Just looking? Explore demo <ArrowRight size={15} />
              </button>
            )}
          </div>
        </section>
      </main>
      <footer className="site-footer container">
        <Brand />
        <span>Made for the people who make campus.</span>
        <span>© {new Date().getFullYear()} JAVAC</span>
      </footer>
    </div>
  );
}
