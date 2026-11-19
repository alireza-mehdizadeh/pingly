// Phone numbers are stored canonically as `989xxxxxxxxx` (Iranian mobile, no plus sign).
// Users type them in every imaginable way — Persian or Arabic digits, with +98 / 0098 / 0,
// with spaces or dashes — so everything funnels through normalizeIranMobile().

const PERSIAN_ZERO = 0x06f0
const ARABIC_ZERO = 0x0660

/** `۰۹۱۲` / `٠٩١٢` → `0912`. Other characters pass through untouched. */
export function toAsciiDigits(input: string): string {
  return input.replace(/[۰-۹٠-٩]/g, (ch) => {
    const code = ch.charCodeAt(0)
    const base = code >= PERSIAN_ZERO ? PERSIAN_ZERO : ARABIC_ZERO
    return String(code - base)
  })
}

/** Returns the canonical `989xxxxxxxxx` form, or null when it isn't an Iranian mobile number. */
export function normalizeIranMobile(input: string): string | null {
  let digits = toAsciiDigits(input).replace(/[\s\-().]/g, '')
  if (digits.startsWith('+')) digits = digits.slice(1)
  if (!/^\d+$/.test(digits)) return null

  if (digits.startsWith('0098')) digits = digits.slice(4)
  else if (digits.startsWith('98') && digits.length === 12) digits = digits.slice(2)
  else if (digits.startsWith('0') && digits.length === 11) digits = digits.slice(1)

  return /^9\d{9}$/.test(digits) ? `98${digits}` : null
}

/** `989121234567` → `09121234567` (the form people recognise). */
export function toLocalPhone(canonical: string): string {
  return canonical.startsWith('98') && canonical.length === 12 ? `0${canonical.slice(2)}` : canonical
}

/** `989121234567` → `0912***4567`, for logs. */
export function maskPhone(canonical: string): string {
  const local = toLocalPhone(canonical)
  return local.length > 7 ? `${local.slice(0, 4)}***${local.slice(-4)}` : '***'
}
