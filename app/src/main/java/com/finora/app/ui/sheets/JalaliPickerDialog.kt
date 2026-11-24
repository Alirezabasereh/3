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
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.finora.app.finance.Jd
import com.finora.app.ui.common.FText
import com.finora.app.ui.common.FaGlyph
import com.finora.app.ui.common.FaIcon
import com.finora.app.ui.common.cssLinearGradient
import com.finora.app.ui.theme.Finora
import com.finora.app.ui.theme.rgba
import com.finora.app.util.Fa
import com.finora.app.util.Jalali

private val WEEK = listOf("ش", "ی", "د", "س", "چ", "پ", "ج")

/** `.jd-dialog` — Jalali month grid with min/max limits, month/year pickers, «امروز», optional «حذف تاریخ». */
@Composable
fun JalaliPickerDialog(
    title: String,
    value: String,
    min: String?,
    max: String?,
    todayIso: String,
    clearable: Boolean,
    yearsAhead: Int = 1,
    onPick: (String) -> Unit,
    onClear: () -> Unit,
    onDismiss: () -> Unit
) {
    val c = Finora.colors
    val minJ = min?.takeIf { it.isNotEmpty() }?.let { Jd.toJ(it) }
    val maxJ = max?.takeIf { it.isNotEmpty() }?.let { Jd.toJ(it) }
    val nowJ = Jd.toJ(todayIso)!!

    // seed the visible month (web: jdOpen)
    var seed = if (Jd.toJ(value) != null) value else todayIso
    if (!max.isNullOrEmpty() && seed > max) seed = max
    if (!min.isNullOrEmpty() && seed < min) seed = min
    val seedJ = Jd.toJ(seed)!!
    var vy by remember { mutableStateOf(seedJ.y) }
    var vm by remember { mutableStateOf(seedJ.m) }
    var sub by remember { mutableStateOf(0) }   // 1 = month list, 2 = year list

    fun clamp() {
        var cur = Jd.ym(vy, vm)
        if (minJ != null) cur = maxOf(cur, Jd.ym(minJ.y, minJ.m))
        if (maxJ != null) cur = minOf(cur, Jd.ym(maxJ.y, maxJ.m))
        vy = Math.floorDiv(cur, 12)
        vm = (cur % 12) + 1
    }

    val canPrev = minJ == null || Jd.ym(vy, vm) > Jd.ym(minJ.y, minJ.m)
    val canNext = maxJ == null || Jd.ym(vy, vm) < Jd.ym(maxJ.y, maxJ.m)
    var y0 = minJ?.y ?: (nowJ.y - 8)
    var y1 = maxJ?.y ?: (nowJ.y + yearsAhead)
    y0 = minOf(y0, vy); y1 = maxOf(y1, vy)
    val todayOk = !(!min.isNullOrEmpty() && todayIso < min) && !(!max.isNullOrEmpty() && todayIso > max)

    DialogHost(onDismiss) {
        Row(Modifier.fillMaxWidth().padding(bottom = 12.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            NavButton(FaGlyph.CHEVRON_RIGHT, canPrev) {
                vm--; if (vm < 1) { vm = 12; vy-- }; clamp()
            }
            Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                SelectChip(Jalali.monthNames[vm - 1], Modifier.weight(1f)) { sub = 1 }
                SelectChip(Fa.digits(vy), Modifier.weight(1f)) { sub = 2 }
            }
            NavButton(FaGlyph.CHEVRON_LEFT, canNext) {
                vm++; if (vm > 12) { vm = 1; vy++ }; clamp()
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            for (w in WEEK) FText(w, 11.52f, Modifier.weight(1f).padding(vertical = 4.dp), color = c.muted, align = TextAlign.Center)
        }
        val lead = Jd.leadingBlanks(vy, vm)
        val n = Jd.daysIn(vy, vm)
        val cells: List<Int> = List(lead) { 0 } + (1..n).toList()
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            for (row in cells.chunked(7)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    for (d in row) {
                        if (d == 0) Spacer(Modifier.weight(1f).aspectRatio(1f))
                        else {
                            val iso = Jd.fromJ(vy, vm, d)
                            val disabled = (!min.isNullOrEmpty() && iso < min) || (!max.isNullOrEmpty() && iso > max)
                            DayCell(d, iso == value, iso == todayIso, disabled, Modifier.weight(1f)) { onPick(iso) }
                        }
                    }
                    repeat(7 - row.size) { Spacer(Modifier.weight(1f).aspectRatio(1f)) }
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FooterButton("امروز", Modifier.weight(1f), primary = true, enabled = todayOk) { onPick(todayIso) }
            if (clearable) FooterButton("حذف تاریخ", Modifier.weight(1f)) { onClear() }
            FooterButton("بستن", Modifier.weight(1f), onClick = onDismiss)
        }
    }

    if (sub == 1) {
        val opts = (1..12).filter { m ->
            !((minJ != null && Jd.ym(vy, m) < Jd.ym(minJ.y, minJ.m)) || (maxJ != null && Jd.ym(vy, m) > Jd.ym(maxJ.y, maxJ.m)))
        }.map { it.toString() to Jalali.monthNames[it - 1] }
        PickerDialog("ماه", opts, vm.toString(), { vm = it.toInt(); clamp(); sub = 0 }, { sub = 0 })
    } else if (sub == 2) {
        val opts = (y1 downTo y0).map { it.toString() to Fa.digits(it) }
        PickerDialog("سال", opts, vy.toString(), { vy = it.toInt(); clamp(); sub = 0 }, { sub = 0 })
    }
}

@Composable
private fun NavButton(glyph: String, enabled: Boolean, onClick: () -> Unit) {
    val c = Finora.colors
    val shape = RoundedCornerShape(12.dp)
    Box(
        Modifier
            .size(36.dp)
            .alpha(if (enabled) 1f else 0.3f)
            .clip(shape)
            .background(if (c.isLight) Color(0xFFF5F7FC) else rgba(255, 255, 255, .06f))
            .border(1.dp, if (c.isLight) rgba(15, 23, 42, .12f) else rgba(255, 255, 255, .10f), shape)
            .clickable(enabled = enabled, interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center
    ) { FaIcon(glyph, 12.8f, color = c.text) }
}

@Composable
private fun SelectChip(text: String, modifier: Modifier, onClick: () -> Unit) {
    val c = Finora.colors
    val shape = RoundedCornerShape(12.dp)
    Row(
        modifier
            .height(41.dp)
            .clip(shape)
            .background(if (c.isLight) Color(0xFFF5F7FC) else rgba(255, 255, 255, .06f))
            .border(1.dp, if (c.isLight) rgba(15, 23, 42, .12f) else rgba(255, 255, 255, .10f), shape)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick)
            .padding(horizontal = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        FaIcon(FaGlyph.CHEVRON_DOWN, 9.6f, color = c.text)
        FText(text, 13.6f, Modifier.weight(1f), FontWeight.Bold, c.text, TextAlign.Center, maxLines = 1)
        Spacer(Modifier.size(9.6.dp))
    }
}

@Composable
private fun DayCell(day: Int, selected: Boolean, today: Boolean, disabled: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val c = Finora.colors
    val shape = RoundedCornerShape(12.dp)
    Box(
        modifier
            .aspectRatio(1f)
            .alpha(if (disabled) 0.28f else 1f)
            .clip(shape)
            .then(
                if (selected) Modifier.drawBehind {
                    drawRect(
                        cssLinearGradient(
                            135f, size.width, size.height,
                            0f to (if (c.isLight) Color(0xFF5A49F2) else Color(0xFF6A5CFF)),
                            1f to (if (c.isLight) Color(0xFFD6247F) else Color(0xFFFF3CAC))
                        )
                    )
                } else if (today) Modifier.border(1.dp, c.accent2.copy(alpha = .55f), shape) else Modifier
            )
            .clickable(enabled = !disabled, interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        FText(
            Fa.digits(day), 14.4f, weight = if (selected) FontWeight.Bold else FontWeight.Normal,
            color = if (selected) Color.White else c.text, align = TextAlign.Center
        )
    }
}
