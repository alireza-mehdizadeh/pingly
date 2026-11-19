package ir.byalireza.pingly.data.api

import ir.byalireza.pingly.core.Persian

/** A failed call, carrying the server's stable error `code` (or `network`). */
class ApiException(
    val status: Int,
    val code: String,
    val retryAfter: Int? = null,
    val attemptsLeft: Int? = null,
    cause: Throwable? = null,
) : Exception(code, cause) {

    val isUnauthorized: Boolean get() = status == 401

    /** Friendly Persian copy for this error — what the user sees. */
    val persianMessage: String get() = messageFor(code, retryAfter, attemptsLeft)

    companion object {
        fun messageFor(code: String, retryAfter: Int? = null, attemptsLeft: Int? = null): String = when (code) {
            "network" -> "اینترنت در دسترس نیست یا سرور جواب نمی‌ده. دوباره امتحان کن 🌐"
            "invalid_phone" -> "شماره موبایل درست نیست. مثلاً این‌طوری بنویسش: ۰۹۱۲۱۲۳۴۵۶۷"
            "rate_limited" -> retryAfter?.let { "یه کم صبر کن؛ ${Persian.digits(it)} ثانیه دیگه دوباره امتحان کن ⏳" }
                ?: "یه کم صبر کن و دوباره امتحان کن ⏳"
            "sms_failed" -> "ارسال پیامک انجام نشد. چند لحظه دیگه دوباره امتحان کن."
            "sms_not_configured" -> "سرویس پیامک فعلاً در دسترس نیست؛ کمی بعد امتحان کن."
            "invalid_code" -> "کد تأیید باید ۶ رقم باشه."
            "wrong_code" -> attemptsLeft?.let { "کد اشتباهه. ${Persian.digits(it)} بار دیگه می‌تونی امتحان کنی." }
                ?: "کد اشتباهه."
            "code_expired" -> "این کد منقضی شده؛ یه کد جدید بگیر."
            "too_many_attempts" -> "تعداد تلاش‌ها زیاد شد؛ یه کد جدید بگیر."
            "unauthorized" -> "نشستت تموم شده؛ لطفاً دوباره وارد شو."
            "invalid_name" -> "اسم باید بین ۱ تا ۳۲ حرف باشه."
            "self_invite" -> "نمی‌شه خودت رو دعوت کنی 😄"
            "already_paired" -> "تو الان به یه نفر وصلی."
            "inviter_already_paired" -> "این دعوت دیگه معتبر نیست؛ دعوت‌کننده به کس دیگه‌ای وصل شده."
            "invite_not_found", "invite_not_pending" -> "این دعوت دیگه معتبر نیست."
            "not_your_invite" -> "این دعوت برای تو نیست."
            "not_paired" -> "اول باید به پارتنرت وصل بشی 💞"
            "partner_paused" -> "پارتنرت فعلاً پینگلی رو روی مکث گذاشته ⏸"
            "not_allowed" -> "پارتنرت این قابلیت رو برای خودش خاموش کرده."
            "missing_partner_key" -> "رمزنگاری با گوشی پارتنرت هنوز آماده نیست؛ چند لحظه دیگه امتحان کن 🔐"
            "media_locked" -> "این فایل با کلیدِ گوشی قبلی رمز شده و اینجا باز نمی‌شه 🔒"
            "media_not_found" -> "این فایل پیدا نشد؛ شاید پاک شده باشه."
            "too_large" -> "این فایل خیلی بزرگه."
            "media_limit" -> "گالری پر شده؛ چندتا فایل قدیمی رو پاک کن."
            "already_answered" -> "به این سؤال قبلاً جواب داده شده."
            "question_not_found" -> "این سؤال دیگه در دسترس نیست."
            "missing_content", "invalid_content" -> "متن خالیه یا خیلی طولانیه."
            else -> "یه مشکلی پیش اومد؛ دوباره امتحان کن 🙏"
        }
    }
}

/** Persian message for any failure thrown by the data layer. */
fun Throwable.toPersianMessage(): String = when (this) {
    is ApiException -> persianMessage
    else -> ApiException.messageFor("unknown")
}
