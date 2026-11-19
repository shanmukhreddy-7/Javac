import { beforeEach, describe, expect, it, vi } from "vitest";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import JobDetail from "../marketplace/JobDetail";
import { api } from "../services/api";
import type { Job } from "../marketplace/types";

const identity = vi.hoisted(() => ({ id: 1 }));
vi.mock("../contexts/AuthContext", () => ({ useAuth: () => ({ student: identity }) }));
vi.mock("../services/api", () => ({ api: vi.fn() }));
const mock = vi.mocked(api);
let job: Job;

function show() {
  return render(<MemoryRouter initialEntries={["/jobs/7"]}>
    <Routes><Route path="/jobs/:id" element={<JobDetail />} /></Routes>
  </MemoryRouter>);
}

beforeEach(() => {
  identity.id = 1;
  job = {
    id: 7, owner_id: 1, helper_id: 2, owner_name: "Requester", helper_name: "Helper",
    title: "Collect notes", description: "Bring my notes to class", category: "Food pickup",
    reward: 20, pickup: "Gate 1", destination: "Academic block",
    deadline: "2026-12-01T12:00:00Z", created_at: "2026-11-19T06:00:00Z",
    status: "DELIVERED", progress_step: "AT_BLOCK", payment_status: "UNPAID",
    payment_method: null, hidden: false,
  };
  mock.mockReset();
  mock.mockImplementation(async (path, method) => {
    if (path.endsWith("/handoff-code")) return { code: "246810" };
    if (path.endsWith("/actions") && method === "POST") {
      job = { ...job, status: "ACCEPTED", helper_id: identity.id };
      return job;
    }
    if (path === "/api/jobs/7") return { ...job };
    return [];
  });
});

describe("job acceptance and requester handoff", () => {
  it("accepts a job and refreshes its helper status", async () => {
    identity.id = 2;
    job = { ...job, status: "OPEN", helper_id: null, helper_name: null, progress_step: "NOT_STARTED" };
    show();
    await userEvent.click(await screen.findByRole("button", { name: "Accept job" }));
    await waitFor(() => expect(mock).toHaveBeenCalledWith("/api/jobs/7/actions", "POST", { action: "accept" }));
    expect(await screen.findByRole("button", { name: "Start delivery" })).toBeInTheDocument();
    expect(screen.queryByRole("alert")).not.toBeInTheDocument();
  });

  it("lets only the delivered job requester generate and read a replacement code", async () => {
    show();
    await userEvent.click(await screen.findByRole("button", { name: "Generate new handoff code" }));
    expect(await screen.findByText("246810")).toBeInTheDocument();
    expect(mock).toHaveBeenCalledWith("/api/jobs/7/handoff-code", "POST");
    expect(screen.getByText(/This replaces any earlier code/)).toBeInTheDocument();
    expect(sessionStorage.length).toBe(0);
  });

  it.each([2, 3])("does not expose code generation to account %s", async (id) => {
    identity.id = id;
    show();
    await screen.findByRole("heading", { name: "Collect notes" });
    expect(screen.queryByRole("button", { name: "Generate new handoff code" })).not.toBeInTheDocument();
    expect(mock).not.toHaveBeenCalledWith("/api/jobs/7/handoff-code", "POST");
  });

  it("waits for delivery and displays generation errors without inventing a code", async () => {
    job.status = "IN_PROGRESS";
    const view = show();
    await screen.findByRole("heading", { name: "Delivery checklist" });
    expect(screen.queryByRole("button", { name: "Generate new handoff code" })).not.toBeInTheDocument();
    view.unmount();
    job.status = "DELIVERED";
    const normal = mock.getMockImplementation()!;
    mock.mockImplementation(async (path, ...args) => {
      if (path.endsWith("/handoff-code")) throw new Error("Please sign in again.");
      return normal(path, ...args);
    });
    show();
    await userEvent.click(await screen.findByRole("button", { name: "Generate new handoff code" }));
    expect(await screen.findByText("Please sign in again.")).toBeInTheDocument();
    expect(screen.queryByText("246810")).not.toBeInTheDocument();
  });

  it("hides private Xerox download links from students who are not participants", async () => {
    identity.id = 3;
    job = { ...job, category: "Xerox", original_name: "private-notes.pdf" };
    show();
    await screen.findByRole("heading", { name: "Collect notes" });
    expect(screen.queryByRole("link", { name: /Download/ })).not.toBeInTheDocument();
  });
});
