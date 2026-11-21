package com.example.oneuilauncher

import android.Manifest
import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.app.Activity
import android.app.AlertDialog
import android.app.WallpaperManager
import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.BroadcastReceiver
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ApplicationInfo
import android.content.pm.LauncherApps
import android.content.pm.ShortcutInfo
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.util.TypedValue
import android.view.DragEvent
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.animation.DecelerateInterpolator
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.viewpager.widget.ViewPager
import org.json.JSONArray
import org.json.JSONObject
import java.util.IdentityHashMap
import kotlin.concurrent.thread
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/** مرجع یک پوشه: home=true یعنی پوشهٔ صفحهٔ هوم (id)، وگرنه پوشهٔ اپ‌دراور (id = نام) */
class FolderRef(val home: Boolean, val id: String)

class HomeFolder(var name: String, val members: ArrayList<String>)

class DragItem(
    val comp: String,           // comp اپ، folder:<name> برای پوشهٔ دراور، hf:<id> برای پوشهٔ هوم
    val srcView: View?,
    val srcDock: Int,
    val homeItem: HomeItem?,
    val fromDrawer: Boolean,
    val fromFolder: FolderRef?
)

class MainActivity : Activity() {

    private val REQ_BIND = 101
    private val REQ_CONFIG = 102
    private val REQ_STORAGE = 103
    private val ROWS = 4

    private lateinit var prefs: Prefs
    private lateinit var icons: Icons
    private lateinit var root: GestureRoot
    private lateinit var homeLayer: FrameLayout
    private lateinit var homePager: ViewPager
    private lateinit var homeOverlay: ReorderOverlay
    private lateinit var homeIndicator: PageIndicator
    private lateinit var dockLayout: LinearLayout
    private lateinit var editBar: EditBar
    private lateinit var editHome: HomeMark
    private lateinit var widgetEditor: WidgetEditor
    private lateinit var drawer: DrawerView
    private lateinit var removeBar: LinearLayout
    private lateinit var addBar: TextView
    private lateinit var selBar: LinearLayout
    private lateinit var selText: TextView
    private lateinit var awm: AppWidgetManager
    private lateinit var widgetHost: AppWidgetHost

    private var f = 1f
    private var bottomInset = 0
    private var apps: List<AppInfo> = emptyList()
    private var byComp: Map<String, AppInfo> = emptyMap()
    private var drawerLayout: DrawerLayout? = null
    private val homeItems = ArrayList<HomeItem>()
    private val homeFolders = LinkedHashMap<String, HomeFolder>()
    private val dock = Array(5) { "" }
    private val widgetViews = IdentityHashMap<HomeItem, WidgetFrame>()
    private val main = Handler(Looper.getMainLooper())

    private var wallBlur: Bitmap? = null
    private var wallLight = false
    private var wallTopLight = false

    private var progress = 0f
    private var drawerOpen = false
    private var gestureStartOpen = false
    private var anim: ValueAnimator? = null
    private var lastSysUi = -1

    private var editMode = false

    // ---- کشیدن و رها کردن
    private var drag: DragItem? = null
    private var dragActive = false
    private var dragCtx = 0            // 0 هوم، 1 دراور، 2 پوشهٔ باز
    private var dragStartX = 0f
    private var dragStartY = 0f
    private var edgeDir = 0
    private val edgeRunnable = Runnable { flipPage() }
    private var pendingWid = -1

    // ---- هدف کشیدن در هوم
    private var hCurCell = -1
    private var hMode = 0              // 0 جابه‌جایی، 1 افزودن به پوشه، 2 ساخت پوشه
    private var hSpot: Pair<Int, Int>? = null
    private var hOcc: HomeItem? = null
    private var hInner = false
    private val hEval = Runnable { homeEvaluate() }
    private val hMerge = Runnable { homeMergeDwell() }

    private var menuLayer: FrameLayout? = null

    // ---- پوشهٔ باز
    private var folderLayer: FrameLayout? = null
    private var folderCol: View? = null
    private var folderRef: FolderRef? = null
    private var folderGrid: CellLayout? = null
    private var folderOverlay: ReorderOverlay? = null
    private var folderTitleEdit: EditText? = null
    private val folderCells = HashMap<String, View>()
    private var folderList = ArrayList<String>()
    private var fOrig = -1
    private var fCurT = -1
    private var fPreview: ArrayList<String>? = null
    private val fEval = Runnable { folderEvaluate() }

    private val pkgReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            reloadApps()
        }
    }

    // ================================================================ lifecycle

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.TRANSPARENT

        prefs = Prefs(this)
        icons = Icons(this, prefs)
        f = resources.displayMetrics.widthPixels / 720f
        awm = AppWidgetManager.getInstance(this)
        widgetHost = AppWidgetHost(this, 1024)
        loadState()
        buildUi()

        val filter = IntentFilter()
        filter.addAction(Intent.ACTION_PACKAGE_ADDED)
        filter.addAction(Intent.ACTION_PACKAGE_REMOVED)
        filter.addAction(Intent.ACTION_PACKAGE_CHANGED)
        filter.addDataScheme("package")
        registerReceiver(pkgReceiver, filter)

        reloadApps()
    }

    override fun onStart() {
        super.onStart()
        try { widgetHost.startListening() } catch (e: Exception) {}
        loadWallpaperBlur()
    }

    override fun onStop() {
        try { widgetHost.stopListening() } catch (e: Exception) {}
        super.onStop()
    }

    override fun onDestroy() {
        unregisterReceiver(pkgReceiver)
        super.onDestroy()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (drawer.selectMode) { endSelect(); return }
        if (menuLayer != null) { dismissMenu(); return }
        if (folderLayer != null) { closeFolder(); return }
        if (widgetEditor.active) { widgetEditor.hide(); return }
        if (editMode) { exitEditMode(homePager.currentItem); return }
        if (drawer.searching) { drawer.exitSearch(); return }
        if (progress > 0.5f) animateTo(0f)
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        dismissMenu()
        closeFolderNow()
        widgetEditor.hide()
        if (drawer.selectMode) endSelect()
        if (editMode) exitEditMode(prefs.homePage)
        else if (progress > 0.5f) animateTo(0f)
        else homePager.setCurrentItem(prefs.homePage.coerceAtMost(max(1, prefs.pages) - 1), true)
    }

    // ================================================================ state

    private fun loadState() {
        homeItems.clear()
        try {
            val a = JSONArray(prefs.home)
            for (i in 0 until a.length()) {
                val o = a.getJSONObject(i)
                homeItems.add(
                    HomeItem(
                        o.getString("t"), o.getInt("p"), o.getInt("x"), o.getInt("y"),
                        o.getInt("sx"), o.getInt("sy"), o.optString("c", ""), o.optInt("w", -1)
                    )
                )
            }
        } catch (e: Exception) {
        }
        homeFolders.clear()
        try {
            val o = JSONObject(prefs.homeFolders)
            val it = o.keys()
            while (it.hasNext()) {
                val id = it.next()
                val fo = o.getJSONObject(id)
                val arr = fo.getJSONArray("members")
                val list = ArrayList<String>()
                for (i in 0 until arr.length()) list.add(arr.getString(i))
                homeFolders[id] = HomeFolder(fo.optString("name", ""), list)
            }
        } catch (e: Exception) {
        }
        val parts = prefs.dock.split("|")
        for (i in 0 until 5) dock[i] = parts.getOrNull(i) ?: ""

        // یک‌بار: گرید ۴×۴ و اندازهٔ درست ویجت‌ها (minWidth/minHeight بر حسب پیکسل است)
        if (!prefs.gridMigrated) {
            for (h in homeItems) {
                if (h.type == "widget") {
                    val info = awm.getAppWidgetInfo(h.wid)
                    if (info != null) {
                        val s = spansFor(info)
                        h.sx = s.first
                        h.sy = s.second
                    }
                }
                h.sx = h.sx.coerceIn(1, 4)
                h.sy = h.sy.coerceIn(1, ROWS)
                if (h.y + h.sy > ROWS) h.y = ROWS - h.sy
                if (h.x + h.sx > 4) h.x = 4 - h.sx
            }
            prefs.gridMigrated = true
            saveHome()
        }
    }

    private fun saveHome() {
        val a = JSONArray()
        for (h in homeItems) {
            a.put(
                JSONObject().put("t", h.type).put("p", h.page).put("x", h.x).put("y", h.y)
                    .put("sx", h.sx).put("sy", h.sy).put("c", h.comp).put("w", h.wid)
            )
        }
        prefs.home = a.toString()
    }

    private fun saveHomeFolders() {
        val o = JSONObject()
        for ((id, hf) in homeFolders) {
            o.put(id, JSONObject().put("name", hf.name).put("members", JSONArray(hf.members)))
        }
        prefs.homeFolders = o.toString()
    }

    private fun saveDock() {
        prefs.dock = dock.joinToString("|")
    }

    private fun loadOrder(): List<String> {
        return try {
            val a = JSONArray(prefs.drawerOrder)
            val l = ArrayList<String>()
            for (i in 0 until a.length()) l.add(a.getString(i))
            l
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun saveOrder(slots: List<String>) {
        prefs.drawerOrder = JSONArray(slots).toString()
    }

    private fun loadFolderOrders(): Map<String, List<String>> {
        val m = HashMap<String, List<String>>()
        try {
            val o = JSONObject(prefs.folderOrders)
            val it = o.keys()
            while (it.hasNext()) {
                val k = it.next()
                val arr = o.getJSONArray(k)
                val l = ArrayList<String>()
                for (i in 0 until arr.length()) l.add(arr.getString(i))
                m[k] = l
            }
        } catch (e: Exception) {
        }
        return m
    }

    private fun saveFolderOrder(name: String, list: List<String>) {
        val o = try { JSONObject(prefs.folderOrders) } catch (e: Exception) { JSONObject() }
        o.put(name, JSONArray(list))
        prefs.folderOrders = o.toString()
    }

    private fun reloadApps() {
        val ctx = applicationContext
        thread {
            val list = Repo.load(ctx)
            main.post {
                apps = list
                byComp = list.associateBy { it.comp }
                ensureDock()
                refresh()
            }
        }
    }

    private fun ensureDock() {
        if (dock.all { it.isEmpty() } && prefs.dock.isEmpty()) {
            val keys = listOf("phone", "messages", "internet", "gallery", "camera")
            for (i in keys.indices) {
                dock[i] = apps.firstOrNull { it.key == keys[i] }?.comp ?: ""
            }
            saveDock()
        }
    }

    private fun refresh() {
        rebuildDock()
        rebuildHome()
        refreshDrawer()
    }

    private fun refreshDrawer() {
        val lay = Repo.layout(apps, prefs.assignMap(), loadOrder(), loadFolderOrders())
        drawerLayout = lay
        saveOrder(lay.slots)
        drawer.setLayout(lay, apps)
    }

    // ================================================================ رنگ‌های وابسته به والپیپر

    private fun labelDark(): Boolean = prefs.autoColors && wallLight
    private fun labelColor(): Int = if (labelDark()) 0xFF222222.toInt() else Color.WHITE

    private fun applyStatusBar() {
        var flags = View.SYSTEM_UI_FLAG_LAYOUT_STABLE or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
        val light = prefs.autoColors && wallTopLight && progress < 0.5f
        if (light) flags = flags or View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
        if (flags != lastSysUi) {
            lastSysUi = flags
            root.systemUiVisibility = flags
        }
    }

    private fun lum(b: Bitmap, y0: Int, y1: Int): Float {
        var sum = 0.0
        var n = 0
        var y = y0
        while (y < y1 && y < b.height) {
            var x = 0
            while (x < b.width) {
                val c = b.getPixel(x, y)
                sum += (0.299 * Color.red(c) + 0.587 * Color.green(c) + 0.114 * Color.blue(c)) / 255.0
                n++
                x += 3
            }
            y += 3
        }
        return if (n == 0) 0f else (sum / n).toFloat()
    }

    private fun applyWallColors(top: Boolean, mid: Boolean) {
        if (top == wallTopLight && mid == wallLight) return
        wallTopLight = top
        wallLight = mid
        lastSysUi = -1
        applyStatusBar()
        homeIndicator.dark = labelDark()
        homeIndicator.invalidate()
        rebuildDock()
        rebuildHome()
    }

    private fun loadWallpaperBlur() {
        thread {
            try {
                val d = WallpaperManager.getInstance(this).drawable ?: return@thread
                val bw = 180
                val bh = 320
                val dw = d.intrinsicWidth.toFloat()
                val dh = d.intrinsicHeight.toFloat()
                if (dw <= 0f || dh <= 0f) return@thread
                val bmp = Bitmap.createBitmap(bw, bh, Bitmap.Config.ARGB_8888)
                val sc = max(bw / dw, bh / dh)
                val w = dw * sc
                val h = dh * sc
                val l = (bw - w) / 2f
                val t = (bh - h) / 2f
                d.setBounds(l.toInt(), t.toInt(), (l + w).toInt(), (t + h).toInt())
                d.draw(Canvas(bmp))
                val top = lum(bmp, 0, 40) > 0.58f
                val mid = lum(bmp, 40, 270) > 0.58f
                val blurred = Blur.blur(this, bmp, 14f, 1)
                main.post {
                    wallBlur = blurred
                    drawer.setBlur(blurred)
                    applyWallColors(top, mid)
                }
            } catch (e: SecurityException) {
                main.post { askStorage() }
            } catch (e: Throwable) {
            }
        }
    }

    private fun askStorage() {
        if (prefs.askedStorage || Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return
        prefs.askedStorage = true
        requestPermissions(arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE), REQ_STORAGE)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQ_STORAGE) loadWallpaperBlur()
    }

    // ================================================================ UI

    private fun buildUi() {
        root = GestureRoot(this)

        homeLayer = FrameLayout(this)
        homeLayer.clipChildren = false
        root.addView(homeLayer, FrameLayout.LayoutParams(-1, -1))

        homePager = ViewPager(this)
        homePager.clipChildren = false
        homeLayer.addView(homePager, FrameLayout.LayoutParams(-1, (908 * f).toInt()))
        homeOverlay = ReorderOverlay(this, f)
        homeOverlay.visibility = View.GONE
        homeLayer.addView(homeOverlay, FrameLayout.LayoutParams(-1, (908 * f).toInt()))
        homeIndicator = PageIndicator(this, f, false)
        homeLayer.addView(homeIndicator, FrameLayout.LayoutParams(-1, (36 * f).toInt()))
        dockLayout = LinearLayout(this)
        dockLayout.orientation = LinearLayout.HORIZONTAL
        dockLayout.setPadding((12 * f).toInt(), 0, (12 * f).toInt(), 0)
        homeLayer.addView(dockLayout, FrameLayout.LayoutParams(-1, (160 * f).toInt()))

        editHome = HomeMark(this, f)
        editHome.visibility = View.GONE
        editHome.setOnClickListener {
            prefs.homePage = homePager.currentItem.coerceAtMost(max(1, prefs.pages) - 1)
            homeIndicator.homeIndex = prefs.homePage
            homeIndicator.invalidate()
            editHome.filled = true
            editHome.invalidate()
        }
        val hlp = FrameLayout.LayoutParams((140 * f).toInt(), (80 * f).toInt())
        hlp.gravity = Gravity.CENTER_HORIZONTAL
        homeLayer.addView(editHome, hlp)

        editBar = EditBar(this, f)
        editBar.visibility = View.GONE
        editBar.onItem = { i -> onEditBar(i) }
        homeLayer.addView(editBar, FrameLayout.LayoutParams(-1, (160 * f).toInt()))

        homePager.addOnPageChangeListener(object : ViewPager.SimpleOnPageChangeListener() {
            override fun onPageSelected(position: Int) {
                val last = max(1, prefs.pages) - 1
                homeIndicator.current = min(position, last)
                homeIndicator.invalidate()
                editHome.filled = min(position, last) == prefs.homePage
                editHome.invalidate()
            }
        })

        widgetEditor = WidgetEditor(this, f)
        widgetEditor.onCommit = { item, x, y, sx, sy -> onWidgetCommit(item, x, y, sx, sy) }
        widgetEditor.onRemove = { item -> removeWidget(item) }
        root.addView(widgetEditor, FrameLayout.LayoutParams(-1, -1))

        drawer = DrawerView(this, f, icons, object : DrawerView.Host {
            override fun launch(app: AppInfo) = launchApp(app)
            override fun longPress(view: View, key: String) {
                if (key.startsWith("folder:")) {
                    startDrag(view, DragItem(key, view, -1, null, true, null))
                } else {
                    val app = byComp[key] ?: return
                    showAppMenu(view, app, 0, null, -1)
                    startDrag(view, DragItem(key, view, -1, null, true, null))
                }
            }
            override fun openFolder(name: String, view: View) = showFolder(FolderRef(false, name), view, false)
            override fun menu(anchor: View) {
                val pm = PopupMenu(this@MainActivity, anchor, Gravity.END)
                pm.menu.add(0, 1, 0, "Sort")
                pm.menu.add(0, 2, 1, "Clean up pages")
                pm.menu.add(0, 3, 2, "Home screen settings")
                pm.setOnMenuItemClickListener { mi ->
                    when (mi.itemId) {
                        1 -> sortDrawer()
                        2 -> drawer.startCleanup()
                        else -> showSettings()
                    }
                    true
                }
                pm.show()
            }
            override fun folderColor(name: String): Int = prefs.folderColor(name)
            override fun recents(): List<AppInfo> = suggestedApps()
            override fun selectionChanged(count: Int) {
                selText.text = "$count selected"
                selBar.visibility = if (drawer.selectMode) View.VISIBLE else View.GONE
            }
            override fun slotsChanged(slots: List<String>) = onDrawerSlots(slots)
            override fun dropIntoFolder(key: String, folder: String) = onDrawerDropIntoFolder(key, folder)
            override fun mergeIntoNewFolder(key: String, target: String) = onDrawerMerge(key, target)
        })
        drawer.visibility = View.GONE
        drawer.alpha = 0f
        root.addView(drawer, FrameLayout.LayoutParams(-1, -1))

        removeBar = LinearLayout(this)
        removeBar.orientation = LinearLayout.HORIZONTAL
        removeBar.setBackgroundColor(0xCC202020.toInt())
        removeBar.addView(barLabel("Remove"), LinearLayout.LayoutParams(0, -1, 1f))
        removeBar.addView(barLabel("Uninstall"), LinearLayout.LayoutParams(0, -1, 1f))
        removeBar.visibility = View.GONE
        val rlp = FrameLayout.LayoutParams(-1, (80 * f).toInt())
        rlp.topMargin = (48 * f).toInt()
        root.addView(removeBar, rlp)

        addBar = barLabel("Drag here to add to Home screen.")
        addBar.setBackgroundColor(0xB02B6CB8.toInt())
        addBar.visibility = View.GONE
        val alp = FrameLayout.LayoutParams(-1, (80 * f).toInt())
        alp.topMargin = (48 * f).toInt()
        root.addView(addBar, alp)

        selBar = LinearLayout(this)
        selBar.orientation = LinearLayout.HORIZONTAL
        selBar.gravity = Gravity.CENTER_VERTICAL
        selBar.setBackgroundColor(0xE6202020.toInt())
        selBar.setPadding((24 * f).toInt(), 0, (12 * f).toInt(), 0)
        selText = barLabel("0 selected")
        selText.gravity = Gravity.CENTER_VERTICAL
        selBar.addView(selText, LinearLayout.LayoutParams(0, -1, 1f))
        selBar.addView(selButton("Add to Home") {
            for (a in drawer.selectedApps()) addAppToHome(a, false)
            endSelect()
        })
        selBar.addView(selButton("Uninstall") {
            for (a in drawer.selectedApps()) if (!isSystemApp(a.pkg)) uninstall(a.comp)
            endSelect()
        })
        selBar.addView(selButton("Cancel") { endSelect() })
        selBar.visibility = View.GONE
        val slp = FrameLayout.LayoutParams(-1, (84 * f).toInt())
        slp.topMargin = (48 * f).toInt()
        root.addView(selBar, slp)

        root.canIntercept = { dy ->
            if (dragActive || editMode || widgetEditor.active || menuLayer != null || folderLayer != null || drawer.selectMode || drawer.dragging) false
            else if (progress < 0.5f && !drawerOpen) true
            else dy > 0 && !drawer.searching
        }
        root.onStart = {
            anim?.cancel()
            gestureStartOpen = progress > 0.5f
        }
        root.onDrag = { dy ->
            val range = root.height * 0.3f
            val p = if (gestureStartOpen) 1f - (dy / range).coerceIn(0f, 1f)
            else (abs(dy) / range).coerceIn(0f, 1f)
            setProgress(p)
        }
        root.onRelease = { _, vy ->
            val open = if (gestureStartOpen) !(progress < 0.7f || vy > 900f)
            else (progress > 0.25f || abs(vy) > 900f)
            animateTo(if (open) 1f else 0f)
        }
        root.setOnDragListener(dragListener)
        root.addOnLayoutChangeListener { _, l, t, r, b, _, _, _, _ -> layoutAll(r - l, b - t) }
        root.setOnApplyWindowInsetsListener { _, ins ->
            bottomInset = ins.systemWindowInsetBottom
            layoutAll(root.width, root.height)
            ins
        }
        setContentView(root)
        applyStatusBar()
    }

    private fun barLabel(text: String): TextView {
        val t = TextView(this)
        t.text = text
        t.setTextColor(Color.WHITE)
        t.setTextSize(TypedValue.COMPLEX_UNIT_PX, 28f * f)
        t.gravity = Gravity.CENTER
        return t
    }

    private fun selButton(text: String, action: () -> Unit): TextView {
        val t = barLabel(text)
        t.setTextSize(TypedValue.COMPLEX_UNIT_PX, 25f * f)
        t.setPadding((18 * f).toInt(), 0, (18 * f).toInt(), 0)
        t.setOnClickListener { action() }
        return t
    }

    private fun layoutAll(w: Int, h: Int) {
        if (w == 0 || h == 0) return
        val bh = h - bottomInset
        setTop(homePager, (75 * f).toInt())
        setTop(homeOverlay, (75 * f).toInt())
        setTop(homeIndicator, bh - (303 * f).toInt())
        setTop(dockLayout, bh - (220 * f).toInt())
        setTop(editBar, bh - (222 * f).toInt())
        setTop(editHome, (76 * f).toInt())
    }

    private fun setTop(v: View, top: Int) {
        val lp = v.layoutParams as FrameLayout.LayoutParams
        if (lp.topMargin != top) {
            lp.topMargin = top
            v.layoutParams = lp
        }
    }

    // ================================================================ drawer open/close

    /** p=0 هوم، p=1 دراور. هوم بالا می‌رود و کوچک می‌شود؛ دراور از پایین می‌آید و بزرگ می‌شود (اندازه‌گیری‌شده از ویدیو) */
    private fun setProgress(p: Float) {
        progress = p
        val homeA = (1f - 2.3f * p).coerceIn(0f, 1f)
        val drawA = ((p - 0.3f) * 2.4f).coerceIn(0f, 1f)
        homeLayer.alpha = homeA
        homeLayer.translationY = -45f * f * p
        homeLayer.scaleX = 1f - 0.033f * p
        homeLayer.scaleY = 1f - 0.033f * p
        homeLayer.visibility = if (homeA <= 0f) View.INVISIBLE else View.VISIBLE
        drawer.alpha = drawA
        drawer.translationY = 80f * f * (1f - p)
        drawer.scaleX = 0.97f + 0.03f * p
        drawer.scaleY = 0.97f + 0.03f * p
        drawer.visibility = if (p <= 0f) View.GONE else View.VISIBLE
        applyStatusBar()
    }

    private fun animateTo(target: Float) {
        anim?.cancel()
        val a = ValueAnimator.ofFloat(progress, target)
        a.duration = 300
        a.interpolator = DecelerateInterpolator(1.6f)
        a.addUpdateListener { setProgress(it.animatedValue as Float) }
        a.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator) {
                drawerOpen = target >= 1f
                if (!drawerOpen) {
                    drawer.exitSearch()
                    if (drawer.selectMode) endSelect()
                }
            }
        })
        anim = a
        a.start()
    }

    // ================================================================ home & dock

    private fun curHomePage(): Int = homePager.currentItem.coerceAtMost(max(1, prefs.pages) - 1)

    private fun cellLayoutAt(page: Int): CellLayout? =
        ((homePager.adapter as? ViewsAdapter)?.views?.getOrNull(page) as? PageCard)?.grid

    private fun rebuildHome() {
        widgetViews.clear()
        val pageCount = max(1, prefs.pages)
        val views = ArrayList<View>()
        for (p in 0 until pageCount) {
            val grid = CellLayout(this, 4, ROWS)
            grid.setOnLongClickListener {
                if (!editMode) enterEditMode()
                true
            }
            val items = homeItems.filter { it.page == p }
            for (item in items) {
                val v = makeHomeView(item) ?: continue
                grid.addView(v, CellLayout.LP(item.x, item.y, item.sx, item.sy))
            }
            val card = PageCard(this, grid, f)
            card.showTrash = pageCount > 1 && items.isEmpty()
            card.editMode = editMode
            card.onTrash = { deletePage(p) }
            card.onSelect = { exitEditMode(p) }
            views.add(card)
        }
        if (editMode && pageCount < 7) {
            val add = AddCard(this, f)
            add.setOnClickListener { addPage() }
            views.add(add)
        }
        val cur = homePager.currentItem
        homePager.adapter = ViewsAdapter(views)
        val target = min(cur, pageCount - 1)
        homePager.setCurrentItem(target, false)
        homeIndicator.count = pageCount
        homeIndicator.homeIndex = prefs.homePage.coerceAtMost(pageCount - 1)
        homeIndicator.dark = labelDark()
        homeIndicator.current = target
        homeIndicator.invalidate()
        editHome.filled = target == prefs.homePage
        editHome.invalidate()
    }

    private fun folderApps(comps: List<String>): List<AppInfo> = comps.mapNotNull { byComp[it] }

    private fun makeHomeView(item: HomeItem): View? {
        val px = (102 * f).toInt()
        if (item.type == "widget") {
            val info = awm.getAppWidgetInfo(item.wid) ?: return null
            val hv = widgetHost.createView(this, item.wid, info)
            hv.setAppWidget(item.wid, info)
            val frame = WidgetFrame(this, hv)
            frame.onLongPress = {
                openWidgetEditor(item, frame)
                frame.forward = { ev -> widgetEditor.externalTouch(ev) }
            }
            val dens = resources.displayMetrics.density
            frame.addOnLayoutChangeListener { _, l, t, r, b, _, _, _, _ ->
                val wDp = ((r - l) / dens).toInt()
                val hDp = ((b - t) / dens).toInt()
                if (wDp > 0 && hDp > 0) {
                    try { hv.updateAppWidgetSize(null, wDp, hDp, wDp, hDp) } catch (e: Exception) {}
                }
            }
            widgetViews[item] = frame
            return frame
        }
        if (item.type == "folder") {
            val hf = homeFolders[item.comp] ?: return null
            val members = folderApps(hf.members)
            val v = AppCellView(this, px, 25f * f, labelColor(), !labelDark())
            v.bind(icons.folder(members, px, prefs.folderColor("hf:" + item.comp)), if (hf.name.isBlank()) "Folder" else hf.name)
            v.setOnClickListener { showFolder(FolderRef(true, item.comp), v, false) }
            v.setOnLongClickListener {
                startDrag(v, DragItem("hf:" + item.comp, v, -1, item, false, null))
                true
            }
            return v
        }
        val app = byComp[item.comp] ?: return null
        val v = AppCellView(this, px, 25f * f, labelColor(), !labelDark())
        v.bind(icons.get(app, px), app.label)
        v.setOnClickListener { launchApp(app) }
        v.setOnLongClickListener {
            showAppMenu(v, app, 1, item, -1)
            startDrag(v, DragItem(app.comp, v, -1, item, false, null))
            true
        }
        return v
    }

    private fun rebuildDock() {
        dockLayout.removeAllViews()
        val px = (92 * f).toInt()
        for (i in 0 until 5) {
            val slot = FrameLayout(this)
            val app = if (dock[i].isEmpty()) null else byComp[dock[i]]
            if (app != null) {
                val v = AppCellView(this, px, 25f * f, labelColor(), !labelDark())
                v.bind(icons.get(app, px), app.label)
                v.setOnClickListener { launchApp(app) }
                v.setOnLongClickListener {
                    showAppMenu(v, app, 2, null, i)
                    startDrag(v, DragItem(app.comp, v, i, null, false, null))
                    true
                }
                slot.addView(v, FrameLayout.LayoutParams(-1, -2))
            }
            dockLayout.addView(slot, LinearLayout.LayoutParams(0, -1, 1f))
        }
    }

    private fun launchApp(app: AppInfo) {
        try {
            prefs.pushRecent(app.comp)
            val i = Intent(Intent.ACTION_MAIN)
            i.addCategory(Intent.CATEGORY_LAUNCHER)
            i.component = app.component
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
            startActivity(i)
        } catch (e: Exception) {
        }
    }

    private fun suggestedApps(): List<AppInfo> {
        val out = ArrayList<AppInfo>()
        for (c in prefs.recentList()) byComp[c]?.let { out.add(it) }
        for (a in apps) {
            if (out.size >= 5) break
            if (!out.contains(a)) out.add(a)
        }
        return out.take(5)
    }

    // ---- مدل صفحه‌ها: اشغال سلول‌ها

    private fun modelOccupied(page: Int, x: Int, y: Int, sx: Int, sy: Int, ignore: HomeItem?): Boolean {
        if (x < 0 || y < 0 || x + sx > 4 || y + sy > ROWS) return true
        for (o in homeItems) {
            if (o === ignore || o.page != page) continue
            if (x < o.x + o.sx && x + sx > o.x && y < o.y + o.sy && y + sy > o.y) return true
        }
        return false
    }

    private fun modelFree(page: Int, sx: Int, sy: Int, nx: Int, ny: Int, ignore: HomeItem?): Pair<Int, Int>? {
        var best: Pair<Int, Int>? = null
        var bd = Int.MAX_VALUE
        for (y in 0..(ROWS - sy)) {
            for (x in 0..(4 - sx)) {
                if (!modelOccupied(page, x, y, sx, sy, ignore)) {
                    val d = abs(x - nx) + abs(y - ny)
                    if (d < bd) { bd = d; best = Pair(x, y) }
                }
            }
        }
        return best
    }

    private fun itemAt(page: Int, cx: Int, cy: Int, ignore: HomeItem?): HomeItem? {
        for (o in homeItems) {
            if (o === ignore || o.page != page) continue
            if (cx >= o.x && cx < o.x + o.sx && cy >= o.y && cy < o.y + o.sy) return o
        }
        return null
    }

    /** اپ را در اولین جای خالی (صفحهٔ فعلی، بعد بقیه، بعد صفحهٔ جدید) می‌گذارد */
    private fun addAppToHome(app: AppInfo, toast: Boolean) {
        val count = max(1, prefs.pages)
        val start = curHomePage()
        val order = (0 until count).sortedBy { abs(it - start) }
        for (p in order) {
            val spot = modelFree(p, 1, 1, 0, 0, null)
            if (spot != null) {
                homeItems.add(HomeItem("app", p, spot.first, spot.second, 1, 1, app.comp, -1))
                saveHome()
                rebuildHome()
                if (toast) Toast.makeText(this, "Added to Home screen", Toast.LENGTH_SHORT).show()
                return
            }
        }
        if (count < 7) {
            prefs.pages = count + 1
            homeItems.add(HomeItem("app", count, 0, 0, 1, 1, app.comp, -1))
            saveHome()
            rebuildHome()
            if (toast) Toast.makeText(this, "Added to Home screen", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "Home screen is full", Toast.LENGTH_SHORT).show()
        }
    }

    // ================================================================ edit mode (pages overview)

    private fun enterEditMode() {
        if (editMode) return
        widgetEditor.hide()
        editMode = true
        homePager.offscreenPageLimit = 2
        rebuildHome()
        dockLayout.visibility = View.INVISIBLE
        editHome.visibility = View.VISIBLE
        editBar.textColor = if (labelDark()) 0xFF2A2A2A.toInt() else Color.WHITE
        editHome.color = editBar.textColor
        editBar.visibility = View.VISIBLE
        homePager.animate().scaleX(0.633f).scaleY(0.633f).translationY(24f * f)
            .setDuration(220).setInterpolator(DecelerateInterpolator()).start()
    }

    private fun exitEditMode(page: Int) {
        if (!editMode) return
        editMode = false
        homePager.offscreenPageLimit = 1
        dockLayout.visibility = View.VISIBLE
        editHome.visibility = View.GONE
        editBar.visibility = View.GONE
        homePager.animate().scaleX(1f).scaleY(1f).translationY(0f)
            .setDuration(200).setInterpolator(DecelerateInterpolator()).start()
        rebuildHome()
        homePager.setCurrentItem(page.coerceIn(0, max(1, prefs.pages) - 1), false)
    }

    private fun onEditBar(i: Int) {
        when (i) {
            0 -> {
                exitEditMode(homePager.currentItem)
                try {
                    startActivity(Intent.createChooser(Intent(Intent.ACTION_SET_WALLPAPER), "Wallpapers"))
                } catch (e: Exception) {
                }
            }
            1 -> {
                val li = packageManager.getLaunchIntentForPackage("com.samsung.android.themestore")
                if (li != null) startActivity(li)
                else Toast.makeText(this, "Galaxy Themes is not installed", Toast.LENGTH_SHORT).show()
            }
            2 -> {
                exitEditMode(homePager.currentItem)
                showWidgetPicker()
            }
            3 -> showSettings()
        }
    }

    private fun addPage() {
        if (prefs.pages >= 7) return
        prefs.pages = prefs.pages + 1
        rebuildHome()
        homePager.setCurrentItem(prefs.pages - 1, true)
    }

    private fun deletePage(p: Int) {
        if (prefs.pages <= 1) return
        homeItems.removeAll { it.page == p }
        for (h in homeItems) if (h.page > p) h.page = h.page - 1
        prefs.pages = prefs.pages - 1
        if (prefs.homePage >= prefs.pages) prefs.homePage = prefs.pages - 1
        saveHome()
        rebuildHome()
    }

    // ================================================================ drag & drop

    private fun startDrag(view: View, item: DragItem) {
        drag = item
        val clip = ClipData.newPlainText("launcher", item.comp)
        val ok = view.startDragAndDrop(clip, View.DragShadowBuilder(view), item, 0)
        if (ok) {
            if (!item.fromDrawer && item.fromFolder == null) item.srcView?.alpha = 0.25f
        } else {
            drag = null
        }
    }

    private val dragListener = View.OnDragListener { _, ev ->
        when (ev.action) {
            DragEvent.ACTION_DRAG_STARTED -> {
                dragStartX = ev.x
                dragStartY = ev.y
                val d = drag
                if (d != null) {
                    dragActive = true
                    beginDragCtx(d)
                }
                true
            }
            DragEvent.ACTION_DRAG_LOCATION -> {
                onDragMove(ev.x, ev.y)
                true
            }
            DragEvent.ACTION_DROP -> {
                onDropEvent(ev.x, ev.y)
                true
            }
            DragEvent.ACTION_DRAG_ENDED -> {
                if (dragActive) cancelDrag()
                removeBar.visibility = View.GONE
                addBar.visibility = View.GONE
                main.removeCallbacks(edgeRunnable)
                edgeDir = 0
                drag?.srcView?.alpha = 1f
                drag = null
                dragActive = false
                true
            }
            else -> true
        }
    }

    private fun beginDragCtx(d: DragItem) {
        if (d.fromFolder != null) {
            dragCtx = 2
            folderDragStart(d.comp)
            addBar.text = "Drag here to move from folder."
            addBar.visibility = View.VISIBLE
        } else if (d.fromDrawer) {
            dragCtx = 1
            drawer.startDrag(d.comp, false)
            if (!d.comp.startsWith("folder:")) {
                addBar.text = "Drag here to add to Home screen."
                addBar.visibility = View.VISIBLE
            }
        } else {
            dragCtx = 0
            homeDragStart()
            removeBar.visibility = View.VISIBLE
        }
    }

    private fun cancelDrag() {
        when (dragCtx) {
            0 -> homeDragEnd()
            1 -> drawer.endDrag(false)
            2 -> folderDragEnd(false)
        }
    }

    private fun onDragMove(x: Float, y: Float) {
        val d = drag ?: return
        // با شروع واقعی کشیدن، منوی نگه‌داشتن بسته می‌شود
        if (menuLayer != null && (abs(x - dragStartX) > 24f * f || abs(y - dragStartY) > 24f * f)) dismissMenu()
        when (dragCtx) {
            0 -> homeDragMove(x, y)
            1 -> {
                if (y < 150f * f && !d.comp.startsWith("folder:")) {
                    // کشیدن از دراور به نوار بالا = رفتن به هوم
                    drawer.endDrag(false)
                    addBar.visibility = View.GONE
                    dragCtx = 0
                    animateTo(0f)
                    homeDragStart()
                } else {
                    drawer.dragMove(x, y)
                }
            }
            2 -> {
                if (y < 150f * f) {
                    // کشیدن از پوشه به نوار بالا = بیرون آوردن از پوشه
                    val ref = d.fromFolder
                    folderDragEnd(false)
                    closeFolderNow()
                    if (ref != null && !ref.home) {
                        dragCtx = 1
                        drawer.startDrag(d.comp, true)
                        addBar.text = "Drag here to add to Home screen."
                        addBar.visibility = View.VISIBLE
                    } else {
                        dragCtx = 0
                        addBar.visibility = View.GONE
                        homeDragStart()
                    }
                } else {
                    folderDragMove(x, y)
                }
            }
        }
    }

    private fun onDropEvent(x: Float, y: Float) {
        val d = drag ?: return
        when (dragCtx) {
            0 -> {
                homeDragEnd()
                onHomeDrop(d, x, y)
            }
            1 -> drawer.endDrag(true)
            2 -> folderDragEnd(true)
        }
        dragActive = false
    }

    private fun flipPage() {
        if (drag == null || edgeDir == 0 || dragCtx != 0) return
        val count = max(1, prefs.pages)
        val target = homePager.currentItem + edgeDir
        if (target in 0 until count) {
            homePager.setCurrentItem(target, true)
        } else if (target == count && count < 7) {
            prefs.pages = count + 1
            rebuildHome()
            homePager.setCurrentItem(target, true)
        }
        hCurCell = -1
        main.postDelayed(edgeRunnable, 900)
    }

    // ---------------------------------------------------------- هدف کشیدن: صفحهٔ هوم

    private fun homeDragStart() {
        hCurCell = -1
        hMode = 0
        hSpot = null
        hOcc = null
        hInner = false
        homeOverlay.cols = 4
        homeOverlay.rows = ROWS
        homeOverlay.padLeft = 0f
        homeOverlay.cellW = homePager.width / 4f
        homeOverlay.cellH = homePager.height / ROWS.toFloat()
        homeOverlay.iconSize = 102f * f
        homeOverlay.iconTop = 0f
        homeOverlay.holeIndex = -1
        homeOverlay.targetIndex = -1
        homeOverlay.showDots = true
        homeOverlay.visibility = View.VISIBLE
        homeOverlay.invalidate()
    }

    private fun homeDragEnd() {
        main.removeCallbacks(hEval)
        main.removeCallbacks(hMerge)
        main.removeCallbacks(edgeRunnable)
        edgeDir = 0
        homeOverlay.visibility = View.GONE
    }

    private fun homeDragMove(x: Float, y: Float) {
        val cw = homePager.width / 4f
        val ch = homePager.height / ROWS.toFloat()
        if (cw <= 0f || ch <= 0f) return
        val ly = y - homePager.top
        val cx = (x / cw).toInt().coerceIn(0, 3)
        val cy = (ly / ch).toInt().coerceIn(0, ROWS - 1)
        val cell = cy * 4 + cx
        val icx = cx * cw + cw / 2f
        val icy = homePager.top + cy * ch + 51f * f
        val inner = hypot(x - icx, y - icy) < 102f * f * 0.42f
        hInner = inner
        if (cell != hCurCell) {
            hCurCell = cell
            main.removeCallbacks(hEval)
            main.removeCallbacks(hMerge)
            hMode = 0
            homeOverlay.targetIndex = -1
            main.postDelayed(hEval, 150)
        } else if (hMode != 0 && !inner) {
            hMode = 0
            homeOverlay.targetIndex = -1
            main.removeCallbacks(hMerge)
            main.postDelayed(hEval, 50)
        }
        // لبه‌ها: عوض کردن صفحه
        val w = root.width
        val dir = if (x < 36f * f) -1 else if (x > w - 36f * f) 1 else 0
        if (dir != edgeDir) {
            edgeDir = dir
            main.removeCallbacks(edgeRunnable)
            if (dir != 0) main.postDelayed(edgeRunnable, 700)
        }
    }

    private fun homeEvaluate() {
        val d = drag ?: return
        if (hCurCell < 0 || dragCtx != 0) return
        val page = curHomePage()
        val cx = hCurCell % 4
        val cy = hCurCell / 4
        val folderDrag = d.comp.startsWith("hf:")
        val occ = itemAt(page, cx, cy, d.homeItem)
        hOcc = occ
        if (occ != null && hInner && !folderDrag) {
            if (occ.type == "folder") {
                hMode = 1
                homeOverlay.targetIndex = hCurCell
                homeOverlay.holeIndex = -1
                homeOverlay.invalidate()
                return
            }
            if (occ.type == "app") {
                main.removeCallbacks(hMerge)
                main.postDelayed(hMerge, 320)
                return
            }
        }
        hMode = 0
        val spot: Pair<Int, Int>? =
            if (!modelOccupied(page, cx, cy, 1, 1, d.homeItem)) Pair(cx, cy)
            else modelFree(page, 1, 1, cx, cy, d.homeItem)
        hSpot = spot
        homeOverlay.targetIndex = -1
        homeOverlay.holeIndex = if (spot != null) spot.second * 4 + spot.first else -1
        homeOverlay.invalidate()
    }

    private fun homeMergeDwell() {
        if (drag == null || !hInner || hCurCell < 0 || dragCtx != 0) return
        val occ = hOcc
        if (occ != null && occ.type == "app") {
            hMode = 2
            homeOverlay.targetIndex = hCurCell
            homeOverlay.holeIndex = -1
            homeOverlay.invalidate()
        }
    }

    // ---------------------------------------------------------- رها کردن روی هوم

    private fun detachDragged(d: DragItem) {
        d.homeItem?.let { homeItems.remove(it) }
        if (d.srcDock >= 0) dock[d.srcDock] = ""
        d.fromFolder?.let { removeFromFolder(it, d.comp) }
    }

    private fun removeFromFolder(ref: FolderRef, comp: String) {
        if (ref.home) removeFromHomeFolder(ref.id, comp) else removeFromDrawerFolder(ref.id, comp)
    }

    private fun removeFromHomeFolder(id: String, comp: String) {
        val hf = homeFolders[id] ?: return
        hf.members.remove(comp)
        if (hf.members.size <= 1) dissolveHomeFolder(id)
        saveHomeFolders()
    }

    private fun dissolveHomeFolder(id: String) {
        val hf = homeFolders.remove(id)
        val item = homeItems.firstOrNull { it.type == "folder" && it.comp == id } ?: return
        homeItems.remove(item)
        val lone = hf?.members?.firstOrNull()
        if (lone != null && byComp.containsKey(lone)) {
            homeItems.add(HomeItem("app", item.page, item.x, item.y, 1, 1, lone, -1))
        }
    }

    private fun onHomeDrop(d: DragItem, x: Float, y: Float) {
        val w = root.width

        // نوار بالا: Remove / Uninstall (فقط برای کشیدن از هوم/داک)
        if (removeBar.visibility == View.VISIBLE && y < removeBar.bottom) {
            if (x < w / 2f || d.comp.startsWith("hf:")) removeDragged(d) else uninstall(d.comp)
            main.post { refresh() }
            return
        }

        val isFolderKey = d.comp.startsWith("hf:") || d.comp.startsWith("folder:")

        // داک
        if (!isFolderKey && y >= dockLayout.top - 24f * f) {
            val slot = (x / (w / 5f)).toInt().coerceIn(0, 4)
            if (d.srcDock >= 0) {
                val old = dock[slot]
                dock[slot] = d.comp
                dock[d.srcDock] = old
            } else {
                dock[slot] = d.comp
                d.homeItem?.let { homeItems.remove(it) }
                d.fromFolder?.let { removeFromFolder(it, d.comp) }
            }
            saveDock()
            saveHome()
            main.post { refresh() }
            return
        }
        if (d.comp.startsWith("folder:")) return   // پوشهٔ دراور روی هوم نمی‌آید

        val page = curHomePage()

        // افزودن به پوشه / ساخت پوشهٔ جدید
        val occ = hOcc
        if (hMode == 1 && occ != null && occ.type == "folder" && !d.comp.startsWith("hf:")) {
            val hf = homeFolders[occ.comp]
            if (hf != null) {
                detachDragged(d)
                if (!hf.members.contains(d.comp)) hf.members.add(d.comp)
                saveHome(); saveHomeFolders(); saveDock()
                main.post { refresh() }
                return
            }
        }
        if (hMode == 2 && occ != null && occ.type == "app" && !d.comp.startsWith("hf:")) {
            val id = "hf" + System.currentTimeMillis()
            detachDragged(d)
            homeItems.remove(occ)
            homeFolders[id] = HomeFolder("", arrayListOf(occ.comp, d.comp))
            homeItems.add(HomeItem("folder", occ.page, occ.x, occ.y, 1, 1, id, -1))
            saveHome(); saveHomeFolders(); saveDock()
            main.post {
                refresh()
                main.post { showFolder(FolderRef(true, id), null, true) }
            }
            return
        }

        // جابه‌جایی عادی
        val cw = homePager.width / 4f
        val ch = homePager.height / ROWS.toFloat()
        if (cw <= 0f || ch <= 0f) return
        val hi = d.homeItem
        var spot = hSpot
        if (spot == null) {
            val ly = (y - homePager.top).coerceAtLeast(0f)
            val cx = (x / cw).toInt().coerceIn(0, 3)
            val cy = (ly / ch).toInt().coerceIn(0, ROWS - 1)
            spot = if (!modelOccupied(page, cx, cy, 1, 1, hi)) Pair(cx, cy) else modelFree(page, 1, 1, cx, cy, hi)
        }
        if (spot == null) {
            Toast.makeText(this, "No room on this page", Toast.LENGTH_SHORT).show()
            return
        }
        if (hi != null) {
            hi.page = page
            hi.x = spot.first
            hi.y = spot.second
        } else {
            homeItems.add(HomeItem("app", page, spot.first, spot.second, 1, 1, d.comp, -1))
            if (d.srcDock >= 0) dock[d.srcDock] = ""
            d.fromFolder?.let { removeFromFolder(it, d.comp) }
        }
        saveHome()
        saveDock()
        saveHomeFolders()
        main.post { refresh() }
    }

    private fun removeDragged(d: DragItem) {
        val hi = d.homeItem
        if (hi != null) {
            if (hi.type == "folder") homeFolders.remove(hi.comp)
            homeItems.remove(hi)
        }
        if (d.srcDock >= 0) dock[d.srcDock] = ""
        saveHome()
        saveHomeFolders()
        saveDock()
    }

    private fun uninstall(comp: String) {
        val pkg = comp.substringBefore("/")
        try {
            startActivity(Intent(Intent.ACTION_DELETE, Uri.parse("package:$pkg")))
        } catch (e: Exception) {
        }
    }

    private fun isSystemApp(pkg: String): Boolean {
        return try {
            (packageManager.getApplicationInfo(pkg, 0).flags and ApplicationInfo.FLAG_SYSTEM) != 0
        } catch (e: Exception) {
            false
        }
    }

    // ---------------------------------------------------------- دراور: نتیجهٔ جابه‌جایی‌ها

    private fun uniqueFolderName(): String {
        val used = HashSet<String>()
        drawerLayout?.folders?.keys?.let { used.addAll(it) }
        used.addAll(prefs.assignMap().values)
        if (!used.contains("Folder")) return "Folder"
        var i = 2
        while (used.contains("Folder $i")) i++
        return "Folder $i"
    }

    private fun checkDissolveDrawer(name: String) {
        if (name == "Samsung" || name == "Google") return
        val members = prefs.assignMap().filter { it.value == name }.keys.toList()
        if (members.size > 1) return
        for (m in members) prefs.setAssign(m, "")
        val order = ArrayList(loadOrder())
        val i = order.indexOf("folder:$name")
        if (i >= 0) order[i] = members.firstOrNull() ?: ""
        saveOrder(order)
    }

    private fun removeFromDrawerFolder(name: String, comp: String) {
        prefs.setAssign(comp, "")
        checkDissolveDrawer(name)
    }

    private fun onDrawerSlots(slots: List<String>) {
        val d = drag
        val ff = d?.fromFolder
        val key = d?.comp ?: ""
        saveOrder(slots)
        if (ff != null) {
            if (ff.home) removeFromHomeFolder(ff.id, key) else removeFromDrawerFolder(ff.id, key)
            main.post { rebuildHome() }
        }
        refreshDrawer()
    }

    private fun onDrawerDropIntoFolder(key: String, folder: String) {
        if (key.startsWith("folder:")) {
            refreshDrawer()
            return
        }
        val ff = drag?.fromFolder
        val cur = ArrayList(drawer.currentSlots())
        val i = cur.indexOf(key)
        if (i >= 0) cur[i] = ""
        saveOrder(cur)
        prefs.setAssign(key, folder)
        if (ff != null) {
            if (ff.home) {
                removeFromHomeFolder(ff.id, key)
                main.post { rebuildHome() }
            } else if (ff.id != folder) {
                checkDissolveDrawer(ff.id)
            }
        }
        refreshDrawer()
    }

    private fun onDrawerMerge(key: String, target: String) {
        if (key.startsWith("folder:") || target.startsWith("folder:")) {
            refreshDrawer()
            return
        }
        val ff = drag?.fromFolder
        val name = uniqueFolderName()
        val cur = ArrayList(drawer.currentSlots())
        val ti = cur.indexOf(target)
        if (ti >= 0) cur[ti] = "folder:$name"
        val ki = cur.indexOf(key)
        if (ki >= 0) cur[ki] = ""
        saveOrder(cur)
        prefs.setAssign(target, name)
        prefs.setAssign(key, name)
        if (ff != null) {
            if (ff.home) {
                removeFromHomeFolder(ff.id, key)
                main.post { rebuildHome() }
            } else {
                checkDissolveDrawer(ff.id)
            }
        }
        refreshDrawer()
        main.post { showFolder(FolderRef(false, name), null, true) }
    }

    private fun sortDrawer() {
        val lay = drawerLayout ?: return
        val folders = lay.slots.filter { it.startsWith("folder:") }.sortedBy { it.lowercase() }
        val appsKeys = lay.slots.filter { it.isNotEmpty() && !it.startsWith("folder:") }
            .sortedBy { (byComp[it]?.label ?: it).lowercase() }
        saveOrder(folders + appsKeys)
        refreshDrawer()
    }

    // ================================================================ long-press app menu

    private fun dismissMenu() {
        menuLayer?.let { root.removeView(it) }
        menuLayer = null
    }

    private fun menuItem(kind: String, label: String, enabled: Boolean, action: () -> Unit): View {
        val col = LinearLayout(this)
        col.orientation = LinearLayout.VERTICAL
        col.gravity = Gravity.CENTER_HORIZONTAL
        col.setPadding((14 * f).toInt(), (16 * f).toInt(), (14 * f).toInt(), (14 * f).toInt())
        col.addView(IcoView(this, f, kind), LinearLayout.LayoutParams((50 * f).toInt(), (50 * f).toInt()))
        val t = TextView(this)
        t.text = label
        t.setTextColor(0xFF333333.toInt())
        t.setTextSize(TypedValue.COMPLEX_UNIT_PX, 21f * f)
        t.gravity = Gravity.CENTER
        val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        lp.topMargin = (6 * f).toInt()
        col.addView(t, lp)
        if (enabled) {
            col.setOnClickListener { action() }
        } else {
            col.alpha = 0.38f
        }
        return col
    }

    private fun cardBg(): GradientDrawable {
        val g = GradientDrawable()
        g.setColor(0xFAFFFFFF.toInt())
        g.cornerRadius = 22f * f
        return g
    }

    private fun shortcutsFor(app: AppInfo): List<ShortcutInfo> {
        return try {
            val la = getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps
            if (!la.hasShortcutHostPermission()) return emptyList()
            val q = LauncherApps.ShortcutQuery()
            q.setPackage(app.pkg)
            q.setQueryFlags(
                LauncherApps.ShortcutQuery.FLAG_MATCH_DYNAMIC or
                    LauncherApps.ShortcutQuery.FLAG_MATCH_MANIFEST or
                    LauncherApps.ShortcutQuery.FLAG_MATCH_PINNED
            )
            (la.getShortcuts(q, Process.myUserHandle()) ?: emptyList()).filter { it.isEnabled }.take(4)
        } catch (e: Exception) {
            emptyList()
        }
    }

    /** origin: 0 دراور، 1 هوم، 2 داک */
    private fun showAppMenu(anchor: View, app: AppInfo, origin: Int, hi: HomeItem?, dockSlot: Int) {
        dismissMenu()
        val layer = FrameLayout(this)
        layer.setOnClickListener { dismissMenu() }

        val sys = isSystemApp(app.pkg)
        val actions = LinearLayout(this)
        actions.orientation = LinearLayout.HORIZONTAL
        actions.background = cardBg()
        if (origin == 0) {
            actions.addView(menuItem("select", "Select items", true) {
                dismissMenu()
                drawer.startSelect(app)
            })
            actions.addView(menuItem("home", "Add to Home", true) {
                dismissMenu()
                addAppToHome(app, true)
            })
        } else {
            actions.addView(menuItem("remove", "Remove", true) {
                dismissMenu()
                if (hi != null) homeItems.remove(hi)
                if (dockSlot >= 0) dock[dockSlot] = ""
                saveHome()
                saveDock()
                refresh()
            })
        }
        if (sys) {
            actions.addView(menuItem("disable", "Disable", false) {})
        } else {
            actions.addView(menuItem("trash", "Uninstall", true) {
                dismissMenu()
                uninstall(app.comp)
            })
        }

        val col = LinearLayout(this)
        col.orientation = LinearLayout.VERTICAL
        col.addView(actions, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        val shortcuts = shortcutsFor(app)
        if (shortcuts.isNotEmpty()) {
            val la = getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps
            val card = LinearLayout(this)
            card.orientation = LinearLayout.VERTICAL
            card.background = cardBg()
            for (si in shortcuts) {
                val row = LinearLayout(this)
                row.orientation = LinearLayout.HORIZONTAL
                row.gravity = Gravity.CENTER_VERTICAL
                row.setPadding((18 * f).toInt(), (12 * f).toInt(), (18 * f).toInt(), (12 * f).toInt())
                val iv = ImageView(this)
                try { iv.setImageDrawable(la.getShortcutIconDrawable(si, 0)) } catch (e: Exception) {}
                row.addView(iv, LinearLayout.LayoutParams((44 * f).toInt(), (44 * f).toInt()))
                val t = TextView(this)
                t.text = si.shortLabel ?: ""
                t.setTextColor(0xFF222222.toInt())
                t.setTextSize(TypedValue.COMPLEX_UNIT_PX, 24f * f)
                t.maxLines = 1
                t.ellipsize = android.text.TextUtils.TruncateAt.END
                val tlp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                tlp.leftMargin = (14 * f).toInt()
                row.addView(t, tlp)
                row.setOnClickListener {
                    dismissMenu()
                    try { la.startShortcut(si, null, null) } catch (e: Exception) {}
                }
                card.addView(row)
            }
            val clp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            clp.topMargin = (8 * f).toInt()
            col.addView(card, clp)
        }

        actions.measure(
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        )
        val cw = max(actions.measuredWidth, (330 * f).toInt())
        col.measure(
            View.MeasureSpec.makeMeasureSpec(cw, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        )
        val ch = col.measuredHeight
        val loc = IntArray(2)
        anchor.getLocationOnScreen(loc)
        val rootLoc = IntArray(2)
        root.getLocationOnScreen(rootLoc)
        var x = loc[0] - rootLoc[0] + anchor.width / 2 - cw / 2
        x = x.coerceIn((16 * f).toInt(), max((16 * f).toInt(), root.width - cw - (16 * f).toInt()))
        var y = loc[1] - rootLoc[1] - ch - (10 * f).toInt()
        if (y < (80 * f).toInt()) y = loc[1] - rootLoc[1] + anchor.height + (10 * f).toInt()
        val lp = FrameLayout.LayoutParams(cw, ViewGroup.LayoutParams.WRAP_CONTENT)
        lp.leftMargin = x
        lp.topMargin = y
        layer.addView(col, lp)

        root.addView(layer, FrameLayout.LayoutParams(-1, -1))
        menuLayer = layer
    }

    // ================================================================ select mode

    private fun endSelect() {
        drawer.endSelect()
        selBar.visibility = View.GONE
    }

    // ================================================================ folders

    private fun folderName(ref: FolderRef): String =
        if (ref.home) (homeFolders[ref.id]?.name ?: "") else ref.id

    private fun folderColorKey(ref: FolderRef): String = if (ref.home) "hf:" + ref.id else ref.id

    private fun folderEditable(ref: FolderRef): Boolean = ref.home || (ref.id != "Samsung" && ref.id != "Google")

    private fun folderMembers(ref: FolderRef): ArrayList<String> {
        val raw: List<String> = if (ref.home) (homeFolders[ref.id]?.members ?: emptyList())
        else (drawerLayout?.folders?.get(ref.id) ?: emptyList()).map { it.comp }
        val out = ArrayList<String>()
        for (c in raw) if (byComp.containsKey(c)) out.add(c)
        return out
    }

    private fun hideKeyboard(v: View) {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(v.windowToken, 0)
    }

    private fun fillFolderGrid() {
        val ref = folderRef ?: return
        val grid = folderGrid ?: return
        grid.removeAllViews()
        folderCells.clear()
        val px = (80 * f).toInt()
        folderList.forEachIndexed { i, comp ->
            val a = byComp[comp] ?: return@forEachIndexed
            val v = AppCellView(this, px, 20f * f, Color.WHITE, true)
            v.bind(icons.get(a, px), a.label)
            v.setPadding(0, (16 * f).toInt(), 0, 0)
            v.setOnClickListener { closeFolder(); launchApp(a) }
            v.setOnLongClickListener {
                startDrag(v, DragItem(comp, v, -1, null, false, ref))
                true
            }
            folderCells[comp] = v
            grid.addView(v, CellLayout.LP(i % 4, i / 4))
        }
    }

    private fun showFolder(ref: FolderRef, anchor: View?, edit: Boolean) {
        closeFolderNow()
        folderRef = ref
        folderList = folderMembers(ref)
        val layer = FrameLayout(this)
        layer.isClickable = true
        layer.setOnClickListener { closeFolder() }

        // پشت پوشه: والپیپرِ بلور و تیره؛ محتوای دراور/هوم دیده نمی‌شود
        val wb = wallBlur
        if (wb != null) {
            val iv = ImageView(this)
            iv.scaleType = ImageView.ScaleType.CENTER_CROP
            iv.setImageBitmap(wb)
            layer.addView(iv, FrameLayout.LayoutParams(-1, -1))
            val tint = View(this)
            tint.setBackgroundColor(0x73000000)
            layer.addView(tint, FrameLayout.LayoutParams(-1, -1))
        } else {
            layer.setBackgroundColor(0xF2101010.toInt())
        }

        val colW = (608 * f).toInt()
        val col = LinearLayout(this)
        col.orientation = LinearLayout.VERTICAL
        col.gravity = Gravity.CENTER_HORIZONTAL

        val panel = LinearLayout(this)
        panel.orientation = LinearLayout.VERTICAL
        panel.isClickable = true
        val bg = GradientDrawable()
        bg.cornerRadius = 30f * f
        bg.setStroke((2 * f).toInt(), 0x99FFFFFF.toInt())
        fun fillOf(c: Int): Int = (c and 0x00FFFFFF) or 0x40000000
        val ckey = folderColorKey(ref)
        bg.setColor(fillOf(prefs.folderColor(ckey)))
        panel.background = bg

        // ردیف عنوان / پالت رنگ
        val header = FrameLayout(this)
        val titleView: View
        folderTitleEdit = null
        if (folderEditable(ref)) {
            val et = EditText(this)
            et.setText(folderName(ref))
            et.hint = "Enter folder name"
            et.background = null
            et.setTextColor(Color.WHITE)
            et.setHintTextColor(0xB3FFFFFF.toInt())
            et.setTextSize(TypedValue.COMPLEX_UNIT_PX, 31f * f)
            et.setSingleLine(true)
            et.imeOptions = EditorInfo.IME_ACTION_DONE
            et.gravity = Gravity.CENTER_VERTICAL
            et.setPadding((29 * f).toInt(), 0, (90 * f).toInt(), 0)
            et.setOnEditorActionListener { v, actionId, _ ->
                if (actionId == EditorInfo.IME_ACTION_DONE) {
                    hideKeyboard(v)
                    v.clearFocus()
                    true
                } else false
            }
            folderTitleEdit = et
            titleView = et
        } else {
            val tv = TextView(this)
            tv.text = folderName(ref)
            tv.setTextColor(Color.WHITE)
            tv.setTextSize(TypedValue.COMPLEX_UNIT_PX, 31f * f)
            tv.gravity = Gravity.CENTER_VERTICAL
            tv.setPadding((29 * f).toInt(), 0, 0, 0)
            titleView = tv
        }
        header.addView(titleView, FrameLayout.LayoutParams(-1, -1))

        val dot = View(this)
        val dotBg = GradientDrawable()
        dotBg.shape = GradientDrawable.OVAL
        dotBg.setColor(prefs.folderColor(ckey))
        dotBg.setStroke((2 * f).toInt(), 0xFFFFFFFF.toInt())
        dot.background = dotBg
        val dlp = FrameLayout.LayoutParams((34 * f).toInt(), (34 * f).toInt())
        dlp.gravity = Gravity.END or Gravity.CENTER_VERTICAL
        dlp.rightMargin = (28 * f).toInt()
        header.addView(dot, dlp)

        val palette = LinearLayout(this)
        palette.orientation = LinearLayout.HORIZONTAL
        palette.gravity = Gravity.CENTER_VERTICAL
        palette.setPadding((20 * f).toInt(), 0, (20 * f).toInt(), 0)
        palette.visibility = View.GONE
        header.addView(palette, FrameLayout.LayoutParams(-1, -1))
        panel.addView(header, LinearLayout.LayoutParams(-1, (96 * f).toInt()))

        fun applyColor(c: Int) {
            prefs.setFolderColor(ckey, c)
            dotBg.setColor(c)
            bg.setColor(fillOf(c))
            icons.clear()
            refreshDrawer()
            main.post { rebuildHome() }
            palette.visibility = View.GONE
            titleView.visibility = View.VISIBLE
            dot.visibility = View.VISIBLE
        }

        fun swatch(c: Int, checked: Boolean, onClick: () -> Unit): View {
            val v = FrameLayout(this)
            val d = View(this)
            val g = GradientDrawable()
            g.shape = GradientDrawable.OVAL
            g.setColor(c)
            d.background = g
            v.addView(d, FrameLayout.LayoutParams((40 * f).toInt(), (40 * f).toInt(), Gravity.CENTER))
            if (checked) {
                val tick = TextView(this)
                tick.text = "✓"
                tick.setTextColor(0xFF555555.toInt())
                tick.setTextSize(TypedValue.COMPLEX_UNIT_PX, 26f * f)
                tick.gravity = Gravity.CENTER
                v.addView(tick, FrameLayout.LayoutParams(-1, -1))
            }
            v.setOnClickListener { onClick() }
            return v
        }

        val cur = prefs.folderColor(ckey)
        val swatches = intArrayOf(
            0xFFB4D0F2.toInt(), 0xFF9BDCCB.toInt(), 0xFFF4B994.toInt(), 0xFFF6DB9B.toInt()
        )
        palette.addView(swatch(prefs.defaultFolderColor(), cur == prefs.defaultFolderColor()) {
            applyColor(prefs.defaultFolderColor())
        }, LinearLayout.LayoutParams(0, -1, 1f))
        for (c in swatches) {
            palette.addView(swatch(c, cur == c) { applyColor(c) }, LinearLayout.LayoutParams(0, -1, 1f))
        }
        palette.addView(swatch(0xFFE9507A.toInt(), false) { showColorPicker(cur) { c -> applyColor(c) } },
            LinearLayout.LayoutParams(0, -1, 1f))
        val close = TextView(this)
        close.text = "✕"
        close.setTextColor(Color.WHITE)
        close.setTextSize(TypedValue.COMPLEX_UNIT_PX, 28f * f)
        close.gravity = Gravity.CENTER
        close.setOnClickListener {
            palette.visibility = View.GONE
            titleView.visibility = View.VISIBLE
            dot.visibility = View.VISIBLE
        }
        palette.addView(close, LinearLayout.LayoutParams(0, -1, 1f))

        dot.setOnClickListener {
            folderTitleEdit?.let { hideKeyboard(it) }
            titleView.visibility = View.INVISIBLE
            dot.visibility = View.INVISIBLE
            palette.visibility = View.VISIBLE
        }

        val line = View(this)
        line.setBackgroundColor(0x66FFFFFF)
        panel.addView(line, LinearLayout.LayoutParams(-1, max(1, (2 * f).toInt())))

        // آیکون‌ها (با کادر جای فرود هنگام جابه‌جایی)
        val rows = max(1, (folderList.size + 3) / 4)
        val cellH = (163 * f).toInt()
        val grid = CellLayout(this, 4, rows)
        grid.padH = (4 * f).toInt()
        val ov = ReorderOverlay(this, f)
        ov.cols = 4
        ov.rows = rows
        ov.padLeft = grid.padH.toFloat()
        ov.cellW = (colW - 2f * grid.padH) / 4f
        ov.cellH = cellH.toFloat()
        ov.iconSize = 80f * f
        ov.iconTop = 16f * f
        ov.visibility = View.GONE
        folderGrid = grid
        folderOverlay = ov
        fillFolderGrid()
        val wrap = FrameLayout(this)
        wrap.addView(grid, FrameLayout.LayoutParams(-1, -1))
        wrap.addView(ov, FrameLayout.LayoutParams(-1, -1))
        val sv = ScrollView(this)
        sv.isVerticalScrollBarEnabled = false
        sv.addView(wrap, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, rows * cellH))
        panel.addView(sv, LinearLayout.LayoutParams(-1, min(rows * cellH, (820 * f).toInt())))

        // ADD APPS
        val line2 = View(this)
        line2.setBackgroundColor(0x66FFFFFF)
        panel.addView(line2, LinearLayout.LayoutParams(-1, max(1, (2 * f).toInt())))
        val add = TextView(this)
        add.text = "⊕  ADD APPS"
        add.setTextColor(0xE6FFFFFF.toInt())
        add.setTextSize(TypedValue.COMPLEX_UNIT_PX, 22f * f)
        add.letterSpacing = 0.05f
        add.gravity = Gravity.CENTER
        add.setOnClickListener { showAddApps(ref) }
        panel.addView(add, LinearLayout.LayoutParams(-1, (88 * f).toInt()))

        col.addView(panel, LinearLayout.LayoutParams(colW, ViewGroup.LayoutParams.WRAP_CONTENT))
        val ind = PageIndicator(this, f, true)
        ind.count = 1
        col.addView(ind, LinearLayout.LayoutParams(colW, (44 * f).toInt()))

        val clp = FrameLayout.LayoutParams(colW, ViewGroup.LayoutParams.WRAP_CONTENT)
        clp.gravity = Gravity.CENTER_HORIZONTAL or Gravity.TOP
        clp.topMargin = (150 * f).toInt()
        layer.addView(col, clp)
        root.addView(layer, FrameLayout.LayoutParams(-1, -1))
        folderLayer = layer
        folderCol = col

        // انیمیشن باز شدن از جای آیکون پوشه
        var ax = root.width / 2f
        var ay = root.height / 2f
        if (anchor != null) {
            val loc = IntArray(2)
            anchor.getLocationOnScreen(loc)
            val rootLoc = IntArray(2)
            root.getLocationOnScreen(rootLoc)
            ax = loc[0] - rootLoc[0] + anchor.width / 2f
            ay = loc[1] - rootLoc[1] + (51 * f)
        }
        col.alpha = 0f
        layer.alpha = 0f
        layer.animate().alpha(1f).setDuration(200).start()
        col.post {
            col.pivotX = ax - col.left
            col.pivotY = ay - col.top
            col.scaleX = 0.2f
            col.scaleY = 0.2f
            col.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(230)
                .setInterpolator(DecelerateInterpolator()).start()
        }
        val et = folderTitleEdit
        if (edit && et != null) {
            et.requestFocus()
            et.postDelayed({
                val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                imm.showSoftInput(et, 0)
            }, 260)
        }
    }

    private fun commitFolderName() {
        val ref = folderRef ?: return
        val et = folderTitleEdit ?: return
        val n = et.text.toString().trim()
        hideKeyboard(et)
        if (ref.home) {
            val hf = homeFolders[ref.id]
            if (hf != null && hf.name != n) {
                hf.name = n
                saveHomeFolders()
                main.post { rebuildHome() }
            }
        } else if (n.isNotEmpty() && n != ref.id && drawerLayout?.folders?.containsKey(n) != true) {
            prefs.renameFolder(ref.id, n)
            val order = ArrayList(loadOrder())
            val i = order.indexOf("folder:" + ref.id)
            if (i >= 0) order[i] = "folder:$n"
            saveOrder(order)
            refreshDrawer()
        }
    }

    private fun closeFolder() {
        val layer = folderLayer ?: return
        val col = folderCol
        commitFolderName()
        folderLayer = null
        folderCol = null
        folderRef = null
        folderGrid = null
        folderOverlay = null
        folderTitleEdit = null
        if (col == null) {
            root.removeView(layer)
            return
        }
        col.animate().alpha(0f).scaleX(0.2f).scaleY(0.2f).setDuration(170).start()
        layer.animate().alpha(0f).setDuration(190).withEndAction { root.removeView(layer) }.start()
    }

    private fun closeFolderNow() {
        folderLayer?.let { root.removeView(it) }
        folderLayer = null
        folderCol = null
        folderRef = null
        folderGrid = null
        folderOverlay = null
        folderTitleEdit = null
    }

    // ---- جابه‌جایی داخل پوشه (دومینویی)

    private var fKey = ""

    private fun folderDragStart(key: String) {
        fKey = key
        fOrig = folderList.indexOf(key)
        fCurT = -1
        fPreview = null
        folderCells[key]?.visibility = View.INVISIBLE
        folderTitleEdit?.let { hideKeyboard(it) }
        folderOverlay?.let {
            it.showDots = true
            it.holeIndex = -1
            it.targetIndex = -1
            it.visibility = View.VISIBLE
            it.invalidate()
        }
    }

    private fun folderDragMove(x: Float, y: Float) {
        val grid = folderGrid ?: return
        if (folderList.isEmpty()) return
        val loc = IntArray(2)
        grid.getLocationOnScreen(loc)
        val rootLoc = IntArray(2)
        root.getLocationOnScreen(rootLoc)
        val gx = loc[0] - rootLoc[0]
        val gy = loc[1] - rootLoc[1]
        val cw = grid.cellW.toFloat()
        val ch = grid.cellH.toFloat()
        if (cw <= 0f || ch <= 0f) return
        val col = ((x - gx - grid.padH) / cw).toInt().coerceIn(0, 3)
        val row = ((y - gy) / ch).toInt().coerceIn(0, grid.rows - 1)
        val t = min(row * 4 + col, folderList.size - 1)
        if (t != fCurT) {
            fCurT = t
            main.removeCallbacks(fEval)
            main.postDelayed(fEval, 150)
        }
    }

    private fun folderEvaluate() {
        val grid = folderGrid ?: return
        val t = fCurT
        if (t < 0 || folderList.isEmpty()) return
        val pv = previewOrder(folderList, fOrig, t)
        fPreview = pv
        val cw = grid.cellW.toFloat()
        val ch = grid.cellH.toFloat()
        for ((key, v) in folderCells) {
            if (key == fKey) continue
            val oldI = folderList.indexOf(key)
            val newI = pv.indexOf(key)
            if (oldI < 0 || newI < 0) continue
            val dx = ((newI % 4) - (oldI % 4)) * cw
            val dy = ((newI / 4) - (oldI / 4)) * ch
            v.animate().translationX(dx).translationY(dy).setDuration(190).start()
        }
        folderOverlay?.let {
            it.holeIndex = t
            it.invalidate()
        }
    }

    private fun folderDragEnd(drop: Boolean) {
        main.removeCallbacks(fEval)
        folderOverlay?.visibility = View.GONE
        for ((_, v) in folderCells) {
            v.animate().cancel()
            v.translationX = 0f
            v.translationY = 0f
            v.visibility = View.VISIBLE
        }
        val ref = folderRef
        val pv = fPreview
        val key = fKey
        fKey = ""
        fPreview = null
        fCurT = -1
        if (drop && ref != null && pv != null && key.isNotEmpty()) {
            val result = ArrayList<String>()
            for (k in pv) if (k == HOLE) result.add(key) else if (k.isNotEmpty()) result.add(k)
            folderList = result
            if (ref.home) {
                val hf = homeFolders[ref.id]
                if (hf != null) {
                    hf.members.clear()
                    hf.members.addAll(result)
                    saveHomeFolders()
                    main.post { rebuildHome() }
                }
            } else {
                saveFolderOrder(ref.id, result)
                refreshDrawer()
            }
            fillFolderGrid()
        }
    }

    private fun showAddApps(ref: FolderRef) {
        val inFolder = HashSet(folderList)
        val cands = apps.filter { !inFolder.contains(it.comp) }.sortedBy { it.label.lowercase() }
        val labels = cands.map { it.label }.toTypedArray()
        val checked = BooleanArray(cands.size)
        AlertDialog.Builder(this)
            .setTitle("Add apps")
            .setMultiChoiceItems(labels, checked) { _, i, c -> checked[i] = c }
            .setPositiveButton("Add") { _, _ ->
                val picked = ArrayList<String>()
                for (i in cands.indices) if (checked[i]) picked.add(cands[i].comp)
                if (picked.isNotEmpty()) addToFolder(ref, picked)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun addToFolder(ref: FolderRef, comps: List<String>) {
        if (ref.home) {
            val hf = homeFolders[ref.id] ?: return
            for (c in comps) if (!hf.members.contains(c)) hf.members.add(c)
            saveHomeFolders()
            rebuildHome()
        } else {
            val order = ArrayList(loadOrder())
            for (c in comps) {
                prefs.setAssign(c, ref.id)
                val i = order.indexOf(c)
                if (i >= 0) order[i] = ""
            }
            saveOrder(order)
            refreshDrawer()
        }
        // پوشه را با محتوای جدید دوباره باز می‌کنیم تا تعداد ردیف‌ها درست شود
        closeFolderNow()
        showFolder(ref, null, false)
    }

    private fun showColorPicker(initial: Int, onPick: (Int) -> Unit) {
        val hsv = FloatArray(3)
        Color.colorToHSV(initial or 0xFF000000.toInt(), hsv)
        val box = LinearLayout(this)
        box.orientation = LinearLayout.VERTICAL
        box.setPadding((24 * f).toInt(), (16 * f).toInt(), (24 * f).toInt(), 0)
        val preview = View(this)
        box.addView(preview, LinearLayout.LayoutParams(-1, (64 * f).toInt()))
        fun update() { preview.setBackgroundColor(Color.HSVToColor(hsv)) }
        fun bar(maxV: Int, value: Int, set: (Int) -> Unit): SeekBar {
            val s = SeekBar(this)
            s.max = maxV
            s.progress = value
            s.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(sb: SeekBar?, p: Int, fromUser: Boolean) {
                    set(p)
                    update()
                }
                override fun onStartTrackingTouch(sb: SeekBar?) {}
                override fun onStopTrackingTouch(sb: SeekBar?) {}
            })
            return s
        }
        box.addView(bar(360, hsv[0].toInt()) { hsv[0] = it.toFloat().coerceAtMost(359f) })
        box.addView(bar(100, (hsv[1] * 100).toInt()) { hsv[1] = it / 100f })
        box.addView(bar(100, (hsv[2] * 100).toInt()) { hsv[2] = it / 100f })
        update()
        AlertDialog.Builder(this)
            .setTitle("Custom color")
            .setView(box)
            .setPositiveButton("OK") { _, _ -> onPick(Color.HSVToColor(hsv)) }
            .setNegativeButton("Cancel", null)
            .show()
    }

    // ================================================================ settings

    private fun showSettings() {
        AlertDialog.Builder(this)
            .setTitle("Settings")
            .setMultiChoiceItems(
                arrayOf("One UI icons", "Adapt labels & status bar to wallpaper"),
                booleanArrayOf(prefs.oneUiIcons, prefs.autoColors)
            ) { _, which, checked ->
                if (which == 0) {
                    prefs.oneUiIcons = checked
                    icons.clear()
                    refresh()
                } else {
                    prefs.autoColors = checked
                    lastSysUi = -1
                    applyStatusBar()
                    homeIndicator.dark = labelDark()
                    homeIndicator.invalidate()
                    rebuildDock()
                    rebuildHome()
                }
            }
            .setPositiveButton("OK", null)
            .show()
    }

    // ================================================================ widgets

    /** minWidth/minHeight پیکسل هستند؛ فرمول رسمی اندروید: n = (dp + 30) / 70 */
    private fun spansFor(info: AppWidgetProviderInfo): Pair<Int, Int> {
        val dens = resources.displayMetrics.density
        val wDp = info.minWidth / dens
        val hDp = info.minHeight / dens
        val sx = ((wDp + 30f) / 70f + 0.02f).toInt().coerceIn(1, 4)
        val sy = ((hDp + 30f) / 70f + 0.02f).toInt().coerceIn(1, ROWS)
        return Pair(sx, sy)
    }

    private fun showWidgetPicker() {
        WidgetPicker(this, f, awm, { info -> spansFor(info) }, wallBlur) { info -> bindWidget(info) }.show()
    }

    private fun bindWidget(p: AppWidgetProviderInfo) {
        val id = widgetHost.allocateAppWidgetId()
        if (awm.bindAppWidgetIdIfAllowed(id, p.provider)) {
            afterBind(id)
        } else {
            pendingWid = id
            val i = Intent(AppWidgetManager.ACTION_APPWIDGET_BIND)
            i.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
            i.putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, p.provider)
            try {
                startActivityForResult(i, REQ_BIND)
            } catch (e: Exception) {
                widgetHost.deleteAppWidgetId(id)
            }
        }
    }

    private fun afterBind(id: Int) {
        val info = awm.getAppWidgetInfo(id)
        if (info?.configure != null) {
            pendingWid = id
            try {
                widgetHost.startAppWidgetConfigureActivityForResult(this, id, 0, REQ_CONFIG, null)
            } catch (e: Exception) {
                addWidgetItem(id)
            }
        } else {
            addWidgetItem(id)
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        val id = data?.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, -1)?.takeIf { it != -1 } ?: pendingWid
        if (requestCode == REQ_BIND || requestCode == REQ_CONFIG) {
            if (resultCode == RESULT_OK && id != -1) {
                if (requestCode == REQ_BIND) afterBind(id) else addWidgetItem(id)
            } else if (id != -1) {
                try { widgetHost.deleteAppWidgetId(id) } catch (e: Exception) {}
            }
        }
    }

    private fun addWidgetItem(id: Int) {
        val info = awm.getAppWidgetInfo(id)
        if (info == null) {
            widgetHost.deleteAppWidgetId(id)
            return
        }
        val spans = spansFor(info)
        val sx = spans.first
        val sy = spans.second
        val count = max(1, prefs.pages)
        val start = curHomePage()
        val order = (0 until count).sortedBy { abs(it - start) }
        var placed: HomeItem? = null
        for (p in order) {
            val spot = modelFree(p, sx, sy, 0, 0, null)
            if (spot != null) {
                placed = HomeItem("widget", p, spot.first, spot.second, sx, sy, "", id)
                break
            }
        }
        if (placed == null && count < 7) {
            prefs.pages = count + 1
            placed = HomeItem("widget", count, 0, 0, sx, sy, "", id)
        }
        if (placed == null) {
            Toast.makeText(this, "No room for this widget", Toast.LENGTH_SHORT).show()
            widgetHost.deleteAppWidgetId(id)
            return
        }
        homeItems.add(placed)
        saveHome()
        rebuildHome()
        homePager.setCurrentItem(placed.page, false)
        val item = placed
        main.post { widgetViews[item]?.let { openWidgetEditor(item, it) } }
    }

    private fun openWidgetEditor(item: HomeItem, frame: View) {
        val info = awm.getAppWidgetInfo(item.wid)
        val dens = resources.displayMetrics.density
        var canH = true
        var canV = true
        var minSx = 1
        var minSy = 1
        if (info != null) {
            canH = (info.resizeMode and AppWidgetProviderInfo.RESIZE_HORIZONTAL) != 0
            canV = (info.resizeMode and AppWidgetProviderInfo.RESIZE_VERTICAL) != 0
            minSx = max(1, ((info.minResizeWidth / dens + 30f) / 70f + 0.02f).toInt())
            minSy = max(1, ((info.minResizeHeight / dens + 30f) / 70f + 0.02f).toInt())
        }
        widgetEditor.show(
            item, frame, 0f, homePager.top.toFloat(), homePager.width.toFloat(), homePager.height.toFloat(),
            4, ROWS, minSx, minSy, canH, canV
        )
    }

    private fun onWidgetCommit(item: HomeItem, x: Int, y: Int, sx: Int, sy: Int): Boolean {
        if (modelOccupied(item.page, x, y, sx, sy, item)) return false
        item.x = x
        item.y = y
        item.sx = sx
        item.sy = sy
        saveHome()
        main.post {
            rebuildHome()
            main.post { widgetViews[item]?.let { openWidgetEditor(item, it) } }
        }
        return true
    }

    private fun removeWidget(item: HomeItem) {
        widgetEditor.hide()
        try { widgetHost.deleteAppWidgetId(item.wid) } catch (e: Exception) {}
        homeItems.remove(item)
        saveHome()
        rebuildHome()
    }
}
