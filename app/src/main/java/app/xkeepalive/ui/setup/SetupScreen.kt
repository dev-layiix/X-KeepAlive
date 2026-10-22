package app.xkeepalive.ui.setup

import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.xkeepalive.R
import app.xkeepalive.data.SessionUi
import app.xkeepalive.data.UserSettings
import app.xkeepalive.ui.LogoMark
import app.xkeepalive.ui.guide.GoodToKnowCard
import app.xkeepalive.ui.guide.GuideAction
import app.xkeepalive.ui.guide.GuideInfoGrid
import app.xkeepalive.ui.guide.GuideProgress
import app.xkeepalive.ui.guide.GuideStep
import app.xkeepalive.ui.guide.SetupProgressCard
import app.xkeepalive.ui.guide.StepState
import app.xkeepalive.ui.guide.TimelineStepRow
import app.xkeepalive.ui.guide.guideSteps
import app.xkeepalive.ui.theme.Ink
import app.xkeepalive.ui.theme.Mist
import app.xkeepalive.ui.theme.Ok

@Composable
fun SetupScreen(
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
    onFinish: () -> Unit,
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
    val ready = currentIndex < 0
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
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            LogoMark(size = 54.dp)
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(stringResource(R.string.guide_title), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text(
                    stringResource(R.string.guide_subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Mist,
                )
            }
        }
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
        if (ready) {
            Button(
                onClick = onFinish,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = Ok, contentColor = Ink),
            ) {
                Text(stringResource(R.string.setup_finish), fontWeight = FontWeight.Bold)
            }
        } else {
            TextButton(onClick = onFinish, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.setup_skip))
            }
        }
    }
}
