import assert from 'node:assert/strict'
import { mkdtempSync, rmSync } from 'node:fs'
import { tmpdir } from 'node:os'
import { join } from 'node:path'
import { after, before, describe, test } from 'node:test'
import type { FastifyInstance } from 'fastify'
import WebSocket from 'ws'
import { buildApp } from '../src/app.ts'
import { loadConfig } from '../src/config.ts'
import type { Ctx } from '../src/context.ts'
import { openDatabase } from '../src/db.ts'
import { maskPhone, normalizeIranMobile } from '../src/lib/phone.ts'
import { expireStaleEvents } from '../src/services/events.ts'

const PHONE_A = '09121111111'
const PHONE_B = '۰۹۳۵۲۲۲۲۲۲۲' // typed with Persian digits on purpose
const PHONE_C = '+989133333333'

let app: FastifyInstance
let ctx: Ctx
let baseUrl = ''
let dataDir = ''
let clock = Date.now()
const sentCodes = new Map<string, string>()

before(async () => {
  dataDir = mkdtempSync(join(tmpdir(), 'pingly-test-'))
  const config = loadConfig({ DATA_DIR: dataDir, NODE_ENV: 'test' })
  ;({ app, ctx } = await buildApp({
    config,
    db: openDatabase(':memory:'),
    logger: false,
    now: () => clock,
    sms: async (phone, code) => {
      sentCodes.set(phone, code)
      return { ok: true }
    },
  }))
  await app.listen({ port: 0, host: '127.0.0.1' })
  const address = app.server.address()
  baseUrl = typeof address === 'object' && address ? `127.0.0.1:${address.port}` : ''
})

after(async () => {
  await app.close()
  rmSync(dataDir, { recursive: true, force: true })
})

async function api(method: string, url: string, token?: string, body?: unknown) {
  const res = await app.inject({
    method: method as 'GET',
    url,
    headers: token ? { authorization: `Bearer ${token}` } : {},
    ...(body !== undefined ? { payload: body as Record<string, unknown> } : {}),
  })
  return { status: res.statusCode, json: res.body ? (JSON.parse(res.body) as Record<string, any>) : {} }
}

async function login(phone: string): Promise<{ token: string; userId: string }> {
  const req = await api('POST', '/v1/auth/otp/request', undefined, { phone })
  assert.equal(req.status, 200, JSON.stringify(req.json))
  const code = sentCodes.get(normalizeIranMobile(phone)!)!
  const res = await api('POST', '/v1/auth/otp/verify', undefined, { phone, code, device: 'test' })
  assert.equal(res.status, 200, JSON.stringify(res.json))
  return { token: res.json.token, userId: res.json.user.id }
}

/** A connected socket that records every message it receives. */
async function connect(token: string) {
  const ws = new WebSocket(`ws://${baseUrl}/v1/ws`, { headers: { authorization: `Bearer ${token}` } })
  const messages: any[] = []
  const waiters: { pred: (m: any) => boolean; resolve: (m: any) => void }[] = []
  ws.on('message', (raw) => {
    const msg = JSON.parse(String(raw))
    messages.push(msg)
    for (const w of [...waiters]) {
      if (w.pred(msg)) {
        waiters.splice(waiters.indexOf(w), 1)
        w.resolve(msg)
      }
    }
  })
  await new Promise<void>((resolve, reject) => {
    ws.once('open', () => resolve())
    ws.once('error', reject)
  })
  const next = (pred: (m: any) => boolean, timeoutMs = 2000) =>
    new Promise<any>((resolve, reject) => {
      const found = messages.find(pred)
      if (found) {
        messages.splice(messages.indexOf(found), 1)
        return resolve(found)
      }
      const timer = setTimeout(() => reject(new Error('timed out waiting for ws message')), timeoutMs)
      waiters.push({
        pred,
        resolve: (m) => {
          clearTimeout(timer)
          messages.splice(messages.indexOf(m), 1)
          resolve(m)
        },
      })
    })
  return { ws, messages, next, close: () => ws.close() }
}

describe('phone numbers', () => {
  test('normalizes the many ways people type Iranian mobiles', () => {
    for (const input of ['09121234567', '9121234567', '+989121234567', '00989121234567', '0912 123 4567', '۰۹۱۲۱۲۳۴۵۶۷', '٠٩١٢١٢٣٤٥٦٧']) {
      assert.equal(normalizeIranMobile(input), '989121234567', input)
    }
    for (const input of ['', '12345', '02112345678', '+14155550123', 'abc']) {
      assert.equal(normalizeIranMobile(input), null, input)
    }
    assert.equal(maskPhone('989121234567'), '0912***4567')
  })
})

describe('authentication', () => {
  test('rejects wrong codes, enforces resend cooldown, then logs in', async () => {
    const phone = '09124444444'
    assert.equal((await api('POST', '/v1/auth/otp/request', undefined, { phone })).status, 200)
    const again = await api('POST', '/v1/auth/otp/request', undefined, { phone })
    assert.equal(again.status, 429)
    assert.ok(again.json.error.retryAfter > 0)

    const code = sentCodes.get('989124444444')!
    const wrong = await api('POST', '/v1/auth/otp/verify', undefined, { phone, code: code === '000000' ? '111111' : '000000' })
    assert.equal(wrong.status, 400)
    assert.equal(wrong.json.error.code, 'wrong_code')

    const ok = await api('POST', '/v1/auth/otp/verify', undefined, { phone, code })
    assert.equal(ok.status, 200)
    assert.match(ok.json.token, /^pgl_/)
    assert.equal(ok.json.isNewUser, true)

    // The code is single-use.
    assert.equal((await api('POST', '/v1/auth/otp/verify', undefined, { phone, code })).json.error.code, 'code_expired')
  })

  test('requires a valid bearer token', async () => {
    assert.equal((await api('GET', '/v1/me')).status, 401)
    assert.equal((await api('GET', '/v1/me', 'pgl_nope')).status, 401)
  })

  test('rejects invalid phone numbers', async () => {
    const res = await api('POST', '/v1/auth/otp/request', undefined, { phone: '12345' })
    assert.equal(res.status, 400)
    assert.equal(res.json.error.code, 'invalid_phone')
  })

  test('a new login revokes the previous session (one phone per account)', async () => {
    const first = await login('09125555555')
    clock += 61_000
    const second = await login('09125555555')
    assert.equal((await api('GET', '/v1/me', first.token)).status, 401)
    assert.equal((await api('GET', '/v1/me', second.token)).status, 200)
  })
})

describe('couple flow', () => {
  let a: { token: string; userId: string }
  let b: { token: string; userId: string }
  let c: { token: string; userId: string }

  test('pairing requires an explicit accept', async () => {
    a = await login(PHONE_A)
    await api('PATCH', '/v1/me', a.token, { displayName: 'علیرضا', avatar: 'bear', publicKey: 'A'.repeat(88) })

    const invite = await api('POST', '/v1/pair/invites', a.token, { phone: PHONE_B })
    assert.equal(invite.status, 200)
    assert.equal(invite.json.outgoing.toPhone, '09352222222')

    // B signs up afterwards and finds the invite waiting.
    b = await login(PHONE_B)
    await api('PATCH', '/v1/me', b.token, { displayName: 'زینب', avatar: 'bunny', publicKey: 'B'.repeat(88) })
    const meB = await api('GET', '/v1/me', b.token)
    assert.equal(meB.json.partner, null)
    assert.equal(meB.json.invites.incoming.length, 1)
    assert.equal(meB.json.invites.incoming[0].from.displayName, 'علیرضا')

    // Nothing is connected yet, so actions are refused.
    assert.equal((await api('POST', '/v1/events', a.token, { type: 'ping' })).json.error.code, 'not_paired')

    // A third person cannot accept someone else's invite.
    c = await login(PHONE_C)
    const steal = await api('POST', `/v1/pair/invites/${meB.json.invites.incoming[0].id}/accept`, c.token)
    assert.equal(steal.status, 403)

    const accepted = await api('POST', `/v1/pair/invites/${meB.json.invites.incoming[0].id}/accept`, b.token)
    assert.equal(accepted.status, 200)
    assert.equal(accepted.json.partner.displayName, 'علیرضا')
    assert.equal(accepted.json.partner.publicKey, 'A'.repeat(88))

    const meA = await api('GET', '/v1/me', a.token)
    assert.equal(meA.json.partner.id, b.userId)
    assert.equal(meA.json.invites.outgoing, null)

    // Already-paired people cannot be re-invited into another pair.
    assert.equal((await api('POST', '/v1/pair/invites', a.token, { phone: PHONE_C })).json.error.code, 'already_paired')
  })

  test('actions reach the partner live and statuses flow back', async () => {
    const sa = await connect(a.token)
    const sb = await connect(b.token)
    await sa.next((m) => m.t === 'hello')
    await sb.next((m) => m.t === 'hello')

    const sent = await api('POST', '/v1/events', a.token, { type: 'vibrate', meta: { pattern: 'heartbeat', junk: 1 } })
    assert.equal(sent.status, 200)
    assert.deepEqual(sent.json.event.meta, { pattern: 'heartbeat' })
    assert.ok(sent.json.event.expiresAt)

    const received = await sb.next((m) => m.t === 'event')
    assert.equal(received.event.id, sent.json.event.id)
    assert.equal(received.event.type, 'vibrate')

    sb.ws.send(JSON.stringify({ t: 'ack', id: received.event.id }))
    const delivered = await sa.next((m) => m.t === 'status' && m.status === 'delivered')
    assert.equal(delivered.id, sent.json.event.id)

    sb.ws.send(JSON.stringify({ t: 'opened', id: received.event.id }))
    await sa.next((m) => m.t === 'status' && m.status === 'opened')

    // Bad meta is rejected.
    assert.equal((await api('POST', '/v1/events', a.token, { type: 'sound', meta: { sound: 'nope' } })).json.error.code, 'invalid_sound')
    assert.equal((await api('POST', '/v1/events', a.token, { type: 'message' })).json.error.code, 'missing_content')

    sa.close()
    sb.close()
  })

  test('questions can be answered exactly once', async () => {
    const sa = await connect(a.token)
    const q = await api('POST', '/v1/events', a.token, { type: 'question', meta: { kind: 'choice' }, enc: 'ciphertext-q' })
    assert.equal(q.status, 200)

    // Only the person who was asked can answer.
    assert.equal((await api('POST', '/v1/events', a.token, { type: 'answer', replyTo: q.json.event.id, enc: 'x' })).status, 404)

    const ans = await api('POST', '/v1/events', b.token, { type: 'answer', replyTo: q.json.event.id, enc: 'ciphertext-a' })
    assert.equal(ans.status, 200)
    assert.equal(ans.json.event.replyTo, q.json.event.id)
    await sa.next((m) => m.t === 'status' && m.id === q.json.event.id && m.status === 'answered')

    const twice = await api('POST', '/v1/events', b.token, { type: 'answer', replyTo: q.json.event.id, enc: 'again' })
    assert.equal(twice.json.error.code, 'already_answered')
    sa.close()
  })

  test('the receiver controls consent: per-action switches and Pause', async () => {
    await api('PATCH', '/v1/me', b.token, { settings: { allow: { sound: false } } })
    const blocked = await api('POST', '/v1/events', a.token, { type: 'sound', meta: { sound: 'chime' } })
    assert.equal(blocked.status, 403)
    assert.equal(blocked.json.error.code, 'not_allowed')
    const meA = await api('GET', '/v1/me', a.token)
    assert.equal(meA.json.partner.allow.sound, false)
    await api('PATCH', '/v1/me', b.token, { settings: { allow: { sound: true } } })

    const sa = await connect(a.token)
    const paused = await api('POST', '/v1/me/pause', b.token, { paused: true })
    assert.equal(paused.json.user.paused, true)
    const presence = await sa.next((m) => m.t === 'presence')
    assert.equal(presence.paused, true)

    const refused = await api('POST', '/v1/events', a.token, { type: 'ping' })
    assert.equal(refused.status, 409)
    assert.equal(refused.json.error.code, 'partner_paused')

    await api('POST', '/v1/me/pause', b.token, { paused: false })
    assert.equal((await api('POST', '/v1/events', a.token, { type: 'ping' })).status, 200)
    sa.close()
  })

  test('offline phones get queued events on reconnect; instant ones expire', async () => {
    // Drain anything queued for B so far.
    const drain = await connect(b.token)
    await drain.next((m) => m.t === 'hello')
    await new Promise((r) => setTimeout(r, 100))
    for (const m of drain.messages.filter((x) => x.t === 'event')) drain.ws.send(JSON.stringify({ t: 'ack', id: m.event.id }))
    await new Promise((r) => setTimeout(r, 100))
    drain.close()
    await new Promise((r) => setTimeout(r, 100))

    const msg = await api('POST', '/v1/events', a.token, { type: 'message', meta: { style: 'sunset' }, enc: 'love-note' })
    const ping = await api('POST', '/v1/events', a.token, { type: 'ping' })

    const sb = await connect(b.token)
    const first = await sb.next((m) => m.t === 'event' && m.event.id === msg.json.event.id)
    const second = await sb.next((m) => m.t === 'event' && m.event.id === ping.json.event.id)
    assert.equal(first.event.enc, 'love-note')
    assert.equal(second.event.type, 'ping')
    sb.close()
    await new Promise((r) => setTimeout(r, 100))

    const late = await api('POST', '/v1/events', a.token, { type: 'vibrate', meta: { pattern: 'hug' } })
    clock += 11 * 60_000
    assert.ok(expireStaleEvents(ctx) >= 1)
    const history = await api('GET', '/v1/events?limit=100', a.token)
    assert.equal(history.json.events.find((e: any) => e.id === late.json.event.id).status, 'expired')
  })

  test('media is private to the couple', async () => {
    const blob = Buffer.from('encrypted-voice-bytes')
    const up = await app.inject({
      method: 'POST',
      url: '/v1/media?kind=voice&durationMs=2300&title=enc-title',
      headers: { authorization: `Bearer ${a.token}`, 'content-type': 'application/octet-stream' },
      payload: blob,
    })
    assert.equal(up.statusCode, 200, up.body)
    const media = JSON.parse(up.body).media
    assert.equal(media.durationMs, 2300)

    const down = await app.inject({ method: 'GET', url: `/v1/media/${media.id}`, headers: { authorization: `Bearer ${b.token}` } })
    assert.equal(down.statusCode, 200)
    assert.deepEqual(down.rawPayload, blob)

    // C is not part of this couple.
    assert.equal((await api('GET', `/v1/media/${media.id}`, c.token)).status, 409)

    const play = await api('POST', '/v1/events', a.token, { type: 'voice', meta: { mediaId: media.id } })
    assert.equal(play.status, 200)
    assert.equal((await api('POST', '/v1/events', a.token, { type: 'image', meta: { mediaId: media.id } })).status, 404)

    // Only the owner can delete.
    assert.equal((await api('DELETE', `/v1/media/${media.id}`, b.token)).status, 403)
    assert.equal((await api('GET', '/v1/media?kind=voice', b.token)).json.media.length, 1)
  })

  test('either partner can unpair, which erases shared history', async () => {
    const sa = await connect(a.token)
    const res = await api('DELETE', '/v1/pair', b.token)
    assert.equal(res.status, 200)
    assert.equal(res.json.partner, null)
    await sa.next((m) => m.t === 'sync' && m.reason === 'pair')
    assert.equal((await api('GET', '/v1/me', a.token)).json.partner, null)
    assert.equal((await api('GET', '/v1/events', a.token)).json.error.code, 'not_paired')
    sa.close()
  })

  test('deleting an account erases it and frees the partner', async () => {
    const x = await login('09126666666')
    const y = await login('09127777777')
    await api('POST', '/v1/pair/invites', x.token, { phone: '09127777777' })
    const invite = (await api('GET', '/v1/me', y.token)).json.invites.incoming[0]
    await api('POST', `/v1/pair/invites/${invite.id}/accept`, y.token)
    await api('POST', '/v1/events', y.token, { type: 'ping' })

    const sy = await connect(y.token)
    assert.equal((await api('DELETE', '/v1/me', x.token)).status, 200)
    await sy.next((m) => m.t === 'sync' && m.reason === 'pair')
    sy.close()

    assert.equal((await api('GET', '/v1/me', x.token)).status, 401)
    assert.equal((await api('GET', '/v1/me', y.token)).json.partner, null)
    clock += 61_000
    const again = await api('POST', '/v1/auth/otp/request', undefined, { phone: '09126666666' })
    assert.equal(again.status, 200)
    const code = sentCodes.get('989126666666')!
    assert.equal((await api('POST', '/v1/auth/otp/verify', undefined, { phone: '09126666666', code })).json.isNewUser, true)
  })
})
