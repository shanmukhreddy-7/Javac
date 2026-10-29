import { beforeEach, describe, expect, it, vi } from "vitest";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import { AuthProvider } from "../contexts/AuthContext";
import PresentationLogin from "../pages/PresentationLogin";
import PresentationRegister from "../pages/PresentationRegister";
import { api, RequestError } from "../services/api";
import { sampleStudent } from "../services/demo";
vi.mock("../services/mode", () => ({ presentationMode: true }));
vi.mock("../services/api", async (original) => ({
  ...(await original<typeof import("../services/api")>()),
  api: vi.fn(),
}));
const mocked = vi.mocked(api);
function wrap(element: React.ReactNode) {
  return render(
    <MemoryRouter>
      <AuthProvider>{element}</AuthProvider>
    </MemoryRouter>,
  );
}
beforeEach(() => {
  mocked.mockReset();
  mocked.mockImplementation(async (path) => {
    if (path === "/api/auth/config")
      return { approvedDomains: ["srmap.edu.in"] } as never;
    throw new RequestError(401, { message: "Sign in first" });
  });
});
describe("no-OTP presentation screens", () => {
  it("shows no password or verification controls at sign-in", async () => {
    wrap(<PresentationLogin />);
    expect(screen.queryByLabelText("Password")).not.toBeInTheDocument();
    expect(screen.queryByText("Forgot password?")).not.toBeInTheDocument();
    await userEvent.click(screen.getByRole("button", { name: "Log in" }));
    expect(
      await screen.findByText("Enter a valid university email."),
    ).toBeInTheDocument();
  });
  it("logs in by email only and opens the dashboard", async () => {
    mocked.mockImplementation(async (path) => {
      if (path === "/api/auth/login") return sampleStudent as never;
      throw new RequestError(401, { message: "Sign in first" });
    });
    wrap(
      <Routes>
        <Route path="/" element={<PresentationLogin />} />
        <Route path="/dashboard" element={<p>My campus dashboard</p>} />
      </Routes>,
    );
    await userEvent.type(
      screen.getByLabelText("University email"),
      "alex@srmap.edu.in",
    );
    await userEvent.click(screen.getByRole("button", { name: "Log in" }));
    expect(await screen.findByText("My campus dashboard")).toBeInTheDocument();
    expect(mocked).toHaveBeenCalledWith("/api/auth/login", "POST", {
      email: "alex@srmap.edu.in",
    });
  });
  it("registers and routes directly to login without sending passwords or verification requests", async () => {
    mocked.mockImplementation(async (path) => {
      if (path === "/api/auth/config")
        return { approvedDomains: ["srmap.edu.in"] } as never;
      if (path === "/api/auth/register") return { message: "Created" } as never;
      throw new RequestError(401, { message: "Sign in first" });
    });
    wrap(
      <Routes>
        <Route path="/" element={<PresentationRegister />} />
        <Route path="/login" element={<PresentationLogin />} />
      </Routes>,
    );
    await userEvent.type(screen.getByLabelText("Full name"), "Alex Student");
    await userEvent.type(
      screen.getByLabelText("University email"),
      "alex@srmap.edu.in",
    );
    await userEvent.type(
      screen.getByLabelText("Registration number"),
      "CS-001",
    );
    await userEvent.selectOptions(
      screen.getByLabelText("Department"),
      "Computer Science",
    );
    await userEvent.click(screen.getByRole("checkbox"));
    await userEvent.click(
      screen.getByRole("button", { name: "Create my account" }),
    );
    expect(
      await screen.findByText(
        "Account created. Sign in now — no OTP or email verification is needed.",
      ),
    ).toBeInTheDocument();
    expect(screen.getByLabelText("University email")).toHaveValue(
      "alex@srmap.edu.in",
    );
    const sent = mocked.mock.calls.find((c) => c[0] === "/api/auth/register");
    expect(sent?.[2]).not.toHaveProperty("password");
    expect(mocked.mock.calls.some((c) => c[0].includes("verify-email"))).toBe(
      false,
    );
  });
});
