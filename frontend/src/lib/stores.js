import { writable } from "svelte/store";

// Mirrors Client.java's in-memory state: currentUser/users/chats/messages, kept in sync by
// REST responses (lib/api.js) and WebSocket broadcasts (lib/ws.js) the way Client.handleIncomingPacket
// used to fold incoming Packets into its own fields.

export const currentUser = writable(null); // UserDto | null
export const users = writable([]); // UserDto[]
export const chats = writable([]); // ChatDto[]
export const messagesByChat = writable({}); // { [chatId]: MessageDto[] }
export const selectedChatId = writable(null);

export function upsertUser(user) {
  users.update((list) => {
    const next = list.filter((u) => u.id !== user.id);
    next.push(user);
    return next;
  });
  currentUser.update((me) => (me && me.id === user.id ? user : me));
}

export function upsertChat(chat) {
  chats.update((list) => {
    const next = list.filter((c) => c.id !== chat.id);
    next.push(chat);
    return next;
  });
}

export function removeChat(chatId) {
  chats.update((list) => list.filter((c) => c.id !== chatId));
  messagesByChat.update((byChat) => {
    const next = { ...byChat };
    delete next[chatId];
    return next;
  });
}

export function setMessagesForChat(chatId, messages) {
  messagesByChat.update((byChat) => ({ ...byChat, [chatId]: messages }));
}

export function appendMessage(chatId, message) {
  messagesByChat.update((byChat) => {
    const existing = byChat[chatId] || [];
    if (existing.some((m) => m.id === message.id)) {
      return byChat;
    }
    return { ...byChat, [chatId]: [...existing, message] };
  });
}

export function resetSession() {
  currentUser.set(null);
  users.set([]);
  chats.set([]);
  messagesByChat.set({});
  selectedChatId.set(null);
}
