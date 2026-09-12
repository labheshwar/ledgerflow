/**
 * Compatibility shim. The implementation moved to lib/http.ts when it grew
 * query-parameter building and 401 handling; the views still importing from
 * here are migrating to the typed per-resource modules in lib/api/, after
 * which this file goes away.
 */
export { apiFetch, ApiError, clearToken, getToken, setToken } from './http'
export type { RequestOptions } from './http'
