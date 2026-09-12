import { computed, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'

export interface ListQueryState {
  page: number
  size: number
  sort: string
  q: string
  [key: string]: string | number
}

export interface UseListQueryOptions {
  /** Extra filters beyond page/size/sort/q, with their defaults. */
  filters?: Record<string, string>
  defaultSize?: number
  defaultSort?: string
  /** Milliseconds to wait before a typed search term reaches the URL. */
  debounceMs?: number
}

/**
 * Keeps list state in the URL so a filtered view can be bookmarked, shared,
 * and survive back/forward. It is the only place route query and fetch
 * params are translated into each other.
 */
export function useListQuery(options: UseListQueryOptions = {}) {
  const { filters = {}, defaultSize = 25, defaultSort = '', debounceMs = 300 } = options

  const route = useRoute()
  const router = useRouter()

  const readString = (key: string, fallback: string) => {
    const value = route.query[key]
    return typeof value === 'string' ? value : fallback
  }
  const readNumber = (key: string, fallback: number) => {
    const value = Number(route.query[key])
    return Number.isFinite(value) && value >= 0 ? value : fallback
  }

  const state = ref<ListQueryState>({
    page: readNumber('page', 0),
    size: readNumber('size', defaultSize),
    sort: readString('sort', defaultSort),
    q: readString('q', ''),
    ...Object.fromEntries(Object.entries(filters).map(([key, fallback]) => [key, readString(key, fallback)])),
  })

  // The search box updates `qInput` on every keystroke; only the debounced
  // value reaches the URL and therefore the query key.
  const qInput = ref(state.value.q)
  let debounceTimer: ReturnType<typeof setTimeout> | undefined
  watch(qInput, (value) => {
    clearTimeout(debounceTimer)
    debounceTimer = setTimeout(() => setFilter('q', value), debounceMs)
  })

  function syncToUrl() {
    const query: Record<string, string> = {}
    for (const [key, value] of Object.entries(state.value)) {
      const isDefault =
        (key === 'page' && value === 0) ||
        (key === 'size' && value === defaultSize) ||
        (key === 'sort' && value === defaultSort) ||
        value === '' ||
        value === filters[key]
      if (!isDefault) query[key] = String(value)
    }
    // replace, not push: typing in a search box should not fill up history
    router.replace({ query })
  }

  /** Any filter change resets to the first page, or you land on an empty page 3. */
  function setFilter(key: string, value: string) {
    if (state.value[key] === value) return
    state.value[key] = value
    state.value.page = 0
    syncToUrl()
  }

  function setPage(page: number) {
    state.value.page = page
    syncToUrl()
  }

  function setSort(sort: string) {
    state.value.sort = sort
    state.value.page = 0
    syncToUrl()
  }

  /** Back/forward changes the URL without going through the setters. */
  watch(
    () => route.query,
    (query) => {
      state.value.page = Number(query.page ?? 0)
      state.value.size = Number(query.size ?? defaultSize)
      state.value.sort = typeof query.sort === 'string' ? query.sort : defaultSort
      const nextQ = typeof query.q === 'string' ? query.q : ''
      state.value.q = nextQ
      if (qInput.value !== nextQ) qInput.value = nextQ
      for (const key of Object.keys(filters)) {
        state.value[key] = typeof query[key] === 'string' ? (query[key] as string) : filters[key]
      }
    },
  )

  /** Shaped for the API modules: blank values are dropped by the param builder. */
  const params = computed(() => ({ ...state.value }))

  return { state, params, qInput, setFilter, setPage, setSort }
}
