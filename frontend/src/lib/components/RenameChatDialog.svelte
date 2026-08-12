<script>
  import Modal from "./Modal.svelte";
  import { api } from "../api.js";
  import { upsertChat } from "../stores.js";

  let { chat, onClose } = $props();

  // Intentional one-time seed from a prop -- see the matching comment in EditUserDialog.svelte.
  // svelte-ignore state_referenced_locally
  let roomName = $state(chat.roomName);
  let error = $state("");
  let submitting = $state(false);

  async function handleSubmit(e) {
    e.preventDefault();
    error = "";
    submitting = true;
    try {
      const updated = await api.renameChat(chat.id, roomName.trim());
      upsertChat(updated);
      onClose();
    } catch (err) {
      error = err.message;
    } finally {
      submitting = false;
    }
  }
</script>

<Modal title="Rename Chat" {onClose}>
  <form onsubmit={handleSubmit}>
    <div class="field">
      <label for="roomName">Name</label>
      <input id="roomName" type="text" bind:value={roomName} required />
    </div>
    {#if error}<p class="error-text">{error}</p>{/if}
    <div class="modal-actions">
      <button type="button" class="btn" onclick={onClose}>Cancel</button>
      <button type="submit" class="btn btn-primary" disabled={submitting}>Save</button>
    </div>
  </form>
</Modal>

<style>
  .modal-actions {
    display: flex;
    justify-content: flex-end;
    gap: 8px;
    margin-top: 8px;
  }
</style>
