<script>
  import Modal from "./Modal.svelte";
  import { api } from "../api.js";
  import { currentUser, upsertUser } from "../stores.js";
  import { theme, toggleTheme } from "../theme.js";

  let { onClose } = $props();

  // Intentional one-time seed from a store snapshot -- see the matching comment in
  // EditUserDialog.svelte. MainLayout only ever renders this dialog behind a boolean flag, so a
  // fresh mount is guaranteed rather than these fields drifting under an in-progress edit.
  // svelte-ignore state_referenced_locally
  let username = $state($currentUser.username);
  // svelte-ignore state_referenced_locally
  let firstName = $state($currentUser.firstName);
  // svelte-ignore state_referenced_locally
  let lastName = $state($currentUser.lastName);
  let password = $state("");
  let error = $state("");
  let submitting = $state(false);

  async function handleSubmit(e) {
    e.preventDefault();
    error = "";
    submitting = true;
    try {
      const updated = await api.updateMe({
        username: username.trim(),
        password,
        firstName: firstName.trim(),
        lastName: lastName.trim(),
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

<Modal title="Account Settings" {onClose}>
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
    {#if error}<p class="error-text">{error}</p>{/if}
    <div class="modal-actions">
      <button type="button" class="btn" onclick={onClose}>Cancel</button>
      <button type="submit" class="btn btn-primary" disabled={submitting}>Save</button>
    </div>
  </form>

  <div class="divider"></div>

  <div class="appearance">
    <div class="appearance-text">
      <div class="appearance-title">Dark mode</div>
      <div class="appearance-subtitle">Switch the interface to a dark color scheme.</div>
    </div>
    <button
      type="button"
      class="switch"
      class:on={$theme === "dark"}
      role="switch"
      aria-checked={$theme === "dark"}
      aria-label="Toggle dark mode"
      onclick={toggleTheme}
    >
      <span class="knob"></span>
    </button>
  </div>
</Modal>

<style>
  .modal-actions {
    display: flex;
    justify-content: flex-end;
    gap: 8px;
    margin-top: 8px;
  }

  .divider {
    height: 1px;
    background: var(--card-border);
    margin: 22px 0;
  }

  .appearance {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 16px;
  }

  .appearance-title {
    font-weight: 600;
    font-size: 0.92rem;
  }

  .appearance-subtitle {
    font-size: 0.82rem;
    color: var(--muted-text);
    margin-top: 2px;
  }

  .switch {
    flex-shrink: 0;
    width: 42px;
    height: 24px;
    border-radius: 999px;
    border: none;
    background: var(--card-border);
    position: relative;
    padding: 0;
    transition: background-color 0.15s ease;
  }

  .switch.on {
    background: var(--brand);
  }

  .knob {
    position: absolute;
    top: 2px;
    left: 2px;
    width: 20px;
    height: 20px;
    border-radius: 50%;
    background: #fff;
    box-shadow: var(--shadow-sm);
    transition: transform 0.15s ease;
  }

  .switch.on .knob {
    transform: translateX(18px);
  }
</style>
