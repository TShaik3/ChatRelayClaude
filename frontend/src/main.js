import { mount } from "svelte";
import "./app.css";
import "./lib/theme.js"; // applies the saved/system theme before first paint, avoiding a flash
import App from "./App.svelte";

// Svelte 5's client API: components are mounted via mount(), not `new Component()` (removed).
const app = mount(App, {
  target: document.getElementById("app"),
});

export default app;
