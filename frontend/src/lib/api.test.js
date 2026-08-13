import { describe, it, expect, vi, beforeEach } from "vitest";
import { api } from "./api.js";

function mockFetchOnce({ status = 200, body = null, ok } = {}) {
  const resolvedOk = ok ?? (status >= 200 && status < 300);
  global.fetch = vi.fn().mockResolvedValue({
    ok: resolvedOk,
    status,
    json: async () => body,
    text: async () => (body === null ? "" : JSON.stringify(body)),
  });
}

beforeEach(() => {
  vi.restoreAllMocks();
});

describe("api requests", () => {
  it("sends login as a POST with the credentials as a JSON body", async () => {
    mockFetchOnce({ body: { id: "1", username: "alice" } });

    const result = await api.login("alice", "secret");

    expect(fetch).toHaveBeenCalledWith(
      "/api/auth/login",
      expect.objectContaining({
        method: "POST",
        credentials: "same-origin",
        body: JSON.stringify({ username: "alice", password: "secret" }),
      }),
    );
    expect(result).toEqual({ id: "1", username: "alice" });
  });

  it("sends GET requests for reads with no body", async () => {
    mockFetchOnce({ body: [{ id: "1" }] });

    await api.getUsers();

    const [url, options] = fetch.mock.calls[0];
    expect(url).toBe("/api/users");
    expect(options.method).toBeUndefined();
    expect(options.body).toBeUndefined();
  });

  it("scopes message endpoints under the chat id", async () => {
    mockFetchOnce({ body: [] });
    await api.getMessages("42");
    expect(fetch.mock.calls[0][0]).toBe("/api/chats/42/messages");

    mockFetchOnce({ body: { id: "1", content: "hi" } });
    await api.sendMessage("42", "hi");
    expect(fetch.mock.calls[0][0]).toBe("/api/chats/42/messages");
    expect(fetch.mock.calls[0][1].body).toBe(JSON.stringify({ content: "hi" }));
  });

  it("uses DELETE for removeMember with no body", async () => {
    mockFetchOnce({ status: 204 });

    await api.removeMember("42", "7");

    expect(fetch).toHaveBeenCalledWith("/api/chats/42/members/7", expect.objectContaining({ method: "DELETE" }));
  });

  it("uses DELETE for deleteUser with no body", async () => {
    mockFetchOnce({ status: 204 });

    await api.deleteUser("7");

    expect(fetch).toHaveBeenCalledWith("/api/users/7", expect.objectContaining({ method: "DELETE" }));
  });

  it("uses DELETE for deleteChat with no body", async () => {
    mockFetchOnce({ status: 204 });

    await api.deleteChat("42");

    expect(fetch).toHaveBeenCalledWith("/api/chats/42", expect.objectContaining({ method: "DELETE" }));
  });

  it("returns null for a 204 No Content response", async () => {
    mockFetchOnce({ status: 204 });
    const result = await api.logout();
    expect(result).toBeNull();
  });

  it("throws the server's error message on a non-2xx response", async () => {
    mockFetchOnce({ status: 401, ok: false, body: { error: "Bad credentials" } });

    await expect(api.login("alice", "wrong")).rejects.toThrow("Bad credentials");
  });

  it("falls back to a generic message when the error response isn't JSON", async () => {
    global.fetch = vi.fn().mockResolvedValue({
      ok: false,
      status: 500,
      json: async () => {
        throw new Error("not json");
      },
      text: async () => "",
    });

    await expect(api.getUsers()).rejects.toThrow("Request failed: 500");
  });
});
