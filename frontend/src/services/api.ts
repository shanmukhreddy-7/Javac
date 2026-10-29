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
    const headers: Record<string, string> = {
      "Content-Type": "application/json",
    };
    if (method !== "GET") {
      const csrf = await fetch(base + "/api/auth/csrf", {
        credentials: "include",
        cache: "no-store",
        signal: AbortSignal.timeout(10000),
      });
      if (!csrf.ok)
        throw new RequestError(csrf.status, {
          message:
            "Unable to establish a secure session. Refresh and try again.",
        });
      const token = await csrf.json();
      headers[token.headerName] = token.token;
    }
    const response = await fetch(base + path, {
      method,
      headers,
      credentials: "include",
      cache: "no-store",
      signal: AbortSignal.timeout(10000),
      body: body === undefined ? undefined : JSON.stringify(body),
    });
    const result = await response.json().catch(() => ({
      message: "The server returned an unreadable response.",
    }));
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
