<script setup lang="ts">
import { useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { useToastStore } from '@/stores/toast'

const auth = useAuthStore()
const toasts = useToastStore()
const router = useRouter()

function signOut() {
  auth.logout()
  router.push({ name: 'login' })
  toasts.success('Signed out')
}

const navItems = [
  {
    to: '/dashboard',
    label: 'Dashboard',
    path: '<rect x="2" y="2" width="5" height="5" rx="1"></rect><rect x="9" y="2" width="5" height="5" rx="1"></rect><rect x="2" y="9" width="5" height="5" rx="1"></rect><rect x="9" y="9" width="5" height="5" rx="1"></rect>',
  },
  {
    to: '/accounts',
    label: 'Accounts',
    path: '<rect x="2" y="3" width="12" height="10" rx="1.5"></rect><path d="M2 6.7h12"></path><path d="M5 9.7h3"></path>',
  },
  {
    to: '/transactions',
    label: 'Transactions',
    path: '<path d="M3 5.2h8"></path><path d="M8.2 2.4l2.8 2.8-2.8 2.8"></path><path d="M13 10.8H5"></path><path d="M7.8 13.6L5 10.8l2.8-2.8"></path>',
  },
  {
    to: '/reconciliation',
    label: 'Reconciliation',
    path: '<circle cx="8" cy="8" r="6"></circle><path d="M5.3 8.2l1.8 1.8L10.7 6"></path>',
  },
  {
    to: '/audit-log',
    label: 'Audit Log',
    path: '<circle cx="8" cy="8" r="6"></circle><path d="M8 5.2v3l2 1.1"></path>',
  },
]
</script>

<template>
  <div class="app theme-light">
    <aside class="sidebar">
      <div class="brand">
        <div class="mark">LF</div>
        <div class="name">LedgerFlow</div>
      </div>

      <RouterLink
        v-for="item in navItems"
        :key="item.to"
        :to="item.to"
        class="nav-item"
        active-class="active"
      >
        <!-- eslint-disable-next-line vue/no-v-html -- item.path is a hardcoded SVG constant in this file, never user input -->
        <svg viewBox="0 0 16 16" v-html="item.path"></svg>
        {{ item.label }}
      </RouterLink>

      <div class="spacer"></div>
      <div class="role-box">
        <div class="avatar">{{ auth.initials }}</div>
        <div>
          <div class="who">{{ auth.username }}</div>
          <div class="role-tag">{{ auth.role }}</div>
        </div>
      </div>
      <button class="sign-out" @click="signOut">Sign out</button>
    </aside>

    <div class="main">
      <div class="topbar">
        <div>
          <h1><slot name="title" /></h1>
          <div class="sub"><slot name="sub" /></div>
        </div>
        <div class="actions"><slot name="actions" /></div>
      </div>
      <div class="content"><slot /></div>
    </div>
  </div>
</template>

<style scoped>
.app {
  display: flex;
  width: 100%;
  height: 100%;
}

.sidebar {
  width: 212px;
  flex: none;
  display: flex;
  flex-direction: column;
  background: var(--panel);
  border-right: 1px solid var(--line);
  padding: 18px 12px;
  gap: 2px;
}
.brand {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 0 6px 16px;
  margin-bottom: 6px;
  border-bottom: 1px solid var(--line);
}
.brand .mark {
  width: 22px;
  height: 22px;
  border-radius: 5px;
  background: var(--green);
  color: var(--paper);
  display: flex;
  align-items: center;
  justify-content: center;
  font-family: 'IBM Plex Mono', monospace;
  font-size: 11px;
  font-weight: 600;
}
.brand .name {
  font-family: 'Source Serif 4', serif;
  font-weight: 600;
  font-size: 15px;
}
.nav-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 8px 10px;
  border-radius: 5px;
  color: var(--ink-soft);
  font-size: 13px;
  font-weight: 500;
}
.nav-item:hover {
  background: var(--raised);
  color: var(--ink);
}
.nav-item svg {
  width: 15px;
  height: 15px;
  flex: none;
  stroke: currentColor;
  fill: none;
  stroke-width: 1.6;
  stroke-linecap: round;
  stroke-linejoin: round;
}
.nav-item.active {
  background: var(--raised);
  color: var(--ink);
  box-shadow: inset 0 0 0 1px var(--line);
}
.sidebar .spacer {
  flex: 1;
}
.role-box {
  display: flex;
  align-items: center;
  gap: 8px;
  padding-top: 14px;
  margin-top: 8px;
  border-top: 1px solid var(--line);
}
.avatar {
  width: 26px;
  height: 26px;
  border-radius: 50%;
  background: var(--green-soft);
  color: var(--green);
  display: flex;
  align-items: center;
  justify-content: center;
  font-family: 'IBM Plex Mono', monospace;
  font-size: 11px;
  font-weight: 600;
  flex: none;
}
.role-box .who {
  font-size: 12.5px;
  line-height: 1.3;
}
.role-box .role-tag {
  font-family: 'IBM Plex Mono', monospace;
  font-size: 10px;
  letter-spacing: 0.04em;
  color: var(--ink-faint);
}
.sign-out {
  background: none;
  border: none;
  padding: 6px 10px 0;
  font-family: 'IBM Plex Sans', sans-serif;
  font-size: 11.5px;
  color: var(--ink-faint);
  text-align: left;
}
.sign-out:hover {
  color: var(--green);
}

.main {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  height: 100%;
  overflow: hidden;
}
.topbar {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  padding: 22px 32px 16px;
  border-bottom: 1px solid var(--line);
  flex: none;
  gap: 16px;
}
.topbar h1 {
  font-family: 'Source Serif 4', serif;
  font-size: 22px;
  font-weight: 600;
  margin: 0;
  display: flex;
  align-items: center;
  gap: 10px;
}
.topbar .sub {
  color: var(--ink-soft);
  font-size: 12.5px;
  margin-top: 4px;
}
.content {
  flex: 1;
  overflow: auto;
  padding: 24px 32px 40px;
}
</style>
