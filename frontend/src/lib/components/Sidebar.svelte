<script>
  import { currentUser, users, chats, selectedChatId } from "../stores.js";
  import { subscribeToChat } from "../ws.js";
  import Avatar from "./Avatar.svelte";

  let { onNewChat, onNewUser, onEditUser, onOpenAccountSettings, onLogout } = $props();

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
    <button class="icon-btn subtle" onclick={onOpenAccountSettings} title="Account settings" aria-label="Account settings">
      <svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
        <circle cx="12" cy="12" r="3" />
        <path
          d="M19.4 15a1.65 1.65 0 0 0 .33 1.82l.06.06a2 2 0 1 1-2.83 2.83l-.06-.06a1.65 1.65 0 0 0-1.82-.33 1.65 1.65 0 0 0-1 1.51V21a2 2 0 0 1-4 0v-.09A1.65 1.65 0 0 0 9 19.4a1.65 1.65 0 0 0-1.82.33l-.06.06a2 2 0 1 1-2.83-2.83l.06-.06a1.65 1.65 0 0 0 .33-1.82 1.65 1.65 0 0 0-1.51-1H3a2 2 0 0 1 0-4h.09A1.65 1.65 0 0 0 4.6 9a1.65 1.65 0 0 0-.33-1.82l-.06-.06a2 2 0 1 1 2.83-2.83l.06.06a1.65 1.65 0 0 0 1.82.33H9a1.65 1.65 0 0 0 1-1.51V3a2 2 0 0 1 4 0v.09a1.65 1.65 0 0 0 1 1.51 1.65 1.65 0 0 0 1.82-.33l.06-.06a2 2 0 1 1 2.83 2.83l-.06.06a1.65 1.65 0 0 0-.33 1.82V9a1.65 1.65 0 0 0 1.51 1H21a2 2 0 0 1 0 4h-.09a1.65 1.65 0 0 0-1.51 1z"
        />
      </svg>
    </button>
  </div>

  <div class="panels">
    <div class="panel">
      <div class="section-label-row">
        <span class="section-label">Chats</span>
        <button class="icon-btn" onclick={onNewChat} title="New chat" aria-label="New chat">
          <svg viewBox="0 0 24 24" width="14" height="14" fill="none" stroke="currentColor" stroke-width="2.6" stroke-linecap="round">
            <path d="M12 5v14M5 12h14" />
          </svg>
        </button>
      </div>
      <div class="list">
        {#if sortedChats.length === 0}
          <p class="empty-hint">No chats yet — start one with the + button.</p>
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
    </div>

    {#if $currentUser.admin}
      <div class="panel">
        <div class="section-label-row">
          <span class="section-label admin">All Users</span>
          <button class="icon-btn admin" onclick={onNewUser} title="New user" aria-label="New user">
            <svg viewBox="0 0 24 24" width="14" height="14" fill="none" stroke="currentColor" stroke-width="2.6" stroke-linecap="round">
              <path d="M12 5v14M5 12h14" />
            </svg>
          </button>
        </div>
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
      </div>
    {/if}
  </div>

  <div class="toolbar">
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

  .section-label-row {
    flex-shrink: 0;
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 8px;
    padding: 12px 10px 6px 14px;
  }

  .section-label {
    font-size: 0.72rem;
    font-weight: 700;
    text-transform: uppercase;
    letter-spacing: 0.04em;
    color: var(--muted-text);
  }

  .section-label.admin {
    color: var(--it-badge);
  }

  .icon-btn {
    width: 22px;
    height: 22px;
    border-radius: 50%;
    border: none;
    color: #fff;
    background: var(--brand);
    display: flex;
    align-items: center;
    justify-content: center;
    flex-shrink: 0;
    transition: background-color 0.15s ease, transform 0.05s ease;
  }

  .icon-btn:hover {
    background: var(--brand-hover);
  }

  .icon-btn:active {
    transform: translateY(1px);
  }

  .icon-btn.admin {
    background: var(--it-badge);
  }

  .icon-btn.admin:hover {
    background: #b91c1c;
  }

  .icon-btn.subtle {
    background: none;
    color: var(--muted-text);
  }

  .icon-btn.subtle:hover {
    background: var(--hover-overlay);
    color: var(--text);
  }

  .panels {
    flex: 1;
    min-height: 0;
    display: flex;
    flex-direction: column;
  }

  .panel {
    flex: 1;
    min-height: 0;
    display: flex;
    flex-direction: column;
  }

  .panel + .panel {
    border-top: 1px solid var(--card-border);
  }

  .empty-hint {
    margin: 4px 14px 8px;
    font-size: 0.85rem;
    color: var(--muted-text);
  }

  .list {
    flex: 1;
    min-height: 0;
    overflow-y: auto;
    display: flex;
    flex-direction: column;
    padding: 0 6px 6px;
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
    background: var(--hover-overlay);
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
