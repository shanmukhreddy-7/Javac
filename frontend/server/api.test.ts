// @vitest-environment node
import { afterEach, beforeEach, describe, expect, it } from "vitest";
import { createServer } from "node:http";
import type { Server } from "node:http";
import { CampusStore, sqliteDatabase } from "./store";
import { createCampusHandler } from "./handler";
let server: Server;
let origin: string;
let closeDb: (() => void) | undefined;
class Client {
  cookies = new Map<string, string>();
  async request(
    path: string,
    method = "GET",
    body?: unknown,
    withCsrf = true,
  ): Promise<{ status: number; data: any }> {
    const headers: Record<string, string> = {
      "Content-Type": "application/json",
    };
    if (method !== "GET" && withCsrf) {
      const result = await this.request("/api/auth/csrf");
      headers["X-CSRF-TOKEN"] = result.data.token;
    }
    headers.Cookie = [...this.cookies].map(([k, v]) => k + "=" + v).join("; ");
    const response = await fetch(origin + path, {
      method,
      headers,
      body: body === undefined ? undefined : JSON.stringify(body),
    });
    for (const value of response.headers.getSetCookie()) {
      const pair = value.split(";")[0];
      const index = pair.indexOf("=");
      this.cookies.set(pair.slice(0, index), pair.slice(index + 1));
    }
    return { status: response.status, data: await response.json() };
  }
}
const registration = (name: string) => ({
  fullName: name + " Student",
  email: name.toLowerCase() + "@srmap.edu.in",
  registrationNumber: name.toUpperCase() + "-001",
  department: "Computer Science",
  studyYear: 2,
  termsAccepted: true,
});
async function account(name: string) {
  const client = new Client();
  expect(
    (await client.request("/api/auth/register", "POST", registration(name)))
      .status,
  ).toBe(201);
  const result = await client.request("/api/auth/login", "POST", {
    email: registration(name).email,
  });
  expect(result.status).toBe(200);
  return { client, student: result.data };
}
beforeEach(async () => {
  const db = sqliteDatabase(":memory:");
  closeDb = db.close;
  const store = new CampusStore(db);
  server = createServer(
    createCampusHandler({
      store,
      secret: "test-secret-with-at-least-32-characters",
      domains: ["srmap.edu.in"],
    }),
  );
  await new Promise<void>((r) => server.listen(0, "127.0.0.1", r));
  const address = server.address();
  if (!address || typeof address === "string")
    throw new Error("No test address");
  origin = "http://127.0.0.1:" + address.port;
});
afterEach(async () => {
  await new Promise<void>((resolve, reject) =>
    server.close((e) => (e ? reject(e) : resolve())),
  );
  closeDb?.();
});
describe("same-deployment presentation API", () => {
  it("routes requests forwarded by the Vercel function rewrite", async () => {
    const result = await new Client().request("/api/campus?route=auth/config");
    expect(result.status).toBe(200);
    expect(result.data.verificationRequired).toBe(false);
  });
  it("creates an active presentation session immediately without passwords, OTP or verification", async () => {
    const { client, student } = await account("Alex");
    expect(student.emailVerified).toBe(false);
    expect(student.accountStatus).toBe("PRESENTATION");
    expect(student.password).toBeUndefined();
    expect((await client.request("/api/auth/me")).data.id).toBe(student.id);
    expect(
      (await client.request("/api/auth/config")).data.verificationRequired,
    ).toBe(false);
  });
  it("shares requests across independent account sessions and shares recipient responses back", async () => {
    const a = await account("Alex");
    const b = await account("Taylor");
    const before = await b.client.request("/api/requests");
    expect(before.data).toEqual([]);
    const sent = await a.client.request("/api/requests", "POST", {
      recipientId: b.student.id,
      title: "Java assignment help",
      description: "Could you help me understand interfaces?",
    });
    expect(sent.status).toBe(201);
    const inbox = await b.client.request("/api/requests");
    expect(inbox.data).toHaveLength(1);
    expect(inbox.data[0].senderName).toBe("Alex Student");
    expect(
      (
        await b.client.request("/api/requests/" + sent.data.id, "PATCH", {
          status: "ACCEPTED",
        })
      ).status,
    ).toBe(200);
    const outbox = await a.client.request("/api/requests");
    expect(outbox.data[0].status).toBe("ACCEPTED");
  });
  it("prevents the sender or an unrelated account from responding to someone else’s request", async () => {
    const a = await account("Alex");
    const b = await account("Taylor");
    const c = await account("Morgan");
    const sent = await a.client.request("/api/requests", "POST", {
      recipientId: b.student.id,
      title: "Design help",
      description: "Can you help with my slides?",
    });
    expect(
      (
        await a.client.request("/api/requests/" + sent.data.id, "PATCH", {
          status: "ACCEPTED",
        })
      ).status,
    ).toBe(409);
    expect((await c.client.request("/api/requests")).data).toEqual([]);
    expect(
      (
        await c.client.request("/api/requests/" + sent.data.id, "PATCH", {
          status: "DECLINED",
        })
      ).status,
    ).toBe(409);
  });
  it("validates duplicates, university domains, profile updates and directory skill IDs", async () => {
    const { client } = await account("Alex");
    expect(
      (await client.request("/api/auth/register", "POST", registration("Alex")))
        .status,
    ).toBe(409);
    expect(
      (
        await client.request("/api/auth/register", "POST", {
          ...registration("Taylor"),
          email: "not@another.example",
        })
      ).status,
    ).toBe(400);
    const update = {
      fullName: "Alex Updated",
      department: "Sciences",
      studyYear: 3,
      bio: "Happy to help.",
      preferredLocation: "Library",
    };
    expect(
      (await client.request("/api/students/me", "PATCH", update)).data
        .completion,
    ).toBe(85);
    expect(
      (
        await client.request("/api/students/me/skills", "PUT", {
          skillIds: [1, 2],
        })
      ).data.completion,
    ).toBe(100);
    expect(
      (
        await client.request("/api/students/me/skills", "PUT", {
          skillIds: [999],
        })
      ).status,
    ).toBe(400);
    expect(
      (
        await client.request("/api/students/me", "PATCH", {
          ...update,
          userId: 999,
        })
      ).status,
    ).toBe(400);
  });
  it("keeps session and CSRF handling functional without external services", async () => {
    const client = new Client();
    expect((await client.request("/api/auth/me")).status).toBe(401);
    expect(
      (
        await client.request(
          "/api/auth/register",
          "POST",
          registration("Alex"),
          false,
        )
      ).status,
    ).toBe(403);
    const a = await account("Alex");
    expect((await a.client.request("/api/auth/logout", "POST")).status).toBe(
      200,
    );
    expect((await a.client.request("/api/auth/me")).status).toBe(401);
  });
});
