import { createHash, createHmac, randomBytes, randomInt, timingSafeEqual } from 'node:crypto'

/** URL-safe random identifier (128 bits). */
export function newId(): string {
  return randomBytes(16).toString('base64url')
}

/** Opaque bearer token handed to the app. Only its SHA-256 is stored. */
export function newSessionToken(): string {
  return `pgl_${randomBytes(32).toString('base64url')}`
}

export function sha256(value: string): string {
  return createHash('sha256').update(value).digest('hex')
}

/** Uniformly random 6-digit code. */
export function newOtpCode(): string {
  return String(randomInt(0, 1_000_000)).padStart(6, '0')
}

/** OTP codes are stored as HMAC(secret, phone:code) so a leaked database reveals nothing usable. */
export function hashOtp(secret: string, phone: string, code: string): string {
  return createHmac('sha256', secret).update(`${phone}:${code}`).digest('hex')
}

export function safeEqualHex(a: string, b: string): boolean {
  const ab = Buffer.from(a, 'hex')
  const bb = Buffer.from(b, 'hex')
  return ab.length === bb.length && timingSafeEqual(ab, bb)
}
