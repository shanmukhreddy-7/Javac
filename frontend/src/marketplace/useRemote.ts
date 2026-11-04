import { useCallback, useEffect, useRef, useState } from "react";
import { api } from "../services/api";
export function useRemote<T>(path: string) {
  const [data, setData] = useState<T | null>(null),
    [error, setError] = useState("");
  const generation = useRef(0);
  const reload = useCallback(async () => {
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
    const tick = () => {
      if (!document.hidden) void reload();
    };
    const timer = setInterval(tick, 5000);
    window.addEventListener("focus", tick);
    return () => {
      generation.current++;
      clearInterval(timer);
      window.removeEventListener("focus", tick);
    };
  }, [reload]);
  return { data, error, reload };
}
