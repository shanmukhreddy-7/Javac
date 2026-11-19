/**
 * Route map for the browser application. Screens load lazily; AuthProvider shares session state.
 * ProtectedRoute controls signed-in navigation, while Java independently authorizes API access.
 */
import { BrowserRouter, Routes, Route, Link, Navigate } from "react-router-dom";
import { lazy, Suspense } from "react";
import { AuthProvider } from "./contexts/AuthContext";
import ProtectedRoute from "./components/common/ProtectedRoute";
import AppLayout from "./components/layout/AppLayout";
// Load screen bundles on demand; common routing and session state remain available immediately.
const Landing = lazy(() => import("./pages/Landing"));
const Login = lazy(() => import("./pages/Login"));
const Register = lazy(() => import("./pages/Register"));
const PresentationRegister = lazy(() => import("./pages/PresentationRegister"));
const PresentationLogin = lazy(() => import("./pages/PresentationLogin"));
const Jobs = lazy(() => import("./marketplace/Jobs"));
const PostJob = lazy(() => import("./marketplace/PostJob"));
const JobDetail = lazy(() => import("./marketplace/JobDetail"));
const Earnings = lazy(() => import("./marketplace/Earnings"));
const Wallet = lazy(() => import("./marketplace/Wallet"));
const Notifications = lazy(() => import("./marketplace/Notifications"));
const Admin = lazy(() => import("./marketplace/Admin"));
const StudentPage = lazy(() => import("./marketplace/StudentPage"));
import { presentationMode } from "./services/mode";
const VerifyEmail = lazy(() => import("./pages/VerifyEmail"));
const PasswordRecovery = lazy(() => import("./pages/PasswordRecovery"));
const Dashboard = lazy(() => import("./pages/Dashboard"));
const Profile = lazy(() => import("./pages/Profile"));
const Settings = lazy(() => import("./pages/Settings"));
import { useScrollReset } from "./hooks/useScrollReset";
function ScrollReset() {
  useScrollReset();
  return null;
}
export default function App() {
  return (
    <BrowserRouter>
      <ScrollReset />
      <AuthProvider>
        <Suspense fallback={<div className="route-loader" role="status">Opening your campus…</div>}>
        <Routes>
          <Route path="/" element={<Landing />} />
          <Route
            path="/login"
            element={presentationMode ? <PresentationLogin /> : <Login />}
          />
          <Route
            path="/register"
            element={presentationMode ? <PresentationRegister /> : <Register />}
          />
          <Route
            path="/verify-email"
            element={
              presentationMode ? (
                <Navigate to="/login" replace />
              ) : (
                <VerifyEmail />
              )
            }
          />
          <Route
            path="/forgot-password"
            element={
              presentationMode ? (
                <Navigate to="/login" replace />
              ) : (
                <PasswordRecovery />
              )
            }
          />
          <Route
            path="/reset-password"
            element={
              presentationMode ? (
                <Navigate to="/login" replace />
              ) : (
                <PasswordRecovery />
              )
            }
          />
          <Route element={<ProtectedRoute />}>
            <Route element={<AppLayout />}>
              <Route path="/dashboard" element={<Dashboard />} />
              <Route path="/profile" element={<Profile />} />
              <Route path="/settings" element={<Settings />} />
              <Route
                path="/requests"
                element={<Navigate to="/my-jobs" replace />}
              />
              <Route path="/jobs" element={<Jobs />} />
              <Route path="/my-jobs" element={<Jobs />} />
              <Route path="/jobs/new" element={<PostJob />} />
              <Route path="/jobs/:id" element={<JobDetail />} />
              <Route path="/earnings" element={<Earnings />} />
              <Route path="/wallet" element={<Wallet />} />
              <Route path="/notifications" element={<Notifications />} />
              <Route path="/admin" element={<Admin />} />
              <Route path="/students/:id" element={<StudentPage />} />
            </Route>
          </Route>
          <Route
            path="*"
            element={
              <main className="not-found">
                <span className="eyebrow">A LITTLE OFF CAMPUS</span>
                <h1>This page isn’t here.</h1>
                <p>Let’s get you back to familiar ground.</p>
                <Link to="/" className="button">
                  Back to JAVAC
                </Link>
              </main>
            }
          />
        </Routes>
        </Suspense>
      </AuthProvider>
    </BrowserRouter>
  );
}
