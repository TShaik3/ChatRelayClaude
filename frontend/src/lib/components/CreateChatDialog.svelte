<script>
  import Modal from "./Modal.svelte";
  import Avatar from "./Avatar.svelte";
  import { currentUser, users, upsertChat } from "../stores.js";
  import { api } from "../api.js";
  import { subscribeToChat } from "../ws.js";

  let { onClose } = $props();

  let roomName = $state("");
  let selectedUserIds = $state(new Set());
  let error = $state("");
  let submitting = $state(false);

  const otherUsers = $derived($users.filter((u) => u.id !== $currentUser.id));

  function toggle(id) {
    const next = new Set(selectedUserIds);
    if (next.has(id)) {
      next.delete(id);
    } else {
      next.add(id);
    }
    selectedUserIds = next;
  }

  async function handleSubmit(e) {
    e.preventDefault();
    error = "";
    submitting = true;
    try {
      const ids = [...selectedUserIds];
      const chat = await api.createChat(ids, roomName.trim(), ids.length === 1);
      upsertChat(chat);
      subscribeToChat(chat);
      onClose();
    } catch (err) {
      error = err.message;
    } finally {
      submitting = false;
    }
  }
</script>

<Modal title="Create Chat" {onClose}>
  <form onsubmit={handleSubmit}>
    <div class="field">
      <label for="roomName">Name</label>
      <input id="roomName" type="text" bind:value={roomName} required />
    </div>
    <div class="field">
      <label for="members">Add to Group</label>
      <div id="members" class="member-list">
        {#each otherUsers as user (user.id)}
          <label class="checkbox-row">
            <input type="checkbox" checked={selectedUserIds.has(user.id)} onchange={() => toggle(user.id)} />
            <Avatar name={`${user.firstName} ${user.lastName}`} size={26} />
            <span>{user.firstName} {user.lastName} <span class="muted">(@{user.username})</span></span>
          </label>
        {/each}
      </div>
    </div>
    {#if error}<p class="error-text">{error}</p>{/if}
    <div class="modal-actions">
      <button type="button" class="btn" onclick={onClose}>Cancel</button>
      <button type="submit" class="btn btn-primary" disabled={submitting}>Create</button>
    </div>
  </form>
</Modal>

<style>
  .member-list {
    max-height: 220px;
    overflow-y: auto;
    border: 1px solid var(--card-border);
    border-radius: var(--radius-md);
    padding: 6px;
    display: flex;
    flex-direction: column;
    gap: 2px;
  }

  .checkbox-row {
    display: flex;
    align-items: center;
    gap: 10px;
    padding: 6px 8px;
    border-radius: var(--radius-sm);
    font-weight: 400;
  }

  .checkbox-row:hover {
    background: var(--sidebar-bg);
  }

  .checkbox-row .muted {
    color: var(--muted-text);
    font-weight: 400;
  }

  .modal-actions {
    display: flex;
    justify-content: flex-end;
    gap: 8px;
    margin-top: 8px;
  }
</style>
