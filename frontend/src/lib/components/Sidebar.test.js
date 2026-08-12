import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, screen, fireEvent } from "@testing-library/svelte";
import Sidebar from "./Sidebar.svelte";
import { currentUser, users, chats, resetSession } from "../stores.js";

function renderSidebar(overrides = {}) {
  return render(Sidebar, {
    props: {
      onNewChat: vi.fn(),
      onNewUser: vi.fn(),
      onEditUser: vi.fn(),
      onLogout: vi.fn(),
      ...overrides,
    },
  });
}

beforeEach(() => {
  resetSession();
});

describe("Sidebar", () => {
  it("shows the current user's name and an IT View badge for admins", () => {
    currentUser.set({ id: "1", firstName: "Ari", lastName: "Fisher", admin: true });

    renderSidebar();

    expect(screen.getByText("Ari Fisher")).toBeInTheDocument();
    expect(screen.getByText("IT View")).toBeInTheDocument();
  });

  it("hides the All Users section and Create User button for non-admins", () => {
    currentUser.set({ id: "1", firstName: "Bob", lastName: "B", admin: false });

    renderSidebar();

    expect(screen.queryByText("All Users")).not.toBeInTheDocument();
    expect(screen.queryByText("Create User")).not.toBeInTheDocument();
  });

  it("shows a private 2-person chat's title as the other member's name", () => {
    currentUser.set({ id: "1", firstName: "Me", lastName: "M", admin: false });
    users.set([
      { id: "1", firstName: "Me", lastName: "M" },
      { id: "2", firstName: "Other", lastName: "Person" },
    ]);
    chats.set([{ id: "10", roomName: "irrelevant-room-name", isPrivate: true, chatterIds: ["1", "2"] }]);

    renderSidebar();

    expect(screen.getByText("Other Person")).toBeInTheDocument();
    expect(screen.queryByText("irrelevant-room-name")).not.toBeInTheDocument();
  });

  it("shows a group chat's actual room name, tagged as a group", () => {
    currentUser.set({ id: "1", firstName: "Me", lastName: "M", admin: false });
    chats.set([{ id: "10", roomName: "Team Standup", isPrivate: false, chatterIds: ["1", "2", "3"] }]);

    renderSidebar();

    expect(screen.getByText("Team Standup")).toBeInTheDocument();
    expect(screen.getByText("· Group")).toBeInTheDocument();
  });

  it("calls onNewChat when the + button is clicked", async () => {
    currentUser.set({ id: "1", firstName: "Me", lastName: "M", admin: false });
    const onNewChat = vi.fn();

    renderSidebar({ onNewChat });
    await fireEvent.click(screen.getByTitle("New chat"));

    expect(onNewChat).toHaveBeenCalledTimes(1);
  });

  it("calls onEditUser with the clicked user from the All Users list", async () => {
    currentUser.set({ id: "1", firstName: "Admin", lastName: "A", admin: true });
    const bob = { id: "2", username: "bob", firstName: "Bob", lastName: "B", admin: false, disabled: false };
    users.set([bob]);
    const onEditUser = vi.fn();

    renderSidebar({ onEditUser });
    await fireEvent.click(screen.getByText("Bob B"));

    expect(onEditUser).toHaveBeenCalledWith(bob);
  });
});
