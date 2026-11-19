// Row shapes as stored in SQLite, the DTOs sent to the app, and the mapping between them.

import { toLocalPhone } from './lib/phone.ts'

export const ALLOW_KEYS = ['vibrate', 'sound', 'voice', 'image', 'message', 'question'] as const
export type AllowKey = (typeof ALLOW_KEYS)[number]

export const AVATARS = ['bunny', 'bear', 'cat', 'fox', 'panda', 'chick', 'frog', 'koala', 'unicorn', 'penguin', 'blossom', 'moon'] as const

export interface Settings {
  allow: Record<AllowKey, boolean>
}

export interface UserRow {
  id: string
  phone: string
  display_name: string
  avatar: string
  public_key: string | null
  paused: number
  paused_at: number | null
  settings: string
  created_at: number
  last_seen_at: number | null
}

export interface PairRow {
  id: string
  user_a: string
  user_b: string
  created_at: number
  ended_at: number | null
}

export interface InviteRow {
  id: string
  from_user_id: string
  to_phone: string
  status: 'pending' | 'accepted' | 'declined' | 'cancelled' | 'expired'
  created_at: number
  expires_at: number
  responded_at: number | null
}

export type EventStatus = 'sent' | 'delivered' | 'opened' | 'answered' | 'expired'

export interface EventRow {
  id: string
  pair_id: string
  sender_id: string
  recipient_id: string
  type: string
  meta: string
  enc: string | null
  reply_to: string | null
  status: EventStatus
  created_at: number
  delivered_at: number | null
  opened_at: number | null
  expires_at: number | null
}

export interface MediaRow {
  id: string
  pair_id: string
  owner_id: string
  kind: 'voice' | 'image'
  size: number
  enc_title: string | null
  duration_ms: number | null
  created_at: number
}

export function defaultSettings(): Settings {
  return { allow: Object.fromEntries(ALLOW_KEYS.map((k) => [k, true])) as Record<AllowKey, boolean> }
}

export function parseSettings(raw: string): Settings {
  const settings = defaultSettings()
  try {
    const parsed = JSON.parse(raw) as Partial<Settings>
    for (const key of ALLOW_KEYS) {
      const value = parsed.allow?.[key]
      if (typeof value === 'boolean') settings.allow[key] = value
    }
  } catch {
    // Corrupt settings fall back to defaults rather than locking the account.
  }
  return settings
}

export function userDto(row: UserRow) {
  return {
    id: row.id,
    phone: toLocalPhone(row.phone),
    displayName: row.display_name,
    avatar: row.avatar,
    publicKey: row.public_key,
    paused: row.paused === 1,
    settings: parseSettings(row.settings),
    createdAt: row.created_at,
  }
}

export function partnerDto(row: UserRow, online: boolean) {
  return {
    id: row.id,
    phone: toLocalPhone(row.phone),
    displayName: row.display_name,
    avatar: row.avatar,
    publicKey: row.public_key,
    paused: row.paused === 1,
    online: online && row.paused !== 1,
    lastSeenAt: row.last_seen_at,
    allow: parseSettings(row.settings).allow,
  }
}

export type EventDto = ReturnType<typeof eventDto>

export function eventDto(row: EventRow) {
  let meta: Record<string, unknown> = {}
  try {
    meta = JSON.parse(row.meta) as Record<string, unknown>
  } catch {
    // keep empty meta
  }
  return {
    id: row.id,
    type: row.type,
    senderId: row.sender_id,
    recipientId: row.recipient_id,
    meta,
    enc: row.enc,
    replyTo: row.reply_to,
    status: row.status,
    createdAt: row.created_at,
    deliveredAt: row.delivered_at,
    openedAt: row.opened_at,
    expiresAt: row.expires_at,
  }
}

export function mediaDto(row: MediaRow) {
  return {
    id: row.id,
    ownerId: row.owner_id,
    kind: row.kind,
    size: row.size,
    encTitle: row.enc_title,
    durationMs: row.duration_ms,
    createdAt: row.created_at,
  }
}
