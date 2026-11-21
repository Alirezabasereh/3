package com.example.oneuilauncher

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory

/**
 * نگاشت پکیج/نام اپ  ->  فایل آیکون داخل assets/icons/<key>.png
 *
 * برای اضافه کردن آیکون جدید:
 *  1) فایل PNG را (با حروف کوچک و بدون فاصله) در app/src/main/assets/icons بریز
 *  2) پکیج اپ را در PACKAGE_TO_KEY به نام فایل (بدون .png) وصل کن
 */
object IconMapper {

    private val PACKAGE_TO_KEY: Map<String, String> = buildMap {
        fun add(key: String, vararg pkgs: String) = pkgs.forEach { put(it, key) }

        add("settings", "com.android.settings")
        add("gallery", "com.sec.android.gallery3d")
        add("camera", "com.sec.android.app.camera")
        add("phone", "com.samsung.android.dialer", "com.android.dialer",
            "com.samsung.android.app.telephonyui", "com.google.android.dialer")
        add("contacts", "com.samsung.android.contacts", "com.android.contacts",
            "com.samsung.android.app.contacts", "com.google.android.contacts")
        add("messages", "com.samsung.android.messaging", "com.android.mms",
            "com.google.android.apps.messaging")
        add("calculator", "com.sec.android.app.popupcalculator", "com.google.android.calculator")
        add("calendar", "com.samsung.android.calendar", "com.google.android.calendar",
            "com.android.calendar")
        add("clock", "com.sec.android.app.clockpackage", "com.google.android.deskclock")
        add("email", "com.samsung.android.email.provider", "com.google.android.gm")
        add("internet", "com.sec.android.app.sbrowser")
        add("music", "com.sec.android.app.music", "com.samsung.android.app.music.chn")
        add("my_files", "com.sec.android.app.myfiles", "com.samsung.android.myfiles")
        add("memo", "com.samsung.android.app.memo", "com.samsung.android.app.notes")
        add("reminder", "com.samsung.android.app.reminder")
        add("voice_recorder", "com.sec.android.app.voicenote")
        add("weather", "com.sec.android.daemonapp")
        add("themes", "com.samsung.android.themestore")
        add("game_center", "com.samsung.android.game.gamehome")
        add("heart_rate", "com.sec.android.app.shealth", "com.samsung.android.shealth")
        add("bluetooth", "com.android.bluetooth")
        add("playstore", "com.android.vending")
    }

    /** اگر پکیج در جدول بالا نبود، با نام نمایشی اپ تطبیق می‌دهیم (فارسی/انگلیسی). */
    private val LABEL_TO_KEY: Map<String, String> = buildMap {
        fun add(key: String, vararg labels: String) = labels.forEach { put(it.lowercase(), key) }

        add("settings", "settings", "تنظیمات")
        add("gallery", "gallery", "گالری")
        add("camera", "camera", "دوربین")
        add("phone", "phone", "تلفن", "تماس")
        add("contacts", "contacts", "مخاطبین", "مخاطب‌ها")
        add("messages", "messages", "پیام‌ها", "پیامها", "پیام")
        add("calculator", "calculator", "ماشین حساب", "ماشین‌حساب")
        add("calendar", "calendar", "تقویم")
        add("clock", "clock", "ساعت")
        add("email", "email", "ایمیل")
        add("internet", "internet", "اینترنت")
        add("music", "music", "موسیقی")
        add("my_files", "my files", "فایل‌های من", "فایل های من")
        add("memo", "memo", "samsung notes", "notes", "یادداشت")
        add("reminder", "reminder", "یادآور")
        add("voice_recorder", "voice recorder", "ضبط صدا")
        add("weather", "weather", "آب و هوا")
        add("themes", "themes", "galaxy themes", "تم‌ها", "تم ها")
        add("game_center", "game launcher", "game center")
        add("heart_rate", "s health", "samsung health", "heart rate")
        add("playstore", "play store", "google play store")
    }

    fun keyFor(packageName: String, label: String): String? =
        PACKAGE_TO_KEY[packageName] ?: LABEL_TO_KEY[label.trim().lowercase()]

    private val cache = HashMap<String, Bitmap?>()

    /** آیکون را یک بار از assets می‌خواند، به اندازهٔ لازم کوچک می‌کند و کش می‌کند. */
    @Synchronized
    fun load(context: Context, key: String, sizePx: Int): Bitmap? {
        val cacheKey = "$key@$sizePx"
        if (cache.containsKey(cacheKey)) return cache[cacheKey]
        val bmp = try {
            context.assets.open("icons/$key.png").use { BitmapFactory.decodeStream(it) }
                ?.let { src ->
                    if (src.width == sizePx) src
                    else Bitmap.createScaledBitmap(src, sizePx, sizePx, true).also { src.recycle() }
                }
        } catch (e: Exception) {
            null
        }
        cache[cacheKey] = bmp
        return bmp
    }
}
