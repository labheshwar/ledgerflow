import { apiFetch } from '@/lib/http'

/** The envelope shape RealtimeRelay republishes over Redis, unwrapped exactly as the outbox recorded it. */
export interface RealtimeMessage {
  sequence: number
  eventType: string
  orgId: number
  aggregateType: string
  aggregateId: string
  occurredAt: string
  payload: unknown
}

type Listener = (message: RealtimeMessage) => void

/**
 * One live connection per session, opened after login and closed on logout
 * -- never per-view, so navigating between pages does not tear down and
 * reopen the stream on every route change.
 *
 * A ticket stands in for the JWT: EventSource cannot set an Authorization
 * header, and the connection is otherwise unauthenticated at the transport
 * level. See RealtimeTicketService on the backend for why.
 */
class RealtimeConnection {
  private source: EventSource | null = null
  private listeners = new Set<Listener>()
  private lastSequence = 0
  private closed = true

  async connect() {
    if (!this.closed) return
    this.closed = false

    let ticket: string
    try {
      ;({ ticket } = await apiFetch<{ ticket: string }>('/events/ticket', { method: 'POST' }))
    } catch {
      // Not signed in, or the request failed -- the dashboard and detail
      // views still work from their own fetches, just without live push
      // until the next login or an explicit retry.
      this.closed = true
      return
    }
    if (this.closed) return // logout raced the ticket request

    const params = new URLSearchParams({ ticket })
    if (this.lastSequence > 0) params.set('lastEventId', String(this.lastSequence))
    const source = new EventSource(`/api/events/stream?${params.toString()}`)
    this.source = source

    source.addEventListener('message', (event) => this.handle(event as MessageEvent))
    // Named events (event: transaction.posted) arrive as their own type in
    // the browser's EventSource API, not as generic "message" events --
    // every eventType this stream ever sends needs its own listener here.
    source.addEventListener('transaction.posted', (event) => this.handle(event as MessageEvent))
  }

  private handle(event: MessageEvent) {
    let message: RealtimeMessage
    try {
      message = JSON.parse(event.data)
    } catch {
      return
    }
    if (message.sequence > this.lastSequence) {
      this.lastSequence = message.sequence
    }
    for (const listener of this.listeners) listener(message)
  }

  subscribe(listener: Listener): () => void {
    this.listeners.add(listener)
    return () => this.listeners.delete(listener)
  }

  close() {
    this.closed = true
    this.source?.close()
    this.source = null
    this.lastSequence = 0
  }
}

export const realtime = new RealtimeConnection()
