import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, screen, waitFor, act } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter, Routes, Route } from "react-router-dom";
import { AuthProvider, useAuth } from "../contexts/AuthContext";
import Login from "../pages/Login";
import Register from "../pages/Register";
import Profile from "../pages/Profile";
import Dashboard from "../pages/Dashboard";
import ProtectedRoute from "../components/common/ProtectedRoute";
import { api, RequestError } from "../services/api";
import { sampleStudent } from "../services/demo";
import { registerSchema } from "../services/validation";
vi.mock("../services/mode", () => ({ presentationMode: false }));
vi.mock("../services/api", async (importOriginal) => {
  const original = await importOriginal<typeof import("../services/api")>();
  return { ...original, api: vi.fn() };
});
vi.mock("../services/demo", async (importOriginal) => ({
  ...(await importOriginal<typeof import("../services/demo")>()),
  demoEnabled: true,
}));
const mockedApi = vi.mocked(api);
function wrap(element: React.ReactNode, path = "/") {
  return render(
    <MemoryRouter initialEntries={[path]}>
      <AuthProvider>{element}</AuthProvider>
    </MemoryRouter>,
  );
}
beforeEach(() => {
  mockedApi.mockReset();
  mockedApi.mockImplementation(async (path) => {
    if (path === "/api/auth/config")
      return { approvedDomains: ["example.edu"] } as never;
    throw new RequestError(401, { message: "Please log in." });
  });
});
describe("authentication forms", () => {
  it("validates login without calling the login API", async () => {
    wrap(<Login />);
    await userEvent.click(screen.getByRole("button", { name: "Log in" }));
    expect(
      await screen.findByText("Enter a valid university email."),
    ).toBeInTheDocument();
    expect(screen.getByText("Enter your password.")).toBeInTheDocument();
    expect(mockedApi).not.toHaveBeenCalledWith(
      "/api/auth/login",
      expect.anything(),
      expect.anything(),
    );
  });
  it("validates registration fields and requires terms", async () => {
    wrap(<Register />);
    await userEvent.click(
      screen.getByRole("button", { name: "Create my account" }),
    );
    expect(
      await screen.findByText("Enter your full name."),
    ).toBeInTheDocument();
    expect(
      screen.getByText("Accept the terms to continue."),
    ).toBeInTheDocument();
    expect(screen.getByText("Use at least 12 characters.")).toBeInTheDocument();
  });
  it("rejects mismatched passwords in the shared registration schema", () => {
    const r = registerSchema.safeParse({
      fullName: "Alex Morgan",
      email: "alex@example.edu",
      registrationNumber: "ABC123",
      department: "Sciences",
      studyYear: 2,
      password: "StrongCampus!2026",
      confirmPassword: "DifferentCampus!2026",
      termsAccepted: true,
    });
    expect(r.success).toBe(false);
    if (!r.success)
      expect(r.error.issues.some((i) => i.path[0] === "confirmPassword")).toBe(
        true,
      );
  });
  it("uses the real login result and navigates to dashboard", async () => {
    mockedApi.mockImplementation(async (path) => {
      if (path === "/api/auth/login") return sampleStudent as never;
      throw new RequestError(401, { message: "Please log in." });
    });
    wrap(
      <Routes>
        <Route path="/" element={<Login />} />
        <Route path="/dashboard" element={<p>My authenticated campus</p>} />
      </Routes>,
    );
    await userEvent.type(
      screen.getByLabelText("University email"),
      "alex@example.edu",
    );
    await userEvent.type(
      screen.getByLabelText("Password"),
      "StrongCampus!2026",
    );
    await userEvent.click(screen.getByRole("button", { name: "Log in" }));
    expect(
      await screen.findByText("My authenticated campus"),
    ).toBeInTheDocument();
  });
});
describe("private campus and demo", () => {
  it("redirects unauthenticated students", async () => {
    wrap(
      <Routes>
        <Route element={<ProtectedRoute />}>
          <Route path="/dashboard" element={<p>Private dashboard</p>} />
        </Route>
        <Route path="/login" element={<p>Login required</p>} />
      </Routes>,
      "/dashboard",
    );
    expect(await screen.findByText("Login required")).toBeInTheDocument();
    expect(screen.queryByText("Private dashboard")).not.toBeInTheDocument();
  });
  it("renders an explicit sample identity and future modules without fabricated earnings", async () => {
    sessionStorage.setItem("javac-demo", "active");
    wrap(<Dashboard />);
    expect(await screen.findByText("Hey, Alex.")).toBeInTheDocument();
    expect(
      screen.getByText("Sample account · not verified"),
    ).toBeInTheDocument();
    expect(screen.getAllByText("Coming soon")).toHaveLength(4);
    expect(mockedApi).not.toHaveBeenCalled();
  });
  it("temporarily edits the demo profile and saves skills without backend calls", async () => {
    sessionStorage.setItem("javac-demo", "active");
    wrap(<Profile />);
    const name = await screen.findByLabelText("Full name");
    await userEvent.clear(name);
    await userEvent.type(name, "Taylor Demo");
    await userEvent.click(screen.getByRole("button", { name: "Save profile" }));
    expect(
      await screen.findByText("Demo profile updated for this visit."),
    ).toBeInTheDocument();
    expect(screen.getByText("Taylor Demo")).toBeInTheDocument();
    await userEvent.click(
      screen.getByRole("button", { name: /Python.*Programming/ }),
    );
    await userEvent.click(
      screen.getByRole("button", { name: "Save my skills" }),
    );
    expect(
      await screen.findByText("Demo skills updated for this visit."),
    ).toBeInTheDocument();
    expect(
      screen.getByRole("button", { name: "Remove Python" }),
    ).toBeInTheDocument();
    expect(mockedApi).not.toHaveBeenCalled();
  });
  it("shows API failure without silently entering demo", async () => {
    function Probe() {
      const { isDemo, loading } = useAuth();
      return (
        <p>
          {loading ? "Loading" : isDemo ? "Demo" : "Real session unavailable"}
        </p>
      );
    }
    wrap(<Probe />);
    await waitFor(() =>
      expect(screen.getByText("Real session unavailable")).toBeInTheDocument(),
    );
  });
});

it("keeps an explicit demo active when an earlier real session request fails", async () => {
  let rejectRequest: (error: Error) => void = () => {};
  mockedApi.mockImplementation(
    () =>
      new Promise((_, reject) => {
        rejectRequest = reject;
      }),
  );
  function Entry() {
    const { startDemo, isDemo, student } = useAuth();
    return (
      <>
        <button onClick={startDemo}>Begin sample</button>
        <p>{isDemo && student ? student.fullName : "No sample yet"}</p>
      </>
    );
  }
  wrap(<Entry />);
  await userEvent.click(screen.getByRole("button", { name: "Begin sample" }));
  expect(await screen.findByText("Alex Morgan")).toBeInTheDocument();
  await act(async () => {
    rejectRequest(new RequestError(503, { message: "Backend unavailable" }));
  });
  expect(screen.getByText("Alex Morgan")).toBeInTheDocument();
});
