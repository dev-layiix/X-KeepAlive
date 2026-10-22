package app.xkeepalive.permissions

import android.Manifest
import android.app.AppOpsManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.core.content.ContextCompat

data class PermissionSnapshot(
    val usageAccess: Boolean = false,
    val notifications: Boolean = false,
    val ownBatteryUnrestricted: Boolean = false,
    val developerOptions: Boolean = false,
    val wirelessDebugging: Boolean = false,
) {
    val monitoringReady: Boolean
        get() = usageAccess && (notifications || Build.VERSION.SDK_INT < 33)
}

class PermissionGateway(private val context: Context) {
    fun snapshot(): PermissionSnapshot = PermissionSnapshot(
        usageAccess = hasUsageAccess(),
        notifications = hasNotifications(),
        ownBatteryUnrestricted = isIgnoringBatteryOptimizations(),
        developerOptions = developerOptionsEnabled(),
        wirelessDebugging = wirelessDebuggingEnabled(),
    )

    fun hasUsageAccess(): Boolean {
        val appOps = context.getSystemService(AppOpsManager::class.java) ?: return false
        val mode = if (Build.VERSION.SDK_INT >= 29) {
            appOps.unsafeCheckOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                android.os.Process.myUid(),
                context.packageName,
            )
        } else {
            @Suppress("DEPRECATION")
            appOps.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                android.os.Process.myUid(),
                context.packageName,
            )
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }

    fun hasNotifications(): Boolean {
        if (Build.VERSION.SDK_INT < 33) return true
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun isIgnoringBatteryOptimizations(): Boolean {
        val power = context.getSystemService(PowerManager::class.java) ?: return false
        return power.isIgnoringBatteryOptimizations(context.packageName)
    }

    fun openUsageAccess() {
        val targeted = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).apply {
            data = Uri.parse("package:${context.packageName}")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        if (!start(targeted)) {
            start(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }

    fun openOwnBatterySettings() {
        val power = context.getSystemService(PowerManager::class.java)
        if (power != null && !power.isIgnoringBatteryOptimizations(context.packageName)) {
            val request = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                data = Uri.parse("package:${context.packageName}")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            if (start(request)) return
        }
        openAppDetails(context.packageName)
    }

    fun openGamePowerSettings(packageName: String) {
        // App info first: on most phones (including Nothing OS) its battery
        // section is exactly the "allow background use" page.
        val details = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.parse("package:$packageName")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        if (start(details)) return
        val advanced = Intent("android.settings.VIEW_ADVANCED_POWER_USAGE_DETAIL").apply {
            putExtra("android.provider.extra.APP_PACKAGE", packageName)
            putExtra("app_package", packageName)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        start(advanced)
    }

    /**
     * Reads whether background use is allowed for another package, so the app
     * only asks when it is really off. Uses the raw op string (not the
     * constant) to stay safe on old Android versions; unknown ops fail open.
     *
     * Only an explicit restriction counts as off: the default mode already
     * means allowed, otherwise every phone would ask even with the system
     * toggle on.
     */
    fun isBackgroundUnrestricted(packageName: String): Boolean {
        return try {
            val uid = context.packageManager.getApplicationInfo(packageName, 0).uid
            val appOps = context.getSystemService(AppOpsManager::class.java) ?: return true
            val mode = appOps.checkOpNoThrow(OP_RUN_IN_BACKGROUND, uid, packageName)
            mode != AppOpsManager.MODE_IGNORED && mode != AppOpsManager.MODE_ERRORED
        } catch (_: Throwable) {
            true
        }
    }

    fun openAppDetails(packageName: String) {
        start(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.parse("package:$packageName")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            },
        )
    }

    fun developerOptionsEnabled(): Boolean =
        settingEnabled(Settings.Global.DEVELOPMENT_SETTINGS_ENABLED) || settingEnabled(Settings.Global.ADB_ENABLED)

    fun wirelessDebuggingEnabled(): Boolean {
        if (Build.VERSION.SDK_INT < 30) return false
        return settingEnabled("adb_wifi_enabled")
    }

    fun openAboutPhone(): Boolean {
        val about = Intent(Settings.ACTION_DEVICE_INFO_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (start(about)) return true
        return start(Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    fun openDeveloperOptions(): Boolean =
        start(Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))

    fun openShizuku(): Boolean {
        val launch = context.packageManager.getLaunchIntentForPackage(SHIZUKU_PACKAGE) ?: return false
        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return start(launch)
    }

    fun openShizukuStore(): Boolean {
        val market = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$SHIZUKU_PACKAGE")).apply {
            setPackage("com.android.vending")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        if (start(market)) return true
        return start(
            Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=$SHIZUKU_PACKAGE"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }

    private fun settingEnabled(name: String): Boolean {
        val resolver = context.contentResolver
        val global = try {
            Settings.Global.getInt(resolver, name, 0) == 1
        } catch (_: Throwable) {
            false
        }
        if (global) return true
        return try {
            Settings.Secure.getInt(resolver, name, 0) == 1
        } catch (_: Throwable) {
            false
        }
    }

    private fun start(intent: Intent): Boolean = try {
        context.startActivity(intent)
        true
    } catch (_: ActivityNotFoundException) {
        false
    } catch (_: SecurityException) {
        false
    }

    companion object {
        const val SHIZUKU_PACKAGE = "moe.shizuku.privileged.api"
        private const val OP_RUN_IN_BACKGROUND = "android:run_in_background"
    }
}
