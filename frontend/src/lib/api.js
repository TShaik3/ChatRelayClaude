// fetch-based REST client for the Phase 3 backend controllers. Requests are same-origin (Vite's
// dev proxy forwards /api to the backend, and Phase 5's production build serves both from one
// origin), so the session cookie set by /auth/login rides along automatically.

const BASE = "/api";

async function request(path, options = {}) {
  const response = await fetch(BASE + path, {
    credentials: "same-origin",
    headers: { "Content-Type": "application/json", ...(options.headers || {}) },
    ...options,
  });

  if (!response.ok) {
    let message = `Request failed: ${response.status}`;
    try {
      const body = await response.json();
      if (body && body.error) {
        message = body.error;
      }
    } catch {
      // error body wasn't JSON -- keep the generic message
    }
    throw new Error(message);
  }

  if (response.status === 204) {
    return null;
  }
  const text = await response.text();
  return text ? JSON.parse(text) : null;
}

function post(path, body) {
  return request(path, { method: "POST", body: JSON.stringify(body) });
}

function put(path, body) {
  return request(path, { method: "PUT", body: JSON.stringify(body) });
}

export const api = {
  login: (username, password) => post("/auth/login", { username, password }),
  logout: () => post("/auth/logout", {}),
  me: () => request("/auth/me"),

  getUsers: () => request("/users"),
  createUser: (payload) => post("/users", payload),
  updateUser: (id, payload) => put(`/users/${id}`, payload),

  getChats: () => request("/chats"),
  createChat: (otherUserIds, roomName, isPrivate) => post("/chats", { otherUserIds, roomName, isPrivate }),
  renameChat: (chatId, roomName) => put(`/chats/${chatId}/rename`, { roomName }),
  addMember: (chatId, userId) => post(`/chats/${chatId}/members`, { userId }),
  removeMember: (chatId, userId) => request(`/chats/${chatId}/members/${userId}`, { method: "DELETE" }),

  getMessages: (chatId) => request(`/chats/${chatId}/messages`),
  sendMessage: (chatId, content) => post(`/chats/${chatId}/messages`, { content }),
};
