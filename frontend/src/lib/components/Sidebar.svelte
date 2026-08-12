<script>
  import { currentUser, users, chats, selectedChatId } from "../stores.js";
  import { subscribeToChat } from "../ws.js";

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
    <div class="name-block">
      <div class="name">{$currentUser.firstName} {$currentUser.lastName}</div>
      {#if $currentUser.admin}<div class="badge">IT View</div>{/if}
    </div>
    <button class="new-chat" onclick={onNewChat} title="New chat" aria-label="New chat">+</button>
  </div>

  <div class="scroll-area">
    <div class="section-label">Chats</div>
    <div class="list">
      {#each sortedChats as chat (chat.id)}
        <button class="card" class:selected={$selectedChatId === chat.id} onclick={() => selectChat(chat)}>
          <div class="row">
            <span class="title" class:moderating={!isMember(chat)}>{displayTitleFor(chat)}</span>
            {#if !chat.isPrivate}<span class="tag">· Group</span>{/if}
          </div>
          {#if otherMemberNames(chat)}<div class="subtitle">{otherMemberNames(chat)}</div>{/if}
        </button>
      {/each}
    </div>

    {#if $currentUser.admin}
      <div class="section-label admin">All Users</div>
      <div class="list">
        {#each $users as user (user.id)}
          <button class="card" onclick={() => onEditUser(user)}>
            <span class="title">{user.firstName} {user.lastName}</span>
            <span class="subtitle">
              @{user.username}
              {#if user.admin}· IT Admin{/if}
              {#if user.disabled}<span class="disabled-tag">[disabled]</span>{/if}
            </span>
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
    width: 280px;
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
    padding: 10px 12px;
    border-bottom: 1px solid var(--card-border);
  }

  .name {
    font-weight: 700;
  }

  .badge {
    color: var(--it-badge);
    font-weight: 700;
    font-size: 0.75rem;
  }

  .new-chat {
    width: 28px;
    height: 28px;
    border-radius: 50%;
    border: 1px solid var(--accent);
    color: var(--accent);
    background: var(--bg);
    font-weight: 700;
    font-size: 1rem;
    line-height: 1;
  }

  .section-label {
    font-size: 0.75rem;
    font-weight: 700;
    text-transform: uppercase;
    letter-spacing: 0.03em;
    color: var(--muted-text);
    padding: 10px 12px 4px;
  }

  .section-label.admin {
    color: var(--it-badge);
  }

  .scroll-area {
    flex: 1;
    min-height: 0;
    overflow-y: auto;
  }

  .list {
    display: flex;
    flex-direction: column;
  }

  .card {
    text-align: left;
    background: none;
    border: none;
    border-bottom: 1px solid var(--card-border);
    padding: 8px 12px;
    display: flex;
    flex-direction: column;
    gap: 2px;
  }

  .card:hover {
    background: rgba(0, 0, 0, 0.03);
  }

  .card.selected {
    background: var(--card-selected-bg);
  }

  .row {
    display: flex;
    align-items: baseline;
    gap: 6px;
  }

  .title {
    font-weight: 600;
  }

  .title.moderating {
    color: var(--it-badge);
  }

  .tag,
  .subtitle {
    font-size: 0.8rem;
    color: var(--muted-text);
  }

  .disabled-tag {
    color: var(--it-badge);
  }

  .toolbar {
    flex-shrink: 0;
    display: flex;
    gap: 6px;
    padding: 10px 12px;
    border-top: 1px solid var(--card-border);
  }
</style>
