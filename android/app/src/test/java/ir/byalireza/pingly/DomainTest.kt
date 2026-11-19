package ir.byalireza.pingly

import ir.byalireza.pingly.data.api.ApiException
import ir.byalireza.pingly.domain.EventStatus
import ir.byalireza.pingly.domain.EventType
import ir.byalireza.pingly.domain.PinglyEvent
import ir.byalireza.pingly.domain.VibrationPattern
import ir.byalireza.pingly.service.Haptics
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DomainTest {
    @Test
    fun everyVibrationPatternHasMatchingArrays() {
        VibrationPattern.entries.forEach {
            assertEquals(it.name, it.timings.size, it.amplitudes.size)
            assertTrue(it.name, it.amplitudes.all { a -> a in 0..255 })
        }
    }

    @Test
    fun onOffFallbackKeepsTotalDuration() {
        VibrationPattern.entries.forEach { pattern ->
            val folded = Haptics.onOffTimings(pattern)
            assertEquals(pattern.name, pattern.timings.sum(), folded.sum())
            // Alternates off/on starting with "off"; weak segments (≤60) count as off.
            assertTrue(pattern.name, folded.size >= 2 && folded.all { it >= 0 })
        }
    }

    @Test
    fun errorCodesBecomeFriendlyPersian() {
        assertEquals("یه کم صبر کن؛ ۴۵ ثانیه دیگه دوباره امتحان کن", ApiException(429, "rate_limited", retryAfter = 45).persianMessage)
        assertEquals("کد اشتباهه. ۲ بار دیگه می‌تونی امتحان کنی.", ApiException(400, "wrong_code", attemptsLeft = 2).persianMessage)
        assertTrue(ApiException(500, "something_new").persianMessage.isNotBlank())
    }

    @Test
    fun eventsSummariseInPersian() {
        fun event(type: EventType, mine: Boolean, meta: Map<String, String> = emptyMap(), payload: Map<String, String>? = null) = PinglyEvent(
            id = "e1", type = type, mine = mine, status = EventStatus.Sent, createdAt = 0,
            meta = JsonObject(meta.mapValues { JsonPrimitive(it.value) }),
            payload = payload?.let { p -> JsonObject(p.mapValues { JsonPrimitive(it.value) }) },
            locked = false, replyTo = null,
        )
        assertEquals("زینب یه پینگ فرستاد", event(EventType.Ping, mine = false).summary("زینب"))
        assertEquals("لرزش «بغل محکم» فرستادی", event(EventType.Vibrate, mine = true, mapOf("pattern" to "hug")).summary("زینب"))
        assertEquals("یه پینگ فرستادی", event(EventType.Ping, mine = true).summary("زینب"))
        assertEquals("«سلام»", event(EventType.Message, mine = false, payload = mapOf("text" to "سلام")).summary("زینب"))
        assertEquals(EventStatus.Answered, EventStatus.fromWire("answered"))
        assertEquals(EventType.Question, EventType.fromWire("question"))
    }
}
