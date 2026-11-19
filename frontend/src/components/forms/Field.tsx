/**
 * Reusable labelled input with generated IDs and linked error/hint text for assistive technology.
 */
import { useId } from "react";
import type { InputHTMLAttributes, ReactNode } from "react";
interface Props extends InputHTMLAttributes<HTMLInputElement> {
  label: string;
  error?: string;
  hint?: string;
  children?: ReactNode;
}
export default function Field({
  label,
  error,
  hint,
  children,
  ...props
}: Props) {
  // A stable generated ID connects each label, input, and help message when the caller supplies no
  // ID.
  const generated = useId();
  const id = props.id ?? generated;
  return (
    <div className="field">
      <label htmlFor={id}>{label}</label>
      {children ?? (
        <input
          {...props}
          id={id}
          aria-invalid={!!error}
          aria-describedby={error || hint ? id + "-help" : undefined}
        />
      )}
      <div id={id + "-help"}>
        {error ? (
          <span className="field-error" role="alert">
            {error}
          </span>
        ) : hint ? (
          <span className="hint">{hint}</span>
        ) : null}
      </div>
    </div>
  );
}
