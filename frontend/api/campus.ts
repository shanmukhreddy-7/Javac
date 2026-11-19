/**
 * Optional split-deployment gateway that forwards same-origin API traffic to JAVA_BACKEND_URL.
 * The combined container serves /api directly from Java and does not need this forwarding layer.
 */
import type { IncomingMessage, ServerResponse } from "node:http";
export const config = { api: { bodyParser: false, sizeLimit: "4mb" } };
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
    const headers = new Headers();
    const contentType = req.headers["content-type"];
    if (typeof contentType === "string") headers.set("Content-Type", contentType);
    // Forward only the session/origin/CSRF headers Java needs, instead of blindly copying all
    // request headers.
    for (const name of ["cookie", "origin", "x-csrf-token"]) {
      const value = req.headers[name];
      if (typeof value === "string") headers.set(name, value);
    }
    let data: Uint8Array | undefined;
    if (method !== "GET" && method !== "HEAD") {
      if (req.body !== undefined) {
        if (typeof req.body === "string") data = Buffer.from(req.body);
        else if (Buffer.isBuffer(req.body)) data = req.body;
        else data = Buffer.from(JSON.stringify(req.body));
      }
      else {
        const chunks: Buffer[] = [];
        let size = 0;
        for await (const chunk of req) {
          const part=Buffer.isBuffer(chunk)?chunk:Buffer.from(chunk);
          size += part.length;
          if (size > 4 * 1024 * 1024) throw new Error("Too large");
          chunks.push(part);
        }
        data = Buffer.concat(chunks);
      }
      if (data && data.byteLength > 4 * 1024 * 1024) {
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
        body: data ? new Blob([data.buffer.slice(data.byteOffset, data.byteOffset + data.byteLength) as ArrayBuffer]) : undefined,
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
