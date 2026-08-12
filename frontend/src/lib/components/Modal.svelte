<script>
  let { title, onClose, children } = $props();
</script>

<div class="overlay">
  <button class="backdrop" onclick={onClose} aria-label="Close dialog"></button>
  <div class="modal" role="dialog" aria-modal="true" tabindex="-1">
    <div class="modal-header">
      <h2>{title}</h2>
      <button class="close" onclick={onClose} aria-label="Close">×</button>
    </div>
    <div class="modal-body">
      {@render children()}
    </div>
  </div>
</div>

<style>
  .overlay {
    position: fixed;
    inset: 0;
    display: flex;
    align-items: center;
    justify-content: center;
    z-index: 100;
    padding: 16px;
  }

  .backdrop {
    position: absolute;
    inset: 0;
    background: rgba(15, 23, 42, 0.45);
    backdrop-filter: blur(2px);
    border: none;
    padding: 0;
    margin: 0;
    animation: fade-in 0.15s ease;
  }

  .modal {
    position: relative;
    background: var(--bg);
    border-radius: var(--radius-lg);
    min-width: 320px;
    max-width: 90vw;
    max-height: 85vh;
    overflow-y: auto;
    box-shadow: var(--shadow-lg);
    animation: pop-in 0.18s ease;
  }

  .modal-header {
    display: flex;
    align-items: center;
    justify-content: space-between;
    padding: 18px 22px;
    border-bottom: 1px solid var(--card-border);
  }

  .modal-header h2 {
    font-size: 1.1rem;
    margin: 0;
  }

  .close {
    width: 28px;
    height: 28px;
    border-radius: 50%;
    border: none;
    background: none;
    font-size: 1.3rem;
    line-height: 1;
    color: var(--muted-text);
    transition: background-color 0.12s ease;
  }

  .close:hover {
    background: var(--sidebar-bg);
  }

  .modal-body {
    padding: 22px;
  }
</style>
