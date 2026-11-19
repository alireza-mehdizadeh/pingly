package ir.byalireza.pingly.domain

import ir.byalireza.pingly.data.api.InvitesDto
import ir.byalireza.pingly.data.api.PairDto
import ir.byalireza.pingly.data.api.PartnerDto
import ir.byalireza.pingly.data.api.UserDto
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive

/** Everything the UI knows about the user and their connection. */
data class CoupleState(
    val loaded: Boolean = false,
    val me: UserDto? = null,
    val pair: PairDto? = null,
    val partner: PartnerDto? = null,
    val invites: InvitesDto = InvitesDto(),
) {
    val isPaired: Boolean get() = pair != null && partner != null
    val partnerName: String get() = partner?.displayName?.ifBlank { null } ?: "پارتنرت"
}

enum class EventStatus(val wire: String, val label: String, val icon: String) {
    Sending("sending", "در حال ارسال", "🕊️"),
    Sent("sent", "فرستاده شد", "✓"),
    Delivered("delivered", "رسید", "✓✓"),
    Opened("opened", "دید", "👀"),
    Answered("answered", "جواب داد", "💬"),
    Expired("expired", "نرسید (گوشیش خاموش بود)", "⌛");

    companion object {
        fun fromWire(value: String): EventStatus = entries.firstOrNull { it.wire == value } ?: Sent
    }
}

/**
 * A decrypted interaction as the UI sees it. `payload` holds the end-to-end encrypted part
 * (text, question, answer, caption) once decrypted; `locked` means it could not be decrypted
 * (e.g. it was sealed for a previous phone).
 */
data class PinglyEvent(
    val id: String,
    val type: EventType,
    val mine: Boolean,
    val status: EventStatus,
    val createdAt: Long,
    val meta: JsonObject,
    val payload: JsonObject?,
    val locked: Boolean,
    val replyTo: String?,
) {
    fun metaString(key: String): String? = meta[key]?.jsonPrimitive?.contentOrNull
    fun payloadString(key: String): String? = payload?.get(key)?.jsonPrimitive?.contentOrNull
    fun payloadList(key: String): List<String> =
        runCatching { payload?.get(key)?.jsonArray?.mapNotNull { it.jsonPrimitive.contentOrNull } }.getOrNull().orEmpty()

    val text: String? get() = payloadString("text")
    val question: String? get() = payloadString("q")
    val options: List<String> get() = payloadList("options")
    val answer: String? get() = payloadString("a")
    val mediaId: String? get() = metaString("mediaId")

    /** One-line Persian summary for lists and notifications, conjugated for "you" vs. the partner. */
    fun summary(partnerName: String): String {
        fun said(you: String, partner: String) = if (mine) you else "$partnerName $partner"
        val pattern = VibrationPattern.fromId(metaString("pattern"))
        val sound = PinglySound.fromId(metaString("sound"))
        return when (type) {
            EventType.Ping -> said("یه پینگ فرستادی 💗", "یه پینگ فرستاد 💗")
            EventType.Vibrate -> said("لرزش «${pattern.title}» فرستادی ${pattern.emoji}", "لرزش «${pattern.title}» فرستاد ${pattern.emoji}")
            EventType.Sound -> said("صدای «${sound.title}» رو پخش کردی ${sound.emoji}", "صدای «${sound.title}» رو پخش کرد ${sound.emoji}")
            EventType.Voice -> said("یه صدای ضبط‌شده پخش کردی 🎙️", "یه صدای ضبط‌شده پخش کرد 🎙️")
            EventType.Image -> said("یه عکس تمام‌صفحه نشون دادی 🖼️", "یه عکس تمام‌صفحه فرستاد 🖼️")
            EventType.Message -> if (locked) said("یه پیام عاشقانه فرستادی 💌", "یه پیام عاشقانه فرستاد 💌") else "💌 «${text.orEmpty()}»"
            EventType.Question -> if (locked) said("یه سؤال پرسیدی ❓", "یه سؤال پرسید ❓") else "❓ ${question.orEmpty()}"
            EventType.Answer -> if (locked) said("جواب دادی 💬", "جواب داد 💬") else "💬 «${answer.orEmpty()}»"
        }
    }
}
