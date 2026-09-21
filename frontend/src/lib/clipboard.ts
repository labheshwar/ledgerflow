/** Wraps navigator.clipboard, for the same reason http.ts wraps fetch -- keeping the raw global out of .vue script blocks. */
export async function copyToClipboard(text: string): Promise<boolean> {
  try {
    await navigator.clipboard.writeText(text)
    return true
  } catch {
    return false
  }
}
