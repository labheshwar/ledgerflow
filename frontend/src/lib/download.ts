/**
 * Wraps the DOM mechanics of saving a fetched blob to disk, for the same
 * reason http.ts wraps fetch and localStorage: keeping raw browser globals
 * out of .vue script blocks, where ESLint's no-undef has no declared
 * browser globals to check them against.
 */
/** Pulls the first chosen file out of a file-input change event, keeping the raw DOM event type out of .vue script blocks. */
export function firstFileFrom(event: Event): File | null {
  const input = event.target as HTMLInputElement
  return input.files?.[0] ?? null
}

export function downloadBlob(blob: Blob, filename: string) {
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = filename
  link.click()
  // The click triggers the download asynchronously; revoking immediately
  // can race a browser that hasn't started reading the blob yet.
  setTimeout(() => URL.revokeObjectURL(url), 1000)
}
