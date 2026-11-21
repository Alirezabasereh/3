package com.example.oneuilauncher

import android.app.Activity
import android.app.Dialog
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.text.Editable
import android.text.TextWatcher
import android.util.TypedValue
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.viewpager.widget.ViewPager
import kotlin.math.max

/** صفحهٔ انتخاب ویجت (تیره، دو ستون، سوایپ افقی) مثل لانچر سامسونگ */
class WidgetPicker(
    private val act: Activity,
    private val f: Float,
    private val awm: AppWidgetManager,
    private val spansOf: (AppWidgetProviderInfo) -> Pair<Int, Int>,
    private val backdrop: Bitmap?,
    private val onPick: (AppWidgetProviderInfo) -> Unit
) {
    private class Entry(val pkg: String, val appLabel: String, val info: AppWidgetProviderInfo, val label: String)

    private val pm = act.packageManager
    private val dlg = Dialog(act, android.R.style.Theme_Black_NoTitleBar_Fullscreen)
    private val root = FrameLayout(act)
    private val bar = SearchBar(act, f)
    private val title = TextView(act)
    private val pager = ViewPager(act)
    private val indicator = PageIndicator(act, f, true)

    private var all: List<Entry> = emptyList()
    private var group: String? = null
    private var query = ""

    fun show() {
        all = awm.installedProviders.map {
            val pkg = it.provider.packageName
            val app = try {
                pm.getApplicationInfo(pkg, 0).loadLabel(pm).toString()
            } catch (e: Exception) {
                pkg
            }
            Entry(pkg, app, it, (it.loadLabel(pm) ?: app).toString())
        }.sortedBy { it.appLabel.lowercase() }

        if (backdrop != null) {
            // پس‌زمینه: همان والپیپرِ بلور و تینت‌شده (نه سیاه)
            val bgv = ImageView(act)
            bgv.scaleType = ImageView.ScaleType.CENTER_CROP
            bgv.setImageBitmap(backdrop)
            root.addView(bgv, FrameLayout.LayoutParams(-1, -1))
            val tint = View(act)
            tint.setBackgroundColor(0x80000000.toInt())
            root.addView(tint, FrameLayout.LayoutParams(-1, -1))
        } else {
            root.setBackgroundColor(0xE6000000.toInt())
        }
        bar.setHint("Search for widgets")
        root.addView(bar, FrameLayout.LayoutParams(-1, (92 * f).toInt()).apply { topMargin = (48 * f).toInt() })

        title.setTextColor(0xFFEEEEEE.toInt())
        title.setTextSize(TypedValue.COMPLEX_UNIT_PX, 34f * f)
        title.gravity = Gravity.CENTER
        title.visibility = View.GONE
        root.addView(title, FrameLayout.LayoutParams(-1, (92 * f).toInt()).apply { topMargin = (48 * f).toInt() })

        root.addView(pager, FrameLayout.LayoutParams(-1, (880 * f).toInt()).apply { topMargin = (150 * f).toInt() })
        root.addView(indicator, FrameLayout.LayoutParams(-1, (36 * f).toInt()).apply { topMargin = (1230 * f).toInt() })

        pager.addOnPageChangeListener(object : ViewPager.SimpleOnPageChangeListener() {
            override fun onPageSelected(position: Int) {
                indicator.current = position
                indicator.invalidate()
            }
        })
        bar.onActivate = {
            bar.setSearching(true)
            bar.edit.requestFocus()
            bar.edit.post {
                val imm = act.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                imm.showSoftInput(bar.edit, 0)
            }
        }
        bar.edit.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun afterTextChanged(s: Editable?) {
                query = s?.toString() ?: ""
                group = null
                render()
            }
        })

        dlg.setContentView(root)
        dlg.window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        dlg.setOnKeyListener { _, code, ev ->
            if (code == KeyEvent.KEYCODE_BACK && ev.action == KeyEvent.ACTION_UP) {
                if (group != null) {
                    group = null
                    render()
                    true
                } else if (query.isNotEmpty() || bar.edit.visibility == View.VISIBLE) {
                    bar.edit.setText("")
                    bar.setSearching(false)
                    query = ""
                    render()
                    true
                } else false
            } else false
        }
        render()
        dlg.show()
    }

    private fun preview(info: AppWidgetProviderInfo): Drawable? {
        return try {
            info.loadPreviewImage(act, 0) ?: info.loadIcon(act, 0)
        } catch (e: Exception) {
            null
        }
    }

    private fun tile(header: String, right: String, pv: Drawable?, onClick: () -> Unit): View {
        val outer = FrameLayout(act)
        outer.setPadding((8 * f).toInt(), (8 * f).toInt(), (8 * f).toInt(), (8 * f).toInt())
        val box = FrameLayout(act)
        val bg = GradientDrawable()
        bg.setColor(if (backdrop != null) 0x99141414.toInt() else 0xFF1C1C1C.toInt())
        bg.cornerRadius = 6f * f
        box.background = bg
        box.setOnClickListener { onClick() }

        val h = TextView(act)
        h.text = header
        h.setTextColor(0xFF8C8C8C.toInt())
        h.setTextSize(TypedValue.COMPLEX_UNIT_PX, 27f * f)
        h.maxLines = 1
        h.ellipsize = android.text.TextUtils.TruncateAt.END
        val hlp = FrameLayout.LayoutParams(-2, -2)
        hlp.leftMargin = (16 * f).toInt()
        hlp.topMargin = (14 * f).toInt()
        hlp.rightMargin = (96 * f).toInt()
        box.addView(h, hlp)

        val r = TextView(act)
        r.text = right
        r.setTextColor(0xFF8C8C8C.toInt())
        r.setTextSize(TypedValue.COMPLEX_UNIT_PX, 27f * f)
        val rlp = FrameLayout.LayoutParams(-2, -2)
        rlp.gravity = Gravity.END or Gravity.TOP
        rlp.rightMargin = (16 * f).toInt()
        rlp.topMargin = (14 * f).toInt()
        box.addView(r, rlp)

        val iv = ImageView(act)
        iv.setImageDrawable(pv)
        iv.scaleType = ImageView.ScaleType.FIT_CENTER
        iv.adjustViewBounds = true
        val ilp = FrameLayout.LayoutParams((240 * f).toInt(), (140 * f).toInt())
        ilp.gravity = Gravity.CENTER
        ilp.topMargin = (24 * f).toInt()
        box.addView(iv, ilp)

        outer.addView(box, FrameLayout.LayoutParams(-1, -1))
        return outer
    }

    private fun sizeText(info: AppWidgetProviderInfo): String {
        val s = spansOf(info)
        return "${s.first} × ${s.second}"
    }

    private fun render() {
        val tiles = ArrayList<View>()
        val q = query.trim()
        if (q.isNotEmpty()) {
            // نتایج جستجو: خود ویجت‌ها
            for (e in all.filter { it.appLabel.contains(q, true) || it.label.contains(q, true) }) {
                tiles.add(tile(e.label, sizeText(e.info), preview(e.info)) { pick(e.info) })
            }
            title.visibility = View.GONE
            bar.visibility = View.VISIBLE
        } else if (group != null) {
            val g = all.filter { it.pkg == group }
            for (e in g) tiles.add(tile(e.label, sizeText(e.info), preview(e.info)) { pick(e.info) })
            title.text = g.firstOrNull()?.appLabel ?: ""
            title.visibility = View.VISIBLE
            bar.visibility = View.GONE
        } else {
            val byPkg = LinkedHashMap<String, ArrayList<Entry>>()
            for (e in all) byPkg.getOrPut(e.pkg) { ArrayList() }.add(e)
            for ((pkg, list) in byPkg) {
                val first = list[0]
                if (list.size == 1) {
                    tiles.add(tile(first.appLabel, sizeText(first.info), preview(first.info)) { pick(first.info) })
                } else {
                    tiles.add(tile(first.appLabel, "▢ ${list.size}", preview(first.info)) {
                        group = pkg
                        render()
                    })
                }
            }
            title.visibility = View.GONE
            bar.visibility = View.VISIBLE
        }

        val pages = ArrayList<View>()
        for (chunk in tiles.chunked(6)) {
            val cl = CellLayout(act, 2, 3)
            cl.padH = (24 * f).toInt()
            chunk.forEachIndexed { i, v -> cl.addView(v, CellLayout.LP(i % 2, i / 2)) }
            pages.add(cl)
        }
        if (pages.isEmpty()) pages.add(CellLayout(act, 2, 3))
        pager.adapter = ViewsAdapter(pages)
        indicator.count = pages.size
        indicator.current = 0
        indicator.invalidate()
    }

    private fun pick(info: AppWidgetProviderInfo) {
        dlg.dismiss()
        onPick(info)
    }
}
