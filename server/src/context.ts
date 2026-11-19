import type { FastifyBaseLogger } from 'fastify'
import type { Config } from './config.ts'
import type { Db } from './db.ts'
import { RateLimiter } from './lib/rateLimit.ts'
import type { SmsSender } from './lib/sms.ts'
import { Hub } from './realtime.ts'

/** Shared dependencies handed to every service function. */
export interface Ctx {
  db: Db
  config: Config
  hub: Hub
  sms: SmsSender
  log: FastifyBaseLogger
  now: () => number
  limiters: {
    otpRequestPerIp: RateLimiter
    otpRequestPerPhone: RateLimiter
    otpVerifyPerIp: RateLimiter
    invitesPerUser: RateLimiter
    eventsPerUser: RateLimiter
    uploadsPerUser: RateLimiter
  }
}

const MINUTE = 60_000

export function createContext(deps: Pick<Ctx, 'db' | 'config' | 'sms' | 'log'> & { now?: () => number }): Ctx {
  return {
    ...deps,
    hub: new Hub(),
    now: deps.now ?? Date.now,
    limiters: {
      otpRequestPerIp: new RateLimiter(10, 10 * MINUTE),
      otpRequestPerPhone: new RateLimiter(5, 60 * MINUTE),
      otpVerifyPerIp: new RateLimiter(30, 10 * MINUTE),
      invitesPerUser: new RateLimiter(10, 24 * 60 * MINUTE),
      eventsPerUser: new RateLimiter(40, MINUTE),
      uploadsPerUser: new RateLimiter(30, 10 * MINUTE),
    },
  }
}
