import { tooManyRequests } from './errors.ts'

/**
 * Sliding-window limiter kept in memory. Pingly runs as a single instance, so this is enough
 * to stop OTP/SMS abuse and event spam without another moving part.
 */
export class RateLimiter {
  readonly limit: number
  readonly windowMs: number
  private readonly hits = new Map<string, number[]>()

  constructor(limit: number, windowMs: number) {
    this.limit = limit
    this.windowMs = windowMs
  }

  /** Records a hit for `key`; throws a 429 ApiError when the key is over its limit. */
  consume(key: string, now = Date.now()): void {
    const fresh = (this.hits.get(key) ?? []).filter((t) => now - t < this.windowMs)
    if (fresh.length >= this.limit) {
      const retryAfterMs = this.windowMs - (now - fresh[0]!)
      this.hits.set(key, fresh)
      throw tooManyRequests(Math.max(1, Math.ceil(retryAfterMs / 1000)))
    }
    fresh.push(now)
    this.hits.set(key, fresh)
  }

  /** Drops keys whose hits have all aged out. */
  prune(now = Date.now()): void {
    for (const [key, times] of this.hits) {
      if (times.every((t) => now - t >= this.windowMs)) this.hits.delete(key)
    }
  }
}
