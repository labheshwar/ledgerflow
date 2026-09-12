import { defineStore } from 'pinia'
import { apiFetch, clearToken, getToken, setToken } from '@/lib/http'

interface JwtPayload {
  sub: string
  role: 'ADMIN' | 'VIEWER'
  exp: number
}

function decodeToken(token: string): JwtPayload | null {
  try {
    const payload = token.split('.')[1]
    const json = decodeURIComponent(
      atob(payload.replace(/-/g, '+').replace(/_/g, '/'))
        .split('')
        .map((c) => '%' + c.charCodeAt(0).toString(16).padStart(2, '0'))
        .join(''),
    )
    return JSON.parse(json)
  } catch {
    return null
  }
}

export const useAuthStore = defineStore('auth', {
  state: () => ({
    token: getToken() as string | null,
  }),
  getters: {
    payload(state): JwtPayload | null {
      return state.token ? decodeToken(state.token) : null
    },
    /** Seconds since the epoch, matching the JWT's own units. */
    expiresAt(): number | null {
      return this.payload?.exp ?? null
    },
    isExpired(): boolean {
      const exp = this.expiresAt
      return exp !== null && exp * 1000 <= Date.now()
    },
    /**
     * An expired token is treated as no token, so the router redirects to
     * login instead of letting every request 401 behind a blank screen.
     */
    isAuthenticated(state): boolean {
      return !!state.token && !this.isExpired
    },
    username(): string {
      return this.payload?.sub ?? ''
    },
    role(): 'ADMIN' | 'VIEWER' {
      return this.payload?.role ?? 'VIEWER'
    },
    isAdmin(): boolean {
      return this.role === 'ADMIN'
    },
    initials(): string {
      return this.username ? this.username.slice(0, 2).toUpperCase() : ''
    },
  },
  actions: {
    async login(username: string, password: string) {
      const response = await apiFetch<{ token: string }>('/auth/login', {
        method: 'POST',
        body: JSON.stringify({ username, password }),
      })
      this.token = response.token
      setToken(response.token)
    },
    logout() {
      this.token = null
      clearToken()
    },
  },
})
