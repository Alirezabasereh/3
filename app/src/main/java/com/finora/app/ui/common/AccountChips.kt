package com.finora.app.ui.common

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.finora.app.ui.theme.Finora
import com.finora.app.ui.theme.rgba

private fun mixOver(c: Color, a: Float, base: Color): Color =
    Color(
        red = c.red * a + base.red * (1 - a), green = c.green * a + base.green * (1 - a),
        blue = c.blue * a + base.blue * (1 - a), alpha = 1f
    )

@Composable
fun AcctIcon(icon: String, color: Color, bankId: String?) {
    val c = Finora.colors
    val logo = bankId?.let { BankLogos.byId[it] }
    if (logo != null) {
        Box(
            Modifier.size(28.dp).clip(CircleShape).background(Color.White)
                .border(1.dp, rgba(15, 23, 42, .10f), CircleShape).padding(2.dp),
            contentAlignment = Alignment.Center
        ) {
            Image(painterResource(logo), null, Modifier.size(24.dp).clip(CircleShape), contentScale = ContentScale.Fit)
        }
    } else {
        Box(
            Modifier.size(28.dp).clip(CircleShape).background(if (c.isLight) mixOver(color, .18f, Color.White) else color.copy(alpha = .28f)),
            contentAlignment = Alignment.Center
        ) { FText(icon, 15.2f, lineHeight = 15.2f, align = TextAlign.Center) }
    }
}

@Composable
fun AcctChip(
    label: String, color: Color, pressed: Boolean, add: Boolean = false, onClick: () -> Unit, icon: @Composable () -> Unit
) {
    val c = Finora.colors
    val shape = RoundedCornerShape(999.dp)
    val bg = when {
        add -> Color.Transparent
        pressed -> if (c.isLight) mixOver(color, .14f, Color.White) else color.copy(alpha = .18f)
        else -> if (c.isLight) c.surface else rgba(255, 255, 255, .05f)
    }
    val borderCol = when {
        pressed -> color
        add -> if (c.isLight) c.lineStrong else rgba(255, 255, 255, .10f)
        else -> if (c.isLight) rgba(15, 23, 42, .10f) else rgba(255, 255, 255, .10f)
    }
    Row(
        Modifier
            .height(40.dp)
            .clip(shape)
            .background(bg)
            .drawBehind {
                if (pressed) {
                    val sw = 1.dp.toPx()
                    drawRoundRect(
                        color = color, topLeft = Offset(1.5f * sw, 1.5f * sw),
                        size = androidx.compose.ui.geometry.Size(size.width - 3f * sw, size.height - 3f * sw),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(size.height / 2),
                        style = Stroke(sw)
                    )
                }
                if (add) {
                    val sw = 1.dp.toPx()
                    drawRoundRect(
                        color = borderCol, topLeft = Offset(sw / 2, sw / 2),
                        size = androidx.compose.ui.geometry.Size(size.width - sw, size.height - sw),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(size.height / 2),
                        style = Stroke(sw, pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 3.dp.toPx())))
                    )
                }
            }
            .then(if (!add) Modifier.border(1.dp, borderCol, shape) else Modifier)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick)
            .padding(start = 5.dp, end = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // RTL: first child (icon) sits at the right edge
        icon()
        FText(label, 12.8f, weight = FontWeight.Bold, color = if (add) c.muted else c.text, maxLines = 1, softWrap = false)
    }
}

