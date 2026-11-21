package com.example.oneuilauncher

import android.content.Context
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.widget.FrameLayout
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** آیکون‌های خطی ساده برای منوها و نوار پایین حالت ویرایش */
object Ico {
    fun draw(c: Canvas, kind: String, cx: Float, cy: Float, s: Float, p: Paint) {
        p.style = Paint.Style.STROKE
        p.strokeCap = Paint.Cap.ROUND
        p.strokeJoin = Paint.Join.ROUND
        p.pathEffect = null
        when (kind) {
            "trash" -> {
                c.drawLine(cx - 0.36f * s, cy - 0.26f * s, cx + 0.36f * s, cy - 0.26f * s, p)
                c.drawRect(cx - 0.12f * s, cy - 0.40f * s, cx + 0.12f * s, cy - 0.26f * s, p)
                val path = Path()
                path.moveTo(cx - 0.28f * s, cy - 0.26f * s)
                path.lineTo(cx - 0.22f * s, cy + 0.40f * s)
                path.lineTo(cx + 0.22f * s, cy + 0.40f * s)
                path.lineTo(cx + 0.28f * s, cy - 0.26f * s)
                c.drawPath(path, p)
                c.drawLine(cx - 0.08f * s, cy - 0.12f * s, cx - 0.08f * s, cy + 0.26f * s, p)
                c.drawLine(cx + 0.08f * s, cy - 0.12f * s, cx + 0.08f * s, cy + 0.26f * s, p)
            }
            "select" -> {
                c.drawRoundRect(RectF(cx - 0.34f * s, cy - 0.34f * s, cx + 0.34f * s, cy + 0.34f * s), 0.08f * s, 0.08f * s, p)
                val path = Path()
                path.moveTo(cx - 0.17f * s, cy)
                path.lineTo(cx - 0.04f * s, cy + 0.14f * s)
                path.lineTo(cx + 0.18f * s, cy - 0.14f * s)
                c.drawPath(path, p)
            }
            "home" -> {
                val path = Path()
                path.moveTo(cx - 0.34f * s, cy - 0.02f * s)
                path.lineTo(cx, cy - 0.36f * s)
                path.lineTo(cx + 0.34f * s, cy - 0.02f * s)
                path.lineTo(cx + 0.34f * s, cy + 0.34f * s)
                path.lineTo(cx - 0.34f * s, cy + 0.34f * s)
                path.close()
                c.drawPath(path, p)
            }
            "disable" -> {
                p.pathEffect = DashPathEffect(floatArrayOf(0.12f * s, 0.10f * s), 0f)
                c.drawCircle(cx, cy, 0.34f * s, p)
                p.pathEffect = null
            }
            "remove" -> {
                c.drawCircle(cx, cy, 0.34f * s, p)
                c.drawLine(cx - 0.16f * s, cy, cx + 0.16f * s, cy, p)
            }
            "wallpaper" -> {
                c.drawRect(cx - 0.38f * s, cy - 0.32f * s, cx + 0.38f * s, cy + 0.32f * s, p)
                val path = Path()
                path.moveTo(cx - 0.38f * s, cy + 0.22f * s)
                path.lineTo(cx - 0.12f * s, cy - 0.04f * s)
                path.lineTo(cx + 0.06f * s, cy + 0.14f * s)
                path.lineTo(cx + 0.18f * s, cy + 0.02f * s)
                path.lineTo(cx + 0.38f * s, cy + 0.22f * s)
                c.drawPath(path, p)
                c.drawCircle(cx + 0.18f * s, cy - 0.14f * s, 0.06f * s, p)
            }
            "themes" -> {
                c.drawRect(cx - 0.20f * s, cy - 0.40f * s, cx + 0.20f * s, cy - 0.08f * s, p)
                c.drawLine(cx - 0.20f * s, cy - 0.08f * s, cx + 0.12f * s, cy + 0.04f * s, p)
                c.drawLine(cx + 0.20f * s, cy - 0.08f * s, cx + 0.12f * s, cy + 0.04f * s, p)
                c.drawLine(cx, cy + 0.02f * s, cx, cy + 0.2f * s, p)
                c.drawCircle(cx, cy + 0.34f * s, 0.07f * s, p)
            }
            "widgets" -> {
                c.drawRect(cx - 0.38f * s, cy - 0.38f * s, cx + 0.38f * s, cy + 0.38f * s, p)
                c.drawLine(cx - 0.38f * s, cy, cx + 0.38f * s, cy, p)
                c.drawLine(cx, cy - 0.38f * s, cx, cy + 0.38f * s, p)
            }
            "gear" -> {
                c.drawCircle(cx, cy, 0.16f * s, p)
                c.drawCircle(cx, cy, 0.30f * s, p)
                for (i in 0 until 8) {
                    val a = Math.PI * 2 * i / 8
                    val x1 = cx + (0.30f * s) * Math.cos(a).toFloat()
                    val y1 = cy + (0.30f * s) * Math.sin(a).toFloat()
                    val x2 = cx + (0.42f * s) * Math.cos(a).toFloat()
                    val y2 = cy + (0.42f * s) * Math.sin(a).toFloat()
                    c.drawLine(x1, y1, x2, y2, p)
                }
            }
            "plus" -> {
                c.drawLine(cx - 0.3f * s, cy, cx + 0.3f * s, cy, p)
                c.drawLine(cx, cy - 0.3f * s, cx, cy + 0.3f * s, p)
            }
        }
    }
}

/** ویجت داخل یک قاب: نگه‌داشتن روی ویجت (حتی اگر خودش کلیک بگیرد) را تشخیص می‌دهد */
class WidgetFrame(ctx: Context, hv: View) : FrameLayout(ctx) {
    var onLongPress: (() -> Unit)? = null
    var forward: ((MotionEvent) -> Unit)? = null

    private val handler = Handler(Looper.getMainLooper())
    private val slop = ViewConfiguration.get(ctx).scaledTouchSlop
    private var fired = false
    private var dx = 0f
    private var dy = 0f
    private val longPress = Runnable {
        fired = true
        onLongPress?.invoke()
    }

    init {
        addView(hv, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
    }

    override fun onInterceptTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                fired = false
                dx = e.x
                dy = e.y
                handler.postDelayed(longPress, ViewConfiguration.getLongPressTimeout().toLong())
            }
            MotionEvent.ACTION_MOVE -> {
                if (abs(e.x - dx) > slop || abs(e.y - dy) > slop) handler.removeCallbacks(longPress)
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> handler.removeCallbacks(longPress)
        }
        return fired
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        if (fired) forward?.invoke(e)
        if (e.actionMasked == MotionEvent.ACTION_UP || e.actionMasked == MotionEvent.ACTION_CANCEL) {
            fired = false
            forward = null
        }
        return true
    }
}

/**
 * لایهٔ ویرایش ویجت: قاب فیروزه‌ای با نقطه‌های تغییر اندازه، جابه‌جایی با کشیدن،
 * و منوی «Remove from Home screen».
 */
class WidgetEditor(ctx: Context, private val f: Float) : View(ctx) {

    var onCommit: ((HomeItem, Int, Int, Int, Int) -> Boolean)? = null
    var onRemove: ((HomeItem) -> Unit)? = null
    var onClosed: (() -> Unit)? = null

    private var item: HomeItem? = null
    private var target: View? = null
    private var gridLeft = 0f
    private var gridTop = 0f
    private var cellW = 1f
    private var cellH = 1f
    private var cols = 4
    private var rows = 5
    private var minSx = 1
    private var minSy = 1
    private var canH = true
    private var canV = true

    private val base = RectF()
    private val rect = RectF()
    private val start = RectF()
    private val popup = RectF()
    private var showPopup = true

    private var mode = 0
    private var downX = 0f
    private var downY = 0f
    private var moved = false
    private val slop = ViewConfiguration.get(ctx).scaledTouchSlop

    private val line = Paint(Paint.ANTI_ALIAS_FLAG)
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val txt = Paint(Paint.ANTI_ALIAS_FLAG)

    init {
        visibility = GONE
        line.style = Paint.Style.STROKE
        line.strokeWidth = 3f * f
        line.color = 0xFF2DD4C7.toInt()
        fill.style = Paint.Style.FILL
        fill.color = 0xFF2DD4C7.toInt()
        txt.textSize = 22f * f
        txt.color = 0xFF222222.toInt()
    }

    val active: Boolean get() = item != null && visibility == VISIBLE

    fun show(
        it: HomeItem, tv: View?, gl: Float, gt: Float, gw: Float, gh: Float,
        c: Int, r: Int, mnx: Int, mny: Int, h: Boolean, v: Boolean
    ) {
        item = it
        target = tv
        gridLeft = gl
        gridTop = gt
        cols = c
        rows = r
        cellW = gw / c
        cellH = gh / r
        minSx = min(max(1, mnx), c)
        minSy = min(max(1, mny), r)
        canH = h
        canV = v
        base.set(gl + it.x * cellW, gt + it.y * cellH, gl + (it.x + it.sx) * cellW, gt + (it.y + it.sy) * cellH)
        rect.set(base)
        showPopup = true
        visibility = VISIBLE
        invalidate()
    }

    fun hide() {
        target?.translationX = 0f
        target?.translationY = 0f
        item = null
        target = null
        visibility = GONE
        onClosed?.invoke()
    }

    /** ادامهٔ همان لمس نگه‌داشتنِ ویجت: بلافاصله حالت جابه‌جایی */
    fun externalTouch(e: MotionEvent) {
        try {
            externalTouchInner(e)
        } catch (ex: Exception) {
            mode = 0
            hide()
        }
    }

    private fun externalTouchInner(e: MotionEvent) {
        val loc = IntArray(2)
        getLocationOnScreen(loc)
        val x = e.rawX - loc[0]
        val y = e.rawY - loc[1]
        when (e.actionMasked) {
            MotionEvent.ACTION_MOVE -> {
                if (mode != 1) {
                    mode = 1
                    downX = x
                    downY = y
                    start.set(rect)
                    moved = false
                }
                handleMove(x, y)
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (mode == 1 && moved) commit()
                mode = 0
            }
        }
    }

    private fun near(x: Float, y: Float, px: Float, py: Float) = hypot(x - px, y - py) < 52f * f

    private fun clampF(v: Float, lo: Float, hi: Float): Float = if (hi < lo) lo else min(max(v, lo), hi)

    private fun clampI(v: Int, lo: Int, hi: Int): Int = if (hi < lo) lo else min(max(v, lo), hi)

    override fun onTouchEvent(e: MotionEvent): Boolean {
        return try {
            touchInner(e)
        } catch (ex: Exception) {
            mode = 0
            hide()
            true
        }
    }

    private fun touchInner(e: MotionEvent): Boolean {
        val it = item ?: return false
        val x = e.x
        val y = e.y
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                mode = when {
                    showPopup && popup.contains(x, y) -> 6
                    canH && near(x, y, rect.left, rect.centerY()) -> 2
                    canH && near(x, y, rect.right, rect.centerY()) -> 3
                    canV && near(x, y, rect.centerX(), rect.top) -> 4
                    canV && near(x, y, rect.centerX(), rect.bottom) -> 5
                    rect.contains(x, y) -> 1
                    else -> 7
                }
                downX = x
                downY = y
                start.set(rect)
                moved = false
            }
            MotionEvent.ACTION_MOVE -> handleMove(x, y)
            MotionEvent.ACTION_UP -> {
                when (mode) {
                    6 -> if (popup.contains(x, y)) { onRemove?.invoke(it) }
                    7 -> hide()
                    1 -> if (moved) commit()
                    2, 3, 4, 5 -> if (moved) commit()
                }
                mode = 0
            }
            MotionEvent.ACTION_CANCEL -> {
                mode = 0
            }
        }
        return true
    }

    private fun handleMove(x: Float, y: Float) {
        val dx = x - downX
        val dy = y - downY
        if (!moved && abs(dx) < slop && abs(dy) < slop) return
        moved = true
        showPopup = false
        val gr = gridLeft + cols * cellW
        val gb = gridTop + rows * cellH
        val minW = minSx * cellW
        val minH = minSy * cellH
        when (mode) {
            1 -> {
                var l = start.left + dx
                var t = start.top + dy
                l = clampF(l, gridLeft, gr - start.width())
                t = clampF(t, gridTop, gb - start.height())
                rect.set(l, t, l + start.width(), t + start.height())
                target?.translationX = rect.left - base.left
                target?.translationY = rect.top - base.top
            }
            2 -> rect.left = clampF(start.left + dx, gridLeft, start.right - minW)
            3 -> rect.right = clampF(start.right + dx, start.left + minW, gr)
            4 -> rect.top = clampF(start.top + dy, gridTop, start.bottom - minH)
            5 -> rect.bottom = clampF(start.bottom + dy, start.top + minH, gb)
        }
        invalidate()
    }

    private fun commit() {
        val it = item ?: return
        val nsx = clampI((rect.width() / cellW).roundToInt(), minSx, cols)
        val nsy = clampI((rect.height() / cellH).roundToInt(), minSy, rows)
        var nx = ((rect.left - gridLeft) / cellW).roundToInt()
        var ny = ((rect.top - gridTop) / cellH).roundToInt()
        // برای تغییر اندازه از چپ/بالا، لبهٔ مقابل ثابت می‌ماند
        if (mode == 2) nx = (it.x + it.sx) - nsx
        if (mode == 4) ny = (it.y + it.sy) - nsy
        nx = clampI(nx, 0, cols - nsx)
        ny = clampI(ny, 0, rows - nsy)
        val ok = onCommit?.invoke(it, nx, ny, nsx, nsy) ?: false
        if (!ok) {
            rect.set(base)
            target?.translationX = 0f
            target?.translationY = 0f
            showPopup = true
            invalidate()
        }
    }

    override fun onDraw(c: Canvas) {
        if (item == null) return
        c.drawRect(rect, line)
        val r = 9f * f
        if (canH) {
            c.drawCircle(rect.left, rect.centerY(), r, fill)
            c.drawCircle(rect.right, rect.centerY(), r, fill)
        }
        if (canV) {
            c.drawCircle(rect.centerX(), rect.top, r, fill)
            c.drawCircle(rect.centerX(), rect.bottom, r, fill)
        }
        if (showPopup) {
            val w = 360f * f
            val h = 84f * f
            var l = rect.centerX() - w / 2f
            l = l.coerceIn(16f * f, width - w - 16f * f)
            var t = rect.top - h - 22f * f
            if (t < 60f * f) t = rect.bottom + 22f * f
            popup.set(l, t, l + w, t + h)
            fill.color = 0xFFFFFFFF.toInt()
            c.drawRoundRect(popup, 16f * f, 16f * f, fill)
            fill.color = 0xFF2DD4C7.toInt()
            val ip = Paint(Paint.ANTI_ALIAS_FLAG)
            ip.color = 0xFF444444.toInt()
            ip.strokeWidth = 2.4f * f
            Ico.draw(c, "trash", popup.left + 44f * f, popup.centerY(), 40f * f, ip)
            txt.textSize = 23f * f
            c.drawText("Remove from Home screen", popup.left + 82f * f, popup.centerY() + 8f * f, txt)
        }
    }
}

/** یک صفحهٔ هوم: در حالت ویرایش کادر دارد، آیکون سطل‌زباله برای صفحهٔ خالی، و با لمس انتخاب می‌شود */
class PageCard(ctx: Context, val grid: CellLayout, private val f: Float) : FrameLayout(ctx) {
    var showTrash = false
    var onTrash: (() -> Unit)? = null
    var onSelect: (() -> Unit)? = null
    private val line = Paint(Paint.ANTI_ALIAS_FLAG)
    private var passToChild = false
    private val trashSize = (64f * f).toInt()

    var editMode = false
        set(v) {
            field = v
            invalidate()
        }

    init {
        setWillNotDraw(false)
        addView(grid, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        line.style = Paint.Style.STROKE
        line.strokeWidth = 3f * f
        line.color = 0xCCFFFFFF.toInt()
    }

    private fun trashRect(): Rect = Rect(width - trashSize - (6 * f).toInt(), (6 * f).toInt(), width - (6 * f).toInt(), (6 * f).toInt() + trashSize)

    override fun onInterceptTouchEvent(e: MotionEvent): Boolean = editMode

    override fun onTouchEvent(e: MotionEvent): Boolean {
        if (!editMode) return false
        if (e.actionMasked == MotionEvent.ACTION_UP) {
            if (showTrash && trashRect().contains(e.x.toInt(), e.y.toInt())) onTrash?.invoke() else onSelect?.invoke()
        }
        return true
    }

    override fun dispatchDraw(c: Canvas) {
        super.dispatchDraw(c)
        if (editMode) {
            c.drawRect(1.5f * f, 1.5f * f, width - 1.5f * f, height - 1.5f * f, line)
            if (showTrash) {
                val r = trashRect()
                val p = Paint(Paint.ANTI_ALIAS_FLAG)
                p.color = 0xFFFFFFFF.toInt()
                p.strokeWidth = 2.6f * f
                Ico.draw(c, "trash", r.centerX().toFloat(), r.centerY().toFloat(), 40f * f, p)
            }
        }
    }
}

/** نوار پایین حالت ویرایش: Wallpapers / Themes / Widgets / Home screen settings */
class EditBar(ctx: Context, private val f: Float) : View(ctx) {
    var onItem: ((Int) -> Unit)? = null
    var textColor = 0xFF2A2A2A.toInt()
    private val kinds = listOf("wallpaper", "themes", "widgets", "gear")
    private val labels = listOf("Wallpapers", "Themes", "Widgets", "Home screen\nsettings")
    private val centers = listOf(116f, 279f, 440f, 603f)
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)

    override fun onDraw(c: Canvas) {
        p.color = textColor
        p.strokeWidth = 2.6f * f
        for (i in 0 until 4) {
            val cx = centers[i] * f
            Ico.draw(c, kinds[i], cx, 52f * f, 76f * f, p)
            p.style = Paint.Style.FILL
            p.textSize = 26f * f
            p.textAlign = Paint.Align.CENTER
            val lines = labels[i].split("\n")
            for (j in lines.indices) c.drawText(lines[j], cx, 118f * f + j * 30f * f, p)
            p.textAlign = Paint.Align.LEFT
        }
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        if (e.actionMasked == MotionEvent.ACTION_UP) {
            var best = 0
            var bd = Float.MAX_VALUE
            for (i in 0 until 4) {
                val d = abs(e.x - centers[i] * f)
                if (d < bd) { bd = d; best = i }
            }
            onItem?.invoke(best)
        }
        return true
    }
}

/** آیکون خانه بالای صفحه در حالت ویرایش */
class HomeMark(ctx: Context, private val f: Float) : View(ctx) {
    var filled = true
    var color = 0xFF2A2A2A.toInt()
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)

    override fun onDraw(c: Canvas) {
        p.color = color
        p.strokeWidth = 2.8f * f
        Ico.draw(c, "home", width / 2f, height / 2f, 52f * f, p)
        if (filled) {
            p.style = Paint.Style.FILL
            p.alpha = 70
            c.drawRect(width / 2f - 14f * f, height / 2f + 2f * f, width / 2f + 14f * f, height / 2f + 17f * f, p)
        }
    }
}

/** آیکون کوچک داخل منوی نگه‌داشتن روی اپ */
class IcoView(ctx: Context, private val f: Float, private val kind: String) : View(ctx) {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)

    override fun onDraw(c: Canvas) {
        p.color = 0xFF555555.toInt()
        p.strokeWidth = 2.6f * f
        Ico.draw(c, kind, width / 2f, height / 2f, min(width, height) * 0.9f, p)
    }
}

/** کارت «+» برای افزودن صفحهٔ جدید در حالت ویرایش */
class AddCard(ctx: Context, private val f: Float) : View(ctx) {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)

    override fun onDraw(c: Canvas) {
        p.style = Paint.Style.STROKE
        p.strokeWidth = 3f * f
        p.color = 0xCCFFFFFF.toInt()
        c.drawRect(1.5f * f, 1.5f * f, width - 1.5f * f, height - 1.5f * f, p)
        p.color = 0xFFFFFFFF.toInt()
        p.strokeWidth = 4f * f
        Ico.draw(c, "plus", width / 2f, height / 2f, 90f * f, p)
    }
}

const val HOLE = "\u0000hole"

/**
 * پیش‌نمایش جابه‌جایی «دومینویی»: آیتم کشیده‌شده از جای خودش (orig) برداشته می‌شود و
 * آیتم‌های بین مبدأ و مقصد یک خانه جابه‌جا می‌شوند تا جا برای مقصد (t) باز شود.
 * orig = -1 یعنی آیتم از بیرون می‌آید (مثلاً از پوشه) و آیتم‌ها تا اولین جای خالی به جلو می‌روند.
 */
fun previewOrder(slots: List<String>, orig: Int, t: Int): ArrayList<String> {
    val base = ArrayList<String>(slots)
    while (base.size <= t) base.add("")
    if (orig >= 0 && orig < base.size) base[orig] = ""
    if (base[t].isEmpty()) {
        base[t] = HOLE
        return base
    }
    if (orig >= 0) {
        if (t > orig) {
            for (i in orig until t) base[i] = base[i + 1]
        } else {
            for (i in orig downTo t + 1) base[i] = base[i - 1]
        }
        base[t] = HOLE
    } else {
        var e = t
        while (e < base.size && base[e].isNotEmpty()) e++
        if (e == base.size) base.add("")
        for (i in e downTo t + 1) base[i] = base[i - 1]
        base[t] = HOLE
    }
    return base
}

/** نقطه‌های خانه‌ها، کادر محل فرود و هایلایت هدف (پوشه/ادغام) هنگام کشیدن آیکون */
class ReorderOverlay(ctx: Context, private val f: Float) : View(ctx) {
    var cols = 4
    var rows = 5
    var padLeft = 0f
    var cellW = 100f
    var cellH = 100f
    var iconSize = 100f
    var iconTop = 0f
    var holeIndex = -1
    var targetIndex = -1
    var showDots = true
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)

    private fun cx(i: Int): Float = padLeft + (i % cols) * cellW + cellW / 2f
    private fun cy(i: Int): Float = (i / cols) * cellH + iconTop + iconSize / 2f

    override fun onDraw(c: Canvas) {
        if (showDots) {
            p.style = Paint.Style.FILL
            p.color = 0x66FFFFFF
            for (i in 0 until cols * rows) c.drawCircle(cx(i), cy(i), 2.4f * f, p)
        }
        if (targetIndex >= 0) {
            p.style = Paint.Style.FILL
            p.color = 0x59FFFFFF
            val h = iconSize * 0.68f
            c.drawRoundRect(RectF(cx(targetIndex) - h, cy(targetIndex) - h, cx(targetIndex) + h, cy(targetIndex) + h), 34f * f, 34f * f, p)
        }
        if (holeIndex >= 0) {
            p.style = Paint.Style.STROKE
            p.strokeWidth = 2.6f * f
            p.color = 0xE6FFFFFF.toInt()
            val h = iconSize / 2f
            c.drawRoundRect(RectF(cx(holeIndex) - h, cy(holeIndex) - h, cx(holeIndex) + h, cy(holeIndex) + h), 26f * f, 26f * f, p)
        }
    }
}
