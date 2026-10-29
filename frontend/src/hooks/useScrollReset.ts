import { useEffect } from "react";
import { useLocation } from "react-router-dom";
/** Each product screen starts at the top; anchor navigation keeps its target. */
export function useScrollReset() {
  const { pathname, hash } = useLocation();
  useEffect(() => {
    if (!hash) window.scrollTo({ top: 0, left: 0, behavior: "instant" });
  }, [pathname, hash]);
}
