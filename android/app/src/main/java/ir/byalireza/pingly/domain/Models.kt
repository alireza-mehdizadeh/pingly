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

    /** One-line Persian summary for lists and notifications. */
    fun summary(partnerName: String): String {
        val who = if (mine) "تو" else partnerName
        return when (type) {
            EventType.Ping -> "$who یه پینگ فرستاد 💗"
            EventType.Vibrate -> "$who لرزش «${VibrationPattern.fromId(metaString("pattern")).title}» فرستاد ${VibrationPattern.fromId(metaString("pattern")).emoji}"
            EventType.Sound -> "$who صدای «${PinglySound.fromId(metaString("sound")).title}» رو پخش کرد ${PinglySound.fromId(metaString("sound")).emoji}"
            EventType.Voice -> "$who یه صدای ضبط‌شده پخش کرد 🎙️"
            EventType.Image -> "$who یه عکس تمام‌صفحه فرستاد 🖼️"
            EventType.Message -> if (locked) "$who یه پیام عاشقانه فرستاد 💌" else "💌 «${text.orEmpty()}»"
            EventType.Question -> if (locked) "$who یه سؤال پرسید ❓" else "❓ ${question.orEmpty()}"
            EventType.Answer -> if (locked) "$who جواب داد 💬" else "💬 «${answer.orEmpty()}»"
        }
    }
}
