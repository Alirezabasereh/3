package com.finora.app.ui.sheets

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalLayoutDirection
import com.finora.app.ui.common.FText
import com.finora.app.ui.common.FaGlyph
import com.finora.app.ui.common.FaIcon
import com.finora.app.ui.common.boxShadow
import com.finora.app.ui.common.cssLinearGradient
import com.finora.app.ui.theme.Finora
import com.finora.app.ui.theme.Vazir
import com.finora.app.ui.theme.rgba

/** .modal-overlay — dimmed full-screen layer; the card sits in the middle, 16dp from the edges. */
@Composable
fun ModalOverlay(onDismiss: () -> Unit, content: @Composable (maxHeight: Dp) -> Unit) {
    val c = Finora.colors
    val alpha = remember { Animatable(0f) }
    LaunchedEffect(Unit) { alpha.animateTo(1f, tween(180)) }
    BackHandler(onBack = onDismiss)
    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .alpha(alpha.value)
            .background(if (c.isLight) rgba(15, 23, 42, .46f) else rgba(28, 26, 42, .80f))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onDismiss)
            .systemBarsPadding()
            .imePadding()
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        content(maxHeight)
    }
}

/** .glass.transaction-modal */
@Composable
fun ModalCard(maxHeight: Dp, bottomPadding: Dp, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val c = Finora.colors
    val shape = RoundedCornerShape(32.dp)
    Column(
        modifier
            .widthIn(max = 460.dp)
            .fillMaxWidth()
            .heightIn(max = maxHeight)
            .then(
                if (c.isLight) Modifier.boxShadow(rgba(15, 23, 42, .35f), offsetY = (-12).dp, blur = 50.dp, spread = (-10).dp, radius = 32.dp)
                else Modifier.boxShadow(rgba(0, 0, 0, .28f), offsetY = 18.dp, blur = 50.dp, radius = 32.dp)
            )
            .clip(shape)
            .background(if (c.isLight) Color.White else Color(0xFF101027))
            .border(1.dp, if (c.isLight) rgba(15, 23, 42, .10f) else rgba(255, 255, 255, .10f), shape)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { }
            .padding(start = 21.dp, top = 21.dp, end = 21.dp, bottom = bottomPadding + 1.dp)
    ) { content() }
}

@Composable
fun ModalHead(title: @Composable () -> Unit, subtitle: String, onClose: () -> Unit) {
    val c = Finora.colors
    Row(Modifier.fillMaxWidth().padding(bottom = 14.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f, fill = false)) {
            title()
            FText(subtitle, 13.92f, Modifier.padding(top = 3.dp), color = c.muted)
        }
        CloseButton(onClose)
    }
}

@Composable
fun CloseButton(onClick: () -> Unit) {
    val c = Finora.colors
    val shape = RoundedCornerShape(14.dp)
    Box(
        Modifier
            .size(42.dp)
            .then(
                if (c.isLight) Modifier
                    .boxShadow(rgba(15, 23, 42, .06f), offsetY = 1.dp, blur = 2.dp, radius = 14.dp)
                    .boxShadow(rgba(15, 23, 42, .18f), offsetY = 8.dp, blur = 22.dp, spread = (-8).dp, radius = 14.dp)
                else Modifier
            )
            .clip(shape)
            .background(if (c.isLight) Color.White else rgba(255, 255, 255, .07f))
            .then(if (c.isLight) Modifier.border(1.dp, rgba(15, 23, 42, .10f), shape) else Modifier)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center
    ) { FaIcon(FaGlyph.XMARK, 16f, color = c.text) }
}

@Composable
fun FieldLabel(text: String, bottom: Dp = 8.dp) {
    val c = Finora.colors
    FText(text, 14.72f, Modifier.fillMaxWidth().padding(bottom = bottom), color = c.muted, lineHeight = 23f)
}

/** `.input` surface: translucent in dark, #f5f7fc in light, 1px border, radius 18, accent border on focus. */
@Composable
fun inputBg(focused: Boolean): Color {
    val c = Finora.colors
    return if (c.isLight) (if (focused) Color.White else Color(0xFFF5F7FC)) else rgba(255, 255, 255, .06f)
}

@Composable
fun inputBorder(focused: Boolean): Color {
    val c = Finora.colors
    return when {
        focused && c.isLight -> c.accent1
        focused -> rgba(106, 92, 255, .50f)
        c.isLight -> rgba(15, 23, 42, .16f)
        else -> rgba(255, 255, 255, .10f)
    }
}

@Composable
fun TextInput(
    value: String,
    onChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    ltr: Boolean = false,
    keyboardType: KeyboardType = KeyboardType.Text,
    imeAction: ImeAction = ImeAction.Done,
    onAction: (() -> Unit)? = null,
    transformation: VisualTransformation = VisualTransformation.None,
    singleLine: Boolean = true,
    minHeight: Dp = 57.dp,
    fontSize: Float = 16f,
    weight: FontWeight = FontWeight.Normal,
    center: Boolean = false,
    horizontalPad: Dp = 15.dp
) {
    val c = Finora.colors
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val shape = RoundedCornerShape(18.dp)
    val dir = if (ltr) LayoutDirection.Ltr else LayoutDirection.Rtl
    CompositionLocalProvider(LocalLayoutDirection provides dir) {
        BasicTextField(
            value = value, onValueChange = onChange, singleLine = singleLine,
            textStyle = TextStyle(
                color = c.text, fontSize = fontSize.sp, fontFamily = Vazir, fontWeight = weight,
                lineHeight = (fontSize * 1.5625f).sp,
                textAlign = if (center) TextAlign.Center else if (ltr) TextAlign.Left else TextAlign.Right,
                textDirection = if (ltr) TextDirection.Ltr else TextDirection.Content, letterSpacing = if (center) 0.5.sp else 0.sp
            ),
            cursorBrush = SolidColor(c.text),
            visualTransformation = transformation,
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = imeAction),
            keyboardActions = KeyboardActions(onDone = { onAction?.invoke() }),
            interactionSource = interaction,
            modifier = modifier
                .fillMaxWidth()
                .heightIn(min = minHeight)
                .background(inputBg(focused), shape)
                .border(1.dp, inputBorder(focused), shape),
            decorationBox = { inner ->
                Box(
                    Modifier.fillMaxWidth().heightIn(min = minHeight - 2.dp).padding(horizontal = horizontalPad, vertical = 14.dp),
                    contentAlignment = if (center) Alignment.Center else if (ltr) Alignment.CenterStart else Alignment.CenterStart
                ) {
                    if (value.isEmpty()) {
                        FText(
                            placeholder, fontSize, Modifier.fillMaxWidth(),
                            weight = weight, color = if (c.isLight) rgba(15, 23, 42, .40f) else Color(0xFF757575),
                            align = if (center) TextAlign.Center else if (ltr) TextAlign.Left else TextAlign.Right, maxLines = 1
                        )
                    }
                    inner()
                }
            }
        )
    }
}

/** A `select` look-alike: shows the chosen label with a chevron; the list opens in [PickerDialog]. */
@Composable
fun SelectBox(label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val c = Finora.colors
    val shape = RoundedCornerShape(18.dp)
    Row(
        modifier
            .fillMaxWidth()
            .height(59.dp)
            .background(inputBg(false), shape)
            .border(1.dp, inputBorder(false), shape)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick)
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        FText(label, 16f, Modifier.weight(1f), color = c.text, maxLines = 1)
        FaIcon(FaGlyph.CHEVRON_DOWN, 11.2f, color = c.muted)
    }
}

/** `.jd-field` button: calendar icon, text, chevron. */
@Composable
fun DateFieldButton(text: String, hasValue: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val c = Finora.colors
    val shape = RoundedCornerShape(18.dp)
    Row(
        modifier
            .fillMaxWidth()
            .height(57.dp)
            .background(inputBg(false), shape)
            .border(1.dp, inputBorder(false), shape)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick)
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        FaIcon(FaGlyph.CALENDAR, 14f, color = c.accent2, regular = true)
        FText(text, 16f, Modifier.weight(1f), if (hasValue) FontWeight.SemiBold else FontWeight.Normal, if (hasValue) c.text else c.muted, maxLines = 1, softWrap = false)
        FaIcon(FaGlyph.CHEVRON_DOWN, 11.2f, color = c.muted)
    }
}

@Composable
fun GradientButton(text: String, modifier: Modifier = Modifier, height: Dp = 57.dp, radius: Dp = 18.dp, fontSize: Float = 16f, icon: String? = null, onClick: () -> Unit) {
    val c = Finora.colors
    Box(
        modifier
            .fillMaxWidth()
            .height(height)
            .then(
                if (c.isLight) Modifier.boxShadow(rgba(90, 73, 242, .40f), offsetY = 10.dp, blur = 24.dp, spread = (-6).dp, radius = radius)
                else Modifier.boxShadow(rgba(106, 92, 255, .25f), offsetY = 12.dp, blur = 28.dp, radius = radius)
            )
            .clip(RoundedCornerShape(radius))
            .drawBehind {
                drawRect(
                    cssLinearGradient(
                        135f, size.width, size.height,
                        0f to (if (c.isLight) Color(0xFF5A49F2) else Color(0xFF6A5CFF)),
                        1f to (if (c.isLight) Color(0xFFD6247F) else Color(0xFFFF3CAC))
                    )
                )
            }
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) FaIcon(icon, fontSize - 2f, color = Color.White)
            FText(text, fontSize, weight = FontWeight.Bold, color = Color.White, align = TextAlign.Center)
        }
    }
}

/** A glass dialog (alert / confirm / list picker host). */
@Composable
fun DialogHost(onDismiss: () -> Unit, content: @Composable () -> Unit) {
    val c = Finora.colors
    val shape = RoundedCornerShape(26.dp)
    BackHandler(onBack = onDismiss)
    Box(
        Modifier
            .fillMaxSize()
            .background(rgba(0, 0, 0, .55f))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onDismiss)
            .systemBarsPadding()
            .imePadding()
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            Modifier
                .widthIn(max = 340.dp)
                .fillMaxWidth()
                .then(
                    if (c.isLight) Modifier.boxShadow(rgba(15, 23, 42, .30f), offsetY = 20.dp, blur = 50.dp, spread = (-10).dp, radius = 26.dp)
                    else Modifier.boxShadow(rgba(0, 0, 0, .28f), offsetY = 18.dp, blur = 50.dp, radius = 26.dp)
                )
                .clip(shape)
                .background(if (c.isLight) Color.White else rgba(28, 26, 42, .96f))
                .border(1.dp, if (c.isLight) rgba(15, 23, 42, .10f) else rgba(255, 255, 255, .10f), shape)
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { }
                .padding(17.dp)
        ) { content() }
    }
}

@Composable
fun FooterButton(text: String, modifier: Modifier = Modifier, primary: Boolean = false, enabled: Boolean = true, onClick: () -> Unit) {
    val c = Finora.colors
    val shape = RoundedCornerShape(14.dp)
    Box(
        modifier
            .alpha(if (enabled) 1f else 0.4f)
            .height(47.dp)
            .clip(shape)
            .then(
                if (primary) Modifier.drawBehind {
                    drawRect(
                        cssLinearGradient(
                            135f, size.width, size.height,
                            0f to (if (c.isLight) Color(0xFF5A49F2) else Color(0xFF6A5CFF)),
                            1f to (if (c.isLight) Color(0xFFD6247F) else Color(0xFFFF3CAC))
                        )
                    )
                } else Modifier
                    .background(if (c.isLight) Color(0xFFF5F7FC) else rgba(255, 255, 255, .06f))
                    .border(1.dp, if (c.isLight) rgba(15, 23, 42, .12f) else rgba(255, 255, 255, .10f), shape)
            )
            .clickable(enabled = enabled, interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center
    ) { FText(text, 16f, weight = FontWeight.Bold, color = if (primary) Color.White else c.text, align = TextAlign.Center) }
}

/** alert()/confirm() replacement. */
@Composable
fun MessageDialog(message: String, confirmText: String = "باشه", cancelText: String? = null, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    val c = Finora.colors
    DialogHost(onDismiss) {
        FText(message, 15.2f, Modifier.fillMaxWidth().padding(vertical = 10.dp, horizontal = 4.dp), color = c.text, align = TextAlign.Center, lineHeight = 15.2f * 1.8f)
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FooterButton(confirmText, Modifier.weight(1f), primary = true, onClick = onConfirm)
            if (cancelText != null) FooterButton(cancelText, Modifier.weight(1f), onClick = onDismiss)
        }
    }
}

/** Option list used instead of native <select>. */
@Composable
fun PickerDialog(title: String, options: List<Pair<String, String>>, selected: String, onSelect: (String) -> Unit, onDismiss: () -> Unit) {
    val c = Finora.colors
    DialogHost(onDismiss) {
        FText(title, 15.2f, Modifier.fillMaxWidth().padding(bottom = 10.dp), FontWeight.Bold, c.text, TextAlign.Center)
        Column(Modifier.heightIn(max = 380.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            for ((value, label) in options) {
                val active = value == selected
                val shape = RoundedCornerShape(14.dp)
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(46.dp)
                        .clip(shape)
                        .background(
                            if (active) (if (c.isLight) rgba(90, 73, 242, .12f) else rgba(106, 92, 255, .25f))
                            else (if (c.isLight) Color(0xFFF5F7FC) else rgba(255, 255, 255, .06f))
                        )
                        .border(1.dp, if (active) c.accent1.copy(alpha = .5f) else (if (c.isLight) rgba(15, 23, 42, .10f) else rgba(255, 255, 255, .10f)), shape)
                        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onSelect(value) }
                        .padding(horizontal = 14.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    FText(label, 15f, Modifier.fillMaxWidth(), if (active) FontWeight.Bold else FontWeight.Normal, c.text, maxLines = 1)
                }
            }
        }
        FooterButton("بستن", Modifier.fillMaxWidth().padding(top = 10.dp), onClick = onDismiss)
    }
}
