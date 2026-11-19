// Partner interactions. Every action is an "event" row: the sender creates it over REST, the
// recipient's phone receives it over its WebSocket (immediately, or as soon as it reconnects),
// and status updates (delivered → opened/answered) flow back to the sender live.

import type { Ctx } from '../context.ts'
import { transaction } from '../db.ts'
import { newId } from '../lib/crypto.ts'
import { badRequest, conflict, forbidden, notFound } from '../lib/errors.ts'
import { eventDto, parseSettings, type AllowKey, type EventRow, type EventStatus, type MediaRow } from '../models.ts'
import { getUser, partnerIdOf, requirePair } from './users.ts'

export const EVENT_TYPES = ['ping', 'vibrate', 'sound', 'voice', 'image', 'message', 'question', 'answer'] as const
export type EventType = (typeof EVENT_TYPES)[number]

export const VIBRATION_PATTERNS = ['heartbeat', 'hug', 'tickle', 'knock', 'butterfly', 'purr'] as const
export const SOUNDS = ['ping', 'chime', 'musicbox', 'bubbles', 'bells', 'heartbeat', 'sparkle', 'harp'] as const
export const MESSAGE_STYLES = ['blush', 'lavender', 'sunset', 'mint', 'night'] as const
export const QUESTION_KINDS = ['choice', 'text'] as const

/** Instant "do it now" actions lose their meaning if they arrive hours late. */
const EPHEMERAL: ReadonlySet<EventType> = new Set(['ping', 'vibrate', 'sound', 'voice'])
export const EPHEMERAL_TTL_MS = 10 * 60_000
/** Ciphertext limits (base64). */
const MAX_ENC = 16_384
const NEEDS_ENC: ReadonlySet<EventType> = new Set(['message', 'question', 'answer'])
const ALLOWS_ENC: ReadonlySet<EventType> = new Set(['message', 'question', 'answer', 'image'])

export interface SendEventInput {
  type?: unknown
  meta?: unknown
  enc?: unknown
  replyTo?: unknown
}

function oneOf<T extends string>(value: unknown, allowed: readonly T[], code: string): T {
  if (typeof value !== 'string' || !(allowed as readonly string[]).includes(value)) throw badRequest(code)
  return value as T
}

function requirePairMedia(ctx: Ctx, pairId: string, mediaId: unknown, kind: MediaRow['kind']): string {
  if (typeof mediaId !== 'string') throw badRequest('invalid_media')
  const row = ctx.db.prepare('SELECT id FROM media WHERE id = ? AND pair_id = ? AND kind = ?').get(mediaId, pairId, kind)
  if (!row) throw notFound('media_not_found')
  return mediaId
}

/** Keeps only the meta keys each type understands, validated. */
function cleanMeta(ctx: Ctx, pairId: string, type: EventType, raw: Record<string, unknown>): Record<string, unknown> {
  switch (type) {
    case 'vibrate':
      return { pattern: oneOf(raw.pattern, VIBRATION_PATTERNS, 'invalid_pattern') }
    case 'sound':
      return { sound: oneOf(raw.sound, SOUNDS, 'invalid_sound') }
    case 'voice':
      return { mediaId: requirePairMedia(ctx, pairId, raw.mediaId, 'voice') }
    case 'image':
      return { mediaId: requirePairMedia(ctx, pairId, raw.mediaId, 'image') }
    case 'message':
      return { style: oneOf(raw.style ?? 'blush', MESSAGE_STYLES, 'invalid_style') }
    case 'question':
      return { kind: oneOf(raw.kind ?? 'choice', QUESTION_KINDS, 'invalid_question_kind') }
    case 'ping':
    case 'answer':
      return {}
  }
}

export function sendEvent(ctx: Ctx, senderId: string, input: SendEventInput) {
  const type = oneOf(input.type, EVENT_TYPES, 'invalid_type')
  const now = ctx.now()
  ctx.limiters.eventsPerUser.consume(senderId, now)

  const pair = requirePair(ctx, senderId)
  const recipientId = partnerIdOf(pair, senderId)
  const recipient = getUser(ctx, recipientId)

  const enc = typeof input.enc === 'string' && input.enc.length > 0 ? input.enc : null
  if (NEEDS_ENC.has(type) && !enc) throw badRequest('missing_content')
  if (enc && (!ALLOWS_ENC.has(type) || enc.length > MAX_ENC)) throw badRequest('invalid_content')

  const metaInput = input.meta && typeof input.meta === 'object' ? (input.meta as Record<string, unknown>) : {}
  const meta = cleanMeta(ctx, pair.id, type, metaInput)

  // Consent, enforced on the server too: a paused partner receives nothing, and each kind of
  // action can be switched off by the person on the receiving end.
  if (type !== 'answer') {
    if (recipient.paused === 1) throw conflict('partner_paused', 'Your partner has paused Pingly')
    const allow = parseSettings(recipient.settings).allow
    if (type !== 'ping' && !allow[type as AllowKey]) throw forbidden('not_allowed', 'Your partner turned this off')
  }

  let question: EventRow | undefined
  if (type === 'answer') {
    if (typeof input.replyTo !== 'string') throw badRequest('missing_reply_to')
    question = ctx.db
      .prepare(`SELECT * FROM events WHERE id = ? AND pair_id = ? AND type = 'question' AND recipient_id = ?`)
      .get(input.replyTo, pair.id, senderId) as EventRow | undefined
    if (!question) throw notFound('question_not_found')
    if (question.status === 'answered') throw conflict('already_answered')
  }

  const id = newId()
  const expiresAt = EPHEMERAL.has(type) ? now + EPHEMERAL_TTL_MS : null
  transaction(ctx.db, () => {
    ctx.db
      .prepare(
        `INSERT INTO events (id, pair_id, sender_id, recipient_id, type, meta, enc, reply_to, status, created_at, expires_at)
         VALUES (?, ?, ?, ?, ?, ?, ?, ?, 'sent', ?, ?)`,
      )
      .run(id, pair.id, senderId, recipientId, type, JSON.stringify(meta), enc, question?.id ?? null, now, expiresAt)
    if (question) {
      ctx.db.prepare(`UPDATE events SET status = 'answered', opened_at = COALESCE(opened_at, ?) WHERE id = ?`).run(now, question.id)
    }
  })

  const event = eventDto(ctx.db.prepare('SELECT * FROM events WHERE id = ?').get(id) as unknown as EventRow)
  ctx.hub.send(recipientId, { t: 'event', event })
  if (question) ctx.hub.send(question.sender_id, { t: 'status', id: question.id, status: 'answered', at: now })
  return event
}

function updateStatus(ctx: Ctx, recipientId: string, eventId: string, to: 'delivered' | 'opened'): boolean {
  const now = ctx.now()
  const row = ctx.db
    .prepare('SELECT * FROM events WHERE id = ? AND recipient_id = ?')
    .get(eventId, recipientId) as EventRow | undefined
  if (!row) return false

  const from: EventStatus[] = to === 'delivered' ? ['sent'] : ['sent', 'delivered']
  if (!from.includes(row.status)) return false

  if (to === 'delivered') {
    ctx.db.prepare(`UPDATE events SET status = 'delivered', delivered_at = ? WHERE id = ?`).run(now, eventId)
  } else {
    ctx.db
      .prepare(`UPDATE events SET status = 'opened', opened_at = ?, delivered_at = COALESCE(delivered_at, ?) WHERE id = ?`)
      .run(now, now, eventId)
  }
  ctx.hub.send(row.sender_id, { t: 'status', id: eventId, status: to, at: now })
  return true
}

/** The recipient's device confirms it received (and handled) the event. */
export function acknowledge(ctx: Ctx, recipientId: string, eventId: string): boolean {
  return updateStatus(ctx, recipientId, eventId, 'delivered')
}

/** The recipient actually saw / heard it (full-screen shown, voice played, message opened). */
export function markOpened(ctx: Ctx, recipientId: string, eventId: string): boolean {
  return updateStatus(ctx, recipientId, eventId, 'opened')
}

/** Events waiting for a device that was offline, oldest first. */
export function pendingFor(ctx: Ctx, userId: string) {
  const rows = ctx.db
    .prepare(
      `SELECT * FROM events WHERE recipient_id = ? AND status = 'sent' AND (expires_at IS NULL OR expires_at > ?)
       ORDER BY created_at ASC LIMIT 200`,
    )
    .all(userId, ctx.now()) as unknown as EventRow[]
  return rows.map(eventDto)
}

export function listEvents(ctx: Ctx, userId: string, beforeRaw: unknown, limitRaw: unknown) {
  const pair = requirePair(ctx, userId)
  const before = Number(beforeRaw)
  const limit = Math.min(Math.max(Number(limitRaw) || 50, 1), 100)
  const rows = (
    Number.isFinite(before) && before > 0
      ? ctx.db
          .prepare('SELECT * FROM events WHERE pair_id = ? AND created_at < ? ORDER BY created_at DESC LIMIT ?')
          .all(pair.id, before, limit)
      : ctx.db.prepare('SELECT * FROM events WHERE pair_id = ? ORDER BY created_at DESC LIMIT ?').all(pair.id, limit)
  ) as unknown as EventRow[]
  return rows.map(eventDto)
}

/** Marks instant actions that never reached an offline phone as expired, and tells the senders. */
export function expireStaleEvents(ctx: Ctx): number {
  const now = ctx.now()
  const stale = ctx.db
    .prepare(`SELECT id, sender_id FROM events WHERE status = 'sent' AND expires_at IS NOT NULL AND expires_at <= ?`)
    .all(now) as { id: string; sender_id: string }[]
  if (stale.length === 0) return 0
  ctx.db.prepare(`UPDATE events SET status = 'expired' WHERE status = 'sent' AND expires_at IS NOT NULL AND expires_at <= ?`).run(now)
  for (const { id, sender_id } of stale) ctx.hub.send(sender_id, { t: 'status', id, status: 'expired', at: now })
  return stale.length
}
