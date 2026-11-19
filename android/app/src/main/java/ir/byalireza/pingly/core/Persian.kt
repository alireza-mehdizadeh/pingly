package ir.byalireza.pingly.core

import java.util.Calendar
import java.util.TimeZone

/** Persian digits, phone numbers and the Jalali (Solar Hijri) calendar. */
object Persian {
    private const val PERSIAN_DIGITS = "۰۱۲۳۴۵۶۷۸۹"
    private val TEHRAN: TimeZone = TimeZone.getTimeZone("Asia/Tehran")

    val MONTHS = listOf(
        "فروردین", "اردیبهشت", "خرداد", "تیر", "مرداد", "شهریور",
        "مهر", "آبان", "آذر", "دی", "بهمن", "اسفند",
    )

    /** `12` → `۱۲`. Used for text shown outside our own font (notifications). */
    fun digits(input: String): String = buildString(input.length) {
        for (ch in input) append(if (ch in '0'..'9') PERSIAN_DIGITS[ch - '0'] else ch)
    }

    fun digits(number: Number): String = digits(number.toString())

    /** `۰۹۱۲` / `٠٩١٢` → `0912`. */
    fun toAsciiDigits(input: String): String = buildString(input.length) {
        for (ch in input) {
            append(
                when (ch) {
                    in '۰'..'۹' -> '0' + (ch - '۰')
                    in '٠'..'٩' -> '0' + (ch - '٠')
                    else -> ch
                },
            )
        }
    }

    /**
     * Normalises an Iranian mobile number typed in any common form to `09xxxxxxxxx`,
     * or returns null. Mirrors the server's rules so errors show up before a round trip.
     */
    fun normalizePhone(input: String): String? {
        var d = toAsciiDigits(input).filterNot { it == ' ' || it == '-' || it == '(' || it == ')' || it == '.' }
        if (d.startsWith("+")) d = d.drop(1)
        if (d.isEmpty() || !d.all { it.isDigit() }) return null
        d = when {
            d.startsWith("0098") -> d.drop(4)
            d.startsWith("98") && d.length == 12 -> d.drop(2)
            d.startsWith("0") && d.length == 11 -> d.drop(1)
            else -> d
        }
        return if (d.length == 10 && d.startsWith("9")) "0$d" else null
    }

    /**
     * `09121234567` → `۰۹۱۲ ۱۲۳ ۴۵۶۷`, wrapped in a left-to-right isolate so the digit groups
     * keep their order inside right-to-left sentences.
     */
    fun prettyPhone(local: String): String {
        val grouped = if (local.length == 11) "${local.take(4)} ${local.substring(4, 7)} ${local.takeLast(4)}" else local
        return "\u2066${digits(grouped)}\u2069"
    }

    data class JalaliDate(val year: Int, val month: Int, val day: Int) {
        val monthName: String get() = MONTHS[month - 1]
        override fun toString() = digits("$day $monthName $year")
    }

    /** Gregorian → Jalali (algorithm by Kazimierz M. Borkowski, as used by jalaali-js). */
    fun toJalali(gy: Int, gm: Int, gd: Int): JalaliDate {
        val gDaysInMonth = intArrayOf(0, 31, 59, 90, 120, 151, 181, 212, 243, 273, 304, 334)
        val gy2 = if (gm > 2) gy + 1 else gy
        var days = 355666 + 365 * gy + (gy2 + 3) / 4 - (gy2 + 99) / 100 + (gy2 + 399) / 400 + gd + gDaysInMonth[gm - 1]
        var jy = -1595 + 33 * (days / 12053)
        days %= 12053
        jy += 4 * (days / 1461)
        days %= 1461
        if (days > 365) {
            jy += (days - 1) / 365
            days = (days - 1) % 365
        }
        val jm = if (days < 186) 1 + days / 31 else 7 + (days - 186) / 30
        val jd = 1 + if (days < 186) days % 31 else (days - 186) % 30
        return JalaliDate(jy, jm, jd)
    }

    fun toJalali(epochMillis: Long): JalaliDate {
        val cal = Calendar.getInstance(TEHRAN).apply { timeInMillis = epochMillis }
        return toJalali(cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1, cal.get(Calendar.DAY_OF_MONTH))
    }

    /** `۱۴:۰۵` in Tehran time. */
    fun clock(epochMillis: Long): String {
        val cal = Calendar.getInstance(TEHRAN).apply { timeInMillis = epochMillis }
        return digits("%02d:%02d".format(cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE)))
    }

    private fun dayIndex(epochMillis: Long): Long {
        val offset = TEHRAN.getOffset(epochMillis)
        return Math.floorDiv(epochMillis + offset, 86_400_000L)
    }

    /** Whole days between two instants, counted on the Tehran calendar. */
    fun daysBetween(fromMillis: Long, toMillis: Long): Long = dayIndex(toMillis) - dayIndex(fromMillis)

    /** "همین الان", "۵ دقیقه پیش", "دیروز ۲۲:۱۰", "۲۸ آبان". */
    fun relative(epochMillis: Long, now: Long = System.currentTimeMillis()): String {
        val diff = now - epochMillis
        val days = daysBetween(epochMillis, now)
        return when {
            diff < 60_000 -> "همین الان"
            diff < 3_600_000 -> digits("${diff / 60_000} دقیقه پیش")
            days == 0L -> digits("${diff / 3_600_000} ساعت پیش")
            days == 1L -> "دیروز ${clock(epochMillis)}"
            else -> dayLabel(epochMillis, now)
        }
    }

    /** Section header for history lists: "امروز", "دیروز" or the Jalali date. */
    fun dayLabel(epochMillis: Long, now: Long = System.currentTimeMillis()): String {
        val days = daysBetween(epochMillis, now)
        if (days == 0L) return "امروز"
        if (days == 1L) return "دیروز"
        val date = toJalali(epochMillis)
        val thisYear = toJalali(now).year
        return if (date.year == thisYear) digits("${date.day} ${date.monthName}") else date.toString()
    }

    /** "۲ دقیقه" / "۱:۲۳" style durations for voice notes. */
    fun duration(millis: Long): String {
        val totalSeconds = (millis + 500) / 1000
        return digits("%d:%02d".format(totalSeconds / 60, totalSeconds % 60))
    }
}
