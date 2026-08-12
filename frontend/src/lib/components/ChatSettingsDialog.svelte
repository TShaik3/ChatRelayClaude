<script>
  import Modal from "./Modal.svelte";
  import Avatar from "./Avatar.svelte";
  import { api } from "../api.js";
  import { users as usersStore, upsertChat } from "../stores.js";

  // `chat` is a reactive prop (ChatArea passes its own $derived selectedChat), so as WS broadcasts
  // update the `chats` store -- a rename, another admin adding/removing a member -- this dialog's
  // member list and name field re-render without needing their own subscription.
  let { chat, onClose } = $props();

  // Intentional one-time seed from a prop -- see the matching comment in EditUserDialog.svelte.
  // svelte-ignore state_referenced_locally
  let roomName = $state(chat.roomName);
  let nameError = $state("");
  let savingName = $state(false);

  let memberError = $state("");
  let pendingMemberId = $state(null);

  const members = $derived(
    chat.chatterIds.map((id) => $usersStore.find((u) => u.id === id)).filter(Boolean),
  );

  const addableUsers = $derived($usersStore.filter((u) => !chat.chatterIds.includes(u.id) && !u.disabled));

  async function saveName(e) {
    e.preventDefault();
    nameError = "";
    savingName = true;
    try {
      const updated = await api.renameChat(chat.id, roomName.trim());
      upsertChat(updated);
    } catch (err) {
      nameError = err.message;
    } finally {
      savingName = false;
    }
  }

  async function addMember(userId) {
    memberError = "";
    pendingMemberId = userId;
    try {
      const updated = await api.addMember(chat.id, userId);
      upsertChat(updated);
    } catch (err) {
      memberError = err.message;
    } finally {
      pendingMemberId = null;
    }
  }

  async function removeMember(userId) {
    memberError = "";
    pendingMemberId = userId;
    try {
      await api.removeMember(chat.id, userId);
      // No optimistic store update here -- the WS broadcast on /topic/chats/{id} (already
      // subscribed, since this chat is open behind the dialog) updates `chats` the same way it
      // does for every other member, and `chat` above picks that up reactively.
    } catch (err) {
      memberError = err.message;
    } finally {
      pendingMemberId = null;
    }
  }
</script>

<Modal title="Chat Settings" {onClose}>
  <form class="name-section" onsubmit={saveName}>
    <div class="field">
      <label for="roomName">Name</label>
      <div class="name-row">
        <input id="roomName" type="text" bind:value={roomName} required />
        <button type="submit" class="btn btn-primary" disabled={savingName}>Save</button>
      </div>
    </div>
    {#if nameError}<p class="error-text">{nameError}</p>{/if}
  </form>

  <div class="section">
    <div class="section-title">Members</div>
    <div class="member-list">
      {#each members as member (member.id)}
        <div class="member-row">
          <Avatar name={`${member.firstName} ${member.lastName}`} size={28} />
          <span class="member-name">
            {member.firstName} {member.lastName}
            {#if member.id === chat.ownerId}<span class="owner-tag">Owner</span>{/if}
          </span>
          {#if member.id !== chat.ownerId}
            <button
              type="button"
              class="btn btn-danger"
              disabled={pendingMemberId === member.id}
              onclick={() => removeMember(member.id)}
            >
              Remove
            </button>
          {/if}
        </div>
      {/each}
    </div>
  </div>

  {#if addableUsers.length > 0}
    <div class="section">
      <div class="section-title">Add people</div>
      <div class="member-list">
        {#each addableUsers as user (user.id)}
          <div class="member-row">
            <Avatar name={`${user.firstName} ${user.lastName}`} size={28} />
            <span class="member-name">{user.firstName} {user.lastName}</span>
            <button
              type="button"
              class="btn"
              disabled={pendingMemberId === user.id}
              onclick={() => addMember(user.id)}
            >
              Add
            </button>
          </div>
        {/each}
      </div>
    </div>
  {/if}

  {#if memberError}<p class="error-text">{memberError}</p>{/if}

  <div class="modal-actions">
    <button type="button" class="btn" onclick={onClose}>Close</button>
  </div>
</Modal>

<style>
  .name-section {
    margin-bottom: 20px;
  }

  .name-row {
    display: flex;
    gap: 8px;
  }

  .name-row input {
    flex: 1;
  }

  .section {
    margin-bottom: 20px;
  }

  .section-title {
    font-size: 0.78rem;
    font-weight: 700;
    text-transform: uppercase;
    letter-spacing: 0.03em;
    color: var(--muted-text);
    margin-bottom: 8px;
  }

  .member-list {
    display: flex;
    flex-direction: column;
    gap: 2px;
    max-height: 200px;
    overflow-y: auto;
    border: 1px solid var(--card-border);
    border-radius: var(--radius-md);
    padding: 6px;
  }

  .member-row {
    display: flex;
    align-items: center;
    gap: 10px;
    padding: 6px 8px;
    border-radius: var(--radius-sm);
  }

  .member-row:hover {
    background: var(--sidebar-bg);
  }

  .member-name {
    flex: 1;
    min-width: 0;
    font-size: 0.9rem;
    white-space: nowrap;
    overflow: hidden;
    text-overflow: ellipsis;
  }

  .owner-tag {
    margin-left: 6px;
    font-size: 0.75rem;
    font-weight: 600;
    color: var(--muted-text);
  }

  .modal-actions {
    display: flex;
    justify-content: flex-end;
  }
</style>
