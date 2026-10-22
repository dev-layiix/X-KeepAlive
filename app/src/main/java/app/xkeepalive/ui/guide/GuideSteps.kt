package app.xkeepalive.ui.guide

import android.os.Build
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Star
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import app.xkeepalive.R

enum class GuideAction {
    AboutPhone,
    DeveloperOptions,
    InstallShizuku,
    OpenShizuku,
    AuthorizeShizuku,
    UsageAccess,
    Notifications,
    OwnBattery,
    GameBattery,
    Home,
}

data class GuideStep(
    val title: String,
    val subtitle: String,
    val body: String,
    val details: List<String> = emptyList(),
    val tip: String = "",
    val icon: ImageVector,
    val button: String,
    val action: GuideAction,
    val done: Boolean,
)

data class GuideProgress(
    val developerOptions: Boolean,
    val wirelessDebugging: Boolean,
    val showWirelessStep: Boolean,
    val shizukuInstalled: Boolean,
    val shizukuRunning: Boolean,
    val shizukuAuthorized: Boolean,
    val usageAccess: Boolean,
    val notifications: Boolean,
    val askNotifications: Boolean,
    val ownBatteryUnrestricted: Boolean,
    val gameBatteryDone: Boolean,
    val appsOn: Boolean,
    val serviceOn: Boolean,
)

@Composable
fun guideSteps(progress: GuideProgress): List<GuideStep> {
    val showWireless = progress.showWirelessStep && Build.VERSION.SDK_INT >= 30
    val developerDone = progress.developerOptions || progress.wirelessDebugging || progress.shizukuRunning
    val steps = mutableListOf(
        GuideStep(
            title = stringResource(R.string.step_dev_title),
            subtitle = stringResource(R.string.step_dev_subtitle),
            body = stringResource(R.string.step_dev_body),
            details = stringArrayResource(R.array.step_dev_details).toList(),
            tip = stringResource(R.string.step_dev_tip),
            icon = Icons.Filled.Build,
            button = stringResource(R.string.step_dev_button),
            action = GuideAction.AboutPhone,
            done = developerDone,
        ),
    )
    if (showWireless) {
        steps += GuideStep(
            title = stringResource(R.string.step_wifi_title),
            subtitle = stringResource(R.string.step_wifi_subtitle),
            body = stringResource(R.string.step_wifi_body),
            details = stringArrayResource(R.array.step_wifi_details).toList(),
            tip = stringResource(R.string.step_wifi_tip),
            icon = Icons.Filled.Settings,
            button = stringResource(R.string.step_wifi_button),
            action = GuideAction.DeveloperOptions,
            done = progress.wirelessDebugging || progress.shizukuRunning,
        )
    }
    steps += GuideStep(
        title = stringResource(R.string.step_install_title),
        subtitle = stringResource(R.string.step_install_subtitle),
        body = stringResource(R.string.step_install_body),
        details = stringArrayResource(R.array.step_install_details).toList(),
        tip = stringResource(R.string.step_install_tip),
        icon = Icons.Filled.ShoppingCart,
        button = stringResource(R.string.step_install_button),
        action = GuideAction.InstallShizuku,
        done = progress.shizukuInstalled,
    )
    steps += GuideStep(
        title = stringResource(R.string.step_start_title),
        subtitle = stringResource(R.string.step_start_subtitle),
        body = if (showWireless) {
            stringResource(R.string.step_start_body)
        } else {
            stringResource(R.string.step_start_body_legacy)
        },
        details = if (showWireless) {
            stringArrayResource(R.array.step_start_details).toList()
        } else {
            stringArrayResource(R.array.step_start_legacy_details).toList()
        },
        tip = stringResource(R.string.step_start_tip),
        icon = Icons.Filled.PlayArrow,
        button = stringResource(R.string.step_start_button),
        action = GuideAction.OpenShizuku,
        done = progress.shizukuRunning,
    )
    steps += GuideStep(
        title = stringResource(R.string.step_auth_title),
        subtitle = stringResource(R.string.step_auth_subtitle),
        body = stringResource(R.string.step_auth_body),
        details = stringArrayResource(R.array.step_auth_details).toList(),
        tip = stringResource(R.string.step_auth_tip),
        icon = Icons.Filled.Lock,
        button = stringResource(R.string.step_auth_button),
        action = GuideAction.AuthorizeShizuku,
        done = progress.shizukuAuthorized && progress.shizukuRunning,
    )
    steps += GuideStep(
        title = stringResource(R.string.step_usage_title),
        subtitle = stringResource(R.string.step_usage_subtitle),
        body = stringResource(R.string.step_usage_body),
        details = stringArrayResource(R.array.step_usage_details).toList(),
        tip = stringResource(R.string.step_usage_tip),
        icon = Icons.Filled.Person,
        button = stringResource(R.string.step_usage_button),
        action = GuideAction.UsageAccess,
        done = progress.usageAccess,
    )
    if (progress.askNotifications) {
        steps += GuideStep(
            title = stringResource(R.string.step_notif_title),
            subtitle = stringResource(R.string.step_notif_subtitle),
            body = stringResource(R.string.step_notif_body),
            details = stringArrayResource(R.array.step_notif_details).toList(),
            tip = stringResource(R.string.step_notif_tip),
            icon = Icons.Filled.Notifications,
            button = stringResource(R.string.step_notif_button),
            action = GuideAction.Notifications,
            done = progress.notifications,
        )
    }
    steps += GuideStep(
        title = stringResource(R.string.step_power_title),
        subtitle = stringResource(R.string.step_power_subtitle),
        body = stringResource(R.string.step_power_body),
        details = stringArrayResource(R.array.step_power_details).toList(),
        tip = stringResource(R.string.step_power_tip),
        icon = Icons.Filled.Star,
        button = stringResource(R.string.step_power_button),
        action = GuideAction.OwnBattery,
        done = progress.ownBatteryUnrestricted,
    )
    steps += GuideStep(
        title = stringResource(R.string.step_gamebatt_title),
        subtitle = stringResource(R.string.step_gamebatt_subtitle),
        body = stringResource(R.string.step_gamebatt_body),
        details = stringArrayResource(R.array.step_gamebatt_details).toList(),
        tip = stringResource(R.string.step_gamebatt_tip),
        icon = Icons.Filled.Check,
        button = stringResource(R.string.step_gamebatt_button),
        action = GuideAction.GameBattery,
        done = progress.gameBatteryDone,
    )
    steps += GuideStep(
        title = stringResource(R.string.step_apps_title),
        subtitle = stringResource(R.string.step_apps_subtitle),
        body = stringResource(R.string.step_apps_body),
        details = stringArrayResource(R.array.step_apps_details).toList(),
        tip = stringResource(R.string.step_apps_tip),
        icon = Icons.Filled.List,
        button = stringResource(R.string.step_apps_button),
        action = GuideAction.Home,
        done = progress.appsOn,
    )
    steps += GuideStep(
        title = stringResource(R.string.step_service_title),
        subtitle = stringResource(R.string.step_service_subtitle),
        body = stringResource(R.string.step_service_body),
        details = stringArrayResource(R.array.step_service_details).toList(),
        tip = stringResource(R.string.step_service_tip),
        icon = Icons.Filled.Refresh,
        button = stringResource(R.string.step_service_button),
        action = GuideAction.Home,
        done = progress.serviceOn,
    )
    return steps
}
