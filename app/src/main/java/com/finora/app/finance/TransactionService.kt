package com.finora.app.finance

import com.finora.app.data.DataParser
import com.finora.app.data.Keys
import com.finora.app.data.KeyValueStore
import com.finora.app.data.Ledger
import com.finora.app.data.Transaction
import com.finora.app.util.Fa
import com.finora.app.util.Jalali
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/** Everything the add/edit sheet submits. Dates are Gregorian ISO days ("yyyy-MM-dd"), like the web app's hidden input. */
data class TxInput(
    val amount: Long,
    val note: String,
    val type: String,            // income | expense
    val categoryKey: String?,
    val accountId: String?,      // null / "" = no account
    val dateIso: String,
    val editingId: String? = null
)

sealed class TxResult {
    object Ok : TxResult()
    /** the web app shows alert(message) and aborts */
    data class Invalid(val message: String) : TxResult()
    /** the web app shows confirm(message); call again with confirmed = true to go on */
    data class NeedsConfirm(val message: String) : TxResult()
}

/** add / edit / delete of transactions — a port of addTransaction(), editTransaction(), deleteTransaction(). */
class TransactionService(
    private val store: KeyValueStore,
    private val ledger: Ledger,
    private val zone: ZoneId = ZoneId.systemDefault(),
    private val nowMillis: () -> Long = { System.currentTimeMillis() },
    private val onChange: (String) -> Unit = {}
) {
    // ───────────── Jalali/ISO date helpers (jd*) ─────────────

    fun todayIso(): String = isoFromTs(nowMillis())

    fun isoFromTs(ms: Long): String {
        val d = Instant.ofEpochMilli(ms).atZone(zone).toLocalDate()
        return String.format("%04d-%02d-%02d", d.year, d.monthValue, d.dayOfMonth)
    }

    fun currentMonthStartIso(): String {
        val p = Jalali.parts(nowMillis(), zone)
        val g = Jalali.fromJalali(p.y, p.m, 1)
        return String.format("%04d-%02d-%02d", g.y, g.m, g.d)
    }

    private fun parseIso(iso: String): LocalDate? =
        if (Regex("^\\d{4}-\\d{2}-\\d{2}$").matches(iso)) runCatching { LocalDate.parse(iso) }.getOrNull() else null

    /** jdTsFromIso(iso, timeSource): that day, keeping the time of day of [timeSource] */
    fun tsFromIso(iso: String, timeSource: Long?): Long {
        val d = parseIso(iso)!!
        val t = Instant.ofEpochMilli(timeSource ?: nowMillis()).atZone(zone).toLocalTime()
        return LocalDateTime.of(d, t).atZone(zone).toInstant().toEpochMilli()
    }

    // ───────────── helpers ─────────────

    private fun fallbackKey(type: String): String {
        val cats = DataParser.parseCategories(store.get(Keys.CATEGORIES))
        return (cats.firstOrNull { it.builtin && it.type == type } ?: cats.firstOrNull { it.type == type }
            ?: cats.firstOrNull { it.type == "expense" })?.key ?: "other"
    }

    private fun accountBalances(): Finance {
        val data = DataParser.parse(store)
        return Finance(data, zone, nowMillis())
    }

    fun save(input: TxInput, confirmedLowBalance: Boolean = false): TxResult {
        if (input.amount <= 0) return TxResult.Invalid("مبلغ معتبر وارد کن.")
        val cats = DataParser.parseCategories(store.get(Keys.CATEGORIES))
        val valid = cats.filter { it.type == input.type }
        val chosen = valid.firstOrNull { it.key == input.categoryKey } ?: valid.firstOrNull() ?: cats.firstOrNull()
        val categoryKey = chosen?.key ?: fallbackKey(input.type)
        val today = todayIso()
        val monthStart = currentMonthStartIso()
        val fin = accountBalances()
        val accounts = fin.data.accounts
        val accId = input.accountId?.takeIf { id -> accounts.any { it.id == id } }
        var dateIso = if (parseIso(input.dateIso) != null) input.dateIso else today

        val live = ledger.liveTransactions().toMutableList()
        val note = input.note.trim()

        if (input.editingId != null) {
            val idx = live.indexOfFirst { it.id == input.editingId }
            if (idx != -1) {
                val prev = live[idx]
                val dateChanged = isoFromTs(prev.createdAt) != dateIso
                if (dateChanged && (dateIso < monthStart || dateIso > today)) {
                    return TxResult.Invalid("تاریخ تراکنش باید بین اول ماه جاری و امروز باشه.")
                }
                live[idx] = prev.copy(
                    amount = input.amount, note = note, category = categoryKey, type = input.type,
                    createdAt = if (dateChanged) tsFromIso(dateIso, prev.createdAt) else prev.createdAt,
                    accountId = accId
                )
            }
        } else {
            if (dateIso > today) dateIso = today
            if (dateIso < monthStart) return TxResult.Invalid("تاریخ تراکنش باید در ماه جاری باشه.")
            if (input.type == "expense" && accId != null && input.amount > (fin.accountBalances().map[accId] ?: 0.0) && !confirmedLowBalance) {
                return TxResult.NeedsConfirm("موجودی این حساب از مبلغ تراکنش کمتره. با این حال ثبت بشه؟")
            }
            live.add(
                0, Transaction(
                    id = DataParser.newId(), amount = input.amount, note = note, category = categoryKey, type = input.type,
                    createdAt = if (dateIso == today) nowMillis() else tsFromIso(dateIso, null), accountId = accId
                )
            )
        }
        // web quirk kept on purpose: editingTransactionId is already cleared at this point, so this always runs
        store.set(Keys.LAST_ACCOUNT, accId ?: "")
        live.sortWith { a, b -> b.createdAt.compareTo(a.createdAt) }
        ledger.persistTransactions(live)
        return TxResult.Ok
    }

    fun delete(id: String) {
        val live = ledger.liveTransactions()
        val removed = live.firstOrNull { it.id == id }
        ledger.persistTransactions(live.filter { it.id != id })
        if (removed?.loanId != null) {
            val raw = store.get(Keys.LOANS)
            val arr = DataParser.safeJson(raw) as? JSONArray
            if (arr != null) {
                var changed = false
                for (i in 0 until arr.length()) {
                    val o = arr.optJSONObject(i) ?: continue
                    if (o.optString("id") == removed.loanId) {
                        val paid = o.optDouble("paidInstallments", 0.0)
                        if (paid > 0) {
                            val next = paid - 1
                            o.put("paidInstallments", if (next == Math.floor(next)) next.toLong() else next)
                            changed = true
                        }
                    }
                }
                if (changed) {
                    store.set(Keys.LOANS, arr.toString())
                    onChange(Keys.LOANS)
                }
            }
        }
    }

    // ───────────── category suggestion from the note ─────────────

    /** suggestCategoryFromNote(): the longest matching keyword wins; only categories that exist are suggested. */
    fun suggestCategory(note: String, categories: List<com.finora.app.data.Category>): com.finora.app.data.Category? {
        if (note.trim().length < 2) return null
        val lower = note.trim().lowercase()
        var best: String? = null
        var bestScore = 0
        for ((catKey, words) in CategoryKeywords.map) {
            for (kw in words) {
                if (lower.contains(kw.lowercase())) {
                    if (kw.length > bestScore) { bestScore = kw.length; best = catKey }
                }
            }
        }
        return best?.let { key -> categories.firstOrNull { it.key == key } }
    }
}

/** The amount fields of the web app: digits only (any digit script), max 15, grouped with Persian digits. */
object AmountInput {
    private const val MAX_DIGITS = 15
    private val ONES = listOf("صفر", "یک", "دو", "سه", "چهار", "پنج", "شش", "هفت", "هشت", "نه")
    private val TEENS = listOf("ده", "یازده", "دوازده", "سیزده", "چهارده", "پانزده", "شانزده", "هفده", "هجده", "نوزده")
    private val TENS = listOf("", "", "بیست", "سی", "چهل", "پنجاه", "شصت", "هفتاد", "هشتاد", "نود")
    private val HUND = listOf("", "صد", "دویست", "سیصد", "چهارصد", "پانصد", "ششصد", "هفتصد", "هشتصد", "نهصد")
    private val SCALES = listOf("", "هزار", "میلیون", "میلیارد", "هزار میلیارد")

    fun digitsOnly(text: String): String {
        val d = Fa.normalizeDigits(text).filter { it in '0'..'9' }.replace(Regex("^0+(?=\\d)"), "")
        return d.take(MAX_DIGITS)
    }

    fun formatDigits(d: String): String {
        if (d.isEmpty()) return ""
        val grouped = d.replace(Regex("\\B(?=(\\d{3})+(?!\\d))"), ",")
        return Fa.digits(grouped).replace(',', '\u066C')
    }

    private fun threeDigitWords(n: Int): String {
        val out = ArrayList<String>()
        val h = n / 100
        val r = n % 100
        if (h != 0) out.add(HUND[h])
        if (r != 0) {
            if (r < 10) out.add(ONES[r])
            else if (r < 20) out.add(TEENS[r - 10])
            else {
                val t = r / 10
                val o = r % 10
                out.add(if (o != 0) TENS[t] + " و " + ONES[o] else TENS[t])
            }
        }
        return out.joinToString(" و ")
    }

    fun numberToWords(d: String): String {
        if (d.isEmpty()) return ""
        if (Regex("^0+$").matches(d)) return "صفر"
        val parts = ArrayList<String>()
        var i = 0
        var s = d
        while (s.isNotEmpty()) {
            val chunk = s.takeLast(3).toInt()
            s = s.dropLast(3)
            if (chunk != 0) {
                var w = threeDigitWords(chunk)
                if (i == 1 && chunk == 1) w = ""
                parts.add(0, ((if (w.isNotEmpty()) "$w " else "") + SCALES[i]).trim())
            }
            i++
        }
        return parts.joinToString(" و ")
    }
}
