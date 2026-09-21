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
  const res = await rawFetch(path, options)

  // 204 (delete) and 202 (queued-for-async-processing, e.g. invoice email)
  // both come back with no body -- res.json() would throw on the empty
  // response.
  if (res.status === 204 || res.status === 202) return undefined as T
  return res.json() as Promise<T>
}

/**
 * For a binary response (a rendered PDF) rather than JSON. Shares every
 * other rule with apiFetch -- auth header, error taxonomy, the 401 hook --
 * so a failed download surfaces the same ApiError a failed JSON call would.
 */
export async function apiFetchBlob(path: string, options: RequestOptions = {}): Promise<Blob> {
  const res = await rawFetch(path, options)
  return res.blob()
}

async function rawFetch(path: string, options: RequestOptions): Promise<Response> {
  const { params, ...init } = options

  const token = getToken()
  const headers = new Headers(init.headers)
  if (token) headers.set('Authorization', `Bearer ${token}`)
  // FormData sets its own multipart boundary in the Content-Type it needs;
  // overwriting it here would strip the boundary and the server could no
  // longer parse the parts.
  if (init.body && !(init.body instanceof FormData) && !headers.has('Content-Type')) {
    headers.set('Content-Type', 'application/json')
  }

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

  return res
}
