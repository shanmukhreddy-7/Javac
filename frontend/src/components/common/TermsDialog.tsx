import { presentationMode } from "../../services/mode";
import { useEffect, useRef } from "react";
export default function TermsDialog({ onClose }: { onClose: () => void }) {
  const dialog = useRef<HTMLDialogElement>(null);
  useEffect(() => {
    dialog.current?.showModal();
  }, []);
  return (
    <dialog
      className="modal"
      ref={dialog}
      onClose={onClose}
      aria-labelledby="terms-title"
    >
      <h2 id="terms-title">A good campus starts with us.</h2>
      <p>
        {presentationMode
          ? "Use fictional student details for this classroom presentation. Treat others respectfully and do not impersonate real students or share personal information."
          : "Use your own university email and accurate student information. Treat others respectfully, share only skills you can offer, and do not impersonate students or misuse personal information."}
      </p>
      <p>
        {presentationMode
          ? "This classroom build stores presentation profiles, selected skills, and campus requests. Sign-in uses email only, with no identity verification. Use fictional details; do not share private information. Payments and jobs are not available."
          : "Module 1 stores your account, profile and selected skills. Passwords are hashed; email is used for account recovery."}
      </p>
      <button className="button" onClick={() => dialog.current?.close()}>
        Understood
      </button>
    </dialog>
  );
}
