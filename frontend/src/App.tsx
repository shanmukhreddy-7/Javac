import { BrowserRouter, Routes, Route, Link, Navigate } from "react-router-dom";
import { AuthProvider } from "./contexts/AuthContext";
import ProtectedRoute from "./components/common/ProtectedRoute";
import AppLayout from "./components/layout/AppLayout";
import Landing from "./pages/Landing";
import Login from "./pages/Login";
import Register from "./pages/Register";
import PresentationRegister from "./pages/PresentationRegister";
import PresentationLogin from "./pages/PresentationLogin";
import Requests from "./pages/Requests";
import { presentationMode } from "./services/mode";
import VerifyEmail from "./pages/VerifyEmail";
import PasswordRecovery from "./pages/PasswordRecovery";
import Dashboard from "./pages/Dashboard";
import Profile from "./pages/Profile";
import Settings from "./pages/Settings";
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
              <Route path="/requests" element={<Requests />} />
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
      </AuthProvider>
    </BrowserRouter>
  );
}
