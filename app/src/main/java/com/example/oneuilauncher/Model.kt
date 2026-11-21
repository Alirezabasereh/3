package com.example.oneuilauncher

import android.content.ComponentName
import android.content.Context
import android.content.Intent

class AppInfo(val label: String, val pkg: String, val cls: String, val key: String?) {
    val comp: String get() = "$pkg/$cls"
    val component: ComponentName get() = ComponentName(pkg, cls)
}

class FolderInfo(val name: String, val apps: List<AppInfo>)

sealed class Entry {
    class App(val app: AppInfo) : Entry()
    class Folder(val folder: FolderInfo) : Entry()
    object Empty : Entry()
}

/** نتیجهٔ چیدمان اپ‌دراور: اسلات‌ها (comp، folder:<name> یا "" برای جای خالی) + محتویات پوشه‌ها */
class DrawerLayout(val slots: List<String>, val folders: Map<String, List<AppInfo>>)

class HomeItem(
    var type: String,   // "app" یا "widget"
    var page: Int,
    var x: Int,
    var y: Int,
    var sx: Int,
    var sy: Int,
    val comp: String,
    val wid: Int
)

object Repo {
    // ترتیب پیش‌فرض اپ‌های اصلی در اپ‌دراور (مثل لانچر سامسونگ)
    private val RANK = listOf(
        "playstore", "phone", "messages", "camera", "contacts", "settings",
        "gallery", "memo", "calendar", "clock", "calculator"
    )

    fun load(ctx: Context): List<AppInfo> {
        val pm = ctx.packageManager
        val q = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val seen = HashSet<String>()
        val out = ArrayList<AppInfo>()
        for (ri in pm.queryIntentActivities(q, 0)) {
            val pkg = ri.activityInfo.packageName
            if (pkg == ctx.packageName) continue
            val label = ri.loadLabel(pm).toString()
            val a = AppInfo(label, pkg, ri.activityInfo.name, IconMapper.keyFor(pkg, label))
            if (seen.add(a.comp)) out.add(a)
        }
        return out
    }

    private fun rank(a: AppInfo): Int {
        val i = RANK.indexOf(a.key ?: "")
        return if (i >= 0) i else 100
    }

    private fun defaultFolder(a: AppInfo): String {
        val k = a.key ?: ""
        if (RANK.contains(k)) return ""
        if (a.pkg.startsWith("com.sec.") || a.pkg.startsWith("com.samsung.")) return "Samsung"
        if (a.pkg.startsWith("com.google.") || a.pkg == "com.android.chrome") return "Google"
        return ""
    }

    /**
     * چیدمان اسلات‌ها. اپ‌ها/پوشه‌های جدید به انتها اضافه می‌شوند؛ جای خالی‌ها حفظ می‌شوند
     * (مثل لانچر سامسونگ) تا کاربر «Clean up pages» را بزند.
     */
    fun layout(
        apps: List<AppInfo>,
        assign: Map<String, String>,
        saved: List<String>,
        folderOrder: Map<String, List<String>>
    ): DrawerLayout {
        val groups = LinkedHashMap<String, ArrayList<AppInfo>>()
        groups["Samsung"] = ArrayList()
        groups["Google"] = ArrayList()
        val rest = ArrayList<AppInfo>()
        for (a in apps) {
            val g = assign[a.comp] ?: defaultFolder(a)
            if (g.isEmpty()) rest.add(a) else groups.getOrPut(g) { ArrayList() }.add(a)
        }
        val folders = LinkedHashMap<String, List<AppInfo>>()
        for ((name, members) in groups) {
            if (members.isEmpty()) continue
            val order = folderOrder[name] ?: emptyList()
            members.sortWith(compareBy({ val i = order.indexOf(it.comp); if (i < 0) Int.MAX_VALUE else i }, { it.label.lowercase() }))
            folders[name] = members
        }
        rest.sortWith(compareBy({ rank(it) }, { it.label.lowercase() }))

        val valid = LinkedHashSet<String>()
        for (n in folders.keys) valid.add("folder:$n")
        for (a in rest) valid.add(a.comp)
        val defaultOrder = ArrayList<String>()
        for (n in folders.keys) defaultOrder.add("folder:$n")
        for (a in rest) defaultOrder.add(a.comp)

        val result = ArrayList<String>()
        val used = HashSet<String>()
        for (k in saved) {
            if (k.isNotEmpty() && valid.contains(k) && used.add(k)) result.add(k) else result.add("")
        }
        for (k in defaultOrder) if (!used.contains(k)) { result.add(k); used.add(k) }
        while (result.isNotEmpty() && result.last().isEmpty()) result.removeAt(result.size - 1)
        return DrawerLayout(result, folders)
    }
}
