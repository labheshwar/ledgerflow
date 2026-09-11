<template>
  <div class="wrap theme-light">
    <div class="card">
      <div class="mark">LF</div>
      <h1 class="serif">Sign in to LedgerFlow</h1>
      <div class="sub">Placeholder — wired up in the next milestone.</div>
    </div>
  </div>
</template>

<style scoped>
.wrap {
  width: 100%;
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
}
.card {
  width: 360px;
  background: var(--raised);
  border: 1px solid var(--line);
  border-radius: 8px;
  padding: 32px;
  box-shadow: 0 2px 8px rgba(27, 36, 32, 0.06), 0 1px 0 rgba(27, 36, 32, 0.02);
}
.mark {
  width: 34px;
  height: 34px;
  border-radius: 8px;
  background: var(--green);
  color: var(--paper);
  display: flex;
  align-items: center;
  justify-content: center;
  font-family: 'IBM Plex Mono', monospace;
  font-size: 15px;
  font-weight: 600;
  margin-bottom: 16px;
}
.card h1 {
  font-size: 21px;
  font-weight: 600;
  margin: 0 0 4px;
}
.sub {
  color: var(--ink-soft);
  font-size: 12.5px;
}
</style>
