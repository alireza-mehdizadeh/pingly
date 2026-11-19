import { randomBytes } from 'node:crypto'
import { existsSync, mkdirSync, readFileSync, writeFileSync } from 'node:fs'
import { join } from 'node:path'
import { normalizeIranMobile } from './lib/phone.ts'

export interface SmsConfig {
  apiKey: string
  templateId: string
  parameter: string
}

export interface Config {
  port: number
  host: string
  isProduction: boolean
  /** Directory holding the SQLite database, the server secret and encrypted media blobs. */
  dataDir: string
  /** Pepper for OTP hashes. Persisted in the data dir when not given through env. */
  secret: string
  sms: SmsConfig
  /**
   * Fixed codes for designated test numbers (e.g. store reviewers). Never sends an SMS.
   * Format: `09120000000:123456,09120000001:654321`. Empty in normal deployments.
   */
  otpTestNumbers: Map<string, string>
}

function loadOrCreateSecret(dataDir: string, fromEnv: string | undefined): string {
  if (fromEnv && fromEnv.trim().length >= 32) return fromEnv.trim()
  const file = join(dataDir, 'server-secret.key')
  if (existsSync(file)) return readFileSync(file, 'utf8').trim()
  const secret = randomBytes(48).toString('base64url')
  writeFileSync(file, secret, { mode: 0o600 })
  return secret
}

function parseTestNumbers(raw: string | undefined): Map<string, string> {
  const map = new Map<string, string>()
  for (const entry of (raw ?? '').split(',')) {
    const [phone, code] = entry.split(':').map((s) => s.trim())
    const normalized = phone ? normalizeIranMobile(phone) : null
    if (normalized && code && /^\d{6}$/.test(code)) map.set(normalized, code)
  }
  return map
}

export function loadConfig(env: NodeJS.ProcessEnv = process.env): Config {
  const dataDir = env.DATA_DIR?.trim() || join(process.cwd(), 'data')
  mkdirSync(join(dataDir, 'media'), { recursive: true })

  return {
    port: Number(env.PORT ?? 3000),
    host: env.HOST ?? '0.0.0.0',
    isProduction: env.NODE_ENV === 'production',
    dataDir,
    secret: loadOrCreateSecret(dataDir, env.SERVER_SECRET),
    sms: {
      apiKey: env.SMSIR_API_KEY?.trim() ?? '',
      templateId: env.SMSIR_TEMPLATE_ID?.trim() ?? '',
      parameter: env.SMSIR_PARAMETER_NAME?.trim() || 'code',
    },
    otpTestNumbers: parseTestNumbers(env.OTP_TEST_NUMBERS),
  }
}
