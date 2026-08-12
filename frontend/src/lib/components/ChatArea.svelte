<script>
  import { currentUser, users, chats, selectedChatId, messagesByChat, setMessagesForChat } from "../stores.js";
  import { api } from "../api.js";
  import ChatSettingsDialog from "./ChatSettingsDialog.svelte";
  import Avatar from "./Avatar.svelte";

  let showSettings = $state(false);
  let messageText = $state("");
  let loadingMessages = $state(false);

  const selectedChat = $derived($chats.find((c) => c.id === $selectedChatId) ?? null);
  const messages = $derived($messagesByChat[$selectedChatId] ?? []);
  const isMember = $derived(selectedChat ? selectedChat.chatterIds.includes($currentUser.id) : false);
  // Only the chat's owner or an IT admin may rename it or add/remove members (mirrors
  // DBManager.assertCanManageChat on the backend).
  const canManage = $derived(
    selectedChat !== null && ($currentUser.admin || selectedChat.ownerId === $currentUser.id),
  );

  // Loads a chat's history the first time it's opened, mirroring how the old GUI's selectChat()
  // triggered a render from whatever was already in memory -- except here that memory (the
  // messagesByChat store) is filled lazily per chat instead of dumped in full at login.
  $effect(() => {
    const chatId = $selectedChatId;
    if (chatId && !(chatId in $messagesByChat)) {
      loadMessages(chatId);
    }
  });

  async function loadMessages(chatId) {
    loadingMessages = true;
    try {
      const msgs = await api.getMessages(chatId);
      setMessagesForChat(chatId, msgs);
    } catch (err) {
      console.error("Failed to load messages", err);
    } finally {
      loadingMessages = false;
    }
  }

  function memberById(id) {
    return $users.find((u) => u.id === id) ?? null;
  }

  function memberFirstName(id) {
    return memberById(id)?.firstName ?? "unknown";
  }

  function memberFullName(id) {
    const member = memberById(id);
    if (!member) return "Unknown";
    return `${member.firstName} ${member.lastName ?? ""}`.trim();
  }

  function displayTitle() {
    if (!selectedChat) return "";
    if (selectedChat.isPrivate && selectedChat.chatterIds.length === 2 && isMember) {
      const otherId = selectedChat.chatterIds.find((id) => id !== $currentUser.id);
      const other = memberById(otherId);
      if (other) return `${other.firstName} ${other.lastName}`;
    }
    return selectedChat.roomName;
  }

  function allMemberNames() {
    return selectedChat.chatterIds.map(memberFirstName).join(", ");
  }

  async function sendMessage(e) {
    e.preventDefault();
    const text = messageText.trim();
    if (!text || !selectedChat) return;
    messageText = "";
    try {
      await api.sendMessage(selectedChat.id, text);
      // No optimistic append here: the WebSocket broadcast on /topic/chats/{id} (subscribed the
      // moment this chat was selected/created) delivers it back to us the same as every other
      // member, so appending it twice would show a duplicate.
    } catch (err) {
      console.error("Failed to send message", err);
    }
  }

  function formatTime(epochSeconds) {
    return new Date(epochSeconds * 1000).toLocaleString();
  }

  function downloadChat() {
    if (!selectedChat) return;
    const lines = [
      `Chat: ${selectedChat.roomName}`,
      ...messages.map((m) => `[${formatTime(m.createdAt)}] ${memberFirstName(m.authorId)}: ${m.content}`),
    ];
    const blob = new Blob([lines.join("\n")], { type: "text/plain" });
    const url = URL.createObjectURL(blob);
    const a = document.createElement("a");
    a.href = url;
    a.download = `chat-${selectedChat.id}-export.txt`;
    a.click();
    URL.revokeObjectURL(url);
  }
</script>

<section class="chat-area">
  {#if !selectedChat}
    <div class="empty">
      <svg viewBox="0 0 24 24" width="40" height="40" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
        <path
          d="M21 11.5a8.4 8.4 0 0 1-8.4 8.4c-1.3 0-2.6-.3-3.7-.9L3 21l1.9-5.9a8.3 8.3 0 0 1-.9-3.8A8.4 8.4 0 1 1 21 11.5z"
        />
      </svg>
      <p>Select a chat</p>
    </div>
  {:else}
    <div class="chat-header">
      <div class="title-block">
        <Avatar name={displayTitle()} size={38} />
        <div class="title-text">
          <span class="title" class:moderating={!isMember}>{displayTitle()}</span>
          <span class="muted">{allMemberNames()}</span>
        </div>
      </div>
      <div class="actions">
        {#if canManage}
          <button class="btn" onclick={() => (showSettings = true)}>Chat Settings</button>
        {/if}
        {#if $currentUser.admin}
          <button class="btn" onclick={downloadChat}>Download</button>
        {/if}
      </div>
    </div>

    <div class="messages">
      {#if loadingMessages}
        <p class="muted center">Loading…</p>
      {:else if messages.length === 0}
        <p class="muted center">No messages yet — say hello.</p>
      {:else}
        {#each messages as message (message.id)}
          {@const isOwn = message.authorId === $currentUser.id}
          <div class="message-row" class:own={isOwn}>
            {#if !isOwn}<Avatar name={memberFullName(message.authorId)} size={28} />{/if}
            <div class="message-card" class:own={isOwn}>
              <div class="message-header">
                {#if !isOwn}
                  <strong>{memberFirstName(message.authorId)}</strong>
                  <span class="dot">·</span>
                {/if}
                <span class="time">{formatTime(message.createdAt)}</span>
              </div>
              <div class="message-content">{message.content}</div>
            </div>
          </div>
        {/each}
      {/if}
    </div>

    <form class="input-bar" onsubmit={sendMessage}>
      <input
        type="text"
        bind:value={messageText}
        disabled={!isMember}
        placeholder={isMember ? "Message" : "Viewing as IT Admin — read only"}
      />
      <button type="submit" class="btn btn-primary" disabled={!isMember}>Send</button>
    </form>
  {/if}
</section>

{#if showSettings && selectedChat}
  <ChatSettingsDialog chat={selectedChat} onClose={() => (showSettings = false)} />
{/if}

<style>
  .chat-area {
    flex: 1;
    display: flex;
    flex-direction: column;
    min-width: 0;
    background: var(--bg);
  }

  .empty {
    flex: 1;
    display: flex;
    flex-direction: column;
    align-items: center;
    justify-content: center;
    gap: 10px;
    color: var(--muted-text);
  }

  .empty p {
    margin: 0;
    font-size: 0.95rem;
  }

  .chat-header {
    display: flex;
    align-items: center;
    justify-content: space-between;
    padding: 12px 20px;
    border-bottom: 1px solid var(--card-border);
    gap: 12px;
  }

  .title-block {
    display: flex;
    align-items: center;
    gap: 10px;
    min-width: 0;
  }

  .title-text {
    display: flex;
    flex-direction: column;
    min-width: 0;
  }

  .title-text .title {
    font-weight: 700;
    font-size: 1.02rem;
    white-space: nowrap;
    overflow: hidden;
    text-overflow: ellipsis;
  }

  .title-text .title.moderating {
    color: var(--it-badge);
  }

  .title-text .muted {
    font-size: 0.82rem;
  }

  .muted {
    color: var(--muted-text);
  }

  .muted.center {
    text-align: center;
    margin-top: 24px;
  }

  .actions {
    display: flex;
    gap: 8px;
    flex-shrink: 0;
  }

  .messages {
    flex: 1;
    overflow-y: auto;
    padding: 20px;
    display: flex;
    flex-direction: column;
    gap: 12px;
    background: var(--sidebar-bg);
  }

  .message-row {
    display: flex;
    align-items: flex-end;
    gap: 8px;
  }

  .message-row.own {
    justify-content: flex-end;
  }

  .message-card {
    max-width: min(70%, 520px);
    background: var(--bg);
    border: 1px solid var(--card-border);
    border-radius: var(--radius-md);
    padding: 8px 12px;
    box-shadow: var(--shadow-sm);
  }

  .message-card.own {
    background: var(--brand);
    border-color: var(--brand);
    color: #fff;
  }

  .message-header {
    display: flex;
    align-items: center;
    gap: 5px;
    margin-bottom: 3px;
    font-size: 0.78rem;
  }

  .message-card.own .message-header {
    color: rgba(255, 255, 255, 0.75);
  }

  .message-card:not(.own) .message-header .time,
  .message-card:not(.own) .message-header .dot {
    color: var(--muted-text);
  }

  .message-content {
    white-space: pre-wrap;
    word-break: break-word;
    line-height: 1.4;
  }

  .input-bar {
    display: flex;
    gap: 10px;
    padding: 14px 20px;
    border-top: 1px solid var(--card-border);
    background: var(--bg);
  }

  .input-bar input {
    flex: 1;
    padding: 10px 14px;
    border: 1px solid var(--card-border);
    border-radius: 999px;
    transition: border-color 0.15s ease, box-shadow 0.15s ease;
  }

  .input-bar input:focus {
    outline: none;
    border-color: var(--brand);
    box-shadow: 0 0 0 3px var(--brand-soft);
  }

  .input-bar .btn-primary {
    border-radius: 999px;
    padding: 8px 20px;
  }
</style>
