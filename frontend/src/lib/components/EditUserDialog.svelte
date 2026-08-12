<script>
  import Modal from "./Modal.svelte";
  import { api } from "../api.js";
  import { upsertUser } from "../stores.js";

  let { user, onClose } = $props();

  // Seeding local editable state from `user` once is intentional, not a bug: MainLayout only
  // ever renders this dialog inside an `{#if editingUser}` block, so a different user means a
  // fresh component instance (via null in between), not this same instance's prop changing
  // underneath an in-progress edit.
  // svelte-ignore state_referenced_locally
  let username = $state(user.username);
  // svelte-ignore state_referenced_locally
  let firstName = $state(user.firstName);
  // svelte-ignore state_referenced_locally
  let lastName = $state(user.lastName);
  let password = $state("");
  // svelte-ignore state_referenced_locally
  let admin = $state(user.admin);
  // svelte-ignore state_referenced_locally
  let disabled = $state(user.disabled);
  let error = $state("");
  let submitting = $state(false);

  async function handleSubmit(e) {
    e.preventDefault();
    error = "";
    submitting = true;
    try {
      const updated = await api.updateUser(user.id, {
        username: username.trim(),
        password,
        firstName: firstName.trim(),
        lastName: lastName.trim(),
        disabled,
        admin,
      });
      upsertUser(updated);
      onClose();
    } catch (err) {
      error = err.message;
    } finally {
      submitting = false;
    }
  }
</script>

<Modal title={`Edit ${user.firstName} ${user.lastName}`} {onClose}>
  <form onsubmit={handleSubmit}>
    <div class="field">
      <label for="username">Username</label>
      <input id="username" type="text" bind:value={username} required />
    </div>
    <div class="field">
      <label for="firstName">First name</label>
      <input id="firstName" type="text" bind:value={firstName} required />
    </div>
    <div class="field">
      <label for="lastName">Last name</label>
      <input id="lastName" type="text" bind:value={lastName} required />
    </div>
    <div class="field">
      <label for="password">New password</label>
      <input id="password" type="password" bind:value={password} placeholder="leave blank to keep current" />
    </div>
    <label class="checkbox-row">
      <input type="checkbox" bind:checked={admin} /> IT Admin
    </label>
    <label class="checkbox-row">
      <input type="checkbox" bind:checked={disabled} /> Disabled
    </label>
    {#if error}<p class="error-text">{error}</p>{/if}
    <div class="modal-actions">
      <button type="button" class="btn" onclick={onClose}>Cancel</button>
      <button type="submit" class="btn btn-primary" disabled={submitting}>Save</button>
    </div>
  </form>
</Modal>

<style>
  .checkbox-row {
    display: flex;
    align-items: center;
    gap: 8px;
    margin-bottom: 12px;
  }

  .modal-actions {
    display: flex;
    justify-content: flex-end;
    gap: 8px;
    margin-top: 8px;
  }
</style>
