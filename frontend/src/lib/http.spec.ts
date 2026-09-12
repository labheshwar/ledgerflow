import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { ApiError, apiFetch, setToken, setUnauthorizedHandler } from './http'

function jsonResponse(body: unknown, status = 200) {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  })
}

describe('apiFetch', () => {
  let fetchMock: ReturnType<typeof vi.fn>

  beforeEach(() => {
    localStorage.clear()
    fetchMock = vi.fn()
    vi.stubGlobal('fetch', fetchMock)
    setUnauthorizedHandler(() => {})
  })

  afterEach(() => {
    vi.unstubAllGlobals()
  })

  const calledUrl = () => String(fetchMock.mock.calls[0][0])
  const calledHeaders = () => fetchMock.mock.calls[0][1].headers as Headers

  it('prefixes the api path', async () => {
    fetchMock.mockResolvedValue(jsonResponse({ ok: true }))

    await apiFetch('/accounts')

    expect(calledUrl()).toBe('/api/accounts')
  })

  it('drops null, undefined and blank params so they never reach the query string', async () => {
    fetchMock.mockResolvedValue(jsonResponse({ ok: true }))

    await apiFetch('/accounts', {
      params: { q: '', type: null, page: 0, size: 25, sort: undefined, entityType: 'ACCOUNT' },
    })

    const url = calledUrl()
    expect(url).toContain('page=0')
    expect(url).toContain('size=25')
    expect(url).toContain('entityType=ACCOUNT')
    expect(url).not.toContain('q=')
    expect(url).not.toContain('type=')
    expect(url).not.toContain('sort=')
  })

  it('keeps page=0, which is a real page and not an absent value', async () => {
    fetchMock.mockResolvedValue(jsonResponse({ ok: true }))

    await apiFetch('/accounts', { params: { page: 0 } })

    expect(calledUrl()).toContain('page=0')
  })

  it('attaches the bearer token when one is stored', async () => {
    setToken('a.b.c')
    fetchMock.mockResolvedValue(jsonResponse({ ok: true }))

    await apiFetch('/accounts')

    expect(calledHeaders().get('Authorization')).toBe('Bearer a.b.c')
  })

  it('sends no Authorization header when signed out', async () => {
    fetchMock.mockResolvedValue(jsonResponse({ ok: true }))

    await apiFetch('/accounts')

    expect(calledHeaders().has('Authorization')).toBe(false)
  })

  it('surfaces the backend error code and message', async () => {
    fetchMock.mockResolvedValue(
      jsonResponse({ code: 'INVALID_SORT', message: "Cannot sort by 'version'." }, 400),
    )

    await expect(apiFetch('/accounts?sort=version')).rejects.toMatchObject({
      status: 400,
      code: 'INVALID_SORT',
      message: "Cannot sort by 'version'.",
    })
  })

  it('falls back to the status text when the error body is not JSON', async () => {
    fetchMock.mockResolvedValue(new Response('gateway blew up', { status: 502, statusText: 'Bad Gateway' }))

    await expect(apiFetch('/accounts')).rejects.toBeInstanceOf(ApiError)
  })

  it('notifies the unauthorized handler on a 401', async () => {
    const onUnauthorized = vi.fn()
    setUnauthorizedHandler(onUnauthorized)
    fetchMock.mockResolvedValue(jsonResponse({ code: 'UNAUTHENTICATED', message: 'nope' }, 401))

    await expect(apiFetch('/accounts')).rejects.toBeInstanceOf(ApiError)

    expect(onUnauthorized).toHaveBeenCalledOnce()
  })

  it('does not treat a failed login as an expired session', async () => {
    const onUnauthorized = vi.fn()
    setUnauthorizedHandler(onUnauthorized)
    fetchMock.mockResolvedValue(jsonResponse({ code: 'INVALID_CREDENTIALS', message: 'nope' }, 401))

    await expect(apiFetch('/auth/login', { method: 'POST', body: '{}' })).rejects.toBeInstanceOf(ApiError)

    expect(onUnauthorized).not.toHaveBeenCalled()
  })

  it('returns undefined for 204 rather than trying to parse a body', async () => {
    fetchMock.mockResolvedValue(new Response(null, { status: 204 }))

    await expect(apiFetch('/something')).resolves.toBeUndefined()
  })
})
