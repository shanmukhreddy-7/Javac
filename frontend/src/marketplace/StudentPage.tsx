import { useParams } from "react-router-dom";
import { Feedback } from "../components/ui/Feedback";
import { useRemote } from "./useRemote";
import type { Review } from "./types";
export default function StudentPage() {
  const { id } = useParams();
  const { data, error } = useRemote<{
    full_name: string;
    department: string;
    study_year: number;
    bio: string;
    preferred_location: string;
    reviews: Review[];
  }>(`/api/students/${id}`);
  return (
    <section className="market-page">
      <Feedback message={error} />
      {data ? (
        <>
          <span className="eyebrow">YOUR CAMPUS COMMUNITY</span>
          <h1>{data.full_name}</h1>
          <div className="market-panel">
            <p>
              {data.department} · Year {data.study_year}
            </p>
            <p>{data.bio || "This student hasn’t added a bio yet."}</p>
            <p>{data.preferred_location}</p>
          </div>
          <section className="market-panel">
            <h2>Student feedback</h2>
            {data.reviews.length === 0 ? (
              <p>No reviews yet.</p>
            ) : (
              <>
                <strong>
                  {(
                    data.reviews.reduce((s, r) => s + r.rating, 0) /
                    data.reviews.length
                  ).toFixed(1)}{" "}
                  / 5 · latest {data.reviews.length} reviews
                </strong>
                {data.reviews.map((r, i) => (
                  <article className="review-line" key={i}>
                    <strong>
                      {r.rating}/5 · {r.reviewer_name}
                    </strong>
                    <p>{r.comment}</p>
                  </article>
                ))}
              </>
            )}
          </section>
        </>
      ) : !error ? (
        <p>Loading student…</p>
      ) : null}
    </section>
  );
}
