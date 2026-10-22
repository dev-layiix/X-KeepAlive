package app.xkeepalive.i18n

import android.content.Context
import android.content.res.Configuration
import java.util.Locale

/**
 * Applies the selected language to a [Context] following Android best
 * practices (per-context [Configuration] locale on top of the system one).
 * The English resources are the fallback whenever a translation is missing.
 */
object AppLocale {
    /**
     * Set synchronously right before an Activity is recreated after a manual
     * language change, so [attachBaseContext] wrappers already see the new
     * code even if the DataStore write has not landed yet.
     */
    @Volatile
    var pendingOverride: String? = null

    fun wrap(base: Context, code: String): Context {
        val locale = (AppLanguages.byCode(code) ?: AppLanguages.byCode(AppLanguages.FALLBACK)!!).toLocale()
        Locale.setDefault(locale)
        val config = Configuration(base.resources.configuration)
        config.setLocale(locale)
        return base.createConfigurationContext(config)
    }
}
