package app.xkeepalive.ui.home

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.clickable
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.draw.clip
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.xkeepalive.R
import app.xkeepalive.apps.InstalledApp
import app.xkeepalive.core.WatchedGames
import app.xkeepalive.core.Blocker
import app.xkeepalive.core.Phase
import app.xkeepalive.core.ProcessOutcome
import app.xkeepalive.data.EventEntry
import app.xkeepalive.data.SessionUi
import app.xkeepalive.data.UserSettings
import app.xkeepalive.ui.AndroidSwitch
import app.xkeepalive.ui.AppFilterChip
import app.xkeepalive.ui.AppIcon
import app.xkeepalive.ui.KeyValue
import app.xkeepalive.ui.LogoMark
import app.xkeepalive.ui.SectionCard
import app.xkeepalive.ui.StatusChip
import app.xkeepalive.ui.StatusDot
import app.xkeepalive.ui.blockerLabel
import app.xkeepalive.ui.eventTitle
import app.xkeepalive.ui.formatEventTime
import app.xkeepalive.ui.outcomeLabel
import app.xkeepalive.ui.processNoteText
import app.xkeepalive.ui.theme.Accent
import app.xkeepalive.ui.theme.Danger
import app.xkeepalive.ui.theme.Line
import app.xkeepalive.ui.theme.Panel
import app.xkeepalive.ui.theme.PanelHigh
import app.xkeepalive.ui.theme.Mist
import app.xkeepalive.ui.theme.Ok
import app.xkeepalive.ui.theme.Warning

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    settings: UserSettings,
    session: SessionUi,
    events: List<EventEntry>,
    apps: List<InstalledApp>,
    appsLoading: Boolean,
    onGameEnabled: (String, Boolean) -> Unit,
    onAdd: (InstalledApp) -> Unit,
    onRemove: (String) -> Unit,
    onGroupEnabled: (Boolean) -> Unit,
    onRefreshApps: () -> Unit,
    onRequestShizuku: () -> Unit,
    onOpenShizuku: () -> Unit,
    onInstallShizuku: () -> Unit,
) {
    var pickerOpen by remember { mutableStateOf(false) }
    val headline = blockerLabel(session.blocker, session.phase)
    val accent = when {
        session.blocker != Blocker.NONE -> Warning
        session.phase == Phase.BACKGROUND_MANAGEMENT_ACTIVE -> Ok
        session.phase == Phase.GAME_FOREGROUND || session.phase == Phase.GAME_RETURNED -> Accent
        session.phase == Phase.INACTIVE -> Mist
        else -> Warning
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            LogoMark(size = 54.dp)
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    buildAnnotatedString {
                        withStyle(SpanStyle(color = Accent, fontWeight = FontWeight.Bold)) { append("X") }
                        withStyle(SpanStyle(color = MaterialTheme.colorScheme.onBackground, fontWeight = FontWeight.Bold)) {
                            append("-KeepAlive")
                        }
                    },
                    fontSize = 22.sp,
                    letterSpacing = (-0.3).sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    stringResource(R.string.home_subtitle),
                    color = Mist,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 1.1.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            StatusDot(accent, pulsing = session.serviceRunning)
        }

        SectionCard {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.home_section_title), style = MaterialTheme.typography.titleMedium)
                    Text(stringResource(R.string.home_section_sub), color = Mist, style = MaterialTheme.typography.bodySmall)
                }
                val active = settings.watched.count { it.enabled }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Text(
                        "$active/${WatchedGames.MAX_ACTIVE}",
                        color = Accent,
                        style = MaterialTheme.typography.titleMedium,
                    )
                    AndroidSwitch(
                        checked = settings.automationEnabled,
                        enabled = active > 0 || settings.automationEnabled,
                        onCheckedChange = onGroupEnabled,
                    )
                }
            }
            if (settings.watched.isEmpty()) {
                Text(stringResource(R.string.home_empty), color = Mist)
            }
            settings.watched.forEach { game ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(PanelHigh)
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    AppIcon(game.packageName)
                    Column(modifier = Modifier.weight(1f)) {
                        Text(game.label, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(game.packageName, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (game.enabled) {
                            val managed = game.packageName in session.managedPackages
                            val status = when {
                                !session.serviceRunning -> stringResource(R.string.home_status_stopped)
                                managed -> stringResource(R.string.home_status_on)
                                session.shizuku.ready -> stringResource(R.string.home_status_applying)
                                else -> stringResource(R.string.home_status_shizuku)
                            }
                            Text(
                                status,
                                color = if (managed) Ok else Mist,
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 1,
                            )
                        }
                    }
                    IconButton(onClick = { onRemove(game.packageName) }) {
                        Icon(
                            painter = painterResource(R.drawable.ic_remove),
                            contentDescription = stringResource(R.string.home_remove),
                            tint = Danger,
                            modifier = Modifier.size(22.dp),
                        )
                    }
                    AndroidSwitch(
                        checked = game.enabled,
                        onCheckedChange = { enabled -> onGameEnabled(game.packageName, enabled) },
                    )
                }
            }
            FilledTonalButton(
                onClick = {
                    pickerOpen = true
                    onRefreshApps()
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.home_add_app))
            }
        }

        SectionCard {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatusDot(accent, pulsing = session.serviceRunning)
                AnimatedContent(targetState = headline, label = "phase") { value ->
                    Text(value, style = MaterialTheme.typography.titleLarge, color = accent)
                }
            }
            Text(
                stringResource(R.string.home_note),
                style = MaterialTheme.typography.bodySmall,
            )
            session.limitNote?.let { Text(processNoteText(it), style = MaterialTheme.typography.bodyMedium) }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                StatusChip(
                    if (session.serviceRunning) stringResource(R.string.home_chip_running) else stringResource(R.string.home_chip_stopped),
                    if (session.serviceRunning) Ok else Mist,
                )
                if (!session.shizuku.ready) StatusChip(stringResource(R.string.home_chip_adb), Danger)
                if (!session.permissions.usageAccess) StatusChip(stringResource(R.string.home_chip_permissions), Danger)
                if (session.outcome != ProcessOutcome.NOT_CHECKED) {
                    StatusChip(outcomeLabel(session.outcome), accent)
                }
            }
            if (session.observedPids.isNotEmpty()) {
                KeyValue(stringResource(R.string.home_pid), session.observedPids.sorted().joinToString(", "))
            } else if (session.serviceRunning) {
                KeyValue(stringResource(R.string.home_pid), stringResource(R.string.home_pid_reading))
            }
            session.pidChange?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            session.foregroundPackage?.let { KeyValue(stringResource(R.string.home_foreground), it) }
        }

        SectionCard {
            Text(stringResource(R.string.home_shizuku_title), style = MaterialTheme.typography.titleMedium)
            Text(session.shizuku.message, style = MaterialTheme.typography.bodyMedium)
            if (!session.shizuku.managerInstalled) {
                Button(onClick = onInstallShizuku, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.home_install_shizuku))
                }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (session.shizuku.binderAlive && !session.shizuku.permissionGranted) {
                        Button(onClick = onRequestShizuku) { Text(stringResource(R.string.home_authorize)) }
                    }
                    TextButton(onClick = onOpenShizuku) { Text(stringResource(R.string.home_open_shizuku)) }
                }
            }
        }

        session.policy?.let { report ->
            SectionCard {
                Text(
                    if (report.anyVerified) stringResource(R.string.home_policy_ok) else stringResource(R.string.home_policy_no),
                    style = MaterialTheme.typography.titleMedium,
                    color = if (report.anyVerified) Ok else Warning,
                )
                Text("${report.mode} · ${report.packageName}", style = MaterialTheme.typography.bodySmall)
                report.operations.take(6).forEach { op ->
                    val mark = if (op.verified) stringResource(R.string.home_policy_ok_mark) else stringResource(R.string.home_policy_no_mark)
                    Text(
                        "$mark  ${op.name}",
                        color = if (op.verified) Ok else Warning,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(op.detail, style = MaterialTheme.typography.bodySmall)
                }
            }
        }

        if (events.isNotEmpty()) {
            SectionCard {
                Text(stringResource(R.string.home_recent), style = MaterialTheme.typography.titleMedium)
                events.takeLast(3).asReversed().forEach { entry ->
                    Text(formatEventTime(entry.timeMs) + "  " + eventTitle(entry.kind), fontWeight = FontWeight.SemiBold)
                    Text(entry.message, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }

    if (pickerOpen) {
        AppPicker(
            apps = apps,
            loading = appsLoading,
            onDismiss = { pickerOpen = false },
            onSelect = {
                onAdd(it)
                pickerOpen = false
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun AppPicker(
    apps: List<InstalledApp>,
    loading: Boolean,
    onDismiss: () -> Unit,
    onSelect: (InstalledApp) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var query by remember { mutableStateOf("") }
    var gamesOnly by remember { mutableStateOf(false) }
    val filtered = remember(apps, query, gamesOnly) {
        apps.filter { app ->
            (!gamesOnly || app.isGame) &&
                (query.isBlank() || app.label.contains(query, true) || app.packageName.contains(query, true))
        }
    }
    val sheetHeight = (LocalConfiguration.current.screenHeightDp * 0.88f).dp
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Panel,
        dragHandle = { BottomSheetDefaults.DragHandle(color = Line) },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .height(sheetHeight)
                .navigationBarsPadding()
                .imePadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(stringResource(R.string.picker_title), style = MaterialTheme.typography.titleLarge)
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = MaterialTheme.shapes.medium,
                label = { Text(stringResource(R.string.picker_search)) },
            )
            AppFilterChip(
                label = if (gamesOnly) stringResource(R.string.picker_games) else stringResource(R.string.picker_all),
                selected = gamesOnly,
                onClick = { gamesOnly = !gamesOnly },
            )
            if (loading) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            if (!loading && filtered.isEmpty()) {
                Text(
                    if (gamesOnly) {
                        stringResource(R.string.picker_no_games)
                    } else {
                        stringResource(R.string.picker_no_results)
                    },
                    color = Mist,
                )
            }
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(filtered, key = { it.packageName }) { app ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(PanelHigh)
                            .clickable { onSelect(app) }
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        AppIcon(app.packageName)
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                app.label + if (app.isGame) stringResource(R.string.picker_game_suffix) else "",
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                app.packageName,
                                fontFamily = FontFamily.Monospace,
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        }
    }
}

