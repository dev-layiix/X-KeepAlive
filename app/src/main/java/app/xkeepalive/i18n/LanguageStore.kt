package app.xkeepalive.i18n

import android.content.Context

/**
 * Synchronous boot copy of the selected language. DataStore cannot be read
 * reliably inside attachBaseContext (it needs applicationContext, which is
 * not usable yet at Application level), so the language is mirrored here with
 * a blocking commit. Fully synchronous and safe to read before any Activity,
 * Service or widget exists.
 */
object LanguageStore {
    private const val FILE = "xkeepalive_boot"
    private const val KEY = "language_code"

    fun read(context: Context): String = runCatching {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).getString(KEY, "").orEmpty()
    }.getOrDefault("")

    fun write(context: Context, code: String) {
        runCatching {
            context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY, code)
                .commit()
        }
    }
}
