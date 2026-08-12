<script>
  import { api } from "../api.js";

  let { onLoggedIn } = $props();

  let username = $state("");
  let password = $state("");
  let error = $state("");
  let submitting = $state(false);

  async function handleSubmit(e) {
    e.preventDefault();
    error = "";
    submitting = true;
    try {
      const user = await api.login(username, password);
      onLoggedIn(user);
    } catch (err) {
      error = err.message;
    } finally {
      submitting = false;
    }
  }
</script>

<div class="login-screen">
  <form class="login-card" onsubmit={handleSubmit}>
    <div class="icon">Chat<br />Relay</div>
    <div class="form">
      <h1>Sign into your Account</h1>
      <div class="field">
        <label for="username">Username</label>
        <input id="username" type="text" bind:value={username} autocomplete="username" />
      </div>
      <div class="field">
        <label for="password">Password</label>
        <input id="password" type="password" bind:value={password} autocomplete="current-password" />
      </div>
      {#if error}<p class="error-text">{error}</p>{/if}
      <div class="actions">
        <button type="submit" class="btn btn-primary" disabled={submitting}>Login</button>
      </div>
    </div>
  </form>
</div>

<style>
  .login-screen {
    height: 100vh;
    display: flex;
    align-items: center;
    justify-content: center;
    background: var(--sidebar-bg);
  }

  .login-card {
    display: flex;
    gap: 24px;
    border: 1px solid var(--card-border);
    border-radius: 8px;
    padding: 24px;
    background: var(--bg);
  }

  .icon {
    width: 120px;
    height: 120px;
    border: 1px solid var(--card-border);
    border-radius: 6px;
    display: flex;
    align-items: center;
    justify-content: center;
    text-align: center;
    font-weight: 700;
    flex-shrink: 0;
  }

  .form {
    width: 260px;
  }

  .form h1 {
    font-size: 1.05rem;
    margin: 0 0 16px;
  }

  .form input {
    width: 100%;
  }

  .actions {
    display: flex;
    justify-content: flex-end;
  }
</style>
