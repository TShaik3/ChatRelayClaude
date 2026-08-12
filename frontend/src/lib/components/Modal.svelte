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
  }

  .backdrop {
    position: absolute;
    inset: 0;
    background: rgba(0, 0, 0, 0.35);
    border: none;
    padding: 0;
    margin: 0;
  }

  .modal {
    position: relative;
    background: var(--bg);
    border-radius: 8px;
    min-width: 320px;
    max-width: 90vw;
    max-height: 85vh;
    overflow-y: auto;
    box-shadow: 0 10px 30px rgba(0, 0, 0, 0.2);
  }

  .modal-header {
    display: flex;
    align-items: center;
    justify-content: space-between;
    padding: 16px 20px;
    border-bottom: 1px solid var(--card-border);
  }

  .modal-header h2 {
    font-size: 1.1rem;
    margin: 0;
  }

  .close {
    border: none;
    background: none;
    font-size: 1.3rem;
    line-height: 1;
    color: var(--muted-text);
  }

  .modal-body {
    padding: 20px;
  }
</style>
