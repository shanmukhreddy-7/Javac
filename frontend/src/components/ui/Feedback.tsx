import { AlertCircle, CheckCircle2, LoaderCircle } from "lucide-react";
export function Feedback({
  message,
  success = false,
}: {
  message: string;
  success?: boolean;
}) {
  if (!message) return null;
  return (
    <div
      className={`feedback ${success ? "success" : ""}`}
      role={success ? "status" : "alert"}
    >
      {success ? <CheckCircle2 size={18} /> : <AlertCircle size={18} />}
      <span>{message}</span>
    </div>
  );
}
export function Spinner() {
  return <LoaderCircle className="spin" size={18} aria-label="Loading" />;
}
