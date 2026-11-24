package com.finora.app.finance

import com.finora.app.data.Account
import com.finora.app.util.Fa
import com.finora.app.util.Jalali
import java.time.DayOfWeek
import java.time.LocalDate

/** «موجودی بانک ملی» / «موجودی کارت X» (web: accountBalanceLabel) */
fun accountBalanceLabel(a: Account): String =
    if (Regex("^(کارت|حساب|بانک)").containsMatchIn(a.name)) "موجودی ${a.name}" else "موجودی کارت ${a.name}"

/** Jalali date-picker maths (web: jd* helpers). Dates travel as Gregorian ISO days ("yyyy-MM-dd"). */
object Jd {
    data class J(val y: Int, val m: Int, val d: Int)

    fun toJ(iso: String): J? {
        if (!Regex("^\\d{4}-\\d{2}-\\d{2}$").matches(iso)) return null
        val dt = runCatching { LocalDate.parse(iso) }.getOrNull() ?: return null
        val j = Jalali.toJalali(dt.year, dt.monthValue, dt.dayOfMonth)
        return J(j.y, j.m, j.d)
    }

    fun fromJ(jy: Int, jm: Int, jd: Int): String {
        val g = Jalali.fromJalali(jy, jm, jd)
        return String.format("%04d-%02d-%02d", g.y, g.m, g.d)
    }

    fun label(iso: String): String {
        val j = toJ(iso) ?: return ""
        return "${Fa.digits(j.d)} ${Jalali.monthNames[j.m - 1]} ${Fa.digits(j.y)}"
    }

    fun daysIn(jy: Int, jm: Int): Int = Jalali.daysInMonth(jy, jm)

    /** blank cells before day 1 in a Saturday-first week */
    fun leadingBlanks(jy: Int, jm: Int): Int {
        val g = Jalali.fromJalali(jy, jm, 1)
        val dow: DayOfWeek = LocalDate.of(g.y, g.m, g.d).dayOfWeek
        return (dow.value % 7 + 1) % 7
    }

    fun ym(y: Int, m: Int): Int = y * 12 + (m - 1)
}
