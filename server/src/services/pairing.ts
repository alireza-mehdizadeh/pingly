// Pairing is strictly mutual: one partner invites a phone number, and nothing is connected
// until the owner of that number explicitly accepts inside their own app.

import type { Ctx } from '../context.ts'
import { transaction } from '../db.ts'
import { newId } from '../lib/crypto.ts'
import { badRequest, conflict, forbidden, notFound } from '../lib/errors.ts'
import { normalizeIranMobile, toLocalPhone } from '../lib/phone.ts'
import type { InviteRow, PairRow, UserRow } from '../models.ts'
import { deletePairMedia } from './media.ts'
import { activePair, getUser, partnerIdOf } from './users.ts'

const INVITE_TTL_MS = 7 * 24 * 60 * 60_000

function findUserByPhone(ctx: Ctx, phone: string): UserRow | undefined {
  return ctx.db.prepare('SELECT * FROM users WHERE phone = ?').get(phone) as UserRow | undefined
}

function getPendingInvite(ctx: Ctx, inviteId: string): InviteRow {
  const invite = ctx.db.prepare('SELECT * FROM invites WHERE id = ?').get(inviteId) as InviteRow | undefined
  if (!invite) throw notFound('invite_not_found')
  if (invite.status !== 'pending' || invite.expires_at < ctx.now()) throw conflict('invite_not_pending')
  return invite
}

export function listInvites(ctx: Ctx, userId: string) {
  const user = getUser(ctx, userId)
  const now = ctx.now()
  const incoming = ctx.db
    .prepare(
      `SELECT i.*, u.display_name AS from_name, u.avatar AS from_avatar, u.phone AS from_phone
       FROM invites i JOIN users u ON u.id = i.from_user_id
       WHERE i.to_phone = ? AND i.status = 'pending' AND i.expires_at > ?
       ORDER BY i.created_at DESC`,
    )
    .all(user.phone, now) as unknown as (InviteRow & { from_name: string; from_avatar: string; from_phone: string })[]
  const outgoing = ctx.db
    .prepare(
      `SELECT * FROM invites WHERE from_user_id = ? AND status = 'pending' AND expires_at > ?
       ORDER BY created_at DESC LIMIT 1`,
    )
    .get(userId, now) as InviteRow | undefined

  return {
    incoming: incoming.map((i) => ({
      id: i.id,
      from: { displayName: i.from_name, avatar: i.from_avatar, phone: toLocalPhone(i.from_phone) },
      createdAt: i.created_at,
      expiresAt: i.expires_at,
    })),
    outgoing: outgoing
      ? { id: outgoing.id, toPhone: toLocalPhone(outgoing.to_phone), createdAt: outgoing.created_at, expiresAt: outgoing.expires_at }
      : null,
  }
}

/**
 * Invites `phoneRaw`. The response never reveals whether that number already has an account,
 * so the endpoint can't be used to discover who uses Pingly.
 */
export function createInvite(ctx: Ctx, userId: string, phoneRaw: unknown) {
  const phone = typeof phoneRaw === 'string' ? normalizeIranMobile(phoneRaw) : null
  if (!phone) throw badRequest('invalid_phone')
  const me = getUser(ctx, userId)
  if (me.phone === phone) throw badRequest('self_invite', 'You cannot invite yourself')
  if (activePair(ctx, userId)) throw conflict('already_paired')
  ctx.limiters.invitesPerUser.consume(userId, ctx.now())

  const now = ctx.now()
  const id = newId()
  transaction(ctx.db, () => {
    // Only one outgoing invite at a time — a new one replaces the previous.
    ctx.db
      .prepare(`UPDATE invites SET status = 'cancelled', responded_at = ? WHERE from_user_id = ? AND status = 'pending'`)
      .run(now, userId)
    ctx.db
      .prepare(`INSERT INTO invites (id, from_user_id, to_phone, status, created_at, expires_at) VALUES (?, ?, ?, 'pending', ?, ?)`)
      .run(id, userId, phone, now, now + INVITE_TTL_MS)
  })

  const target = findUserByPhone(ctx, phone)
  if (target) ctx.hub.send(target.id, { t: 'sync', reason: 'invite' })
  return listInvites(ctx, userId)
}

export function acceptInvite(ctx: Ctx, userId: string, inviteId: string) {
  const me = getUser(ctx, userId)
  const invite = getPendingInvite(ctx, inviteId)
  if (invite.to_phone !== me.phone) throw forbidden('not_your_invite')
  const inviterId = invite.from_user_id
  if (activePair(ctx, userId)) throw conflict('already_paired')
  if (activePair(ctx, inviterId)) throw conflict('inviter_already_paired')

  const now = ctx.now()
  const pairId = newId()
  transaction(ctx.db, () => {
    ctx.db.prepare(`UPDATE invites SET status = 'accepted', responded_at = ? WHERE id = ?`).run(now, inviteId)
    ctx.db.prepare('INSERT INTO pairs (id, user_a, user_b, created_at) VALUES (?, ?, ?, ?)').run(pairId, inviterId, userId, now)
    // Both people are now taken: every other pending invite from or to either of them lapses.
    const inviter = getUser(ctx, inviterId)
    ctx.db
      .prepare(
        `UPDATE invites SET status = 'cancelled', responded_at = ?
         WHERE status = 'pending' AND (from_user_id IN (?, ?) OR to_phone IN (?, ?))`,
      )
      .run(now, userId, inviterId, me.phone, inviter.phone)
  })

  ctx.hub.send(inviterId, { t: 'sync', reason: 'pair' })
  ctx.hub.send(userId, { t: 'sync', reason: 'pair' })
  return { pairId }
}

export function declineInvite(ctx: Ctx, userId: string, inviteId: string): void {
  const me = getUser(ctx, userId)
  const invite = getPendingInvite(ctx, inviteId)
  if (invite.to_phone !== me.phone) throw forbidden('not_your_invite')
  ctx.db.prepare(`UPDATE invites SET status = 'declined', responded_at = ? WHERE id = ?`).run(ctx.now(), inviteId)
  ctx.hub.send(invite.from_user_id, { t: 'sync', reason: 'invite' })
}

export function cancelInvite(ctx: Ctx, userId: string, inviteId: string): void {
  const invite = getPendingInvite(ctx, inviteId)
  if (invite.from_user_id !== userId) throw forbidden('not_your_invite')
  ctx.db.prepare(`UPDATE invites SET status = 'cancelled', responded_at = ? WHERE id = ?`).run(ctx.now(), inviteId)
  const target = findUserByPhone(ctx, invite.to_phone)
  if (target) ctx.hub.send(target.id, { t: 'sync', reason: 'invite' })
}

/** Either partner can end the connection at any time. Shared history and media are erased. */
export function unpair(ctx: Ctx, userId: string): void {
  const pair = activePair(ctx, userId)
  if (!pair) throw conflict('not_paired')
  const partnerId = partnerIdOf(pair, userId)
  endPair(ctx, pair)
  ctx.hub.send(partnerId, { t: 'sync', reason: 'pair' })
  ctx.hub.send(userId, { t: 'sync', reason: 'pair' })
}

export function endPair(ctx: Ctx, pair: PairRow): void {
  transaction(ctx.db, () => {
    ctx.db.prepare('UPDATE pairs SET ended_at = ? WHERE id = ?').run(ctx.now(), pair.id)
    ctx.db.prepare('DELETE FROM events WHERE pair_id = ?').run(pair.id)
  })
  deletePairMedia(ctx, pair.id)
}

export function expireInvites(ctx: Ctx): void {
  ctx.db.prepare(`UPDATE invites SET status = 'expired' WHERE status = 'pending' AND expires_at <= ?`).run(ctx.now())
}
