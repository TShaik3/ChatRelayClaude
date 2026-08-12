<script>
  import { currentUser, users, chats, selectedChatId, messagesByChat, setMessagesForChat } from "../stores.js";
  import { api } from "../api.js";
  import RenameChatDialog from "./RenameChatDialog.svelte";

  let showRename = $state(false);
  let messageText = $state("");
  let loadingMessages = $state(false);

  const selectedChat = $derived($chats.find((c) => c.id === $selectedChatId) ?? null);
  const messages = $derived($messagesByChat[$selectedChatId] ?? []);
  const isMember = $derived(selectedChat ? selectedChat.chatterIds.includes($currentUser.id) : false);
  const canRename = $derived(
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
    <div class="empty">Select a chat</div>
  {:else}
    <div class="chat-header">
      <div class="title-block">
        <span class="title" class:moderating={!isMember}>{displayTitle()}</span>
        <span class="muted">• {allMemberNames()}</span>
      </div>
      <div class="actions">
        <button class="btn" disabled={!canRename} onclick={() => (showRename = true)}>Rename</button>
        {#if $currentUser.admin}
          <button class="btn" onclick={downloadChat}>Download</button>
        {/if}
      </div>
    </div>

    <div class="messages">
      {#if loadingMessages}
        <p class="muted">Loading…</p>
      {:else}
        {#each messages as message (message.id)}
          <div class="message-card">
            <div class="message-header">
              <strong>{memberFirstName(message.authorId)}</strong>
              <span class="muted">• {formatTime(message.createdAt)}</span>
            </div>
            <div class="message-content">{message.content}</div>
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

{#if showRename && selectedChat}
  <RenameChatDialog chat={selectedChat} onClose={() => (showRename = false)} />
{/if}

<style>
  .chat-area {
    flex: 1;
    display: flex;
    flex-direction: column;
    min-width: 0;
  }

  .empty {
    flex: 1;
    display: flex;
    align-items: center;
    justify-content: center;
    color: var(--muted-text);
  }

  .chat-header {
    display: flex;
    align-items: center;
    justify-content: space-between;
    padding: 12px 16px;
    border-bottom: 1px solid var(--card-border);
  }

  .title-block .title {
    font-weight: 700;
    font-size: 1.05rem;
  }

  .title-block .title.moderating {
    color: var(--it-badge);
  }

  .muted {
    color: var(--muted-text);
  }

  .actions {
    display: flex;
    gap: 8px;
  }

  .messages {
    flex: 1;
    overflow-y: auto;
    padding: 12px;
    display: flex;
    flex-direction: column;
    gap: 8px;
  }

  .message-card {
    border: 1px solid var(--card-border);
    border-radius: 6px;
    padding: 8px 10px;
  }

  .message-header {
    margin-bottom: 4px;
    font-size: 0.9rem;
  }

  .message-content {
    white-space: pre-wrap;
    word-break: break-word;
  }

  .input-bar {
    display: flex;
    gap: 8px;
    padding: 12px;
    border-top: 1px solid var(--card-border);
  }

  .input-bar input {
    flex: 1;
    padding: 8px 10px;
    border: 1px solid var(--card-border);
    border-radius: 6px;
  }
</style>
