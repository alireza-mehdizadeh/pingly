// Every failure the app can act on has a stable machine-readable `code`. The Android client
// maps codes to Persian copy, so messages here are English and meant for logs/debugging.

export class ApiError extends Error {
  readonly status: number
  readonly code: string
  readonly details: Record<string, unknown> | undefined

  constructor(status: number, code: string, message?: string, details?: Record<string, unknown>) {
    super(message ?? code)
    this.status = status
    this.code = code
    this.details = details
  }
}

export const badRequest = (code: string, message?: string) => new ApiError(400, code, message)
export const unauthorized = (code = 'unauthorized') => new ApiError(401, code, 'Authentication required')
export const forbidden = (code: string, message?: string) => new ApiError(403, code, message)
export const notFound = (code = 'not_found') => new ApiError(404, code, 'Not found')
export const conflict = (code: string, message?: string) => new ApiError(409, code, message)
export const tooManyRequests = (retryAfterSec: number) =>
  new ApiError(429, 'rate_limited', 'Too many requests', { retryAfter: retryAfterSec })
