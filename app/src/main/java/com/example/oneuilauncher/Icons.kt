package com.example.oneuilauncher

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.Drawable
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sign
import kotlin.math.sin

class Icons(private val ctx: Context, private val prefs: Prefs) {
    private val pm = ctx.packageManager
    private val cache = HashMap<String, Bitmap>()
    private val maskCache = HashMap<Int, Bitmap>()

    fun clear() {
        cache.clear()
    }

    fun get(app: AppInfo, size: Int): Bitmap {
        val k = (if (prefs.oneUiIcons) "1" else "0") + app.comp + "@" + size
        cache[k]?.let { return it }
        var bmp: Bitmap? = null
        val key = app.key
        if (prefs.oneUiIcons && key != null) bmp = IconMapper.load(ctx, key, size)
        if (bmp == null) bmp = framed(app, size)
        cache[k] = bmp
        return bmp
    }

    /** آیکون پوشه: مربع‌گوشه‌گرد روشن با ۹ آیکون کوچک */
    fun folder(apps: List<AppInfo>, size: Int, color: Int): Bitmap {
        val k = "folder:" + color + (if (prefs.oneUiIcons) "1" else "0") + apps.joinToString(",") { it.comp } + "@" + size
        cache[k]?.let { return it }
        val out = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val c = Canvas(out)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.isFilterBitmap = true
        p.colorFilter = PorterDuffColorFilter(color, PorterDuff.Mode.SRC_IN)
        c.drawBitmap(mask(size), 0f, 0f, p)
        val pad = size * 0.15f
        val mini = (size * 0.23f).toInt()
        val gap = (size - 2 * pad - 3 * mini) / 2f
        apps.take(9).forEachIndexed { i, a ->
            val b = get(a, mini)
            c.drawBitmap(b, pad + (i % 3) * (mini + gap), pad + (i / 3) * (mini + gap), null)
        }
        cache[k] = out
        return out
    }

    private fun squircle(size: Int): Path {
        val r = size / 2f
        val steps = 96
        val path = Path()
        for (i in 0..steps) {
            val t = 2.0 * PI * i / steps
            val c = cos(t)
            val s = sin(t)
            val x = r + r * sign(c) * abs(c).pow(0.5)
            val y = r + r * sign(s) * abs(s).pow(0.5)
            if (i == 0) path.moveTo(x.toFloat(), y.toFloat()) else path.lineTo(x.toFloat(), y.toFloat())
        }
        path.close()
        return path
    }

    private var maskSrc: Bitmap? = null

    private fun mask(size: Int): Bitmap {
        maskCache[size]?.let { return it }
        if (maskSrc == null) {
            maskSrc = try {
                ctx.assets.open("shape_mask.png").use { android.graphics.BitmapFactory.decodeStream(it) }
            } catch (e: Exception) {
                null
            }
        }
        val src = maskSrc
        val m: Bitmap
        if (src != null) {
            m = Bitmap.createScaledBitmap(src, size, size, true)
        } else {
            m = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
            val p = Paint(Paint.ANTI_ALIAS_FLAG)
            p.color = Color.WHITE
            Canvas(m).drawPath(squircle(size), p)
        }
        maskCache[size] = m
        return m
    }

    /** آیکون اپ‌های بیرونی را در قالب مربع‌گوشه‌گرد سامسونگ می‌گذارد */
    private fun framed(app: AppInfo, size: Int): Bitmap {
        val out = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val c = Canvas(out)
        val d: Drawable? = try {
            pm.getActivityIcon(app.component)
        } catch (e: Exception) {
            null
        }
        if (d == null) return out

        if (d is AdaptiveIconDrawable) {
            val ext = (size * 1.5f).toInt()
            val off = -(size / 4)
            val bg = d.background
            val fg = d.foreground
            if (bg != null) { bg.setBounds(off, off, off + ext, off + ext); bg.draw(c) }
            if (fg != null) { fg.setBounds(off, off, off + ext, off + ext); fg.draw(c) }
        } else {
            val tmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
            d.setBounds(0, 0, size, size)
            d.draw(Canvas(tmp))
            val e = 3
            val full = Color.alpha(tmp.getPixel(e, e)) > 200 &&
                Color.alpha(tmp.getPixel(size - 1 - e, e)) > 200 &&
                Color.alpha(tmp.getPixel(e, size - 1 - e)) > 200 &&
                Color.alpha(tmp.getPixel(size - 1 - e, size - 1 - e)) > 200
            if (full) {
                c.drawBitmap(tmp, 0f, 0f, null)
            } else {
                c.drawColor(Color.WHITE)
                val inset = (size * 0.13f).toInt()
                c.drawBitmap(tmp, null, Rect(inset, inset, size - inset, size - inset), Paint(Paint.FILTER_BITMAP_FLAG))
            }
        }
        val mp = Paint(Paint.ANTI_ALIAS_FLAG)
        mp.xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN)
        c.drawBitmap(mask(size), 0f, 0f, mp)
        return out
    }
}
