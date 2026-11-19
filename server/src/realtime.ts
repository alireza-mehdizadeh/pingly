import type { WebSocket } from 'ws'
import type { EventDto, EventStatus } from './models.ts'

/** Everything the server pushes down a socket. The app treats unknown `t` values as no-ops. */
export type ServerMessage =
  | { t: 'hello'; serverTime: number }
  | { t: 'event'; event: EventDto }
  | { t: 'status'; id: string; status: EventStatus; at: number }
  | { t: 'presence'; online: boolean; paused: boolean; lastSeenAt: number | null }
  | { t: 'sync'; reason: 'invite' | 'pair' | 'partner' }
  | { t: 'session_revoked' }
  | { t: 'pong'; serverTime: number }

export interface Connection {
  ws: WebSocket
  sessionId: string
  alive: boolean
}

/** Tracks the open sockets of every user and fans messages out to them. */
export class Hub {
  private readonly connections = new Map<string, Set<Connection>>()

  /** Returns true when this is the user's first open socket (they just came online). */
  add(userId: string, conn: Connection): boolean {
    let set = this.connections.get(userId)
    const first = !set || set.size === 0
    if (!set) {
      set = new Set()
      this.connections.set(userId, set)
    }
    set.add(conn)
    return first
  }

  /** Returns true when the user has no sockets left (they just went offline). */
  remove(userId: string, conn: Connection): boolean {
    const set = this.connections.get(userId)
    if (!set) return false
    set.delete(conn)
    if (set.size > 0) return false
    this.connections.delete(userId)
    return true
  }

  isOnline(userId: string): boolean {
    return (this.connections.get(userId)?.size ?? 0) > 0
  }

  send(userId: string, message: ServerMessage): number {
    const set = this.connections.get(userId)
    if (!set) return 0
    const payload = JSON.stringify(message)
    let sent = 0
    for (const conn of set) {
      if (conn.ws.readyState === conn.ws.OPEN) {
        conn.ws.send(payload)
        sent++
      }
    }
    return sent
  }

  /** Tells every socket opened with `sessionId` that it was revoked, then closes them. */
  closeSession(sessionId: string): void {
    for (const set of this.connections.values()) {
      for (const conn of set) {
        if (conn.sessionId !== sessionId) continue
        if (conn.ws.readyState === conn.ws.OPEN) conn.ws.send(JSON.stringify({ t: 'session_revoked' }))
        conn.ws.close(4001, 'session revoked')
      }
    }
  }

  /** Called on an interval: kills sockets that missed the previous ping, then pings the rest. */
  heartbeat(): void {
    for (const set of this.connections.values()) {
      for (const conn of set) {
        if (!conn.alive) {
          conn.ws.terminate()
          continue
        }
        conn.alive = false
        conn.ws.ping()
      }
    }
  }

  closeAll(): void {
    for (const set of this.connections.values()) for (const conn of set) conn.ws.terminate()
    this.connections.clear()
  }
}
