<script>
  import { currentUser, users, chats, selectedChatId } from "../stores.js";
  import { subscribeToChat } from "../ws.js";
  import Avatar from "./Avatar.svelte";

  let { onNewChat, onNewUser, onEditUser, onLogout } = $props();

  // Chats aren't bulk-loaded with their messages anymore (Phase 3 deliberately scoped message
  // loading to one chat at a time), so unlike the old Swing GUI there's no last-message
  // timestamp available to sort by without either reintroducing that bulk load or a dedicated
  // endpoint. Sorting by id descending is a reasonable proxy (newest-created first), not a true
  // most-recent-activity sort.
  const sortedChats = $derived([...$chats].sort((a, b) => Number(b.id) - Number(a.id)));

  function isMember(chat) {
    return chat.chatterIds.includes($currentUser.id);
  }

  function otherMember(chat) {
    const otherId = chat.chatterIds.find((id) => id !== $currentUser.id);
    return $users.find((u) => u.id === otherId) ?? null;
  }

  function displayTitleFor(chat) {
    if (chat.isPrivate && chat.chatterIds.length === 2 && isMember(chat)) {
      const other = otherMember(chat);
      if (other) return `${other.firstName} ${other.lastName}`;
    }
    return chat.roomName;
  }

  function otherMemberNames(chat) {
    return chat.chatterIds
      .filter((id) => id !== $currentUser.id)
      .map((id) => $users.find((u) => u.id === id)?.firstName)
      .filter(Boolean)
      .join(", ");
  }

  function selectChat(chat) {
    selectedChatId.set(chat.id);
    subscribeToChat(chat);
  }
</script>

<aside class="sidebar">
  <div class="header">
    <div class="identity">
      <Avatar name={`${$currentUser.firstName} ${$currentUser.lastName}`} size={36} />
      <div class="name-block">
        <div class="name">{$currentUser.firstName} {$currentUser.lastName}</div>
        {#if $currentUser.admin}<div class="badge">IT View</div>{/if}
      </div>
    </div>
    <button class="new-chat" onclick={onNewChat} title="New chat" aria-label="New chat">
      <svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" stroke-width="2.4" stroke-linecap="round">
        <path d="M12 5v14M5 12h14" />
      </svg>
    </button>
  </div>

  <div class="scroll-area">
    <div class="section-label">Chats</div>
    <div class="list">
      {#if sortedChats.length === 0}
        <p class="empty-hint">No chats yet — start one with the + button above.</p>
      {/if}
      {#each sortedChats as chat (chat.id)}
        <button class="card" class:selected={$selectedChatId === chat.id} onclick={() => selectChat(chat)}>
          <Avatar name={displayTitleFor(chat)} size={36} />
          <div class="card-text">
            <div class="row">
              <span class="title" class:moderating={!isMember(chat)}>{displayTitleFor(chat)}</span>
              {#if !chat.isPrivate}<span class="tag">· Group</span>{/if}
            </div>
            {#if otherMemberNames(chat)}<div class="subtitle">{otherMemberNames(chat)}</div>{/if}
          </div>
        </button>
      {/each}
    </div>

    {#if $currentUser.admin}
      <div class="section-label admin">All Users</div>
      <div class="list">
        {#each $users as user (user.id)}
          <button class="card" onclick={() => onEditUser(user)}>
            <Avatar name={`${user.firstName} ${user.lastName}`} size={36} />
            <div class="card-text">
              <span class="title">{user.firstName} {user.lastName}</span>
              <span class="subtitle">
                @{user.username}
                {#if user.admin}· IT Admin{/if}
                {#if user.disabled}<span class="disabled-tag">[disabled]</span>{/if}
              </span>
            </div>
          </button>
        {/each}
      </div>
    {/if}
  </div>

  <div class="toolbar">
    {#if $currentUser.admin}
      <button class="btn" onclick={onNewUser}>Create User</button>
    {/if}
    <button class="btn" onclick={onLogout}>Log out</button>
  </div>
</aside>

<style>
  .sidebar {
    width: 300px;
    flex-shrink: 0;
    background: var(--sidebar-bg);
    border-right: 1px solid var(--card-border);
    display: flex;
    flex-direction: column;
    overflow: hidden;
  }

  .header {
    display: flex;
    align-items: center;
    justify-content: space-between;
    padding: 14px;
    border-bottom: 1px solid var(--card-border);
    background: var(--bg);
  }

  .identity {
    display: flex;
    align-items: center;
    gap: 10px;
    min-width: 0;
  }

  .name-block {
    min-width: 0;
  }

  .name {
    font-weight: 700;
    white-space: nowrap;
    overflow: hidden;
    text-overflow: ellipsis;
  }

  .badge {
    color: var(--it-badge);
    font-weight: 700;
    font-size: 0.72rem;
    text-transform: uppercase;
    letter-spacing: 0.03em;
  }

  .new-chat {
    width: 30px;
    height: 30px;
    border-radius: 50%;
    border: none;
    color: #fff;
    background: var(--brand);
    display: flex;
    align-items: center;
    justify-content: center;
    flex-shrink: 0;
    box-shadow: var(--shadow-sm);
    transition: background-color 0.15s ease, transform 0.05s ease;
  }

  .new-chat:hover {
    background: var(--brand-hover);
  }

  .new-chat:active {
    transform: translateY(1px);
  }

  .section-label {
    font-size: 0.72rem;
    font-weight: 700;
    text-transform: uppercase;
    letter-spacing: 0.04em;
    color: var(--muted-text);
    padding: 16px 14px 6px;
  }

  .section-label.admin {
    color: var(--it-badge);
  }

  .scroll-area {
    flex: 1;
    min-height: 0;
    overflow-y: auto;
  }

  .empty-hint {
    margin: 4px 14px 8px;
    font-size: 0.85rem;
    color: var(--muted-text);
  }

  .list {
    display: flex;
    flex-direction: column;
    padding: 0 6px;
  }

  .card {
    text-align: left;
    background: none;
    border: none;
    border-radius: var(--radius-md);
    padding: 8px;
    display: flex;
    align-items: center;
    gap: 10px;
    min-width: 0;
    transition: background-color 0.12s ease;
  }

  .card:hover {
    background: rgba(15, 23, 42, 0.05);
  }

  .card.selected {
    background: var(--card-selected-bg);
  }

  .card-text {
    display: flex;
    flex-direction: column;
    gap: 2px;
    min-width: 0;
    flex: 1;
  }

  .row {
    display: flex;
    align-items: baseline;
    gap: 6px;
    min-width: 0;
  }

  .title {
    font-weight: 600;
    white-space: nowrap;
    overflow: hidden;
    text-overflow: ellipsis;
  }

  .title.moderating {
    color: var(--it-badge);
  }

  .tag,
  .subtitle {
    font-size: 0.8rem;
    color: var(--muted-text);
    white-space: nowrap;
    overflow: hidden;
    text-overflow: ellipsis;
  }

  .disabled-tag {
    color: var(--it-badge);
  }

  .toolbar {
    flex-shrink: 0;
    display: flex;
    gap: 6px;
    padding: 12px 14px;
    border-top: 1px solid var(--card-border);
    background: var(--bg);
  }

  .toolbar .btn {
    flex: 1;
  }
</style>
