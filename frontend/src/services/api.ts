/**
 * Shared JSON/multipart client for the Java API.
 * Carries the session cookie, obtains CSRF tokens for writes, and normalizes failures for form feedback.
 */
import type { ApiError } from "../types";
const base = (import.meta.env.VITE_API_BASE_URL ?? "").replace(/\/$/, "");
export class RequestError extends Error {
  status: number;
  fields: Record<string, string>;
  constructor(status: number, error: ApiError) {
    super(error.message);
    this.status = status;
    this.fields = error.fields ?? {};
  }
}
export async function api<T>(
  path: string,
  method = "GET",
  body?: unknown,
): Promise<T> {
  try {
    // Let the browser set multipart boundaries; setting a JSON Content-Type would break file
    // uploads.
    const isMultipart = typeof FormData !== "undefined" && body instanceof FormData;
    const headers: Record<string, string> = {};
    if (!isMultipart) headers["Content-Type"] = "application/json";
    // Fetch the token for the current session before a write, including anonymous
    // login/registration requests.
    if (method !== "GET") {
      const csrf = await fetch(base + "/api/auth/csrf", {
        credentials: "include",
        cache: "no-store",
        signal: AbortSignal.timeout(30000),
      });
      if (!csrf.ok)
        throw new RequestError(csrf.status, {
          message:
            "Unable to establish a secure session. Refresh and try again.",
        });
      const token = await csrf.json();
      headers[token.headerName] = token.token;
    }
    // Include HTTP-only session cookies; Java decides the authenticated identity, not local
    // storage.
    const response = await fetch(base + path, {
      method,
      headers,
      credentials: "include",
      cache: "no-store",
      signal: AbortSignal.timeout(30000),
      body: body === undefined ? undefined : isMultipart ? body : JSON.stringify(body),
    });
    const result = await response.json().catch(() => ({
      message: "The server returned an unreadable response.",
    }));
    // Keep HTTP status and field errors so forms can distinguish validation, authorization, and
    // network failures.
    if (!response.ok) throw new RequestError(response.status, result);
    return result as T;
  } catch (error) {
    if (error instanceof RequestError) throw error;
    throw new RequestError(503, {
      message:
        "Cannot reach the shared data service. Check your connection and try again.",
    });
  }
}
