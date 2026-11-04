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
          : "JAVAC stores your verified account, profile, skills, jobs, messages and reviews. Passwords are hashed. Rewards are paid directly between students; JAVAC records self-reported payment collection and does not transfer funds."}
      </p>
      <button className="button" onClick={() => dialog.current?.close()}>
        Understood
      </button>
    </dialog>
  );
}
