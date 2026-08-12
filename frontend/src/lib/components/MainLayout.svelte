<script>
  import Sidebar from "./Sidebar.svelte";
  import ChatArea from "./ChatArea.svelte";
  import CreateChatDialog from "./CreateChatDialog.svelte";
  import CreateUserDialog from "./CreateUserDialog.svelte";
  import EditUserDialog from "./EditUserDialog.svelte";
  import AccountSettingsDialog from "./AccountSettingsDialog.svelte";

  let { onLogout } = $props();

  let showCreateChat = $state(false);
  let showCreateUser = $state(false);
  let editingUser = $state(null);
  let showAccountSettings = $state(false);
</script>

<div class="layout">
  <Sidebar
    onNewChat={() => (showCreateChat = true)}
    onNewUser={() => (showCreateUser = true)}
    onEditUser={(user) => (editingUser = user)}
    onOpenAccountSettings={() => (showAccountSettings = true)}
    {onLogout}
  />
  <ChatArea />
</div>

{#if showCreateChat}
  <CreateChatDialog onClose={() => (showCreateChat = false)} />
{/if}
{#if showCreateUser}
  <CreateUserDialog onClose={() => (showCreateUser = false)} />
{/if}
{#if editingUser}
  <EditUserDialog user={editingUser} onClose={() => (editingUser = null)} />
{/if}
{#if showAccountSettings}
  <AccountSettingsDialog onClose={() => (showAccountSettings = false)} />
{/if}

<style>
  .layout {
    display: flex;
    height: 100vh;
  }
</style>
