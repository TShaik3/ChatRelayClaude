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
  <div class="login-card">
    <div class="brand-panel">
      <div class="brand-mark" aria-hidden="true">
        <svg viewBox="0 0 24 24" width="26" height="26" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
          <path
            d="M21 11.5a8.4 8.4 0 0 1-8.4 8.4c-1.3 0-2.6-.3-3.7-.9L3 21l1.9-5.9a8.3 8.3 0 0 1-.9-3.8A8.4 8.4 0 1 1 21 11.5z"
          />
        </svg>
      </div>
      <div class="brand-name">ChatRelay</div>
      <p class="brand-tagline">Real-time messaging for your whole team, in one place.</p>
    </div>

    <form class="form-panel" onsubmit={handleSubmit}>
      <h1>Welcome back</h1>
      <p class="subtitle">Sign in to continue to your conversations.</p>
      <div class="field">
        <label for="username">Username</label>
        <input id="username" type="text" bind:value={username} autocomplete="username" />
      </div>
      <div class="field">
        <label for="password">Password</label>
        <input id="password" type="password" bind:value={password} autocomplete="current-password" />
      </div>
      {#if error}<p class="error-text">{error}</p>{/if}
      <button type="submit" class="btn btn-primary submit" disabled={submitting}>Login</button>
    </form>
  </div>
</div>

<style>
  .login-screen {
    height: 100vh;
    display: flex;
    align-items: center;
    justify-content: center;
    background: radial-gradient(circle at 20% 20%, #eef2ff, #f8fafc 55%);
    padding: 24px;
  }

  .login-card {
    display: flex;
    width: 100%;
    max-width: 720px;
    border-radius: var(--radius-lg);
    overflow: hidden;
    background: var(--bg);
    box-shadow: var(--shadow-lg);
    animation: pop-in 0.25s ease;
  }

  .brand-panel {
    flex: 0 0 42%;
    background: linear-gradient(160deg, var(--brand), #7c3aed);
    color: #fff;
    padding: 36px 32px;
    display: flex;
    flex-direction: column;
    justify-content: center;
    gap: 12px;
  }

  .brand-mark {
    width: 52px;
    height: 52px;
    border-radius: var(--radius-md);
    background: rgba(255, 255, 255, 0.15);
    display: flex;
    align-items: center;
    justify-content: center;
    margin-bottom: 8px;
  }

  .brand-name {
    font-size: 1.4rem;
    font-weight: 700;
    letter-spacing: -0.01em;
  }

  .brand-tagline {
    margin: 0;
    font-size: 0.9rem;
    line-height: 1.5;
    color: rgba(255, 255, 255, 0.85);
  }

  .form-panel {
    flex: 1;
    padding: 40px 36px;
    display: flex;
    flex-direction: column;
    justify-content: center;
    min-width: 0;
  }

  .form-panel h1 {
    font-size: 1.4rem;
    margin: 0 0 4px;
  }

  .subtitle {
    margin: 0 0 24px;
    color: var(--muted-text);
    font-size: 0.9rem;
  }

  .form-panel input {
    width: 100%;
  }

  .submit {
    width: 100%;
    padding: 10px 14px;
    font-size: 0.95rem;
    margin-top: 4px;
  }

  @media (max-width: 620px) {
    .login-card {
      flex-direction: column;
    }

    .brand-panel {
      padding: 28px;
    }
  }
</style>
