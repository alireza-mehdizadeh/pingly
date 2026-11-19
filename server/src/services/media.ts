// Voice notes and photos. The app encrypts every blob end-to-end with the couple's shared key
// before upload, so the server only ever stores and serves opaque bytes.

import { existsSync, rmSync, writeFileSync } from 'node:fs'
import { join } from 'node:path'
import type { Ctx } from '../context.ts'
import { newId } from '../lib/crypto.ts'
import { ApiError, badRequest, forbidden, notFound } from '../lib/errors.ts'
import { mediaDto, type MediaRow } from '../models.ts'
import { requirePair } from './users.ts'

export const MEDIA_LIMITS = { voice: 6 * 1024 * 1024, image: 10 * 1024 * 1024 } as const
export const MAX_MEDIA_PER_PAIR = 500
const MAX_ENC_TITLE = 600

type MediaKind = keyof typeof MEDIA_LIMITS

function isKind(value: unknown): value is MediaKind {
  return value === 'voice' || value === 'image'
}

function blobPath(ctx: Ctx, id: string): string {
  return join(ctx.config.dataDir, 'media', `${id}.bin`)
}

export interface UploadInput {
  kind: unknown
  body: unknown
  encTitle?: unknown
  durationMs?: unknown
}

export function uploadMedia(ctx: Ctx, userId: string, input: UploadInput) {
  if (!isKind(input.kind)) throw badRequest('invalid_kind')
  if (!Buffer.isBuffer(input.body) || input.body.length === 0) throw badRequest('empty_body')
  if (input.body.length > MEDIA_LIMITS[input.kind]) throw new ApiError(413, 'too_large')
  const encTitle = typeof input.encTitle === 'string' && input.encTitle.length > 0 ? input.encTitle : null
  if (encTitle && encTitle.length > MAX_ENC_TITLE) throw badRequest('title_too_long')
  const duration = Number(input.durationMs)
  const durationMs = Number.isFinite(duration) && duration > 0 ? Math.round(duration) : null

  ctx.limiters.uploadsPerUser.consume(userId, ctx.now())
  const pair = requirePair(ctx, userId)
  const count = ctx.db.prepare('SELECT COUNT(*) AS n FROM media WHERE pair_id = ?').get(pair.id) as { n: number }
  if (count.n >= MAX_MEDIA_PER_PAIR) throw new ApiError(409, 'media_limit')

  const id = newId()
  writeFileSync(blobPath(ctx, id), input.body)
  ctx.db
    .prepare(
      'INSERT INTO media (id, pair_id, owner_id, kind, size, enc_title, duration_ms, created_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?)',
    )
    .run(id, pair.id, userId, input.kind, input.body.length, encTitle, durationMs, ctx.now())
  return mediaDto(ctx.db.prepare('SELECT * FROM media WHERE id = ?').get(id) as unknown as MediaRow)
}

export function listMedia(ctx: Ctx, userId: string, kind: unknown) {
  const pair = requirePair(ctx, userId)
  const rows = isKind(kind)
    ? ctx.db.prepare('SELECT * FROM media WHERE pair_id = ? AND kind = ? ORDER BY created_at DESC').all(pair.id, kind)
    : ctx.db.prepare('SELECT * FROM media WHERE pair_id = ? ORDER BY created_at DESC').all(pair.id)
  return (rows as unknown as MediaRow[]).map(mediaDto)
}

/** Looks up a blob the caller's couple owns. */
export function getMedia(ctx: Ctx, userId: string, mediaId: string): { row: MediaRow; path: string } {
  const pair = requirePair(ctx, userId)
  const row = ctx.db.prepare('SELECT * FROM media WHERE id = ? AND pair_id = ?').get(mediaId, pair.id) as MediaRow | undefined
  const path = row ? blobPath(ctx, row.id) : ''
  if (!row || !existsSync(path)) throw notFound('media_not_found')
  return { row, path }
}

export function deleteMedia(ctx: Ctx, userId: string, mediaId: string): void {
  const { row, path } = getMedia(ctx, userId, mediaId)
  if (row.owner_id !== userId) throw forbidden('not_owner')
  ctx.db.prepare('DELETE FROM media WHERE id = ?').run(row.id)
  rmSync(path, { force: true })
}

export function deletePairMedia(ctx: Ctx, pairId: string): void {
  const rows = ctx.db.prepare('SELECT id FROM media WHERE pair_id = ?').all(pairId) as { id: string }[]
  for (const { id } of rows) rmSync(blobPath(ctx, id), { force: true })
  ctx.db.prepare('DELETE FROM media WHERE pair_id = ?').run(pairId)
}
