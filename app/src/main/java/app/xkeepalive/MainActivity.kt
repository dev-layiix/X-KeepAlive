package app.xkeepalive

import android.content.Context
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.viewmodel.compose.viewModel
import app.xkeepalive.data.SettingsRepository
import app.xkeepalive.i18n.AppLanguages
import app.xkeepalive.i18n.AppLocale
import app.xkeepalive.i18n.LanguageStore
import app.xkeepalive.ui.XBackgroundRoot
import app.xkeepalive.ui.theme.XBackgroundTheme
import app.xkeepalive.viewmodel.MainViewModel
import app.xkeepalive.widget.WidgetUpdater
import kotlinx.coroutines.runBlocking

class MainActivity : ComponentActivity() {
    override fun attachBaseContext(newBase: Context) {
        // Applies the saved language before any resource is loaded, with
        // English as fallback. On first launch there is no saved language, so
        // the picker is shown and nothing is auto-detected from the system.
        // Reads the synchronous boot copy: DataStore is not usable this early.
        val stored = AppLocale.pendingOverride?.takeIf { AppLanguages.isSupported(it) }
            ?: LanguageStore.read(newBase).ifBlank {
                runBlocking { runCatching { SettingsRepository.readLanguageCode(newBase) }.getOrDefault("") }
            }
        super.attachBaseContext(AppLocale.wrap(newBase, AppLanguages.resolveStoredOrSystem(stored)))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        // Deep link used by the widget gear icon to open the Settings tab.
        val initialTab = intent.getStringExtra(WidgetUpdater.EXTRA_TAB).orEmpty()
        setContent {
            val viewModel: MainViewModel = viewModel()
            XBackgroundTheme {
                XBackgroundRoot(viewModel, initialTab)
            }
        }
    }
}
