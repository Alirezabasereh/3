package com.example.oneuilauncher

import android.content.Context
import android.graphics.Bitmap
import android.renderscript.Allocation
import android.renderscript.Element
import android.renderscript.RenderScript
import android.renderscript.ScriptIntrinsicBlur
import kotlin.math.max

object Blur {
    /** بلور سریع با RenderScript؛ تصویر اول کوچک می‌شود تا روی S7 هم سبک باشد */
    fun blur(ctx: Context, src: Bitmap, radius: Float, downscale: Int): Bitmap? {
        return try {
            val w = max(1, src.width / downscale)
            val h = max(1, src.height / downscale)
            val small = Bitmap.createScaledBitmap(src, w, h, true)
            val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            val rs = RenderScript.create(ctx)
            val ina = Allocation.createFromBitmap(rs, small)
            val outa = Allocation.createFromBitmap(rs, out)
            val sc = ScriptIntrinsicBlur.create(rs, Element.U8_4(rs))
            sc.setRadius(radius.coerceIn(1f, 25f))
            sc.setInput(ina)
            sc.forEach(outa)
            outa.copyTo(out)
            rs.destroy()
            out
        } catch (e: Throwable) {
            null
        }
    }
}
