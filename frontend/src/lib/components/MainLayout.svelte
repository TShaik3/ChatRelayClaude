<script>
  import Sidebar from "./Sidebar.svelte";
  import ChatArea from "./ChatArea.svelte";
  import CreateChatDialog from "./CreateChatDialog.svelte";
  import CreateUserDialog from "./CreateUserDialog.svelte";
  import EditUserDialog from "./EditUserDialog.svelte";

  let { onLogout } = $props();

  let showCreateChat = $state(false);
  let showCreateUser = $state(false);
  let editingUser = $state(null);
</script>

<div class="layout">
  <Sidebar
    onNewChat={() => (showCreateChat = true)}
    onNewUser={() => (showCreateUser = true)}
    onEditUser={(user) => (editingUser = user)}
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

<style>
  .layout {
    display: flex;
    height: 100vh;
  }
</style>
