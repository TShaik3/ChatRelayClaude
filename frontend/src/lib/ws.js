import { Client } from "@stomp/stompjs";
import { get } from "svelte/store";
import { chats, upsertChat, removeChat, upsertUser, appendMessage } from "./stores.js";

// STOMP replacement for Client.handleIncomingPacket's server-push handling. The handshake
// authenticates via the existing session cookie (see api.PrincipalHandshakeInterceptor on the
// backend) -- no separate WebSocket login step needed, just connect after /auth/login succeeds.

let stompClient = null;
const subscribedChatIds = new Set();

export function connect() {
  if (stompClient) {
    return;
  }

  const wsUrl = (location.origin.startsWith("https") ? "wss://" : "ws://") + location.host + "/ws";
  stompClient = new Client({
    brokerURL: wsUrl,
    reconnectDelay: 3000,
    onConnect: () => {
      stompClient.subscribe("/user/queue/updates", (frame) => handleUserEvent(JSON.parse(frame.body)));
      stompClient.subscribe("/topic/users", (frame) => handleUsersTopicEvent(JSON.parse(frame.body)));
      // Pick up broadcasts for every chat already loaded (a page-refresh reconnect case);
      // subscribeToChat() below handles chats created/joined after this point.
      get(chats).forEach(subscribeToChat);
    },
  });
  stompClient.activate();
}

export function disconnect() {
  subscribedChatIds.clear();
  stompClient?.deactivate();
  stompClient = null;
}

export function subscribeToChat(chat) {
  if (!stompClient || subscribedChatIds.has(chat.id)) {
    return;
  }
  subscribedChatIds.add(chat.id);
  stompClient.subscribe(`/topic/chats/${chat.id}`, (frame) => handleChatEvent(chat.id, JSON.parse(frame.body)));
}

function handleChatEvent(chatId, event) {
  switch (event.type) {
    case "NEW_MESSAGE":
      appendMessage(chatId, event.message);
      break;
    case "CHAT_RENAMED":
    case "MEMBER_ADDED":
      upsertChat(event.chat);
      break;
    case "MEMBER_REMOVED":
      chats.update((list) =>
        list.map((c) =>
          c.id === event.chatId ? { ...c, chatterIds: c.chatterIds.filter((id) => id !== event.userId) } : c,
        ),
      );
      break;
    default:
      console.warn("Unhandled chat event", event);
  }
}

function handleUserEvent(event) {
  switch (event.type) {
    case "CHAT_CREATED":
      upsertChat(event.chat);
      subscribeToChat(event.chat);
      break;
    case "REMOVED_FROM_CHAT":
      removeChat(event.chatId);
      break;
    default:
      console.warn("Unhandled user-queue event", event);
  }
}

function handleUsersTopicEvent(event) {
  switch (event.type) {
    case "USER_CREATED":
    case "USER_UPDATED":
      upsertUser(event.user);
      break;
    default:
      console.warn("Unhandled users-topic event", event);
  }
}
