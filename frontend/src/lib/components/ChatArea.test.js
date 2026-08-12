import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, screen, fireEvent, waitFor } from "@testing-library/svelte";
import ChatArea from "./ChatArea.svelte";
import { currentUser, users, chats, selectedChatId, resetSession } from "../stores.js";
import { api } from "../api.js";

vi.mock("../api.js", () => ({
  api: { getMessages: vi.fn(), sendMessage: vi.fn() },
}));

beforeEach(() => {
  resetSession();
  vi.clearAllMocks();
});

describe("ChatArea", () => {
  it("shows a placeholder when no chat is selected", () => {
    currentUser.set({ id: "1", admin: false });

    render(ChatArea);

    expect(screen.getByText("Select a chat")).toBeInTheDocument();
  });

  it("loads and displays the selected chat's messages", async () => {
    currentUser.set({ id: "1", admin: false });
    users.set([
      { id: "1", firstName: "Me" },
      { id: "2", firstName: "Other" },
    ]);
    chats.set([{ id: "10", roomName: "room", isPrivate: false, ownerId: "1", chatterIds: ["1", "2"] }]);
    api.getMessages.mockResolvedValue([{ id: "1", authorId: "2", content: "hello", createdAt: 1700000000 }]);
    selectedChatId.set("10");

    render(ChatArea);

    expect(await screen.findByText("hello")).toBeInTheDocument();
    expect(api.getMessages).toHaveBeenCalledWith("10");
  });

  it("disables the message input for a chat the viewer is not a member of (admin moderating)", async () => {
    currentUser.set({ id: "1", admin: true });
    users.set([{ id: "2", firstName: "Other" }]);
    chats.set([{ id: "10", roomName: "room", isPrivate: false, ownerId: "2", chatterIds: ["2"] }]);
    api.getMessages.mockResolvedValue([]);
    selectedChatId.set("10");

    render(ChatArea);
    await waitFor(() => expect(api.getMessages).toHaveBeenCalled());

    const input = screen.getByPlaceholderText("Viewing as IT Admin — read only");
    expect(input).toBeDisabled();
  });

  it("sends a message through the input form", async () => {
    currentUser.set({ id: "1", admin: false });
    users.set([{ id: "1", firstName: "Me" }]);
    chats.set([{ id: "10", roomName: "room", isPrivate: false, ownerId: "1", chatterIds: ["1"] }]);
    api.getMessages.mockResolvedValue([]);
    api.sendMessage.mockResolvedValue({ id: "1" });
    selectedChatId.set("10");

    render(ChatArea);
    await waitFor(() => expect(api.getMessages).toHaveBeenCalled());

    const input = screen.getByPlaceholderText("Message");
    await fireEvent.input(input, { target: { value: "hi there" } });
    await fireEvent.click(screen.getByRole("button", { name: "Send" }));

    expect(api.sendMessage).toHaveBeenCalledWith("10", "hi there");
  });
});
