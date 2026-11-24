package com.finora.app.ui.common

import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Canvas as AndroidCanvas
import android.graphics.Paint as AndroidPaint
import android.graphics.RectF
import android.util.LruCache
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/** Byte-bounded cache: shadow bitmaps are tiny (nine-patch), but never let them grow without limit. */
private val shadowCache = object : LruCache<String, ImageBitmap>(6 * 1024 * 1024) {
    override fun sizeOf(key: String, value: ImageBitmap): Int = value.width * value.height * 4
}

/**
 * CSS `box-shadow: <x> <y> <blur> <spread> <color>` (outer shadow only).
 *
 * A real Gaussian blur is rendered once into a SMALL software bitmap (corners + a 2px flat middle, at reduced
 * resolution for big blurs) and stretched as a nine-patch to the element's size. This keeps memory tiny and — more
 * importantly — never creates a bitmap bigger than the GPU's texture limit, which crashed older phones when a card
 * was very tall (e.g. a long transaction list). The shape itself is clipped out, like browsers do.
 * Any failure is swallowed: a missing shadow must never crash the app.
 */
fun Modifier.boxShadow(
    color: Color,
    offsetX: Dp = 0.dp,
    offsetY: Dp = 0.dp,
    blur: Dp = 0.dp,
    spread: Dp = 0.dp,
    radius: Dp = 0.dp
): Modifier = this.drawBehind {
    if (color.alpha <= 0f) return@drawBehind
    try {
        drawNineShadow(color, offsetX.toPx(), offsetY.toPx(), blur.toPx(), spread.toPx(), radius.toPx())
    } catch (t: Throwable) {
        // ignore
    }
}

private fun DrawScope.drawNineShadow(color: Color, offX: Float, offY: Float, blurPx: Float, sp: Float, r: Float) {
    val shapeW = size.width + 2 * sp
    val shapeH = size.height + 2 * sp
    if (shapeW <= 0f || shapeH <= 0f) return
    val shapeR = max(0f, r + sp)
    val reach = ceil(blurPx * 1.6f).toInt() + 2          // how far the blur reaches around the edge
    val pad = reach
    val inner = ceil(shapeR).toInt() + reach             // distance from the edge until the shadow is flat
    val scale = if (blurPx >= 120f) 0.25f else if (blurPx >= 48f) 0.5f else 1f

    val bigX = shapeW > 2 * inner + 2
    val bigY = shapeH > 2 * inner + 2
    val shW = if (bigX) (2 * inner + 2).toFloat() else ceil(shapeW)
    val shH = if (bigY) (2 * inner + 2).toFloat() else ceil(shapeH)
    val bw = max(2, ceil((shW + 2 * pad) * scale).toInt())
    val bh = max(2, ceil((shH + 2 * pad) * scale).toInt())

    val key = "$bw|$bh|$shW|$shH|$shapeR|$blurPx|${color.toArgb()}"
    val bmp = shadowCache.get(key) ?: run {
        val bitmap = Bitmap.createBitmap(bw, bh, Bitmap.Config.ARGB_8888)
        val canvas = AndroidCanvas(bitmap)
        val paint = AndroidPaint(AndroidPaint.ANTI_ALIAS_FLAG)
        paint.color = color.toArgb()
        if (blurPx > 0f) {
            val sigma = blurPx / 2f * scale                       // CSS blur radius -> gaussian sigma (bitmap px)
            val skRadius = max(0.01f, (sigma - 0.5f) / 0.57735f)
            paint.maskFilter = BlurMaskFilter(skRadius, BlurMaskFilter.Blur.NORMAL)
        }
        val rr = shapeR * scale
        canvas.drawRoundRect(
            RectF(pad * scale, pad * scale, (pad + shW) * scale, (pad + shH) * scale), rr, rr, paint
        )
        val ib = bitmap.asImageBitmap()
        shadowCache.put(key, ib)
        ib
    }

    // destination box of the whole shadow (relative to the element)
    val dx0 = offX - sp - pad
    val dy0 = offY - sp - pad
    val dw = shapeW + 2 * pad
    val dh = shapeH + 2 * pad

    // slice boundaries on each axis: destination (floats) and source (bitmap px)
    fun axis(big: Boolean, total: Float, srcTotal: Int): Pair<FloatArray, IntArray> {
        return if (big) {
            val edge = (pad + inner).toFloat()
            val srcEdge = min(srcTotal / 2 - 1, max(1, Math.round(edge * scale)))
            Pair(floatArrayOf(0f, edge, total - edge, total), intArrayOf(0, srcEdge, srcTotal - srcEdge, srcTotal))
        } else {
            Pair(floatArrayOf(0f, total), intArrayOf(0, srcTotal))
        }
    }
    val (dxs, sxs) = axis(bigX, dw, bw)
    val (dys, sys) = axis(bigY, dh, bh)

    val outline = Path().apply { addRoundRect(RoundRect(0f, 0f, size.width, size.height, CornerRadius(r, r))) }
    clipPath(outline, clipOp = ClipOp.Difference) {
        for (iy in 0 until dys.size - 1) {
            for (ix in 0 until dxs.size - 1) {
                val sw = sxs[ix + 1] - sxs[ix]
                val sh = sys[iy + 1] - sys[iy]
                if (sw <= 0 || sh <= 0) continue
                val x0 = Math.round(dx0 + dxs[ix])
                val x1 = Math.round(dx0 + dxs[ix + 1])
                val y0 = Math.round(dy0 + dys[iy])
                val y1 = Math.round(dy0 + dys[iy + 1])
                if (x1 <= x0 || y1 <= y0) continue
                drawImage(
                    bmp,
                    srcOffset = IntOffset(sxs[ix], sys[iy]), srcSize = IntSize(sw, sh),
                    dstOffset = IntOffset(x0, y0), dstSize = IntSize(x1 - x0, y1 - y0)
                )
            }
        }
    }
}

/** CSS `linear-gradient(<angle>deg, stops…)` for a box of the given size (angle 180 = top→bottom, 90 = left→right). */
fun cssLinearGradient(angleDeg: Float, width: Float, height: Float, vararg stops: Pair<Float, Color>): Brush {
    val a = Math.toRadians(angleDeg.toDouble())
    val dx = sin(a).toFloat()
    val dy = (-cos(a)).toFloat()
    val len = abs(width * dx) + abs(height * dy)
    val cx = width / 2f
    val cy = height / 2f
    return Brush.linearGradient(
        *stops,
        start = Offset(cx - dx * len / 2f, cy - dy * len / 2f),
        end = Offset(cx + dx * len / 2f, cy + dy * len / 2f)
    )
}

/** `radial-gradient(circle at <corner>, color, transparent <stopPct>%)` — default size = farthest-corner. */
fun cssCornerGlow(w: Float, h: Float, atRight: Boolean, atTop: Boolean, color: Color, stop: Float): Brush {
    val center = Offset(if (atRight) w else 0f, if (atTop) 0f else h)
    val radius = hypot(w, h)
    val clear = color.copy(alpha = 0f)      // same hue -> matches the browser's premultiplied fade
    return Brush.radialGradient(
        0f to color, stop to clear, 1f to clear,
        center = center,
        radius = radius
    )
}
