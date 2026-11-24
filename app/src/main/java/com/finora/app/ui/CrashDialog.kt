package com.finora.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.finora.app.ui.common.FText

/** Shown once after an unexpected crash: the stack trace can be copied and sent for a precise fix. */
@Composable
fun CrashDialog(trace: String, onClose: () -> Unit) {
    val clipboard = LocalClipboardManager.current
    Box(Modifier.fillMaxSize().background(Color(0xFF0B1022)).systemBarsPadding().padding(16.dp)) {
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            FText("برنامه دفعه‌ی قبل ناگهانی بسته شد", 18f, Modifier.fillMaxWidth(), FontWeight.ExtraBold, Color.White, TextAlign.Center)
            FText(
                "متن زیر رو با دکمه‌ی «کپی» برداشته و برای رفع مشکل بفرست.", 13f,
                Modifier.fillMaxWidth(), color = Color(0xB3F8FAFC), align = TextAlign.Center
            )
            Box(
                Modifier.weight(1f).fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Color(0xFF05070F)).padding(10.dp)
            ) {
                SelectionContainer {
                    BasicText(
                        trace,
                        Modifier.verticalScroll(rememberScrollState()),
                        style = TextStyle(
                            color = Color(0xFFE2E8F0), fontSize = 10.sp, fontFamily = FontFamily.Monospace,
                            textDirection = TextDirection.Ltr, textAlign = TextAlign.Left, lineHeight = 14.sp
                        )
                    )
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Btn("کپی", Color(0xFF6A5CFF), Modifier.weight(1f)) { clipboard.setText(AnnotatedString(trace)) }
                Btn("بستن", Color(0x33FFFFFF), Modifier.weight(1f), onClose)
            }
        }
    }
}

@Composable
private fun Btn(text: String, bg: Color, modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier.clip(RoundedCornerShape(14.dp)).background(bg)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick)
            .padding(14.dp),
        contentAlignment = Alignment.Center
    ) { FText(text, 15f, weight = FontWeight.Bold, color = Color.White, align = TextAlign.Center) }
}
