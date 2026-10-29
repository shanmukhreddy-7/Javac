import { Navigate, Outlet, useLocation } from "react-router-dom";
import { useAuth } from "../../contexts/AuthContext";
import { Spinner } from "../ui/Feedback";
export default function ProtectedRoute() {
  const { student, loading } = useAuth();
  const location = useLocation();
  if (loading)
    return (
      <div className="route-loading" role="status">
        <Spinner /> Opening your campus…
      </div>
    );
  return student ? (
    <Outlet />
  ) : (
    <Navigate to="/login" replace state={{ from: location.pathname }} />
  );
}
