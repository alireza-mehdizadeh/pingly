// A command-line "partner phone" for testing Pingly end to end without a second device.
// It speaks the real API and the same end-to-end encryption as the Android app.
//
//   PARTNER_STATE=/tmp/zeynab.json SERVER=http://localhost:3000 node scripts/virtual-partner.ts <command> …
//
// Commands:
//   request <phone>                      ask for an OTP (read it from the dev server log)
//   verify <phone> <code> [name] [avatar] log in, create keys, set profile
//   me                                   print the account / partner / invites
//   invite <phone> | accept | unpair     pairing
//   send <type> [metaJson] [payloadJson] send an event (payload is end-to-end encrypted)
//   upload <voice|image> <file> [title]  encrypt + upload media, prints its id
//   listen <seconds> [answer]            print incoming events (decrypted); ack + open them,
//                                        and answer questions with [answer] if given
//   pause <true|false>                   the Pause switch

import { createCipheriv, createDecipheriv, createHash, createPublicKey, generateKeyPairSync, hkdfSync, randomBytes, diffieHellman, createPrivateKey } from 'node:crypto'
import { existsSync, readFileSync, writeFileSync } from 'node:fs'
import WebSocket from 'ws'

const SERVER = (process.env.SERVER ?? 'http://localhost:3000').replace(/\/$/, '')
const STATE_FILE = process.env.PARTNER_STATE ?? './virtual-partner.json'

interface State {
  token?: string
  userId?: string
  privateKey?: string // PKCS#8 DER, base64
  publicKey?: string // SPKI DER, base64 (same format as Android's PublicKey.encoded)
}

const state: State = existsSync(STATE_FILE) ? JSON.parse(readFileSync(STATE_FILE, 'utf8')) : {}
const save = () => writeFileSync(STATE_FILE, JSON.stringify(state, null, 2))

async function api(method: string, path: string, body?: unknown, raw?: Buffer): Promise<any> {
  const headers: Record<string, string> = {}
  if (state.token) headers.authorization = `Bearer ${state.token}`
  if (raw) headers['content-type'] = 'application/octet-stream'
  else if (body !== undefined) headers['content-type'] = 'application/json'
  const res = await fetch(SERVER + path, { method, headers, body: raw ?? (body !== undefined ? JSON.stringify(body) : undefined) })
  const text = await res.text()
  const json = text ? JSON.parse(text) : {}
  if (!res.ok) throw new Error(`${res.status} ${JSON.stringify(json)}`)
  return json
}

// ── End-to-end crypto (mirrors android/.../data/crypto/E2ee.kt) ────────────────
function sharedKey(partnerPublic: string): Buffer {
  const privateKey = createPrivateKey({ key: Buffer.from(state.privateKey!, 'base64'), format: 'der', type: 'pkcs8' })
  const publicKey = createPublicKey({ key: Buffer.from(partnerPublic, 'base64'), format: 'der', type: 'spki' })
  const secret = diffieHellman({ privateKey, publicKey })
  const [a, b] = [state.publicKey!, partnerPublic].sort()
  const salt = createHash('sha256').update(`${a}|${b}`).digest()
  return Buffer.from(hkdfSync('sha256', secret, salt, Buffer.from('pingly-e2e-v1'), 32))
}

function seal(key: Buffer, plain: Buffer): Buffer {
  const iv = randomBytes(12)
  const cipher = createCipheriv('aes-256-gcm', key, iv)
  const body = Buffer.concat([cipher.update(plain), cipher.final(), cipher.getAuthTag()])
  return Buffer.concat([Buffer.from([1]), iv, body])
}

function open(key: Buffer, sealed: Buffer): Buffer {
  const iv = sealed.subarray(1, 13)
  const tag = sealed.subarray(sealed.length - 16)
  const decipher = createDecipheriv('aes-256-gcm', key, iv)
  decipher.setAuthTag(tag)
  return Buffer.concat([decipher.update(sealed.subarray(13, sealed.length - 16)), decipher.final()])
}

async function partnerKey(): Promise<Buffer> {
  const me = await api('GET', '/v1/me')
  if (!me.partner?.publicKey) throw new Error('no partner key yet')
  return sharedKey(me.partner.publicKey)
}

// ── Commands ─────────────────────────────────────────────────────────────────
const [command, ...args] = process.argv.slice(2)

switch (command) {
  case 'request':
    console.log(await api('POST', '/v1/auth/otp/request', { phone: args[0] }))
    break

  case 'verify': {
    const res = await api('POST', '/v1/auth/otp/verify', { phone: args[0], code: args[1], device: 'virtual-partner' })
    state.token = res.token
    state.userId = res.user.id
    const { privateKey, publicKey } = generateKeyPairSync('ec', { namedCurve: 'prime256v1' })
    state.privateKey = privateKey.export({ format: 'der', type: 'pkcs8' }).toString('base64')
    state.publicKey = publicKey.export({ format: 'der', type: 'spki' }).toString('base64')
    save()
    console.log(await api('PATCH', '/v1/me', { displayName: args[2] ?? 'Zeynab', avatar: args[3] ?? 'bunny', publicKey: state.publicKey }))
    break
  }

  case 'me':
    console.log(JSON.stringify(await api('GET', '/v1/me'), null, 2))
    break

  case 'invite':
    console.log(await api('POST', '/v1/pair/invites', { phone: args[0] }))
    break

  case 'accept': {
    const me = await api('GET', '/v1/me')
    const invite = me.invites.incoming[0]
    if (!invite) throw new Error('no incoming invite')
    console.log((await api('POST', `/v1/pair/invites/${invite.id}/accept`, {})).partner)
    break
  }

  case 'unpair':
    console.log(await api('DELETE', '/v1/pair'))
    break

  case 'pause':
    console.log(await api('POST', '/v1/me/pause', { paused: args[0] === 'true' }))
    break

  case 'send': {
    const [type, metaJson, payloadJson] = args
    const body: Record<string, unknown> = { type, meta: metaJson ? JSON.parse(metaJson) : {} }
    if (payloadJson) body.enc = seal(await partnerKey(), Buffer.from(payloadJson)).toString('base64')
    console.log((await api('POST', '/v1/events', body)).event)
    break
  }

  case 'upload': {
    const [kind, file, title] = args
    const key = await partnerKey()
    const sealed = seal(key, readFileSync(file!))
    const query = new URLSearchParams({ kind: kind! })
    if (title) query.set('title', seal(key, Buffer.from(title)).toString('base64'))
    console.log((await api('POST', `/v1/media?${query}`, undefined, sealed)).media)
    break
  }

  case 'listen': {
    const seconds = Number(args[0] ?? 30)
    const autoAnswer = args[1]
    const key = await partnerKey().catch(() => null)
    const ws = new WebSocket(`${SERVER.replace(/^http/, 'ws')}/v1/ws`, { headers: { authorization: `Bearer ${state.token}` } })
    ws.on('message', async (raw) => {
      const msg = JSON.parse(String(raw))
      if (msg.t === 'event') {
        const e = msg.event
        const payload = e.enc && key ? JSON.parse(open(key, Buffer.from(e.enc, 'base64')).toString()) : null
        console.log(`EVENT ${e.type} meta=${JSON.stringify(e.meta)} payload=${JSON.stringify(payload)}`)
        ws.send(JSON.stringify({ t: 'ack', id: e.id }))
        ws.send(JSON.stringify({ t: 'opened', id: e.id }))
        if (e.type === 'question' && autoAnswer && key) {
          const enc = seal(key, Buffer.from(JSON.stringify({ a: autoAnswer }))).toString('base64')
          await api('POST', '/v1/events', { type: 'answer', replyTo: e.id, enc })
          console.log(`ANSWERED ${autoAnswer}`)
        }
      } else {
        console.log(`MSG ${JSON.stringify(msg)}`)
      }
    })
    await new Promise((r) => setTimeout(r, seconds * 1000))
    ws.close()
    break
  }

  default:
    console.log('commands: request, verify, me, invite, accept, unpair, pause, send, upload, listen')
}

