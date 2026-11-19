// SMS.ir client for OTP delivery through an approved verify template (قالب):
//
//   SMSIR_API_KEY         API key from the SMS.ir panel (sent as x-api-key)
//   SMSIR_TEMPLATE_ID     numeric id of the approved verify template
//   SMSIR_PARAMETER_NAME  parameter name inside the template (default: code)
//
// Without credentials, development prints the code to the log; production refuses rather
// than pretending a code was sent.

import type { FastifyBaseLogger } from 'fastify'
import type { SmsConfig } from '../config.ts'
import { maskPhone, toLocalPhone } from './phone.ts'

const ENDPOINT = 'https://api.sms.ir/v1/send/verify'
const REQUEST_TIMEOUT_MS = 10_000

export type SmsResult = { ok: true } | { ok: false; error: 'sms_not_configured' | 'sms_failed' }
export type SmsSender = (phone: string, code: string) => Promise<SmsResult>

/** SMS.ir can answer HTTP 200 with an error body; only `status: 1` means accepted. */
function providerError(raw: string): string | null {
  try {
    const parsed: unknown = JSON.parse(raw)
    if (parsed && typeof parsed === 'object' && 'status' in parsed) {
      return (parsed as { status: unknown }).status === 1 ? null : raw.slice(0, 300)
    }
  } catch {
    // Not JSON — nothing reliable to inspect.
  }
  return null
}

export function createSmsSender(config: SmsConfig, isProduction: boolean, log: FastifyBaseLogger): SmsSender {
  const configured = Boolean(config.apiKey && config.templateId)

  return async (phone, code) => {
    if (!configured) {
      if (isProduction) {
        log.error('[sms] SMSIR_API_KEY / SMSIR_TEMPLATE_ID missing — refusing to send')
        return { ok: false, error: 'sms_not_configured' }
      }
      log.warn(`[sms:dev] NO SMS SENT — login code for ${toLocalPhone(phone)} is ${code}`)
      return { ok: true }
    }

    const templateId = Number(config.templateId)
    if (!Number.isInteger(templateId)) {
      log.error(`[sms] SMSIR_TEMPLATE_ID must be numeric, got "${config.templateId}"`)
      return { ok: false, error: 'sms_not_configured' }
    }

    try {
      const res = await fetch(ENDPOINT, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json', Accept: 'application/json', 'x-api-key': config.apiKey },
        body: JSON.stringify({
          mobile: toLocalPhone(phone),
          templateId,
          parameters: [{ name: config.parameter, value: code }],
        }),
        signal: AbortSignal.timeout(REQUEST_TIMEOUT_MS),
      })
      const raw = await res.text().catch(() => '')
      if (!res.ok) {
        log.error(`[sms] SMS.ir responded ${res.status}: ${raw.slice(0, 300)}`)
        return { ok: false, error: 'sms_failed' }
      }
      const error = providerError(raw)
      if (error) {
        log.error(`[sms] SMS.ir reported an error: ${error}`)
        return { ok: false, error: 'sms_failed' }
      }
      log.info(`[sms] SMS.ir accepted the code for ${maskPhone(phone)}`)
      return { ok: true }
    } catch (err) {
      log.error({ err }, '[sms] SMS.ir request failed')
      return { ok: false, error: 'sms_failed' }
    }
  }
}
