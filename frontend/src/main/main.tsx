/**
 * React entry point: mount App into index.html and load the shared theme.
 * StrictMode helps expose unsafe effect behavior during development.
 */
import { StrictMode } from "react";
import { createRoot } from "react-dom/client";
import App from "../App";
import "../styles/theme.css";
createRoot(document.getElementById("root")!).render(
  <StrictMode>
    <App />
  </StrictMode>,
);
