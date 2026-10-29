import type { IncomingMessage, ServerResponse } from "node:http";
import {
  createHmac,
  timingSafeEqual,
  randomBytes,
  createHash,
} from "node:crypto";
import { existsSync, mkdirSync, readFileSync, writeFileSync } from "node:fs";
import { resolve } from "node:path";
import { z } from "zod";
import { CampusStore, getStore } from "./store";
import skills from "../src/data/skills.json";
import {
  presentationRegistrationSchema,
  profileSchema,
} from "../src/services/validation";
import type { Student } from "../src/types/index";
class HttpError extends Error {
  constructor(
    public status: number,
    message: string,
  ) {
    super(message);
  }
}
function sessionSecret() {
  if (process.env.JAVAC_SESSION_SECRET) return process.env.JAVAC_SESSION_SECRET;
  if (process.env.DATABASE_URL)
    return createHash("sha256")
      .update("javac-session-v1:" + process.env.DATABASE_URL)
      .digest("hex");
  if (process.env.VERCEL) throw new Error("SHARED_DATABASE_MISSING");
  const dir = resolve(process.env.JAVAC_DATA_DIR ?? ".local-data");
  mkdirSync(dir, { recursive: true, mode: 0o700 });
  const file = resolve(dir, "session-secret");
  if (!existsSync(file))
    writeFileSync(file, randomBytes(32).toString("hex"), { mode: 0o600 });
  return readFileSync(file, "utf8").trim();
}
function completion(s: Student) {
  return (
    60 +
    (s.bio.trim() ? 15 : 0) +
    (s.preferredLocation.trim() ? 10 : 0) +
    (s.skills.length ? 15 : 0)
  );
}
function sign(value: string, secret: string) {
  return (
    value + "." + createHmac("sha256", secret).update(value).digest("base64url")
  );
}
function unseal(value: string | undefined, secret: string) {
  if (!value) return null;
  const split = value.lastIndexOf(".");
  if (split < 0) return null;
  const payload = value.slice(0, split);
  const expected = Buffer.from(sign(payload, secret));
  const actual = Buffer.from(value);
  if (expected.length !== actual.length || !timingSafeEqual(expected, actual))
    return null;
  const data = JSON.parse(Buffer.from(payload, "base64url").toString()) as {
    id: number;
    expires: number;
  };
  return data.expires > Date.now() ? data : null;
}
function sealed(id: number, secret: string) {
  return sign(
    Buffer.from(
      JSON.stringify({ id, expires: Date.now() + 8 * 60 * 60 * 1000 }),
    ).toString("base64url"),
    secret,
  );
}
async function body(request: IncomingMessage) {
  const parsed = (request as IncomingMessage & { body?: unknown }).body;
  if (parsed !== undefined) {
    if (typeof parsed === "string") {
      try {
        return JSON.parse(parsed);
      } catch {
        throw new HttpError(400, "Please send valid JSON.");
      }
    }
    return parsed;
  }
  let data = "";
  for await (const chunk of request) {
    data += chunk.toString();
    if (Buffer.byteLength(data) > 32_000)
      throw new HttpError(413, "The request is too large.");
  }
  try {
    return data ? JSON.parse(data) : {};
  } catch {
    throw new HttpError(400, "Please send valid JSON.");
  }
}
export function createCampusHandler(
  options: { store?: CampusStore; secret?: string; domains?: string[] } = {},
) {
  return async function handler(req: IncomingMessage, res: ServerResponse) {
    const send = (status: number, data: unknown) => {
      res.statusCode = status;
      res.setHeader("Content-Type", "application/json");
      res.end(JSON.stringify(data));
    };
    res.setHeader("Cache-Control", "no-store");
    res.setHeader("X-Content-Type-Options", "nosniff");
    try {
      const url = new URL(
        (req as IncomingMessage & { originalUrl?: string }).originalUrl ??
          req.url ??
          "/",
        "http://localhost",
      );
      const pathname = url.pathname === "/api/campus" && url.searchParams.has("route")
        ? "/api/" + url.searchParams.get("route")
        : url.pathname;
      const path = pathname.replace(/\/$/, "");
      const method = req.method ?? "GET";
      const domains =
        options.domains ??
        (process.env.APP_APPROVED_EMAIL_DOMAINS ?? "srmap.edu.in")
          .split(",")
          .map((s) => s.trim().toLowerCase())
          .filter(Boolean);
      if (path === "/api/auth/config" && method === "GET") {
        send(200, {
          approvedDomains: domains,
          presentationMode: true,
          verificationRequired: false,
        });
        return;
      }
      if (path === "/api/skills" && method === "GET") {
        send(200, skills);
        return;
      }
      const secret = options.secret ?? sessionSecret();
      const cookies = Object.fromEntries(
        (req.headers.cookie ?? "").split(";").map((p) => {
          const i = p.indexOf("=");
          return i < 0
            ? ["", ""]
            : [p.slice(0, i).trim(), p.slice(i + 1).trim()];
        }),
      );
      const secure = process.env.VERCEL ? "; Secure" : "";
      const cookie = (name: string, value: string, age = 28800) =>
        `${name}=${value}; Path=/; HttpOnly; SameSite=Lax; Max-Age=${age}${secure}`;
      if (path === "/api/auth/csrf" && method === "GET") {
        const token = unseal(cookies.JAVAC_CSRF, secret)
          ? cookies.JAVAC_CSRF
          : sealed(0, secret);
        res.setHeader("Set-Cookie", cookie("JAVAC_CSRF", token));
        send(200, { token, headerName: "X-CSRF-TOKEN" });
        return;
      }
      if (method !== "GET") {
        const csrf = req.headers["x-csrf-token"];
        if (
          typeof csrf !== "string" ||
          csrf !== cookies.JAVAC_CSRF ||
          !unseal(csrf, secret)
        )
          throw new HttpError(403, "Refresh this page and try again.");
        if (req.headers.origin) {
          const actualHost =
            req.headers["x-forwarded-host"] ?? req.headers.host;
          if (new URL(req.headers.origin).host !== actualHost)
            throw new HttpError(
              403,
              "Use the same website to submit this form.",
            );
        }
      }
      const store = options.store ?? getStore();
      const input = method === "GET" ? {} : await body(req);
      if (path === "/api/auth/register" && method === "POST") {
        const data = presentationRegistrationSchema.strict().parse(input);
        const email = data.email.trim().toLowerCase();
        if (domains.length && !domains.includes(email.split("@")[1]))
          throw new HttpError(
            400,
            "Use your approved university email domain: " + domains.join(", "),
          );
        const student: Student = {
          id: 0,
          fullName: data.fullName,
          email,
          registrationNumber: data.registrationNumber.toUpperCase(),
          department: data.department,
          studyYear: data.studyYear,
          bio: "",
          preferredLocation: "",
          emailVerified: false,
          accountStatus: "PRESENTATION",
          createdAt: new Date().toISOString(),
          skills: [],
          completion: 60,
        };
        try {
          await store.create(student);
        } catch (error) {
          const e = error as { code?: string; message?: string };
          if (e.code === "23505" || e.message?.includes("UNIQUE constraint"))
            throw new HttpError(
              409,
              "This email or registration number is already registered.",
            );
          throw error;
        }
        send(201, {
          message:
            "Presentation account created. You can sign in now. No OTP or email verification is required.",
        });
        return;
      }
      if (path === "/api/auth/login" && method === "POST") {
        const data = z
          .object({ email: z.email().max(254) })
          .strict()
          .parse(input);
        const user = await store.byEmail(data.email.trim().toLowerCase());
        if (!user)
          throw new HttpError(
            404,
            "Create a presentation account first, then sign in with that email.",
          );
        res.setHeader(
          "Set-Cookie",
          cookie("JAVAC_SESSION", sealed(user.id, secret)),
        );
        send(200, user);
        return;
      }
      if (path === "/api/auth/logout" && method === "POST") {
        res.setHeader("Set-Cookie", [
          cookie("JAVAC_SESSION", "", 0),
          cookie("JAVAC_CSRF", "", 0),
        ]);
        send(200, { message: "Signed out." });
        return;
      }
      const session = unseal(cookies.JAVAC_SESSION, secret);
      const user = session ? await store.find(session.id) : null;
      if (!user)
        throw new HttpError(
          401,
          "Sign in to your presentation account to continue.",
        );
      if (
        (path === "/api/auth/me" || path === "/api/students/me") &&
        method === "GET"
      ) {
        send(200, user);
        return;
      }
      if (path === "/api/students/me" && method === "PATCH") {
        const data = profileSchema.strict().parse(input);
        const updated = { ...user, ...data };
        updated.completion = completion(updated);
        send(200, await store.save(updated));
        return;
      }
      if (path === "/api/students/me/skills" && method === "PUT") {
        const data = z
          .object({ skillIds: z.array(z.number().int().positive()).max(20) })
          .strict()
          .parse(input);
        const ids = new Set(data.skillIds);
        const selected = skills.filter((s) => ids.has(s.id));
        if (selected.length !== ids.size)
          throw new HttpError(400, "Choose skills from the directory.");
        const updated = { ...user, skills: selected };
        updated.completion = completion(updated);
        send(200, await store.save(updated));
        return;
      }
      if (path === "/api/students" && method === "GET") {
        send(200, await store.people(user.id));
        return;
      }
      if (path === "/api/requests" && method === "GET") {
        send(200, await store.requests(user.id));
        return;
      }
      if (path === "/api/requests" && method === "POST") {
        const data = z
          .object({
            recipientId: z.number().int().positive(),
            title: z.string().trim().min(3).max(100),
            description: z.string().trim().min(5).max(1000),
          })
          .strict()
          .parse(input);
        if (data.recipientId === user.id)
          throw new HttpError(400, "Choose another student.");
        if (!(await store.find(data.recipientId)))
          throw new HttpError(404, "This student is no longer available.");
        const id = await store.send(
          user.id,
          data.recipientId,
          data.title,
          data.description,
        );
        send(201, {
          id,
          message: "Request sent. It will appear in the other student’s inbox.",
        });
        return;
      }
      const match = path.match(/^\/api\/requests\/(\d+)$/);
      if (match && method === "PATCH") {
        const data = z
          .object({ status: z.enum(["ACCEPTED", "DECLINED"]) })
          .strict()
          .parse(input);
        if (!(await store.respond(Number(match[1]), user.id, data.status)))
          throw new HttpError(
            409,
            "Only the recipient can respond to a pending request.",
          );
        send(200, {
          message:
            data.status === "ACCEPTED"
              ? "Request accepted."
              : "Request declined.",
        });
        return;
      }
      throw new HttpError(
        404,
        "This action is not part of the presentation build.",
      );
    } catch (error) {
      if (error instanceof z.ZodError) {
        send(400, {
          message: "Please check the form fields.",
          fields: Object.fromEntries(
            error.issues.map((i) => [i.path[0], i.message]),
          ),
        });
        return;
      }
      if (error instanceof HttpError) {
        send(error.status, { message: error.message, fields: {} });
        return;
      }
      if (
        error instanceof Error &&
        error.message === "SHARED_DATABASE_MISSING"
      ) {
        send(503, {
          message:
            "Shared storage is not connected. Add a Neon database in Vercel Storage and set DATABASE_URL, then redeploy. Requests cannot sync without shared storage.",
          fields: {},
        });
        return;
      }
      console.error(
        "JAVAC API operation failed:",
        error instanceof Error ? error.name : "UnknownError",
      );
      send(503, {
        message: "The shared data service is unavailable. Please try again.",
        fields: {},
      });
    }
  };
}
export default createCampusHandler();
