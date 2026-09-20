/**
 * Wraps the one native browser dialog this app uses, for the same reason
 * http.ts wraps `fetch` and `localStorage`: keeping raw DOM globals out of
 * .vue script blocks, where ESLint's no-undef (unlike in .ts files) has no
 * declared browser globals to check them against.
 */
export function confirmDialog(message: string): boolean {
  return window.confirm(message)
}
