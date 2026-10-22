package app.xkeepalive.ui.guide

import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.xkeepalive.R
import app.xkeepalive.data.SessionUi
import app.xkeepalive.data.UserSettings
import app.xkeepalive.ui.theme.Mist

@Composable
fun GuideScreen(
    settings: UserSettings,
    session: SessionUi,
    onAboutPhone: () -> Unit,
    onDeveloperOptions: () -> Unit,
    onOpenShizuku: () -> Unit,
    onInstallShizuku: () -> Unit,
    onRequestShizuku: () -> Unit,
    onUsageAccess: () -> Unit,
    onNotifications: () -> Unit,
    onOwnBattery: () -> Unit,
    onGameBattery: () -> Unit,
    onOpenHome: () -> Unit,
) {
    val steps = guideSteps(
        GuideProgress(
            developerOptions = session.permissions.developerOptions,
            wirelessDebugging = session.permissions.wirelessDebugging,
            showWirelessStep = Build.VERSION.SDK_INT >= 30,
            shizukuInstalled = session.shizuku.managerInstalled,
            shizukuRunning = session.shizuku.binderAlive,
            shizukuAuthorized = session.shizuku.permissionGranted,
            usageAccess = session.permissions.usageAccess,
            notifications = session.permissions.notifications,
            askNotifications = Build.VERSION.SDK_INT >= 33,
            ownBatteryUnrestricted = session.permissions.ownBatteryUnrestricted,
            gameBatteryDone = settings.gameBatteryHintDone,
            appsOn = settings.watched.any { it.enabled },
            serviceOn = settings.automationEnabled && session.serviceRunning && session.shizuku.ready,
        ),
    )
    val doneCount = steps.count { it.done }
    val currentIndex = steps.indexOfFirst { !it.done }
    var expandedIndex by remember(steps.size, currentIndex) {
        mutableStateOf<Int?>(if (currentIndex >= 0) currentIndex else null)
    }

    fun fire(step: GuideStep) {
        when (step.action) {
            GuideAction.AboutPhone -> onAboutPhone()
            GuideAction.DeveloperOptions -> onDeveloperOptions()
            GuideAction.InstallShizuku -> onInstallShizuku()
            GuideAction.OpenShizuku -> onOpenShizuku()
            GuideAction.AuthorizeShizuku -> onRequestShizuku()
            GuideAction.UsageAccess -> onUsageAccess()
            GuideAction.Notifications -> onNotifications()
            GuideAction.OwnBattery -> onOwnBattery()
            GuideAction.GameBattery -> onGameBattery()
            GuideAction.Home -> onOpenHome()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(stringResource(R.string.guide_title), style = MaterialTheme.typography.headlineMedium)
        Text(
            stringResource(R.string.guide_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = Mist,
        )
        SetupProgressCard(doneCount = doneCount, total = steps.size)
        steps.forEachIndexed { index, step ->
            val state = when {
                step.done -> StepState.Done
                index == currentIndex -> StepState.Current
                else -> StepState.Locked
            }
            TimelineStepRow(
                number = index + 1,
                step = step,
                state = state,
                isLast = index == steps.lastIndex,
                expanded = expandedIndex == index,
                onToggle = {
                    expandedIndex = if (expandedIndex == index) null else index
                },
                onAction = { fire(step) },
            )
        }
        GuideInfoGrid()
        GoodToKnowCard()
    }
}
