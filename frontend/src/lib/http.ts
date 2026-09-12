const TOKEN_KEY = 'ledgerflow_token'

export function getToken(): string | null {
  try {
    return localStorage.getItem(TOKEN_KEY)
  } catch {
    return null
  }
}

export function setToken(token: string) {
  try {
    localStorage.setItem(TOKEN_KEY, token)
  } catch {
    // localStorage unavailable — the session just won't survive a reload
  }
}

export function clearToken() {
  try {
    localStorage.removeItem(TOKEN_KEY)
  } catch {
    // ignore
  }
}

export class ApiError extends Error {
  status: number
  /** Stable machine-readable code from the backend's error taxonomy. */
  code: string

  constructor(status: number, code: string, message: string) {
    super(message)
    this.status = status
    this.code = code
  }
}

/**
 * Called when the server rejects our token. Wired to the auth store in
 * main.ts rather than imported here, because the store imports this module
 * to perform the login request and the cycle would be unresolvable.
 */
let onUnauthorized: (() => void) | null = null

export function setUnauthorizedHandler(handler: () => void) {
  onUnauthorized = handler
}

export interface RequestOptions extends RequestInit {
  /** Query parameters; null and undefined entries are dropped. */
  params?: Record<string, string | number | boolean | null | undefined>
}

function buildUrl(path: string, params?: RequestOptions['params']): string {
  const url = `/api${path}`
  if (!params) return url

  const search = new URLSearchParams()
  for (const [key, value] of Object.entries(params)) {
    if (value !== null && value !== undefined && value !== '') {
      search.append(key, String(value))
    }
  }

  const qs = search.toString()
  return qs ? `${url}?${qs}` : url
}

export async function apiFetch<T>(path: string, options: RequestOptions = {}): Promise<T> {
  const { params, ...init } = options

  const token = getToken()
  const headers = new Headers(init.headers)
  if (token) headers.set('Authorization', `Bearer ${token}`)
  if (init.body && !headers.has('Content-Type')) headers.set('Content-Type', 'application/json')

  const res = await fetch(buildUrl(path, params), { ...init, headers })

  if (!res.ok) {
    let code = 'UNKNOWN'
    let message = res.statusText
    try {
      const body = await res.json()
      if (body?.message) message = body.message
      if (body?.code) code = body.code
    } catch {
      // no JSON error body — fall back to statusText
    }

    // A 401 on anything other than the login call itself means the token is
    // gone or expired; sitting on a dead session shows empty screens instead
    // of saying so.
    if (res.status === 401 && !path.startsWith('/auth/')) {
      onUnauthorized?.()
    }

    throw new ApiError(res.status, code, message)
  }

  if (res.status === 204) return undefined as T
  return res.json() as Promise<T>
}
