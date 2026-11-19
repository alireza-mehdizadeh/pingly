import type { Ctx } from '../context.ts'
import { badRequest, conflict, notFound } from '../lib/errors.ts'
import {
  ALLOW_KEYS,
  AVATARS,
  parseSettings,
  partnerDto,
  userDto,
  type PairRow,
  type UserRow,
} from '../models.ts'
import { listInvites } from './pairing.ts'

const PUBLIC_KEY_PATTERN = /^[A-Za-z0-9+/=_-]{40,400}$/

export function getUser(ctx: Ctx, userId: string): UserRow {
  const row = ctx.db.prepare('SELECT * FROM users WHERE id = ?').get(userId) as UserRow | undefined
  if (!row) throw notFound('user_not_found')
  return row
}

export function activePair(ctx: Ctx, userId: string): PairRow | undefined {
  return ctx.db
    .prepare('SELECT * FROM pairs WHERE ended_at IS NULL AND (user_a = ? OR user_b = ?)')
    .get(userId, userId) as PairRow | undefined
}

export function requirePair(ctx: Ctx, userId: string): PairRow {
  const pair = activePair(ctx, userId)
  if (!pair) throw conflict('not_paired', 'Connect with your partner first')
  return pair
}

export function partnerIdOf(pair: PairRow, userId: string): string {
  return pair.user_a === userId ? pair.user_b : pair.user_a
}

/** Pushes the user's presence to their partner, if they have one. */
export function broadcastPresence(ctx: Ctx, userId: string): void {
  const pair = activePair(ctx, userId)
  if (!pair) return
  const user = getUser(ctx, userId)
  ctx.hub.send(partnerIdOf(pair, userId), {
    t: 'presence',
    online: ctx.hub.isOnline(userId) && user.paused !== 1,
    paused: user.paused === 1,
    lastSeenAt: user.last_seen_at,
  })
}

/** Everything the app needs to render after launch, in one round trip. */
export function buildMe(ctx: Ctx, userId: string) {
  const user = getUser(ctx, userId)
  const pair = activePair(ctx, userId)
  const partner = pair ? getUser(ctx, partnerIdOf(pair, userId)) : undefined
  return {
    user: userDto(user),
    pair: pair ? { id: pair.id, createdAt: pair.created_at } : null,
    partner: partner ? partnerDto(partner, ctx.hub.isOnline(partner.id)) : null,
    invites: listInvites(ctx, userId),
    serverTime: ctx.now(),
  }
}

export interface ProfilePatch {
  displayName?: unknown
  avatar?: unknown
  publicKey?: unknown
  settings?: unknown
}

export function updateMe(ctx: Ctx, userId: string, patch: ProfilePatch) {
  const user = getUser(ctx, userId)
  const updates: Record<string, string> = {}

  if (patch.displayName !== undefined) {
    const name = typeof patch.displayName === 'string' ? patch.displayName.trim().replace(/\s+/g, ' ') : ''
    if (name.length < 1 || name.length > 32) throw badRequest('invalid_name', 'Name must be 1-32 characters')
    updates.display_name = name
  }
  if (patch.avatar !== undefined) {
    if (typeof patch.avatar !== 'string' || !(AVATARS as readonly string[]).includes(patch.avatar)) {
      throw badRequest('invalid_avatar')
    }
    updates.avatar = patch.avatar
  }
  if (patch.publicKey !== undefined) {
    if (typeof patch.publicKey !== 'string' || !PUBLIC_KEY_PATTERN.test(patch.publicKey)) {
      throw badRequest('invalid_public_key')
    }
    updates.public_key = patch.publicKey
  }
  if (patch.settings !== undefined) {
    const current = parseSettings(user.settings)
    const allow = (patch.settings as { allow?: Record<string, unknown> } | null)?.allow ?? {}
    for (const key of ALLOW_KEYS) {
      if (typeof allow[key] === 'boolean') current.allow[key] = allow[key] as boolean
    }
    updates.settings = JSON.stringify(current)
  }

  const columns = Object.keys(updates)
  if (columns.length > 0) {
    ctx.db
      .prepare(`UPDATE users SET ${columns.map((c) => `${c} = ?`).join(', ')} WHERE id = ?`)
      .run(...columns.map((c) => updates[c]!), userId)
    const pair = activePair(ctx, userId)
    if (pair) ctx.hub.send(partnerIdOf(pair, userId), { t: 'sync', reason: 'partner' })
  }
  return userDto(getUser(ctx, userId))
}

/** The device owner's explicit Pause switch — the only way Pingly stops receiving. */
export function setPaused(ctx: Ctx, userId: string, paused: boolean) {
  ctx.db.prepare('UPDATE users SET paused = ?, paused_at = ? WHERE id = ?').run(paused ? 1 : 0, paused ? ctx.now() : null, userId)
  broadcastPresence(ctx, userId)
  return userDto(getUser(ctx, userId))
}

export function touchLastSeen(ctx: Ctx, userId: string): void {
  ctx.db.prepare('UPDATE users SET last_seen_at = ? WHERE id = ?').run(ctx.now(), userId)
}
