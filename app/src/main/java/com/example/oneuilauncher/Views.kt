package com.example.oneuilauncher

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.viewpager.widget.PagerAdapter
import kotlin.math.abs

/** آیکون + اسم زیرش */
class AppCellView(
    ctx: Context,
    private val iconPx: Int,
    textPx: Float,
    textColor: Int,
    shadow: Boolean
) : LinearLayout(ctx) {
    private val icon = ImageView(ctx)
    private val label = TextView(ctx)
    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    /** null = نمایش نده، false = انتخاب‌نشده، true = انتخاب‌شده */
    var checkState: Boolean? = null
        set(v) {
            field = v
            invalidate()
        }

    init {
        setWillNotDraw(false)
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER_HORIZONTAL
        addView(icon, LinearLayout.LayoutParams(iconPx, iconPx))
        label.setTextSize(TypedValue.COMPLEX_UNIT_PX, textPx)
        label.setTextColor(textColor)
        label.gravity = Gravity.CENTER
        label.maxLines = 2
        label.ellipsize = TextUtils.TruncateAt.END
        label.includeFontPadding = false
        if (shadow) label.setShadowLayer(3f, 0f, 1f, 0x99000000.toInt())
        val lp = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        lp.topMargin = (textPx * 0.35f).toInt()
        addView(label, lp)
    }

    fun bind(b: Bitmap, text: String) {
        icon.setImageBitmap(b)
        label.text = text
    }

    override fun dispatchDraw(c: Canvas) {
        super.dispatchDraw(c)
        val st = checkState ?: return
        val r = iconPx * 0.17f
        val cx = (width - iconPx) / 2f + r + 2f
        val cy = r + 2f
        ringPaint.style = Paint.Style.FILL
        ringPaint.color = if (st) 0xFF2D7DF0.toInt() else 0x66000000
        c.drawCircle(cx, cy, r, ringPaint)
        ringPaint.style = Paint.Style.STROKE
        ringPaint.strokeWidth = r * 0.14f
        ringPaint.color = 0xFFFFFFFF.toInt()
        c.drawCircle(cx, cy, r, ringPaint)
        if (st) {
            val path = Path()
            path.moveTo(cx - r * 0.45f, cy)
            path.lineTo(cx - r * 0.1f, cy + r * 0.38f)
            path.lineTo(cx + r * 0.5f, cy - r * 0.35f)
            ringPaint.strokeWidth = r * 0.18f
            c.drawPath(path, ringPaint)
        }
    }
}

/** شبکهٔ سلولی (برای صفحات هوم، اپ‌دراور و پوشه‌ها) */
class CellLayout(ctx: Context, val cols: Int, val rows: Int) : ViewGroup(ctx) {

    class LP(var cx: Int, var cy: Int, var sx: Int = 1, var sy: Int = 1) :
        ViewGroup.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)

    var padH = 0

    val cellW: Int get() = (width - 2 * padH) / cols
    val cellH: Int get() = height / rows

    override fun onMeasure(widthSpec: Int, heightSpec: Int) {
        val w = MeasureSpec.getSize(widthSpec)
        val h = MeasureSpec.getSize(heightSpec)
        setMeasuredDimension(w, h)
        val cw = (w - 2 * padH) / cols
        val ch = h / rows
        for (i in 0 until childCount) {
            val c = getChildAt(i)
            val lp = c.layoutParams as LP
            c.measure(
                MeasureSpec.makeMeasureSpec(cw * lp.sx, MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(ch * lp.sy, MeasureSpec.EXACTLY)
            )
        }
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        val cw = (width - 2 * padH) / cols
        val ch = height / rows
        for (i in 0 until childCount) {
            val c = getChildAt(i)
            val lp = c.layoutParams as LP
            val left = padH + lp.cx * cw
            val top = lp.cy * ch
            c.layout(left, top, left + cw * lp.sx, top + ch * lp.sy)
        }
    }

    override fun checkLayoutParams(p: ViewGroup.LayoutParams?): Boolean = p is LP
    override fun generateDefaultLayoutParams(): ViewGroup.LayoutParams = LP(0, 0)
    override fun generateLayoutParams(p: ViewGroup.LayoutParams?): ViewGroup.LayoutParams = LP(0, 0)

    fun occupied(cx: Int, cy: Int, sx: Int, sy: Int, ignore: View?): Boolean {
        if (cx < 0 || cy < 0 || cx + sx > cols || cy + sy > rows) return true
        for (i in 0 until childCount) {
            val c = getChildAt(i)
            if (c === ignore) continue
            val lp = c.layoutParams as LP
            if (cx < lp.cx + lp.sx && cx + sx > lp.cx && cy < lp.cy + lp.sy && cy + sy > lp.cy) return true
        }
        return false
    }

    fun findFree(sx: Int, sy: Int, nx: Int, ny: Int, ignore: View?): Pair<Int, Int>? {
        var best: Pair<Int, Int>? = null
        var bd = Int.MAX_VALUE
        for (y in 0..(rows - sy)) {
            for (x in 0..(cols - sx)) {
                if (!occupied(x, y, sx, sy, ignore)) {
                    val d = abs(x - nx) + abs(y - ny)
                    if (d < bd) { bd = d; best = Pair(x, y) }
                }
            }
        }
        return best
    }
}

class ViewsAdapter(val views: List<View>) : PagerAdapter() {
    override fun getCount(): Int = views.size
    override fun isViewFromObject(view: View, obj: Any): Boolean = view === obj
    override fun instantiateItem(container: ViewGroup, position: Int): Any {
        val v = views[position]
        container.addView(v)
        return v
    }
    override fun destroyItem(container: ViewGroup, position: Int, obj: Any) {
        container.removeView(obj as View)
    }
}

/**
 * نقطه‌های صفحه: در اپ‌دراور دایرهٔ توخالی برای صفحهٔ فعلی،
 * در هوم آیکون خانه برای صفحهٔ اصلی و نقطه برای بقیه.
 */
class PageIndicator(ctx: Context, private val f: Float, private val ring: Boolean) : View(ctx) {
    var count = 1
    var current = 0
    var homeIndex = 0
    var dark = false
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)

    override fun onDraw(c: Canvas) {
        val full = if (dark) 0xFF333333.toInt() else 0xFFFFFFFF.toInt()
        val half = if (dark) 0x80333333.toInt() else 0x80FFFFFF.toInt()
        val gap = 32f * f
        val startX = width / 2f - (count - 1) * gap / 2f
        val cy = height / 2f
        for (i in 0 until count) {
            val cx = startX + i * gap
            val sel = i == current
            if (ring) {
                if (sel) {
                    p.style = Paint.Style.STROKE
                    p.strokeWidth = 2.5f * f
                    p.color = 0xE6FFFFFF.toInt()
                    c.drawCircle(cx, cy, 8f * f, p)
                } else {
                    p.style = Paint.Style.FILL
                    p.color = 0x80FFFFFF.toInt()
                    c.drawCircle(cx, cy, 5f * f, p)
                }
            } else if (i == homeIndex) {
                val s = 8f * f
                val path = Path()
                path.moveTo(cx - s, cy)
                path.lineTo(cx, cy - s)
                path.lineTo(cx + s, cy)
                path.lineTo(cx + s, cy + s)
                path.lineTo(cx - s, cy + s)
                path.close()
                p.style = Paint.Style.STROKE
                p.strokeWidth = 2.5f * f
                p.color = if (sel) full else half
                c.drawPath(path, p)
            } else {
                p.style = Paint.Style.FILL
                p.color = if (sel) full else half
                c.drawCircle(cx, cy, 5f * f, p)
            }
        }
    }
}

/** ریشه: کشیدن عمودی (بالا/پایین) را برای باز و بسته شدن اپ‌دراور تشخیص می‌دهد */
class GestureRoot(ctx: Context) : FrameLayout(ctx) {
    var canIntercept: (Float) -> Boolean = { true }
    var onStart: () -> Unit = {}
    var onDrag: (Float) -> Unit = {}
    var onRelease: (Float, Float) -> Unit = { _, _ -> }

    private var downX = 0f
    private var downY = 0f
    private var dragging = false
    private var vt: VelocityTracker? = null
    private val slop = ViewConfiguration.get(ctx).scaledTouchSlop

    private fun begin(e: MotionEvent) {
        downX = e.x
        downY = e.y
        dragging = false
        vt?.recycle()
        vt = VelocityTracker.obtain()
        vt?.addMovement(e)
    }

    private fun detect(e: MotionEvent): Boolean {
        vt?.addMovement(e)
        val dx = e.x - downX
        val dy = e.y - downY
        if (!dragging && abs(dy) > slop * 1.5f && abs(dy) > abs(dx) * 1.5f && canIntercept(dy)) {
            dragging = true
            downY = e.y
            onStart()
            return true
        }
        return false
    }

    override fun onInterceptTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> begin(e)
            MotionEvent.ACTION_MOVE -> if (detect(e)) return true
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> { vt?.recycle(); vt = null }
        }
        return false
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> { begin(e); return true }
            MotionEvent.ACTION_MOVE -> {
                if (!dragging) detect(e)
                if (dragging) onDrag(e.y - downY)
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (dragging) {
                    vt?.addMovement(e)
                    vt?.computeCurrentVelocity(1000)
                    val vy = vt?.yVelocity ?: 0f
                    dragging = false
                    onRelease(e.y - downY, vy)
                }
                vt?.recycle()
                vt = null
            }
        }
        return true
    }
}

/** نوار جستجوی بالای اپ‌دراور: ذره‌بین، «Search phone»، میکروفون، خط جداکننده و سه‌نقطه */
class SearchBar(ctx: Context, private val f: Float) : FrameLayout(ctx) {
    val edit = android.widget.EditText(ctx)
    var onActivate: (() -> Unit)? = null
    var onMenu: (() -> Unit)? = null
    private var searching = false
    private var hintText = "Search phone"
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)

    fun setHint(h: String) {
        hintText = h
        edit.hint = h
        invalidate()
    }

    init {
        isClickable = true
        setWillNotDraw(false)
        edit.visibility = View.GONE
        edit.background = null
        edit.setTextColor(0xFFFFFFFF.toInt())
        edit.setHintTextColor(0xB3FFFFFF.toInt())
        edit.hint = "Search phone"
        edit.setSingleLine(true)
        edit.setTextSize(TypedValue.COMPLEX_UNIT_PX, 30f * f)
        edit.setPadding(0, 0, 0, 0)
        val lp = FrameLayout.LayoutParams((440 * f).toInt(), (70 * f).toInt())
        lp.leftMargin = (100 * f).toInt()
        lp.topMargin = (19 * f).toInt()
        addView(edit, lp)
    }

    fun setSearching(s: Boolean) {
        searching = s
        edit.visibility = if (s) View.VISIBLE else View.GONE
        invalidate()
    }

    override fun onDraw(c: Canvas) {
        val white = 0xE6FFFFFF.toInt()
        p.style = Paint.Style.STROKE
        p.strokeCap = Paint.Cap.ROUND
        p.strokeWidth = 3f * f
        p.color = white
        // ذره‌بین
        c.drawCircle(64f * f, 50f * f, 14f * f, p)
        c.drawLine(74f * f, 61f * f, 83f * f, 71f * f, p)
        // میکروفون
        val mx = 555f * f
        c.drawRoundRect(RectF(mx - 7f * f, 33f * f, mx + 7f * f, 58f * f), 7f * f, 7f * f, p)
        c.drawArc(RectF(mx - 13f * f, 42f * f, mx + 13f * f, 68f * f), 0f, 180f, false, p)
        c.drawLine(mx, 68f * f, mx, 75f * f, p)
        // جداکننده
        p.strokeWidth = 2f * f
        p.color = 0x66FFFFFF
        c.drawLine(611f * f, 44f * f, 611f * f, 76f * f, p)
        // سه‌نقطه
        p.style = Paint.Style.FILL
        p.color = white
        c.drawCircle(650f * f, 40f * f, 3.6f * f, p)
        c.drawCircle(650f * f, 56f * f, 3.6f * f, p)
        c.drawCircle(650f * f, 72f * f, 3.6f * f, p)
        // خط زیر نوار
        p.style = Paint.Style.STROKE
        p.strokeWidth = 2f * f
        p.color = 0x99FFFFFF.toInt()
        c.drawLine(40f * f, 89f * f, 680f * f, 89f * f, p)
        // متن راهنما
        if (!searching) {
            p.style = Paint.Style.FILL
            p.textSize = 30f * f
            p.color = 0xB3FFFFFF.toInt()
            c.drawText(hintText, 100f * f, 62f * f, p)
        }
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        if (e.actionMasked == MotionEvent.ACTION_UP) {
            if (e.x > 620f * f) onMenu?.invoke() else if (!searching) onActivate?.invoke()
        }
        return true
    }
}
