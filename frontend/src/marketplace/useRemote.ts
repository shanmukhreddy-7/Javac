/**
 * Reusable data loader with optional polling, focus refresh, and stale-response protection.
 * It ignores outdated results; it does not cancel requests already sent to the server.
 */
import { useCallback, useEffect, useRef, useState } from "react";
import { api } from "../services/api";
export function useRemote<T>(path: string, pollMs = 0) {
  const [data, setData] = useState<T | null>(null),
    [error, setError] = useState("");
  const generation = useRef(0);
  const reload = useCallback(async () => {
    // Only the most recent request may update this hook's state.
    const g = ++generation.current;
    try {
      const result = await api<T>(path);
      if (g === generation.current) {
        setData(result);
        setError("");
      }
    } catch (e) {
      if (g === generation.current) setError((e as Error).message);
    }
  }, [path]);
  useEffect(() => {
    setData(null);
    void reload();
    // Skip background polling while the browser tab is hidden.
    const tick = () => {
      if (!document.hidden) void reload();
    };
    const timer = pollMs > 0 ? window.setInterval(tick, pollMs) : undefined;
    window.addEventListener("focus", tick);
    return () => {
      // Ignore in-flight results after unmount or a path change, then release listeners and timers.
      generation.current++;
      if (timer) clearInterval(timer);
      window.removeEventListener("focus", tick);
    };
  }, [reload, pollMs]);
  return { data, error, reload };
}
