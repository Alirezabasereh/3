package com.finora.app.ui.sheets

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.finora.app.core.AppController
import com.finora.app.finance.AmountInput
import com.finora.app.finance.Jd
import com.finora.app.finance.TxFilters
import com.finora.app.ui.common.FText
import com.finora.app.ui.common.FaGlyph
import com.finora.app.ui.common.FaIcon
import com.finora.app.ui.common.boxShadow
import com.finora.app.ui.common.cssLinearGradient
import com.finora.app.ui.theme.Finora
import com.finora.app.ui.theme.rgba

private enum class Pick { None, Type, Category, Account, Sort, From, To }

/** The "فیلتر تراکنش‌ها" modal. */
@Composable
fun FilterSheet(controller: AppController, onClose: () -> Unit) {
    val c = Finora.colors
    val data by controller.data.collectAsState()
    val committed by controller.txFilters.collectAsState()
    var f by remember { mutableStateOf(committed) }
    var pick by remember { mutableStateOf(Pick.None) }
    val today = remember { controller.transactions.todayIso() }

    fun update(n: TxFilters) { f = n }

    val typeOptions = listOf("all" to "همه تراکنش‌ها", "expense" to "فقط هزینه‌ها", "income" to "فقط درآمدها")
    val catOptions = listOf("all" to "همه دسته‌بندی‌ها") + data.categories.map { it.key to "${it.emoji} ${it.name}" }
    val accOptions = listOf("all" to "همه حساب‌ها", "none" to "👛 بدون حساب") + data.accounts.map { it.id to "${it.icon} ${it.name}" }
    val sortOptions = listOf("dateDesc" to "جدیدترین (پیش‌فرض)", "dateAsc" to "قدیمی‌ترین", "amountDesc" to "بیشترین مبلغ", "amountAsc" to "کمترین مبلغ")
    fun labelOf(opts: List<Pair<String, String>>, v: String) = opts.firstOrNull { it.first == v }?.second ?: opts.first().second

    ModalOverlay(onDismiss = onClose) { maxH ->
        ModalCard(maxH, bottomPadding = 18.dp) {
            Column(Modifier.fillMaxWidth()) {
                ModalHead(
                    title = { FText("فیلتر تراکنش‌ها", 18.4f, weight = FontWeight.ExtraBold, color = c.text) },
                    subtitle = "بر اساس دسته بندی، حساب، تاریخ و مبلغ", onClose = onClose
                )
                Column(
                    Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()).padding(start = 2.dp, end = 2.dp, bottom = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    TextInput(f.query, { update(f.copy(query = it)) }, "جستجو در تراکنش‌ها", horizontalPad = 16.dp)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        SelectBox(labelOf(typeOptions, f.type), Modifier.weight(1f)) { pick = Pick.Type }
                        SelectBox(labelOf(catOptions, f.category), Modifier.weight(1f)) { pick = Pick.Category }
                    }
                    if (data.accounts.isNotEmpty()) SelectBox(labelOf(accOptions, f.account)) { pick = Pick.Account }

                    Column {
                        FieldLabel("مبلغ از (تومان)", 6.dp)
                        TextInput(
                            f.minAmount, { update(f.copy(minAmount = AmountInput.digitsOnly(it))) }, "مثلا ۵۰۰۰۰", ltr = true,
                            keyboardType = KeyboardType.Number, transformation = AmountTransformation(), horizontalPad = 16.dp
                        )
                        FText(
                            if (f.minAmount.isNotEmpty()) AmountInput.numberToWords(f.minAmount) + " تومان" else "", 12.8f,
                            Modifier.fillMaxWidth().padding(top = 6.dp).heightIn(min = 20.5.dp), FontWeight.SemiBold, c.muted, lineHeight = 20.48f
                        )
                    }
                    Column {
                        FieldLabel("مبلغ تا (تومان)", 6.dp)
                        TextInput(
                            f.maxAmount, { update(f.copy(maxAmount = AmountInput.digitsOnly(it))) }, "مثلا ۵۰۰۰۰۰۰", ltr = true,
                            keyboardType = KeyboardType.Number, transformation = AmountTransformation(), horizontalPad = 16.dp
                        )
                        FText(
                            if (f.maxAmount.isNotEmpty()) AmountInput.numberToWords(f.maxAmount) + " تومان" else "", 12.8f,
                            Modifier.fillMaxWidth().padding(top = 6.dp).heightIn(min = 20.5.dp), FontWeight.SemiBold, c.muted, lineHeight = 20.48f
                        )
                    }
                    Column {
                        FieldLabel("از تاریخ", 6.dp)
                        DateFieldButton(if (f.from.isNotEmpty()) Jd.label(f.from) else "انتخاب تاریخ", f.from.isNotEmpty()) { pick = Pick.From }
                    }
                    Column {
                        FieldLabel("تا تاریخ", 6.dp)
                        DateFieldButton(if (f.to.isNotEmpty()) Jd.label(f.to) else "انتخاب تاریخ", f.to.isNotEmpty()) { pick = Pick.To }
                    }
                    SelectBox(labelOf(sortOptions, f.sortBy)) { pick = Pick.Sort }
                }

                // actions
                Row(Modifier.fillMaxWidth().padding(top = 14.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    val applyShape = RoundedCornerShape(16.dp)
                    Box(
                        Modifier
                            .weight(1f)
                            .height(48.dp)
                            .then(
                                if (c.isLight) Modifier.boxShadow(rgba(90, 73, 242, .40f), offsetY = 8.dp, blur = 20.dp, spread = (-4).dp, radius = 16.dp)
                                else Modifier.boxShadow(rgba(106, 92, 255, .35f), offsetY = 8.dp, blur = 24.dp, radius = 16.dp)
                            )
                            .clip(applyShape)
                            .drawBehind {
                                drawRect(
                                    cssLinearGradient(
                                        135f, size.width, size.height,
                                        0f to (if (c.isLight) Color(0xFF5A49F2) else Color(0xFF6A5CFF)),
                                        1f to (if (c.isLight) Color(0xFFD6247F) else Color(0xFFFF3CAC))
                                    )
                                )
                            }
                            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                                controller.setTxFilters(f); onClose()
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            FaIcon(FaGlyph.CHECK, 15.68f, color = Color.White)
                            FText("اعمال فیلتر", 15.68f, weight = FontWeight.ExtraBold, color = Color.White)
                        }
                    }
                    val clearShape = RoundedCornerShape(16.dp)
                    Row(
                        Modifier
                            .height(48.dp)
                            .clip(clearShape)
                            .border(1.dp, if (c.isLight) rgba(15, 23, 42, .16f) else rgba(255, 255, 255, .10f), clearShape)
                            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                                f = TxFilters(); controller.setTxFilters(f)
                            }
                            .padding(horizontal = 14.dp),
                        horizontalArrangement = Arrangement.spacedBy(7.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        FaIcon(FaGlyph.ROTATE_LEFT, 13.12f, color = c.muted)
                        FText("پاک‌سازی", 13.12f, weight = FontWeight.SemiBold, color = c.muted, maxLines = 1, softWrap = false)
                    }
                }
            }
        }

        when (pick) {
            Pick.Type -> PickerDialog("نوع تراکنش", typeOptions, f.type, { update(f.copy(type = it)); pick = Pick.None }, { pick = Pick.None })
            Pick.Category -> PickerDialog("دسته‌بندی", catOptions, f.category, { update(f.copy(category = it)); pick = Pick.None }, { pick = Pick.None })
            Pick.Account -> PickerDialog("حساب", accOptions, f.account, { update(f.copy(account = it)); pick = Pick.None }, { pick = Pick.None })
            Pick.Sort -> PickerDialog("مرتب‌سازی", sortOptions, f.sortBy, { update(f.copy(sortBy = it)); pick = Pick.None }, { pick = Pick.None })
            Pick.From -> JalaliPickerDialog(
                "از تاریخ", f.from, null, null, today, true,
                onPick = { update(f.copy(from = it)); pick = Pick.None }, onClear = { update(f.copy(from = "")); pick = Pick.None }, onDismiss = { pick = Pick.None }
            )
            Pick.To -> JalaliPickerDialog(
                "تا تاریخ", f.to, null, null, today, true,
                onPick = { update(f.copy(to = it)); pick = Pick.None }, onClear = { update(f.copy(to = "")); pick = Pick.None }, onDismiss = { pick = Pick.None }
            )
            Pick.None -> {}
        }
    }
}
