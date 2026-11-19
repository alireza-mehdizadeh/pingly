package ir.byalireza.pingly

import ir.byalireza.pingly.core.Persian
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PersianTest {
    @Test
    fun convertsDigitsBothWays() {
        assertEquals("۰۹۱۲ ۳۴۵", Persian.digits("0912 345"))
        assertEquals("0912345", Persian.toAsciiDigits("۰۹۱۲٣٤٥"))
        assertEquals("abc", Persian.digits("abc"))
    }

    @Test
    fun normalizesIranianMobileNumbers() {
        listOf("09121234567", "9121234567", "+989121234567", "00989121234567", "0912 123 4567", "۰۹۱۲۱۲۳۴۵۶۷", "(0912)-123-4567")
            .forEach { assertEquals(it, "09121234567", Persian.normalizePhone(it)) }
        listOf("", "12345", "02112345678", "+14155550123", "0912abc4567").forEach { assertNull(it, Persian.normalizePhone(it)) }
    }

    @Test
    fun prettyPhoneGroupsDigits() {
        assertEquals("۰۹۱۲ ۱۲۳ ۴۵۶۷", Persian.prettyPhone("09121234567"))
    }

    @Test
    fun convertsGregorianToJalali() {
        assertEquals(Persian.JalaliDate(1403, 1, 1), Persian.toJalali(2024, 3, 20))
        assertEquals(Persian.JalaliDate(1404, 1, 1), Persian.toJalali(2025, 3, 21))
        assertEquals(Persian.JalaliDate(1405, 8, 28), Persian.toJalali(2026, 11, 19))
        assertEquals(Persian.JalaliDate(1368, 10, 11), Persian.toJalali(1990, 1, 1))
        assertEquals("آبان", Persian.toJalali(2026, 11, 19).monthName)
    }

    @Test
    fun describesRelativeTimesInPersian() {
        val now = 1_795_000_000_000L
        assertEquals("همین الان", Persian.relative(now - 20_000, now))
        assertEquals("۵ دقیقه پیش", Persian.relative(now - 5 * 60_000, now))
        assertEquals("۱:۰۵", Persian.duration(65_000))
    }
}
