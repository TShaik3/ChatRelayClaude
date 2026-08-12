<script>
  import { onMount } from "svelte";
  import { currentUser, users, chats, resetSession } from "./lib/stores.js";
  import { api } from "./lib/api.js";
  import { connect, disconnect } from "./lib/ws.js";
  import Login from "./lib/components/Login.svelte";
  import MainLayout from "./lib/components/MainLayout.svelte";

  let checkingSession = $state(true);

  onMount(async () => {
    try {
      const me = await api.me();
      currentUser.set(me);
      await loadInitialData();
      connect();
    } catch {
      // No valid session cookie -- fall through to the login screen.
    } finally {
      checkingSession = false;
    }
  });

  async function loadInitialData() {
    const [userList, chatList] = await Promise.all([api.getUsers(), api.getChats()]);
    users.set(userList);
    chats.set(chatList);
  }

  async function handleLoggedIn(user) {
    currentUser.set(user);
    await loadInitialData();
    connect();
  }

  async function handleLogout() {
    try {
      await api.logout();
    } catch {
      // best-effort -- clear local state regardless
    }
    disconnect();
    resetSession();
  }
</script>

{#if checkingSession}
  <div class="loading">Loading…</div>
{:else if $currentUser}
  <MainLayout onLogout={handleLogout} />
{:else}
  <Login onLoggedIn={handleLoggedIn} />
{/if}

<style>
  .loading {
    display: flex;
    align-items: center;
    justify-content: center;
    height: 100vh;
    color: var(--muted-text);
  }
</style>
