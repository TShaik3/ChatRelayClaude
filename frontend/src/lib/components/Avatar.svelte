<script>
  let { name = "", size = 32 } = $props();

  const PALETTE = ["#4f46e5", "#0ea5e9", "#10b981", "#f59e0b", "#e11d48", "#8b5cf6", "#ec4899", "#0d9488"];

  function initials(value) {
    const parts = value.trim().split(/\s+/).filter(Boolean);
    if (parts.length === 0) return "?";
    if (parts.length === 1) return parts[0][0].toUpperCase();
    return (parts[0][0] + parts[parts.length - 1][0]).toUpperCase();
  }

  function colorFor(value) {
    let hash = 0;
    for (let i = 0; i < value.length; i++) {
      hash = (hash << 5) - hash + value.charCodeAt(i);
      hash |= 0;
    }
    return PALETTE[Math.abs(hash) % PALETTE.length];
  }

  const label = $derived(initials(name));
  const background = $derived(colorFor(name || "?"));
</script>

<span
  class="avatar"
  style="width: {size}px; height: {size}px; min-width: {size}px; font-size: {size * 0.4}px; background: {background}"
  aria-hidden="true"
>
  {label}
</span>

<style>
  .avatar {
    border-radius: 50%;
    color: #fff;
    display: inline-flex;
    align-items: center;
    justify-content: center;
    font-weight: 600;
    letter-spacing: 0.02em;
    flex-shrink: 0;
    user-select: none;
  }
</style>
