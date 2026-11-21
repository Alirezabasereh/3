package com.example.oneuilauncher

import android.content.Context
import android.graphics.Color
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.viewpager.widget.ViewPager
import kotlin.math.ceil
import kotlin.math.hypot
import kotlin.math.max

/** اپ‌دراور: اسلات‌های ۴×۵ با جای خالی، جابه‌جایی دومینویی، پوشه، جستجو، حالت انتخاب و Clean up pages */
class DrawerView(
    ctx: Context,
    private val f: Float,
    private val icons: Icons,
    private val host: Host
) : ViewGroup(ctx) {

    interface Host {
        fun launch(app: AppInfo)
        fun longPress(view: View, key: String)
        fun openFolder(name: String, view: View)
        fun menu(anchor: View)
        fun folderColor(name: String): Int
        fun recents(): List<AppInfo>
        fun selectionChanged(count: Int)
        fun slotsChanged(slots: List<String>)
        fun dropIntoFolder(key: String, folder: String)
        fun mergeIntoNewFolder(key: String, target: String)
    }

    private val prefs = Prefs(ctx)
    private val handler = Handler(Looper.getMainLooper())
    private val bg = ImageView(ctx)
    private val dim = View(ctx)
    private val bar = SearchBar(ctx, f)
    private val pager = ViewPager(ctx)
    private val overlay = ReorderOverlay(ctx, f)
    private val indicator = PageIndicator(ctx, f, true)
    private val scroll = ScrollView(ctx)
    private val cleanBar = LinearLayout(ctx)

    private var slots: ArrayList<String> = ArrayList()
    private var folderMap: Map<String, List<AppInfo>> = emptyMap()
    private var appMap: Map<String, AppInfo> = emptyMap()
    private var allApps: List<AppInfo> = emptyList()
    private val cellViews = HashMap<String, View>()
    private val iconPx = (102 * f).toInt()
    private val perPage = 20
    private var extraPages = 0

    var searching = false
        private set
    var selectMode = false
        private set
    private val selected = LinkedHashSet<String>()

    // ---- reorder session
    var dragging = false
        private set
    private var dragKey: String? = null
    private var dragOrig = -1
    private var curT = -1
    private var mode = 0          // 0 جابه‌جایی، 1 افزودن به پوشه، 2 ساخت پوشهٔ جدید
    private var preview: ArrayList<String>? = null
    private var lastInner = false
    private var lastX = 0f
    private var lastY = 0f
    private var edgeSide = 0

    private var cleaning = false
    private var savedSlots: ArrayList<String>? = null

    private val evalRunnable = Runnable { evaluate() }
    private val mergeRunnable = Runnable {
        if (dragging && lastInner && curT >= 0) {
            val occ = occupant(curT)
            if (occ.isNotEmpty() && !occ.startsWith("folder:")) {
                mode = 2
                showBase()
                overlay.targetIndex = curT - pager.currentItem * perPage
                overlay.holeIndex = -1
                overlay.invalidate()
            }
        }
    }
    private val flipRunnable = Runnable { flip() }

    init {
        bg.scaleType = ImageView.ScaleType.CENTER_CROP
        bg.visibility = View.GONE
        dim.setBackgroundColor(0x80000000.toInt())
        addView(bg)
        addView(dim)
        addView(pager)
        addView(overlay)
        addView(scroll)
        addView(indicator)
        addView(bar)
        addView(cleanBar)
        overlay.visibility = View.GONE
        scroll.visibility = View.GONE
        scroll.isVerticalScrollBarEnabled = false

        cleanBar.orientation = LinearLayout.HORIZONTAL
        cleanBar.visibility = View.GONE
        cleanBar.addView(cleanLabel("CANCEL") { cancelCleanup() }, LinearLayout.LayoutParams(0, -1, 1f))
        cleanBar.addView(cleanLabel("APPLY") { applyCleanup() }, LinearLayout.LayoutParams(0, -1, 1f))

        pager.addOnPageChangeListener(object : ViewPager.SimpleOnPageChangeListener() {
            override fun onPageSelected(position: Int) {
                indicator.current = position
                indicator.invalidate()
                if (!cleaning) prefs.drawerPage = position
                if (dragging) {
                    curT = -1
                    handler.removeCallbacks(evalRunnable)
                    handler.postDelayed(evalRunnable, 120)
                }
            }
        })
        bar.onActivate = { enterSearch() }
        bar.onMenu = { host.menu(bar) }
        bar.edit.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun afterTextChanged(s: Editable?) {
                showResults(s?.toString() ?: "")
            }
        })
    }

    private fun cleanLabel(text: String, action: () -> Unit): TextView {
        val t = TextView(context)
        t.text = text
        t.setTextColor(Color.WHITE)
        t.setTextSize(TypedValue.COMPLEX_UNIT_PX, 27f * f)
        t.gravity = Gravity.CENTER
        t.setOnClickListener { action() }
        return t
    }

    /** پس‌زمینهٔ بلورشدهٔ والپیپر (اگر در دسترس بود) */
    fun setBlur(b: android.graphics.Bitmap?) {
        if (b != null) {
            bg.setImageBitmap(b)
            bg.visibility = View.VISIBLE
            dim.setBackgroundColor(0x66000000)
        } else {
            bg.visibility = View.GONE
            dim.setBackgroundColor(0x80000000.toInt())
        }
    }

    // ---------------------------------------------------------- data

    fun setLayout(layout: DrawerLayout, apps: List<AppInfo>) {
        slots = ArrayList(layout.slots)
        folderMap = layout.folders
        allApps = apps
        appMap = apps.associateBy { it.comp }
        buildPages(prefs.drawerPage)
    }

    fun currentSlots(): List<String> = slots

    private fun pageCount(): Int = max(1, ceil(slots.size / perPage.toDouble()).toInt()) + extraPages

    private fun buildPages(keepPage: Int) {
        cellViews.clear()
        val pages = ArrayList<View>()
        val count = pageCount()
        for (p in 0 until count) {
            val cl = CellLayout(context, 4, 5)
            cl.padH = (12 * f).toInt()
            for (i in 0 until perPage) {
                val key = slots.getOrNull(p * perPage + i) ?: ""
                if (key.isEmpty()) continue
                val v = cellFor(key) ?: continue
                if (dragging && key == dragKey) v.visibility = View.INVISIBLE
                if (cleaning) v.alpha = 0.6f
                cellViews[key] = v
                cl.addView(v, CellLayout.LP(i % 4, i / 4))
            }
            pages.add(cl)
        }
        pager.adapter = ViewsAdapter(pages)
        val page = keepPage.coerceIn(0, count - 1)
        pager.setCurrentItem(page, false)
        indicator.count = count
        indicator.current = page
        indicator.invalidate()
    }

    private fun appCell(app: AppInfo, key: String): View {
        val v = AppCellView(context, iconPx, 25f * f, Color.WHITE, true)
        v.bind(icons.get(app, iconPx), app.label)
        if (selectMode) v.checkState = selected.contains(app.comp)
        v.setOnClickListener {
            if (cleaning) return@setOnClickListener
            if (selectMode) {
                if (!selected.remove(app.comp)) selected.add(app.comp)
                v.checkState = selected.contains(app.comp)
                host.selectionChanged(selected.size)
            } else {
                host.launch(app)
            }
        }
        v.setOnLongClickListener {
            if (!selectMode && !cleaning) host.longPress(v, key)
            true
        }
        return v
    }

    private fun cellFor(key: String): View? {
        if (key.startsWith("folder:")) {
            val name = key.substring(7)
            val members = folderMap[name] ?: return null
            val v = AppCellView(context, iconPx, 25f * f, Color.WHITE, true)
            v.bind(icons.folder(members, iconPx, host.folderColor(name)), name)
            v.setOnClickListener { if (!selectMode && !cleaning) host.openFolder(name, v) }
            v.setOnLongClickListener {
                if (!selectMode && !cleaning) host.longPress(v, key)
                true
            }
            return v
        }
        val app = appMap[key] ?: return null
        return appCell(app, key)
    }

    // ---------------------------------------------------------- selection mode

    fun startSelect(first: AppInfo) {
        selectMode = true
        selected.clear()
        selected.add(first.comp)
        buildPages(pager.currentItem)
        host.selectionChanged(selected.size)
    }

    fun endSelect() {
        if (!selectMode) return
        selectMode = false
        selected.clear()
        buildPages(pager.currentItem)
    }

    fun selectedApps(): List<AppInfo> = allApps.filter { selected.contains(it.comp) }

    // ---------------------------------------------------------- clean up pages

    fun startCleanup() {
        if (cleaning || dragging) return
        cleaning = true
        savedSlots = ArrayList(slots)
        val compact = ArrayList<String>()
        for (k in slots) if (k.isNotEmpty()) compact.add(k)
        slots = compact
        bar.visibility = View.INVISIBLE
        cleanBar.visibility = View.VISIBLE
        buildPages(0)
    }

    private fun cancelCleanup() {
        if (!cleaning) return
        val saved = savedSlots
        if (saved != null) slots = saved
        endCleanupUi()
        buildPages(pager.currentItem)
    }

    private fun applyCleanup() {
        if (!cleaning) return
        val result = ArrayList(slots)
        savedSlots = null
        endCleanupUi()
        host.slotsChanged(result)
    }

    private fun endCleanupUi() {
        cleaning = false
        cleanBar.visibility = View.GONE
        bar.visibility = View.VISIBLE
    }

    // ---------------------------------------------------------- reorder drag

    private fun cellW(): Float = (width - 24f * f) / 4f
    private fun cellH(): Float = 214f * f

    private fun occupant(t: Int): String {
        val k = slots.getOrNull(t) ?: ""
        return if (k == dragKey) "" else k
    }

    private fun configureOverlay() {
        overlay.cols = 4
        overlay.rows = 5
        overlay.padLeft = 12f * f
        overlay.cellW = cellW()
        overlay.cellH = cellH()
        overlay.iconSize = iconPx.toFloat()
        overlay.iconTop = 0f
    }

    /** key: comp یا folder:<name>. external=true یعنی آیتم از جای دیگری (مثلاً پوشه) آمده */
    fun startDrag(key: String, external: Boolean) {
        dragging = true
        dragKey = key
        dragOrig = if (external) -1 else slots.indexOf(key)
        curT = -1
        mode = 0
        preview = null
        bar.visibility = View.INVISIBLE
        configureOverlay()
        overlay.visibility = View.VISIBLE
        overlay.showDots = true
        overlay.holeIndex = -1
        overlay.targetIndex = -1
        if (!external) cellViews[key]?.visibility = View.INVISIBLE
        overlay.invalidate()
    }

    fun dragMove(x: Float, y: Float) {
        if (!dragging) return
        lastX = x
        lastY = y
        // لبه‌ها: عوض کردن صفحه
        val side = if (x < 28f * f) -1 else if (x > width - 28f * f) 1 else 0
        if (side != edgeSide) {
            edgeSide = side
            handler.removeCallbacks(flipRunnable)
            if (side != 0) handler.postDelayed(flipRunnable, 650)
        }
        val px = x - 12f * f
        val col = (px / cellW()).toInt().coerceIn(0, 3)
        val row = ((y - pager.top) / cellH()).toInt().coerceIn(0, 4)
        val t = pager.currentItem * perPage + row * 4 + col
        val cx = 12f * f + col * cellW() + cellW() / 2f
        val cy = pager.top + row * cellH() + iconPx / 2f
        val inner = hypot(x - cx, y - cy) < iconPx * 0.42f
        val changed = t != curT
        val innerChanged = inner != lastInner
        lastInner = inner
        if (changed) {
            curT = t
            handler.removeCallbacks(evalRunnable)
            handler.removeCallbacks(mergeRunnable)
            mode = 0
            overlay.targetIndex = -1
            handler.postDelayed(evalRunnable, 170)
        } else if (innerChanged && mode != 0 && !inner) {
            mode = 0
            overlay.targetIndex = -1
            handler.removeCallbacks(mergeRunnable)
            handler.postDelayed(evalRunnable, 60)
        }
    }

    private fun evaluate() {
        if (!dragging || curT < 0) return
        val occ = occupant(curT)
        if (occ.isNotEmpty() && lastInner) {
            if (occ.startsWith("folder:")) {
                mode = 1
                showBase()
                overlay.targetIndex = curT - pager.currentItem * perPage
                overlay.holeIndex = -1
                overlay.invalidate()
            } else {
                // کمی صبر کن؛ اگر ماند، پوشهٔ جدید ساخته می‌شود
                handler.removeCallbacks(mergeRunnable)
                handler.postDelayed(mergeRunnable, 320)
            }
            return
        }
        mode = 0
        val pv = previewOrder(slots, dragOrig, curT)
        preview = pv
        animatePreview(pv)
        overlay.targetIndex = -1
        overlay.holeIndex = curT - pager.currentItem * perPage
        overlay.invalidate()
    }

    /** چیدمان بدون جابه‌جایی (آیتم‌ها سر جای اصلی) */
    private fun showBase() {
        preview = null
        for ((_, v) in cellViews) v.animate().translationX(0f).translationY(0f).setDuration(160).start()
    }

    private fun animatePreview(pv: List<String>) {
        val cw = cellW()
        val ch = cellH()
        for ((key, v) in cellViews) {
            if (key == dragKey) continue
            val oldI = slots.indexOf(key)
            val newI = pv.indexOf(key)
            if (oldI < 0 || newI < 0) continue
            var dx = 0f
            var dy = 0f
            if (oldI / perPage == newI / perPage) {
                dx = ((newI % 4) - (oldI % 4)) * cw
                dy = (((newI % perPage) / 4) - ((oldI % perPage) / 4)) * ch
            }
            v.animate().translationX(dx).translationY(dy).setDuration(190).start()
        }
    }

    private fun flip() {
        if (!dragging || edgeSide == 0) return
        val cur = pager.currentItem
        val target = cur + edgeSide
        if (target < 0) return
        if (target >= pageCount()) {
            if (extraPages == 0) {
                extraPages = 1
                buildPages(cur)
            } else return
        }
        pager.setCurrentItem(target, true)
        handler.postDelayed(flipRunnable, 900)
    }

    /** پایان کشیدن. drop=true یعنی نتیجه اعمال شود. */
    fun endDrag(drop: Boolean) {
        if (!dragging) return
        handler.removeCallbacks(evalRunnable)
        handler.removeCallbacks(mergeRunnable)
        handler.removeCallbacks(flipRunnable)
        val key = dragKey ?: ""
        val m = mode
        val t = curT
        val pv = preview
        val external = dragOrig < 0
        val target = slots.getOrNull(t) ?: ""
        dragging = false
        dragKey = null
        edgeSide = 0
        overlay.visibility = View.GONE
        bar.visibility = View.VISIBLE
        for ((_, v) in cellViews) {
            v.animate().cancel()
            v.translationX = 0f
            v.translationY = 0f
            v.visibility = View.VISIBLE
        }
        val hadExtra = extraPages > 0
        extraPages = 0
        if (drop && key.isNotEmpty()) {
            if (m == 1 && target.startsWith("folder:")) {
                host.dropIntoFolder(key, target.substring(7))
                return
            }
            if (m == 2 && target.isNotEmpty() && target != key) {
                host.mergeIntoNewFolder(key, target)
                return
            }
            if (pv != null) {
                val result = ArrayList<String>()
                for (k in pv) result.add(if (k == HOLE) key else k)
                while (result.isNotEmpty() && result.last().isEmpty()) result.removeAt(result.size - 1)
                host.slotsChanged(result)
                return
            }
            if (external) {
                val result = ArrayList(slots)
                result.add(key)
                host.slotsChanged(result)
                return
            }
        }
        if (hadExtra || true) buildPages(pager.currentItem)
    }

    // ---------------------------------------------------------- search

    private fun smallCell(a: AppInfo): View {
        val px = (84 * f).toInt()
        val v = AppCellView(context, px, 21f * f, Color.WHITE, true)
        v.bind(icons.get(a, px), a.label)
        v.setOnClickListener { host.launch(a) }
        return v
    }

    private fun showResults(q: String) {
        val box = LinearLayout(context)
        box.orientation = LinearLayout.VERTICAL
        if (q.isBlank()) {
            val hdr = TextView(context)
            hdr.text = "SUGGESTED APPS"
            hdr.setTextSize(TypedValue.COMPLEX_UNIT_PX, 21f * f)
            hdr.setTextColor(0xB3FFFFFF.toInt())
            hdr.letterSpacing = 0.06f
            hdr.setPadding((40 * f).toInt(), (26 * f).toInt(), 0, (14 * f).toInt())
            box.addView(hdr)
            val cl = CellLayout(context, 5, 1)
            cl.padH = (20 * f).toInt()
            host.recents().take(5).forEachIndexed { i, a -> cl.addView(smallCell(a), CellLayout.LP(i, 0)) }
            box.addView(cl, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, (150 * f).toInt()))
        } else {
            val res = allApps.filter { it.label.contains(q.trim(), true) }.sortedBy { it.label.lowercase() }
            val rows = max(1, (res.size + 3) / 4)
            val cl = CellLayout(context, 4, rows)
            cl.padH = (12 * f).toInt()
            res.forEachIndexed { i, a -> cl.addView(appCell(a, a.comp), CellLayout.LP(i % 4, i / 4)) }
            box.addView(cl, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, rows * (214 * f).toInt()))
        }
        scroll.removeAllViews()
        scroll.addView(box, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
    }

    fun enterSearch() {
        if (searching) return
        searching = true
        bar.setSearching(true)
        indicator.visibility = View.GONE
        pager.animate().alpha(0f).setDuration(140).withEndAction {
            pager.visibility = View.GONE
            pager.alpha = 1f
        }.start()
        scroll.visibility = View.VISIBLE
        showResults("")
        bar.edit.requestFocus()
        bar.edit.post {
            val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.showSoftInput(bar.edit, 0)
        }
    }

    fun exitSearch() {
        if (!searching) return
        searching = false
        bar.edit.setText("")
        bar.setSearching(false)
        val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(bar.edit.windowToken, 0)
        scroll.visibility = View.GONE
        pager.animate().cancel()
        pager.alpha = 1f
        pager.visibility = View.VISIBLE
        indicator.visibility = View.VISIBLE
    }

    // ---------------------------------------------------------- layout

    override fun onMeasure(wSpec: Int, hSpec: Int) {
        val w = MeasureSpec.getSize(wSpec)
        val h = MeasureSpec.getSize(hSpec)
        setMeasuredDimension(w, h)
        fun exact(v: View, vw: Int, vh: Int) = v.measure(
            MeasureSpec.makeMeasureSpec(vw, MeasureSpec.EXACTLY),
            MeasureSpec.makeMeasureSpec(vh, MeasureSpec.EXACTLY)
        )
        val gridTop = (165 * f).toInt()
        val gh = minOf((1070 * f).toInt(), h - gridTop - (30 * f).toInt())
        exact(bg, w, h)
        exact(dim, w, h)
        exact(bar, w, (92 * f).toInt())
        exact(cleanBar, w, (92 * f).toInt())
        exact(pager, w, gh)
        exact(overlay, w, gh)
        exact(scroll, w, h - gridTop)
        exact(indicator, w, (36 * f).toInt())
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        val w = r - l
        val h = b - t
        val barTop = (48 * f).toInt()
        val gridTop = (165 * f).toInt()
        bg.layout(0, 0, w, h)
        dim.layout(0, 0, w, h)
        bar.layout(0, barTop, w, barTop + bar.measuredHeight)
        cleanBar.layout(0, barTop, w, barTop + cleanBar.measuredHeight)
        pager.layout(0, gridTop, w, gridTop + pager.measuredHeight)
        overlay.layout(0, gridTop, w, gridTop + overlay.measuredHeight)
        scroll.layout(0, gridTop, w, h)
        val indTop = h - (48 * f).toInt() - indicator.measuredHeight / 2
        indicator.layout(0, indTop, w, indTop + indicator.measuredHeight)
    }
}
