package com.finora.app.ui.sheets

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.finora.app.core.AppController
import com.finora.app.data.Category
import com.finora.app.data.Transaction
import com.finora.app.finance.AmountInput
import com.finora.app.finance.Finance
import com.finora.app.finance.Jd
import com.finora.app.finance.TxInput
import com.finora.app.finance.TxResult
import com.finora.app.finance.accountBalanceLabel
import com.finora.app.ui.common.AcctChip
import com.finora.app.ui.common.AcctIcon
import com.finora.app.ui.common.FText
import com.finora.app.ui.common.FaGlyph
import com.finora.app.ui.common.FaIcon
import com.finora.app.ui.common.Wrap
import com.finora.app.ui.common.boxShadow
import com.finora.app.ui.common.cssLinearGradient
import com.finora.app.ui.common.parseHex
import com.finora.app.ui.theme.Finora
import com.finora.app.ui.theme.rgba
import com.finora.app.util.Fa
import kotlinx.coroutines.delay

private val FREE_COLOR = Color(0xFF94A3B8)

/** Shows grouped Persian digits while the state keeps plain ASCII digits (web: the amount-input module). */
class AmountTransformation : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val formatted = AmountInput.formatDigits(text.text)
        val origToTrans = IntArray(text.text.length + 1)
        val transToOrig = IntArray(formatted.length + 1)
        var digits = 0
        for (i in formatted.indices) {
            transToOrig[i] = digits
            if (formatted[i] != '\u066C') { digits++; origToTrans[digits] = i + 1 }
        }
        transToOrig[formatted.length] = digits
        val mapping = object : OffsetMapping {
            override fun originalToTransformed(offset: Int): Int = origToTrans[offset.coerceIn(0, text.text.length)]
            override fun transformedToOriginal(offset: Int): Int = transToOrig[offset.coerceIn(0, formatted.length)]
        }
        return TransformedText(AnnotatedString(formatted), mapping)
    }
}

private fun mix(c: Color, a: Float, base: Color) = Color(
    c.red * a + base.red * (1 - a), c.green * a + base.green * (1 - a), c.blue * a + base.blue * (1 - a), 1f
)

private sealed class Dlg {
    data class Alert(val message: String) : Dlg()
    data class Confirm(val message: String, val input: TxInput) : Dlg()
}

/** The "ثبت تراکنش" / "ویرایش تراکنش" modal. */
@Composable
fun TransactionSheet(controller: AppController, editing: Transaction?, onClose: () -> Unit) {
    val c = Finora.colors
    val data by controller.data.collectAsState()
    val svc = controller.transactions
    val today = remember { svc.todayIso() }
    val monthStart = remember { svc.currentMonthStartIso() }

    var type by rememberSaveable { mutableStateOf(editing?.type ?: "expense") }
    var digits by rememberSaveable { mutableStateOf(editing?.amount?.toString() ?: "") }
    var note by rememberSaveable { mutableStateOf(editing?.note ?: "") }
    val validNow = data.categories.filter { it.type == type }
    var category by rememberSaveable {
        mutableStateOf(editing?.category ?: (data.categories.firstOrNull { it.builtin && it.type == "expense" } ?: data.categories.firstOrNull { it.type == "expense" })?.key ?: "other")
    }
    var account by rememberSaveable {
        mutableStateOf(
            if (editing != null) (editing.accountId?.takeIf { id -> data.accounts.any { it.id == id } } ?: "") else controller.defaultTxAccount()
        )
    }
    var dateIso by rememberSaveable { mutableStateOf(editing?.let { svc.isoFromTs(it.createdAt) } ?: today) }
    var dialog by remember { mutableStateOf<Dlg?>(null) }
    var pickDate by remember { mutableStateOf(false) }
    var suggestion by remember { mutableStateOf<Category?>(null) }
    var suggestionDismissed by remember { mutableStateOf(false) }

    val fin = remember(data) { Finance(data, controller.zone, System.currentTimeMillis()) }
    val balances = remember(data) { fin.accountBalances() }

    fun setType(t: String) {
        type = t
        val valid = data.categories.filter { it.type == t }
        if (valid.none { it.key == category }) category = valid.firstOrNull()?.key ?: category
    }

    LaunchedEffect(note, type) {
        suggestionDismissed = false
        delay(400)
        suggestion = svc.suggestCategory(note, data.categories.filter { it.type == type })
    }

    fun submit(confirmed: Boolean, input: TxInput? = null) {
        val inp = input ?: TxInput(digits.toLongOrNull() ?: 0L, note, type, category, account, dateIso, editing?.id)
        when (val r = controller.saveTransaction(inp, confirmed)) {
            is TxResult.Ok -> onClose()
            is TxResult.Invalid -> dialog = Dlg.Alert(r.message)
            is TxResult.NeedsConfirm -> dialog = Dlg.Confirm(r.message, inp)
        }
    }

    ModalOverlay(onDismiss = onClose) { maxH ->
        ModalCard(maxH, bottomPadding = 28.dp) {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                ModalHead(
                    title = {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            FText(if (editing != null) "ویرایش تراکنش" else "ثبت تراکنش", 18.4f, weight = FontWeight.ExtraBold, color = c.text)
                            if (editing != null) {
                                Row(
                                    Modifier.clip(RoundedCornerShape(99.dp)).background(c.accent1.copy(alpha = .18f)).padding(horizontal = 9.dp, vertical = 3.dp),
                                    horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically
                                ) {
                                    FaIcon(FaGlyph.PEN, 9.6f, color = if (c.isLight) Color(0xFF4A3FD6) else Color(0xFFA89FFF))
                                    FText("ویرایش", 11.5f, weight = FontWeight.Bold, color = if (c.isLight) Color(0xFF4A3FD6) else Color(0xFFA89FFF))
                                }
                            }
                        }
                    },
                    subtitle = if (editing != null) "تغییرات رو اعمال و ذخیره کن" else "هزینه یا درآمد جدید اضافه کن",
                    onClose = onClose
                )

                // type switch
                Row(Modifier.fillMaxWidth().padding(bottom = 14.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    TypeButton("هزینه", type == "expense", Modifier.weight(1f)) { setType("expense") }
                    TypeButton("درآمد", type == "income", Modifier.weight(1f)) { setType("income") }
                }

                // amount
                FieldLabel("مبلغ")
                Box(Modifier.fillMaxWidth()) {
                    TextInput(
                        digits, { digits = AmountInput.digitsOnly(it) }, "۰", ltr = true, keyboardType = KeyboardType.Number,
                        transformation = AmountTransformation(), minHeight = 62.dp, fontSize = 24.8f, weight = FontWeight.ExtraBold,
                        center = true, horizontalPad = 68.dp
                    )
                    FText("تومان", 13.6f, Modifier.align(Alignment.CenterEnd).padding(end = 16.dp), FontWeight.SemiBold, c.muted)
                }
                FText(
                    if (digits.isNotEmpty()) AmountInput.numberToWords(digits) + " تومان" else "", 14.08f,
                    Modifier.fillMaxWidth().padding(top = 6.dp).heightIn(min = 22.5.dp), FontWeight.SemiBold,
                    if (c.isLight) c.accent2 else c.text, TextAlign.Center, lineHeight = 22.528f
                )
                Spacer(Modifier.height(18.dp))

                // note
                FieldLabel("توضیحات (اختیاری)")
                TextInput(note, { note = it }, "ناهار با دوستان", singleLine = false, minHeight = 57.dp, horizontalPad = 16.dp)
                val sug = suggestion
                if (sug != null && sug.key != category && !suggestionDismissed) {
                    SuggestionRow(sug, onApply = { category = sug.key; suggestionDismissed = true }, onDismiss = { suggestionDismissed = true })
                }
                Spacer(Modifier.height(14.dp))

                // category
                FieldLabel("دسته‌بندی")
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    for (row in validNow.chunked(3)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            for (cat in row) CategoryButton(cat, cat.key == category, Modifier.weight(1f)) { category = cat.key }
                            repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                        }
                    }
                }
                Spacer(Modifier.height(14.dp))

                // account
                if (data.accounts.isNotEmpty()) {
                    FieldLabel(if (type == "income") "به کدوم حساب اضافه بشه؟" else "از کدوم حساب کم بشه؟")
                    Wrap(Modifier.fillMaxWidth(), hGap = 8, vGap = 8) {
                        AcctChip("بدون حساب", FREE_COLOR, account.isEmpty(), onClick = { account = "" }) { AcctIcon("👛", FREE_COLOR, null) }
                        for (a in data.accounts) {
                            AcctChip(a.name, parseHex(a.color), account == a.id, onClick = { account = a.id }) {
                                AcctIcon(a.icon, parseHex(a.color), com.finora.app.finance.Banks.detect(a.name)?.id)
                            }
                        }
                    }
                    val acc = data.accounts.firstOrNull { it.id == account }
                    val bal = if (acc != null) (balances.map[acc.id] ?: 0.0) else balances.free
                    val amt = digits.toLongOrNull() ?: 0L
                    var hint = (if (acc != null) accountBalanceLabel(acc) else "موجودی بدون حساب") + ": " + Fa.money(bal)
                    var warn = false
                    if (type == "expense" && editing == null && amt > bal) { hint += " — این مبلغ از موجودی بیشتره"; warn = true }
                    FText(
                        hint, 12.48f, Modifier.fillMaxWidth().padding(top = 8.dp), color = if (warn) (if (c.isLight) Color(0xFFC2700A) else c.warn) else c.muted,
                        lineHeight = 12.48f * 1.6f
                    )
                    Spacer(Modifier.height(14.dp))
                }

                // date
                FieldLabel("تاریخ تراکنش")
                Row(Modifier.fillMaxWidth().padding(top = 2.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    val has = Jd.toJ(dateIso) != null
                    val label = if (has) (if (dateIso == today) "امروز، " else "") + Jd.label(dateIso) else "امروز"
                    Box(Modifier.weight(1f)) { DateFieldButton(label, has) { pickDate = true } }
                    if (has && dateIso != today) {
                        val shape = RoundedCornerShape(12.dp)
                        Box(
                            Modifier
                                .height(36.dp)
                                .clip(shape)
                                .background(if (c.isLight) Color(0xFFF5F7FC) else rgba(255, 255, 255, .06f))
                                .border(1.dp, if (c.isLight) rgba(15, 23, 42, .12f) else rgba(255, 255, 255, .10f), shape)
                                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { dateIso = today }
                                .padding(horizontal = 12.dp),
                            contentAlignment = Alignment.Center
                        ) { FText("امروز", 13f, weight = FontWeight.SemiBold, color = c.muted) }
                    }
                }
                Spacer(Modifier.height(14.dp))

                GradientButton(if (editing != null) "ذخیره تغییرات" else "ذخیره تراکنش", Modifier.padding(top = 10.dp)) { submit(false) }
            }
        }
        if (pickDate) {
            JalaliPickerDialog(
                title = "تاریخ تراکنش", value = dateIso, min = monthStart, max = today, todayIso = today, clearable = false,
                onPick = { dateIso = it; pickDate = false }, onClear = {}, onDismiss = { pickDate = false }
            )
        }
        when (val d = dialog) {
            is Dlg.Alert -> MessageDialog(d.message, onConfirm = { dialog = null }, onDismiss = { dialog = null })
            is Dlg.Confirm -> MessageDialog(
                d.message, confirmText = "بله، ثبت کن", cancelText = "انصراف",
                onConfirm = { dialog = null; submit(true, d.input) }, onDismiss = { dialog = null }
            )
            null -> {}
        }
    }
}

@Composable
private fun TypeButton(text: String, active: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val c = Finora.colors
    val shape = RoundedCornerShape(18.dp)
    Box(
        modifier
            .height(53.dp)
            .clip(shape)
            .then(
                if (active) Modifier.drawBehind {
                    drawRect(
                        cssLinearGradient(
                            135f, size.width, size.height,
                            0f to (if (c.isLight) Color(0xFF5A49F2) else Color(0xFF6A5CFF)),
                            1f to (if (c.isLight) Color(0xFFD6247F) else Color(0xFFFF3CAC))
                        )
                    )
                } else Modifier
                    .background(if (c.isLight) Color(0xFFF5F7FC) else rgba(255, 255, 255, .06f))
                    .then(if (c.isLight) Modifier.border(1.dp, rgba(15, 23, 42, .10f), shape) else Modifier)
            )
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center
    ) { FText(text, 16f, weight = FontWeight.Bold, color = if (active) Color.White else c.text, align = TextAlign.Center) }
}

@Composable
private fun CategoryButton(cat: Category, active: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val c = Finora.colors
    val col = parseHex(cat.color)
    val shape = RoundedCornerShape(16.dp)
    val bg = when {
        active && c.isLight -> mix(col, .12f, Color.White)
        active -> mix(col, .82f, Color.White).copy(alpha = .196f)
        c.isLight -> Color(0xFFF5F7FC)
        else -> rgba(255, 255, 255, .043f)
    }
    val border = when {
        active -> col.copy(alpha = .55f)
        c.isLight -> rgba(15, 23, 42, .10f)
        else -> rgba(255, 255, 255, .07f)
    }
    Column(
        modifier
            .offset(y = if (active) (-1).dp else 0.dp)
            .clip(shape)
            .background(bg)
            .border(1.dp, border, shape)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick)
            .padding(start = 6.dp, top = 12.dp, end = 6.dp, bottom = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(7.dp)
    ) {
        Box(
            Modifier
                .size(34.dp)
                .then(if (active) Modifier.boxShadow(col, offsetY = 6.dp, blur = 16.dp, spread = (-6).dp, radius = 11.dp) else Modifier)
                .clip(RoundedCornerShape(11.dp))
                .background(if (active) col else col.copy(alpha = .24f)),
            contentAlignment = Alignment.Center
        ) { FText(cat.emoji, 16.32f, lineHeight = 16.32f, align = TextAlign.Center) }
        FText(
            cat.name, 11.52f, weight = FontWeight.SemiBold, color = if (active && !c.isLight) Color.White else c.text,
            lineHeight = 14.4f, align = TextAlign.Center, maxLines = 1, softWrap = false
        )
    }
}

@Composable
private fun SuggestionRow(cat: Category, onApply: () -> Unit, onDismiss: () -> Unit) {
    val c = Finora.colors
    val shape = RoundedCornerShape(14.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .padding(top = 8.dp)
            .clip(shape)
            .background(c.accent1.copy(alpha = .12f))
            .border(1.dp, c.accent1.copy(alpha = .25f), shape)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        FText("پیشنهاد دسته: ${cat.emoji} ${cat.name}", 13f, Modifier.weight(1f), FontWeight.SemiBold, c.text, maxLines = 1)
        FText(
            "اعمال", 13f,
            Modifier.clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onApply).padding(4.dp),
            FontWeight.Bold, if (c.isLight) Color(0xFF4A3FD6) else Color(0xFFA89FFF)
        )
        FaIcon(
            FaGlyph.XMARK, 12f,
            Modifier.clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onDismiss).padding(4.dp), c.muted
        )
    }
}
