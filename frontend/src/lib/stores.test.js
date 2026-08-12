import { describe, it, expect, beforeEach } from "vitest";
import { get } from "svelte/store";
import {
  currentUser,
  users,
  chats,
  messagesByChat,
  selectedChatId,
  upsertUser,
  upsertChat,
  removeChat,
  setMessagesForChat,
  appendMessage,
  resetSession,
} from "./stores.js";

// Every test starts from a clean slate -- these are module-level singleton stores shared across
// the whole app (and every test in this file), so leftover state from one test would otherwise
// leak into the next.
beforeEach(() => {
  resetSession();
});

describe("upsertUser", () => {
  it("adds a new user to the users list", () => {
    upsertUser({ id: "1", username: "alice" });
    expect(get(users)).toEqual([{ id: "1", username: "alice" }]);
  });

  it("replaces an existing user with the same id instead of duplicating", () => {
    upsertUser({ id: "1", username: "alice" });
    upsertUser({ id: "1", username: "alice-renamed" });

    const list = get(users);
    expect(list).toHaveLength(1);
    expect(list[0].username).toBe("alice-renamed");
  });

  it("also refreshes currentUser when it's the same id (e.g. an admin editing their own profile)", () => {
    currentUser.set({ id: "1", username: "alice", admin: false });

    upsertUser({ id: "1", username: "alice", admin: true });

    expect(get(currentUser).admin).toBe(true);
  });

  it("leaves currentUser untouched when a different user is upserted", () => {
    currentUser.set({ id: "1", username: "alice" });

    upsertUser({ id: "2", username: "bob" });

    expect(get(currentUser)).toEqual({ id: "1", username: "alice" });
  });
});

describe("upsertChat", () => {
  it("adds a new chat", () => {
    upsertChat({ id: "10", roomName: "room" });
    expect(get(chats)).toEqual([{ id: "10", roomName: "room" }]);
  });

  it("replaces an existing chat with the same id instead of duplicating", () => {
    upsertChat({ id: "10", roomName: "old-name" });
    upsertChat({ id: "10", roomName: "new-name" });

    const list = get(chats);
    expect(list).toHaveLength(1);
    expect(list[0].roomName).toBe("new-name");
  });
});

describe("removeChat", () => {
  it("removes the chat and its message history", () => {
    upsertChat({ id: "10", roomName: "room" });
    setMessagesForChat("10", [{ id: "1", content: "hi" }]);

    removeChat("10");

    expect(get(chats)).toEqual([]);
    expect(get(messagesByChat)).not.toHaveProperty("10");
  });

  it("leaves other chats and their messages alone", () => {
    upsertChat({ id: "10", roomName: "room" });
    upsertChat({ id: "20", roomName: "other room" });
    setMessagesForChat("20", [{ id: "1", content: "hi" }]);

    removeChat("10");

    expect(get(chats)).toEqual([{ id: "20", roomName: "other room" }]);
    expect(get(messagesByChat)["20"]).toEqual([{ id: "1", content: "hi" }]);
  });
});

describe("setMessagesForChat / appendMessage", () => {
  it("stores the full message list for a chat", () => {
    setMessagesForChat("10", [{ id: "1" }, { id: "2" }]);
    expect(get(messagesByChat)["10"]).toEqual([{ id: "1" }, { id: "2" }]);
  });

  it("appends a message to an existing chat's history", () => {
    setMessagesForChat("10", [{ id: "1" }]);
    appendMessage("10", { id: "2" });
    expect(get(messagesByChat)["10"]).toEqual([{ id: "1" }, { id: "2" }]);
  });

  it("starts a chat's history from empty when appending before any load", () => {
    appendMessage("10", { id: "1" });
    expect(get(messagesByChat)["10"]).toEqual([{ id: "1" }]);
  });

  it("does not duplicate a message that arrives twice (e.g. a WebSocket redelivery)", () => {
    setMessagesForChat("10", [{ id: "1" }]);
    appendMessage("10", { id: "1" });
    expect(get(messagesByChat)["10"]).toEqual([{ id: "1" }]);
  });
});

describe("resetSession", () => {
  it("clears every store back to its initial state", () => {
    currentUser.set({ id: "1" });
    users.set([{ id: "1" }]);
    chats.set([{ id: "10" }]);
    setMessagesForChat("10", [{ id: "1" }]);
    selectedChatId.set("10");

    resetSession();

    expect(get(currentUser)).toBeNull();
    expect(get(users)).toEqual([]);
    expect(get(chats)).toEqual([]);
    expect(get(messagesByChat)).toEqual({});
    expect(get(selectedChatId)).toBeNull();
  });
});
