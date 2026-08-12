import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, screen, fireEvent } from "@testing-library/svelte";
import Login from "./Login.svelte";
import { api } from "../api.js";

vi.mock("../api.js", () => ({
  api: { login: vi.fn() },
}));

beforeEach(() => {
  vi.clearAllMocks();
});

describe("Login", () => {
  it("calls onLoggedIn with the returned user on successful login", async () => {
    const user = { id: "1", username: "alice" };
    api.login.mockResolvedValue(user);
    const onLoggedIn = vi.fn();

    render(Login, { props: { onLoggedIn } });
    await fireEvent.input(screen.getByLabelText("Username"), { target: { value: "alice" } });
    await fireEvent.input(screen.getByLabelText("Password"), { target: { value: "secret" } });
    await fireEvent.click(screen.getByRole("button", { name: "Login" }));

    expect(api.login).toHaveBeenCalledWith("alice", "secret");
    expect(onLoggedIn).toHaveBeenCalledWith(user);
  });

  it("shows an error message and does not call onLoggedIn when login fails", async () => {
    api.login.mockRejectedValue(new Error("Bad credentials"));
    const onLoggedIn = vi.fn();

    render(Login, { props: { onLoggedIn } });
    await fireEvent.input(screen.getByLabelText("Username"), { target: { value: "alice" } });
    await fireEvent.input(screen.getByLabelText("Password"), { target: { value: "wrong" } });
    await fireEvent.click(screen.getByRole("button", { name: "Login" }));

    expect(await screen.findByText("Bad credentials")).toBeInTheDocument();
    expect(onLoggedIn).not.toHaveBeenCalled();
  });
});
