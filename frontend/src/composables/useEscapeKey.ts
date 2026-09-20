import { onMounted, onUnmounted } from 'vue'

/**
 * Calls `handler` when Escape is pressed while this component is mounted.
 *
 * Kept out of the component itself: raw DOM globals (`document`, `window`)
 * are wrapped in .ts modules throughout this codebase rather than touched
 * directly from a .vue file's script block, the same reason http.ts wraps
 * `fetch`/`localStorage`. TypeScript's own DOM lib types check these calls;
 * a plain .vue file has no such check from ESLint's no-undef, so the
 * convention is what keeps a typo here from silently doing nothing.
 */
export function useEscapeKey(handler: () => void) {
  function onKeydown(event: KeyboardEvent) {
    if (event.key === 'Escape') handler()
  }

  onMounted(() => document.addEventListener('keydown', onKeydown))
  onUnmounted(() => document.removeEventListener('keydown', onKeydown))
}
