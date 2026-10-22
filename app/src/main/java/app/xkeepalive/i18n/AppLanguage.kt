package app.xkeepalive.i18n

import java.util.Locale

/** A language supported by the X-KeepAlive interface. */
data class AppLanguage(
    /** BCP-47 code used in resources and persistence, e.g. "en" or "pt-BR". */
    val code: String,
    /** Name shown in the selector, always in its own language. */
    val nativeName: String,
    /** Flag shown next to the native name. */
    val flag: String,
) {
    fun toLocale(): Locale {
        val parts = code.split('-', '_')
        return if (parts.size > 1) Locale(parts[0], parts[1]) else Locale(parts[0])
    }
}

object AppLanguages {
    const val FALLBACK = "en"

    val supported = listOf(
        AppLanguage("it", "Italiano", "🇮🇹"),
        AppLanguage("en", "English", "🇬🇧"),
        AppLanguage("es", "Español", "🇪🇸"),
        AppLanguage("pt-BR", "Português (Brasil)", "🇧🇷"),
        AppLanguage("ru", "Русский", "🇷🇺"),
    )

    fun isSupported(code: String): Boolean = supported.any { it.code == code }

    fun byCode(code: String): AppLanguage? = supported.firstOrNull { it.code == code }

    /**
     * Detects the device language on first launch. Portuguese in any region
     * maps to Brazilian Portuguese; anything unsupported falls back to English.
     */
    fun systemDefault(): String {
        val locale = Locale.getDefault()
        val language = locale.language?.lowercase().orEmpty()
        if (language == "pt") return "pt-BR"
        val match = supported.firstOrNull { it.code.equals(language, ignoreCase = true) }
        return match?.code ?: FALLBACK
    }

    /** Resolves a stored code. Blank or unknown codes fall back to English. */
    fun resolveStoredOrSystem(stored: String): String {
        if (isSupported(stored)) return stored
        return FALLBACK
    }
}
