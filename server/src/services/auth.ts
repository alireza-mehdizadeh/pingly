import type { Ctx } from '../context.ts'
import { transaction } from '../db.ts'
import { hashOtp, newId, newOtpCode, newSessionToken, safeEqualHex, sha256 } from '../lib/crypto.ts'
import { ApiError, badRequest, tooManyRequests } from '../lib/errors.ts'
import { normalizeIranMobile, toAsciiDigits } from '../lib/phone.ts'
import { defaultSettings, userDto, type UserRow } from '../models.ts'

const OTP_TTL_MS = 2 * 60_000
const OTP_RESEND_MS = 60_000
const OTP_MAX_ATTEMPTS = 5
/** Sessions are touched at most this often, to keep writes off the hot path. */
const TOUCH_INTERVAL_MS = 60_000

export interface AuthInfo {
  userId: string
  sessionId: string
}

interface OtpRow {
  phone: string
  code_hash: string
  expires_at: number
  attempts: number
  sent_at: number
}

function requirePhone(raw: unknown): string {
  const phone = typeof raw === 'string' ? normalizeIranMobile(raw) : null
  if (!phone) throw badRequest('invalid_phone', 'Expected an Iranian mobile number')
  return phone
}

export async function requestOtp(ctx: Ctx, phoneRaw: unknown, ip: string) {
  const phone = requirePhone(phoneRaw)
  const now = ctx.now()

  const existing = ctx.db.prepare('SELECT * FROM otp_codes WHERE phone = ?').get(phone) as OtpRow | undefined
  if (existing && now - existing.sent_at < OTP_RESEND_MS) {
    throw tooManyRequests(Math.ceil((OTP_RESEND_MS - (now - existing.sent_at)) / 1000))
  }
  ctx.limiters.otpRequestPerIp.consume(ip, now)
  ctx.limiters.otpRequestPerPhone.consume(phone, now)

  const testCode = ctx.config.otpTestNumbers.get(phone)
  const code = testCode ?? newOtpCode()

  ctx.db
    .prepare(
      `INSERT INTO otp_codes (phone, code_hash, expires_at, attempts, sent_at) VALUES (?, ?, ?, 0, ?)
       ON CONFLICT(phone) DO UPDATE SET code_hash = excluded.code_hash, expires_at = excluded.expires_at,
         attempts = 0, sent_at = excluded.sent_at`,
    )
    .run(phone, hashOtp(ctx.config.secret, phone, code), now + OTP_TTL_MS, now)

  if (!testCode) {
    const result = await ctx.sms(phone, code)
    if (!result.ok) {
      ctx.db.prepare('DELETE FROM otp_codes WHERE phone = ?').run(phone)
      throw new ApiError(502, result.error, 'Could not deliver the login code')
    }
  }

  return { expiresIn: OTP_TTL_MS / 1000, retryAfter: OTP_RESEND_MS / 1000 }
}

export function verifyOtp(ctx: Ctx, phoneRaw: unknown, codeRaw: unknown, deviceRaw: unknown, ip: string) {
  const phone = requirePhone(phoneRaw)
  const code = typeof codeRaw === 'string' ? toAsciiDigits(codeRaw).trim() : ''
  if (!/^\d{6}$/.test(code)) throw badRequest('invalid_code', 'Expected a 6-digit code')
  const device = typeof deviceRaw === 'string' ? deviceRaw.slice(0, 80) : ''
  const now = ctx.now()
  ctx.limiters.otpVerifyPerIp.consume(ip, now)

  const otp = ctx.db.prepare('SELECT * FROM otp_codes WHERE phone = ?').get(phone) as OtpRow | undefined
  if (!otp || otp.expires_at < now) throw badRequest('code_expired', 'Request a new code')
  if (otp.attempts >= OTP_MAX_ATTEMPTS) throw badRequest('too_many_attempts', 'Request a new code')

  if (!safeEqualHex(otp.code_hash, hashOtp(ctx.config.secret, phone, code))) {
    ctx.db.prepare('UPDATE otp_codes SET attempts = attempts + 1 WHERE phone = ?').run(phone)
    const left = OTP_MAX_ATTEMPTS - otp.attempts - 1
    throw new ApiError(400, left > 0 ? 'wrong_code' : 'too_many_attempts', 'Wrong code', { attemptsLeft: left })
  }

  const token = newSessionToken()
  const { user, isNewUser, revokedSessions } = transaction(ctx.db, () => {
    ctx.db.prepare('DELETE FROM otp_codes WHERE phone = ?').run(phone)

    let user = ctx.db.prepare('SELECT * FROM users WHERE phone = ?').get(phone) as UserRow | undefined
    const isNewUser = !user
    if (!user) {
      ctx.db
        .prepare('INSERT INTO users (id, phone, settings, created_at, last_seen_at) VALUES (?, ?, ?, ?, ?)')
        .run(newId(), phone, JSON.stringify(defaultSettings()), now, now)
      user = ctx.db.prepare('SELECT * FROM users WHERE phone = ?').get(phone) as unknown as UserRow
    }

    // One phone per account: the end-to-end keys live on the device, so a new login replaces
    // every older session instead of leaving a second device that can't decrypt anything.
    const revokedSessions = ctx.db.prepare('SELECT id FROM sessions WHERE user_id = ?').all(user.id) as { id: string }[]
    ctx.db.prepare('DELETE FROM sessions WHERE user_id = ?').run(user.id)
    ctx.db
      .prepare('INSERT INTO sessions (id, user_id, token_hash, device, created_at, last_used_at) VALUES (?, ?, ?, ?, ?, ?)')
      .run(newId(), user.id, sha256(token), device, now, now)
    return { user, isNewUser, revokedSessions }
  })

  for (const { id } of revokedSessions) ctx.hub.closeSession(id)
  return { token, user: userDto(user), isNewUser }
}

export function authenticate(ctx: Ctx, token: string): AuthInfo | null {
  if (!token.startsWith('pgl_')) return null
  const row = ctx.db
    .prepare('SELECT id, user_id, last_used_at FROM sessions WHERE token_hash = ?')
    .get(sha256(token)) as { id: string; user_id: string; last_used_at: number } | undefined
  if (!row) return null
  const now = ctx.now()
  if (now - row.last_used_at > TOUCH_INTERVAL_MS) {
    ctx.db.prepare('UPDATE sessions SET last_used_at = ? WHERE id = ?').run(now, row.id)
  }
  return { userId: row.user_id, sessionId: row.id }
}

export function logout(ctx: Ctx, auth: AuthInfo): void {
  ctx.db.prepare('DELETE FROM sessions WHERE id = ?').run(auth.sessionId)
  ctx.hub.closeSession(auth.sessionId)
}
