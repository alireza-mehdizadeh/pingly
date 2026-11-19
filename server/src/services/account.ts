// Permanent account deletion — the strongest privacy control: everything tied to the account
// (profile, sessions, invites, the couple's history and encrypted media) is erased.

import type { Ctx } from '../context.ts'
import { transaction } from '../db.ts'
import { deletePairMedia } from './media.ts'
import { endPair } from './pairing.ts'
import { activePair, getUser, partnerIdOf } from './users.ts'

export function deleteAccount(ctx: Ctx, userId: string): void {
  const user = getUser(ctx, userId)
  const pair = activePair(ctx, userId)
  const partnerId = pair ? partnerIdOf(pair, userId) : null
  if (pair) endPair(ctx, pair)

  // Blobs on disk are not covered by the database cascade.
  const pairs = ctx.db.prepare('SELECT id FROM pairs WHERE user_a = ? OR user_b = ?').all(userId, userId) as { id: string }[]
  for (const p of pairs) deletePairMedia(ctx, p.id)
  const sessions = ctx.db.prepare('SELECT id FROM sessions WHERE user_id = ?').all(userId) as { id: string }[]

  transaction(ctx.db, () => {
    ctx.db.prepare('DELETE FROM invites WHERE to_phone = ?').run(user.phone)
    ctx.db.prepare('DELETE FROM otp_codes WHERE phone = ?').run(user.phone)
    // Cascades to sessions, sent invites, pairs and through them events and media rows.
    ctx.db.prepare('DELETE FROM users WHERE id = ?').run(userId)
  })

  for (const { id } of sessions) ctx.hub.closeSession(id)
  if (partnerId) ctx.hub.send(partnerId, { t: 'sync', reason: 'pair' })
}
