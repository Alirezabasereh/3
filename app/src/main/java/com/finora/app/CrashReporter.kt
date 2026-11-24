package com.finora.app

import android.content.Context
import android.os.Build

/**
 * Stores the stack trace of an uncaught crash so the next launch can show it (copyable),
 * which makes it possible to report device-specific crashes precisely.
 */
object CrashReporter {
    private const val PREFS = "finora_crash"
    private const val KEY = "last"

    fun install(context: Context) {
        val app = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            try {
                val sw = java.io.StringWriter()
                error.printStackTrace(java.io.PrintWriter(sw))
                val header = "Finora ${BuildInfo.VERSION} · Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT}) · ${Build.MANUFACTURER} ${Build.MODEL}\nthread=${thread.name}\n\n"
                app.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, (header + sw.toString()).take(12000)).commit()
            } catch (_: Throwable) { }
            previous?.uncaughtException(thread, error)
        }
    }

    fun read(context: Context): String? =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null)

    fun clear(context: Context) {
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove(KEY).apply()
    }
}

object BuildInfo { const val VERSION = "0.3.1" }
