<script>
  import Modal from "./Modal.svelte";
  import { api } from "../api.js";
  import { upsertUser } from "../stores.js";

  let { onClose } = $props();

  let username = $state("");
  let password = $state("");
  let firstName = $state("");
  let lastName = $state("");
  let admin = $state(false);
  let error = $state("");
  let submitting = $state(false);

  async function handleSubmit(e) {
    e.preventDefault();
    error = "";
    submitting = true;
    try {
      const user = await api.createUser({
        username: username.trim(),
        password,
        firstName: firstName.trim(),
        lastName: lastName.trim(),
        disabled: false,
        admin,
      });
      upsertUser(user);
      onClose();
    } catch (err) {
      error = err.message;
    } finally {
      submitting = false;
    }
  }
</script>

<Modal title="New User" {onClose}>
  <form onsubmit={handleSubmit}>
    <div class="field">
      <label for="username">Username</label>
      <input id="username" type="text" bind:value={username} required />
    </div>
    <div class="field">
      <label for="password">Password</label>
      <input id="password" type="password" bind:value={password} required />
    </div>
    <div class="field">
      <label for="firstName">First name</label>
      <input id="firstName" type="text" bind:value={firstName} required />
    </div>
    <div class="field">
      <label for="lastName">Last name</label>
      <input id="lastName" type="text" bind:value={lastName} required />
    </div>
    <label class="checkbox-row">
      <input type="checkbox" bind:checked={admin} /> IT Admin
    </label>
    {#if error}<p class="error-text">{error}</p>{/if}
    <div class="modal-actions">
      <button type="button" class="btn" onclick={onClose}>Cancel</button>
      <button type="submit" class="btn btn-primary" disabled={submitting}>Create</button>
    </div>
  </form>
</Modal>

<style>
  .checkbox-row {
    display: flex;
    align-items: center;
    gap: 8px;
    margin-bottom: 14px;
    font-size: 0.9rem;
  }

  .modal-actions {
    display: flex;
    justify-content: flex-end;
    gap: 8px;
    margin-top: 8px;
  }
</style>
