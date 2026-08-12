import { writable } from "svelte/store";

// Explicit choice persists across sessions; absent that, default to the OS/browser preference
// at first load (not re-checked afterward -- a live OS-level switch mid-session doesn't flip an
// already-loaded app, matching how the toggle below is meant to be the one source of truth once set).

const STORAGE_KEY = "chatrelay-theme";

function initialTheme() {
  const stored = localStorage.getItem(STORAGE_KEY);
  if (stored === "light" || stored === "dark") return stored;
  const prefersDark = window.matchMedia?.("(prefers-color-scheme: dark)")?.matches ?? false;
  return prefersDark ? "dark" : "light";
}

function applyTheme(value) {
  document.documentElement.dataset.theme = value;
}

const initial = initialTheme();
applyTheme(initial);

export const theme = writable(initial);

export function toggleTheme() {
  theme.update((current) => {
    const next = current === "dark" ? "light" : "dark";
    localStorage.setItem(STORAGE_KEY, next);
    applyTheme(next);
    return next;
  });
}
