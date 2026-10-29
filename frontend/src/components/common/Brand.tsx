import { Link } from "react-router-dom";
export default function Brand({ light = false }: { light?: boolean }) {
  return (
    <Link
      to="/"
      className={`brand ${light ? "brand-light" : ""}`}
      aria-label="JAVAC home"
    >
      <img src="/javac-logo.png" alt="JAVAC" />
      <span>
        Job and Value
        <br />
        Across Campus
      </span>
    </Link>
  );
}
