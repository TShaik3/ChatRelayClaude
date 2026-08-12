<script>
  import Modal from "./Modal.svelte";
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
            {user.firstName} {user.lastName} (@{user.username})
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
    max-height: 200px;
    overflow-y: auto;
    border: 1px solid var(--card-border);
    border-radius: 6px;
    padding: 8px;
  }

  .checkbox-row {
    display: flex;
    align-items: center;
    gap: 8px;
    padding: 4px 0;
    font-weight: 400;
  }

  .modal-actions {
    display: flex;
    justify-content: flex-end;
    gap: 8px;
    margin-top: 8px;
  }
</style>
