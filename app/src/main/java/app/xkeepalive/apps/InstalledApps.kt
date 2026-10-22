package app.xkeepalive.apps

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build

data class InstalledApp(
    val packageName: String,
    val label: String,
    val isGame: Boolean,
    val isSystem: Boolean = false,
)

object InstalledApps {
    private const val LEGACY_FLAG_IS_GAME = 1 shl 25

    fun load(context: Context): List<InstalledApp> {
        val manager = context.packageManager
        val probe = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val resolved = query(manager, probe)
        return resolved.mapNotNull { info ->
            val appInfo = info.activityInfo?.applicationInfo ?: return@mapNotNull null
            val packageName = appInfo.packageName ?: return@mapNotNull null
            if (packageName == context.packageName) return@mapNotNull null
            InstalledApp(
                packageName = packageName,
                label = info.loadLabel(manager)?.toString().orEmpty().ifBlank { packageName },
                isGame = appInfo.category == ApplicationInfo.CATEGORY_GAME ||
                    (appInfo.flags and LEGACY_FLAG_IS_GAME) != 0,
                isSystem = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0,
            )
        }.distinctBy { it.packageName }
            .sortedBy { it.label.lowercase() }
    }

    private fun query(manager: PackageManager, intent: Intent) = try {
        if (Build.VERSION.SDK_INT >= 33) {
            manager.queryIntentActivities(
                intent,
                PackageManager.ResolveInfoFlags.of(PackageManager.MATCH_ALL.toLong()),
            )
        } else {
            @Suppress("DEPRECATION")
            manager.queryIntentActivities(intent, PackageManager.MATCH_ALL)
        }
    } catch (_: Throwable) {
        @Suppress("DEPRECATION")
        manager.queryIntentActivities(intent, 0)
    }
}
