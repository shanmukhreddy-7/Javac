import type { IncomingMessage, ServerResponse } from "node:http";
/** Same-origin gateway only. Every business/auth rule runs in the Java backend. */
export default async function campus(
  req: IncomingMessage & { body?: unknown },
  res: ServerResponse,
) {
  res.setHeader("Cache-Control", "no-store");
  const backend = process.env.JAVA_BACKEND_URL;
  if (!backend || !backend.startsWith("https://")) {
    res.statusCode = 503;
    res.setHeader("Content-Type", "application/json");
    res.end(
      JSON.stringify({
        message:
          "The Java backend is not connected. Set JAVA_BACKEND_URL in Vercel.",
      }),
    );
    return;
  }
  try {
    const url = new URL(req.url ?? "/", "https://localhost");
    const route = url.searchParams.get("route");
    const path = route !== null ? "/api/" + route : url.pathname;
    if (!path.startsWith("/api/") || path.includes(".."))
      throw new Error("Invalid API route");
    const method = req.method ?? "GET";
    const headers = new Headers({ "Content-Type": "application/json" });
    for (const name of ["cookie", "origin", "x-csrf-token"]) {
      const value = req.headers[name];
      if (typeof value === "string") headers.set(name, value);
    }
    let data: string | undefined;
    if (method !== "GET" && method !== "HEAD") {
      if (req.body !== undefined)
        data =
          typeof req.body === "string" ? req.body : JSON.stringify(req.body);
      else {
        data = "";
        for await (const chunk of req) {
          data += chunk.toString();
          if (Buffer.byteLength(data) > 32000) throw new Error("Too large");
        }
      }
      if (data && Buffer.byteLength(data) > 32000) {
        res.statusCode = 413;
        res.end(JSON.stringify({ message: "Request too large." }));
        return;
      }
    }
    // Preserve search filters after removing the internal rewrite parameter.
    url.searchParams.delete("route");
    const upstream = await fetch(
      backend.replace(/\/$/, "") +
        path +
        (url.searchParams.size ? "?" + url.searchParams.toString() : ""),
      {
        method,
        headers,
        body: data,
        redirect: "manual",
        signal: AbortSignal.timeout(25000),
      },
    );
    res.statusCode = upstream.status;
    res.setHeader(
      "Content-Type",
      upstream.headers.get("content-type") ?? "application/json",
    );
    const cookies = upstream.headers.getSetCookie();
    if (cookies.length) res.setHeader("Set-Cookie", cookies);
    res.end(await upstream.text());
  } catch {
    res.statusCode = 503;
    res.setHeader("Content-Type", "application/json");
    res.end(
      JSON.stringify({
        message: "Cannot reach the Java backend. Please try again shortly.",
      }),
    );
  }
}
