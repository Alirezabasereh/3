package com.example.oneuilauncher

import android.content.Context
import org.json.JSONObject

class Prefs(ctx: Context) {
    private val sp = ctx.getSharedPreferences("launcher", Context.MODE_PRIVATE)

    var oneUiIcons: Boolean
        get() = sp.getBoolean("oneui_icons", true)
        set(v) { sp.edit().putBoolean("oneui_icons", v).apply() }

    /** آیکون‌های استاتوس‌بار روی هوم تیره باشند (مثل والپیپر روشن سامسونگ) */
    var darkStatus: Boolean
        get() = sp.getBoolean("dark_status", true)
        set(v) { sp.edit().putBoolean("dark_status", v).apply() }

    var pages: Int
        get() = sp.getInt("pages", 1)
        set(v) { sp.edit().putInt("pages", v).apply() }

    var homePage: Int
        get() = sp.getInt("home_page", 0)
        set(v) { sp.edit().putInt("home_page", v).apply() }

    var home: String
        get() = sp.getString("home", "[]") ?: "[]"
        set(v) { sp.edit().putString("home", v).apply() }

    var dock: String
        get() = sp.getString("dock", "") ?: ""
        set(v) { sp.edit().putString("dock", v).apply() }

    var drawerPage: Int
        get() = sp.getInt("drawer_page", 0)
        set(v) { sp.edit().putInt("drawer_page", v).apply() }

    var askedStorage: Boolean
        get() = sp.getBoolean("asked_storage", false)
        set(v) { sp.edit().putBoolean("asked_storage", v).apply() }

    /** رنگ لیبل و آیکون‌های استاتوس‌بار بر اساس روشنی والپیپر */
    var autoColors: Boolean
        get() = sp.getBoolean("auto_colors", true)
        set(v) { sp.edit().putBoolean("auto_colors", v).apply() }

    var gridMigrated: Boolean
        get() = sp.getBoolean("grid44", false)
        set(v) { sp.edit().putBoolean("grid44", v).apply() }

    /** ترتیب اسلات‌های اپ‌دراور: آرایهٔ JSON از comp / folder:<name> / "" (جای خالی) */
    var drawerOrder: String
        get() = sp.getString("drawer_order", "[]") ?: "[]"
        set(v) { sp.edit().putString("drawer_order", v).apply() }

    /** پوشه‌های هوم: {id:{name:..., members:[comp,...]}} */
    var homeFolders: String
        get() = sp.getString("home_folders", "{}") ?: "{}"
        set(v) { sp.edit().putString("home_folders", v).apply() }

    /** ترتیب اپ‌ها داخل پوشه‌های دراور: {name:[comp,...]} */
    var folderOrders: String
        get() = sp.getString("folder_orders", "{}") ?: "{}"
        set(v) { sp.edit().putString("folder_orders", v).apply() }

    private var colorsJson: String
        get() = sp.getString("folder_colors", "{}") ?: "{}"
        set(v) { sp.edit().putString("folder_colors", v).apply() }

    private var assignJson: String
        get() = sp.getString("folder_assign", "{}") ?: "{}"
        set(v) { sp.edit().putString("folder_assign", v).apply() }

    private var recentsRaw: String
        get() = sp.getString("recents", "") ?: ""
        set(v) { sp.edit().putString("recents", v).apply() }

    fun folderColor(name: String): Int {
        return try {
            JSONObject(colorsJson).optInt(name, defaultFolderColor())
        } catch (e: Exception) {
            defaultFolderColor()
        }
    }

    fun defaultFolderColor(): Int = 0xF2F2F2F2.toInt()

    fun setFolderColor(name: String, color: Int) {
        val o = try { JSONObject(colorsJson) } catch (e: Exception) { JSONObject() }
        o.put(name, color)
        colorsJson = o.toString()
    }

    fun assignMap(): Map<String, String> {
        val m = HashMap<String, String>()
        try {
            val o = JSONObject(assignJson)
            val it = o.keys()
            while (it.hasNext()) {
                val k = it.next()
                m[k] = o.getString(k)
            }
        } catch (e: Exception) {
        }
        return m
    }

    fun setAssign(comp: String, folder: String) {
        val o = try { JSONObject(assignJson) } catch (e: Exception) { JSONObject() }
        o.put(comp, folder)
        assignJson = o.toString()
    }

    /** تغییر نام پوشهٔ دراور: عضویت‌ها، رنگ و ترتیب داخلی منتقل می‌شوند */
    fun renameFolder(old: String, new: String) {
        if (old == new) return
        val a = assignMap()
        for ((k, v) in a) if (v == old) setAssign(k, new)
        val c = try { JSONObject(colorsJson) } catch (e: Exception) { JSONObject() }
        if (c.has(old)) { c.put(new, c.getInt(old)); c.remove(old); colorsJson = c.toString() }
        val o = try { JSONObject(folderOrders) } catch (e: Exception) { JSONObject() }
        if (o.has(old)) { o.put(new, o.get(old)); o.remove(old); folderOrders = o.toString() }
    }

    fun pushRecent(comp: String) {
        val list = recentList().toMutableList()
        list.remove(comp)
        list.add(0, comp)
        recentsRaw = list.take(12).joinToString("|")
    }

    fun recentList(): List<String> = if (recentsRaw.isEmpty()) emptyList() else recentsRaw.split("|")
}
