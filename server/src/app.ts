import { createReadStream } from 'node:fs'
import websocket from '@fastify/websocket'
import Fastify, { type FastifyInstance, type FastifyRequest } from 'fastify'
import type { Config } from './config.ts'
import { createContext, type Ctx } from './context.ts'
import { openDatabase, type Db } from './db.ts'
import { ApiError, unauthorized } from './lib/errors.ts'
import { createSmsSender, type SmsSender } from './lib/sms.ts'
import type { Connection } from './realtime.ts'
import { authenticate, logout, requestOtp, verifyOtp, type AuthInfo } from './services/auth.ts'
import { acknowledge, expireStaleEvents, listEvents, markOpened, pendingFor, sendEvent } from './services/events.ts'
import { deleteMedia, getMedia, listMedia, MEDIA_LIMITS, uploadMedia } from './services/media.ts'
import { acceptInvite, cancelInvite, createInvite, declineInvite, expireInvites, listInvites, unpair } from './services/pairing.ts'
import { broadcastPresence, buildMe, setPaused, touchLastSeen, updateMe } from './services/users.ts'

declare module 'fastify' {
  interface FastifyRequest {
    auth: AuthInfo | null
  }
}

export interface BuildOptions {
  config: Config
  db?: Db
  sms?: SmsSender
  logger?: boolean
  now?: () => number
}

const HEARTBEAT_MS = 25_000
const SWEEP_MS = 60_000

export async function buildApp(options: BuildOptions): Promise<{ app: FastifyInstance; ctx: Ctx }> {
  const app = Fastify({
    trustProxy: true,
    logger: options.logger === false ? false : { level: 'info', redact: ['req.headers.authorization'] },
    bodyLimit: 256 * 1024,
  })

  const db = options.db ?? openDatabase(`${options.config.dataDir}/pingly.db`)
  const sms = options.sms ?? createSmsSender(options.config.sms, options.config.isProduction, app.log)
  const ctx = createContext({ db, config: options.config, sms, log: app.log, now: options.now })

  await app.register(websocket, { options: { maxPayload: 64 * 1024 } })

  app.decorateRequest('auth', null)
  app.addContentTypeParser(
    'application/octet-stream',
    { parseAs: 'buffer', bodyLimit: MEDIA_LIMITS.image + 1024 },
    (_req, body, done) => done(null, body),
  )

  app.setErrorHandler((error, request, reply) => {
    if (error instanceof ApiError) {
      return reply.status(error.status).send({ error: { code: error.code, message: error.message, ...error.details } })
    }
    const err = error as { validation?: unknown; statusCode?: number; message?: string }
    if (err.validation) return reply.status(400).send({ error: { code: 'invalid_request', message: err.message } })
    if (err.statusCode === 413) return reply.status(413).send({ error: { code: 'too_large', message: 'Payload too large' } })
    if (err.statusCode && err.statusCode < 500) {
      return reply.status(err.statusCode).send({ error: { code: 'invalid_request', message: err.message } })
    }
    request.log.error(error)
    return reply.status(500).send({ error: { code: 'server_error', message: 'Something went wrong' } })
  })

  const requireAuth = async (request: FastifyRequest) => {
    const header = request.headers.authorization ?? ''
    const token = header.startsWith('Bearer ') ? header.slice(7).trim() : ''
    const auth = token ? authenticate(ctx, token) : null
    if (!auth) throw unauthorized()
    request.auth = auth
  }
  const me = (request: FastifyRequest) => request.auth!.userId
  type IdParams = { Params: { id: string } }

  // ── Health ────────────────────────────────────────────────────────────────
  app.get('/health', async () => ({ ok: true, name: 'pingly', time: ctx.now() }))

  // ── Auth ──────────────────────────────────────────────────────────────────
  app.post<{ Body: { phone: string } }>(
    '/v1/auth/otp/request',
    { schema: { body: { type: 'object', required: ['phone'], properties: { phone: { type: 'string', maxLength: 32 } } } } },
    async (request) => requestOtp(ctx, request.body.phone, request.ip),
  )

  app.post<{ Body: { phone: string; code: string; device?: string } }>(
    '/v1/auth/otp/verify',
    {
      schema: {
        body: {
          type: 'object',
          required: ['phone', 'code'],
          properties: {
            phone: { type: 'string', maxLength: 32 },
            code: { type: 'string', maxLength: 12 },
            device: { type: 'string', maxLength: 120 },
          },
        },
      },
    },
    async (request) => verifyOtp(ctx, request.body.phone, request.body.code, request.body.device, request.ip),
  )

  app.post('/v1/auth/logout', { preHandler: requireAuth }, async (request) => {
    logout(ctx, request.auth!)
    return { ok: true }
  })

  // ── Me ────────────────────────────────────────────────────────────────────
  app.get('/v1/me', { preHandler: requireAuth }, async (request) => buildMe(ctx, me(request)))

  app.patch<{ Body: Record<string, unknown> }>(
    '/v1/me',
    { preHandler: requireAuth, schema: { body: { type: 'object' } } },
    async (request) => ({ user: updateMe(ctx, me(request), request.body) }),
  )

  app.post<{ Body: { paused: boolean } }>(
    '/v1/me/pause',
    {
      preHandler: requireAuth,
      schema: { body: { type: 'object', required: ['paused'], properties: { paused: { type: 'boolean' } } } },
    },
    async (request) => ({ user: setPaused(ctx, me(request), request.body.paused) }),
  )

  // ── Pairing ───────────────────────────────────────────────────────────────
  app.get('/v1/pair/invites', { preHandler: requireAuth }, async (request) => listInvites(ctx, me(request)))

  app.post<{ Body: { phone: string } }>(
    '/v1/pair/invites',
    {
      preHandler: requireAuth,
      schema: { body: { type: 'object', required: ['phone'], properties: { phone: { type: 'string', maxLength: 32 } } } },
    },
    async (request) => createInvite(ctx, me(request), request.body.phone),
  )

  app.post<IdParams>('/v1/pair/invites/:id/accept', { preHandler: requireAuth }, async (request) => {
    acceptInvite(ctx, me(request), request.params.id)
    return buildMe(ctx, me(request))
  })

  app.post<IdParams>('/v1/pair/invites/:id/decline', { preHandler: requireAuth }, async (request) => {
    declineInvite(ctx, me(request), request.params.id)
    return listInvites(ctx, me(request))
  })

  app.delete<IdParams>('/v1/pair/invites/:id', { preHandler: requireAuth }, async (request) => {
    cancelInvite(ctx, me(request), request.params.id)
    return listInvites(ctx, me(request))
  })

  app.delete('/v1/pair', { preHandler: requireAuth }, async (request) => {
    unpair(ctx, me(request))
    return buildMe(ctx, me(request))
  })

  // ── Events ────────────────────────────────────────────────────────────────
  app.post<{ Body: Record<string, unknown> }>(
    '/v1/events',
    { preHandler: requireAuth, schema: { body: { type: 'object', required: ['type'] } } },
    async (request) => ({ event: sendEvent(ctx, me(request), request.body) }),
  )

  app.get<{ Querystring: { before?: string; limit?: string } }>(
    '/v1/events',
    { preHandler: requireAuth },
    async (request) => ({ events: listEvents(ctx, me(request), request.query.before, request.query.limit) }),
  )

  app.post<IdParams>('/v1/events/:id/ack', { preHandler: requireAuth }, async (request) => ({
    ok: acknowledge(ctx, me(request), request.params.id),
  }))

  app.post<IdParams>('/v1/events/:id/opened', { preHandler: requireAuth }, async (request) => ({
    ok: markOpened(ctx, me(request), request.params.id),
  }))

  // ── Media ─────────────────────────────────────────────────────────────────
  app.post<{ Querystring: { kind?: string; title?: string; durationMs?: string } }>(
    '/v1/media',
    { preHandler: requireAuth, bodyLimit: MEDIA_LIMITS.image + 1024 },
    async (request) => ({
      media: uploadMedia(ctx, me(request), {
        kind: request.query.kind,
        body: request.body,
        encTitle: request.query.title ?? request.headers['x-pingly-title'],
        durationMs: request.query.durationMs,
      }),
    }),
  )

  app.get<{ Querystring: { kind?: string } }>('/v1/media', { preHandler: requireAuth }, async (request) => ({
    media: listMedia(ctx, me(request), request.query.kind),
  }))

  app.get<IdParams>('/v1/media/:id', { preHandler: requireAuth }, async (request, reply) => {
    const { row, path } = getMedia(ctx, me(request), request.params.id)
    return reply
      .header('Content-Type', 'application/octet-stream')
      .header('Content-Length', row.size)
      .header('Cache-Control', 'private, max-age=31536000, immutable')
      .send(createReadStream(path))
  })

  app.delete<IdParams>('/v1/media/:id', { preHandler: requireAuth }, async (request) => {
    deleteMedia(ctx, me(request), request.params.id)
    return { ok: true }
  })

  // ── Realtime ──────────────────────────────────────────────────────────────
  app.get('/v1/ws', { websocket: true, preValidation: requireAuth }, (socket, request) => {
    const userId = request.auth!.userId
    const conn: Connection = { ws: socket, sessionId: request.auth!.sessionId, alive: true }
    const cameOnline = ctx.hub.add(userId, conn)
    touchLastSeen(ctx, userId)

    socket.on('pong', () => {
      conn.alive = true
    })

    socket.on('message', (data) => {
      conn.alive = true
      let msg: { t?: unknown; id?: unknown }
      try {
        msg = JSON.parse(String(data)) as typeof msg
      } catch {
        return
      }
      try {
        if (msg.t === 'ack' && typeof msg.id === 'string') acknowledge(ctx, userId, msg.id)
        else if (msg.t === 'opened' && typeof msg.id === 'string') markOpened(ctx, userId, msg.id)
        else if (msg.t === 'ping') socket.send(JSON.stringify({ t: 'pong', serverTime: ctx.now() }))
      } catch (err) {
        request.log.warn({ err }, 'ws message failed')
      }
    })

    socket.on('close', () => {
      touchLastSeen(ctx, userId)
      if (ctx.hub.remove(userId, conn)) broadcastPresence(ctx, userId)
    })

    socket.send(JSON.stringify({ t: 'hello', serverTime: ctx.now() }))
    for (const event of pendingFor(ctx, userId)) socket.send(JSON.stringify({ t: 'event', event }))
    if (cameOnline) broadcastPresence(ctx, userId)
  })

  // ── Background housekeeping ───────────────────────────────────────────────
  let heartbeat: NodeJS.Timeout | undefined
  let sweeper: NodeJS.Timeout | undefined
  app.addHook('onReady', async () => {
    heartbeat = setInterval(() => ctx.hub.heartbeat(), HEARTBEAT_MS)
    sweeper = setInterval(() => {
      try {
        expireStaleEvents(ctx)
        expireInvites(ctx)
        ctx.db.prepare('DELETE FROM otp_codes WHERE expires_at < ?').run(ctx.now() - 60 * 60_000)
        for (const limiter of Object.values(ctx.limiters)) limiter.prune()
      } catch (err) {
        app.log.error({ err }, 'sweep failed')
      }
    }, SWEEP_MS)
  })
  app.addHook('onClose', async () => {
    clearInterval(heartbeat)
    clearInterval(sweeper)
    ctx.hub.closeAll()
  })

  return { app, ctx }
}
