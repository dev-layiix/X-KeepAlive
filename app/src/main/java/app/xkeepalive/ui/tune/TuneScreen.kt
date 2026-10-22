package app.xkeepalive.ui.tune

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.xkeepalive.R
import app.xkeepalive.apps.InstalledApp
import app.xkeepalive.core.TuneAdvice
import app.xkeepalive.core.TuneAdviceKind
import app.xkeepalive.core.TunePlan
import app.xkeepalive.core.TuneReason
import app.xkeepalive.data.UserSettings
import app.xkeepalive.ui.AndroidSwitch
import app.xkeepalive.ui.AppIcon
import app.xkeepalive.ui.SectionCard
import app.xkeepalive.ui.theme.Danger
import app.xkeepalive.ui.theme.Line
import app.xkeepalive.ui.theme.Mist
import app.xkeepalive.ui.theme.Ok
import app.xkeepalive.ui.theme.Panel
import app.xkeepalive.ui.theme.PanelHigh
import app.xkeepalive.ui.theme.Warning

private const val OWN_PACKAGE = "app.xkeepalive"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TuneScreen(
    settings: UserSettings,
    apps: List<InstalledApp>,
    appsLoading: Boolean,
    advice: List<TuneAdvice>,
    usageAccess: Boolean,
    busy: Set<String>,
    onApplyAll: () -> Unit,
    onFastAnims: (Boolean) -> Unit,
    onAddDrain: (InstalledApp) -> Unit,
    onRemoveDrain: (String) -> Unit,
    onDisable: (InstalledApp) -> Unit,
    onEnable: (String) -> Unit,
    onTrim: () -> Unit,
    onRefreshApps: () -> Unit,
) {
    var pickerFor by remember { mutableStateOf<String?>(null) }
    val watched = remember(settings.watched) { settings.watched.map { it.packageName }.toSet() }
    val tuneable = remember(apps, watched) {
        apps.filter { TunePlan.isTuneable(it.packageName, OWN_PACKAGE, watched) }
    }
    val drained = remember(settings.drainedApps) { TunePlan.decodeDrain(settings.drainedApps) }
    val disabled = remember(settings.disabledByUs) { TunePlan.decodePackages(settings.disabledByUs) }
    fun labelOf(packageName: String): String =
        apps.firstOrNull { it.packageName == packageName }?.label ?: packageName

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(stringResource(R.string.tune_title), style = MaterialTheme.typography.headlineMedium)
        Text(stringResource(R.string.tune_sub), style = MaterialTheme.typography.bodyMedium, color = Mist)
        Text(stringResource(R.string.tune_note), style = MaterialTheme.typography.bodySmall, color = Ok)

        SectionCard {
            Text(stringResource(R.string.tune_adv_title), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.tune_adv_body), color = Mist, style = MaterialTheme.typography.bodySmall)
            if (!usageAccess) {
                Text(stringResource(R.string.tune_adv_need_usage), color = Mist, style = MaterialTheme.typography.bodySmall)
            } else if (advice.isEmpty()) {
                Text(stringResource(R.string.tune_adv_empty), color = Mist, style = MaterialTheme.typography.bodySmall)
            } else {
                Button(
                    onClick = onApplyAll,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = "batch" !in busy,
                ) {
                    Text(stringResource(R.string.tune_adv_apply_all))
                }
            }
            advice.forEach { item ->
                val app = apps.firstOrNull { it.packageName == item.packageName }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(PanelHigh)
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    AppIcon(item.packageName)
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            app?.label ?: item.packageName,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            stringResource(
                                when (item.reason) {
                                    TuneReason.NEVER_USED_PREINSTALLED -> R.string.tune_reason_bloat
                                    TuneReason.NEVER_USED -> R.string.tune_reason_unused
                                    TuneReason.RARELY_USED -> R.string.tune_reason_rare
                                },
                            ),
                            color = if (item.kind == TuneAdviceKind.DISABLE) Danger else Warning,
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            usageText(item.foregroundMinutes7d),
                            color = Mist,
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                        )
                    }
                    if (app != null) {
                        val busyKey = if (item.kind == TuneAdviceKind.DISABLE) {
                            "bloat:${app.packageName}"
                        } else {
                            "drain:${app.packageName}"
                        }
                        FilledTonalButton(
                            onClick = {
                                if (item.kind == TuneAdviceKind.DISABLE) onDisable(app) else onAddDrain(app)
                            },
                            enabled = busyKey !in busy,
                        ) {
                            Text(
                                stringResource(
                                    if (item.kind == TuneAdviceKind.DISABLE) R.string.tune_adv_disable else R.string.tune_adv_limit,
                                ),
                            )
                        }
                    }
                }
            }
        }

        SectionCard {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.tune_anims_title), style = MaterialTheme.typography.titleMedium)
                    Text(stringResource(R.string.tune_anims_body), color = Mist, style = MaterialTheme.typography.bodySmall)
                }
                AndroidSwitch(
                    checked = settings.fastAnims,
                    enabled = "anims" !in busy,
                    onCheckedChange = onFastAnims,
                )
            }
        }

        SectionCard {
            Text(stringResource(R.string.tune_drain_title), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.tune_drain_body), color = Mist, style = MaterialTheme.typography.bodySmall)
            if (drained.isEmpty()) {
                Text(stringResource(R.string.tune_drain_empty), color = Mist, style = MaterialTheme.typography.bodySmall)
            }
            drained.forEach { app ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(PanelHigh)
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    AppIcon(app.packageName)
                    Column(modifier = Modifier.weight(1f)) {
                        Text(labelOf(app.packageName), fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(app.packageName, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    IconButton(
                        onClick = { onRemoveDrain(app.packageName) },
                        enabled = "undrain:${app.packageName}" !in busy,
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_remove),
                            contentDescription = stringResource(R.string.tune_remove),
                            tint = Danger,
                            modifier = Modifier.size(22.dp),
                        )
                    }
                }
            }
            FilledTonalButton(
                onClick = {
                    pickerFor = "drain"
                    onRefreshApps()
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.tune_drain_add))
            }
        }

        SectionCard {
            Text(stringResource(R.string.tune_bloat_title), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.tune_bloat_body), color = Mist, style = MaterialTheme.typography.bodySmall)
            if (disabled.isEmpty()) {
                Text(stringResource(R.string.tune_bloat_empty), color = Mist, style = MaterialTheme.typography.bodySmall)
            }
            disabled.forEach { packageName ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(PanelHigh)
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    AppIcon(packageName)
                    Column(modifier = Modifier.weight(1f)) {
                        Text(labelOf(packageName), fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(packageName, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    FilledTonalButton(
                        onClick = { onEnable(packageName) },
                        enabled = "unbloat:$packageName" !in busy,
                    ) {
                        Text(stringResource(R.string.tune_bloat_enable))
                    }
                }
            }
            FilledTonalButton(
                onClick = {
                    pickerFor = "bloat"
                    onRefreshApps()
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.tune_bloat_add))
            }
        }

        SectionCard {
            Text(stringResource(R.string.tune_cache_title), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.tune_cache_body), color = Mist, style = MaterialTheme.typography.bodySmall)
            Button(
                onClick = onTrim,
                modifier = Modifier.fillMaxWidth(),
                enabled = "cache" !in busy,
            ) {
                Text(stringResource(R.string.tune_cache_button))
            }
        }
    }

    pickerFor?.let { mode ->
        TunePicker(
            apps = tuneable.filter { app ->
                if (mode == "drain") drained.none { it.packageName == app.packageName }
                else app.packageName !in disabled
            },
            loading = appsLoading,
            onDismiss = { pickerFor = null },
            onSelect = {
                if (mode == "drain") onAddDrain(it) else onDisable(it)
                pickerFor = null
            },
        )
    }
}

@Composable
private fun usageText(minutes7d: Long): String {
    if (minutes7d <= 0L) return stringResource(R.string.tune_adv_never)
    if (minutes7d < 60L) return stringResource(R.string.tune_adv_minutes, minutes7d.toInt())
    return stringResource(
        R.string.tune_adv_hours,
        (minutes7d / 60L).toInt(),
        (minutes7d % 60L).toInt(),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TunePicker(
    apps: List<InstalledApp>,
    loading: Boolean,
    onDismiss: () -> Unit,
    onSelect: (InstalledApp) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var query by remember { mutableStateOf("") }
    val filtered = remember(apps, query) {
        apps.filter { app ->
            query.isBlank() || app.label.contains(query, true) || app.packageName.contains(query, true)
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
            Text(stringResource(R.string.tune_search_title), style = MaterialTheme.typography.titleLarge)
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = MaterialTheme.shapes.medium,
                label = { Text(stringResource(R.string.tune_search)) },
            )
            if (loading) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            if (!loading && filtered.isEmpty()) {
                Text(stringResource(R.string.tune_no_results), color = Mist)
            }
            LazyColumn(
                modifier = Modifier.fillMaxWidth().weight(1f),
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
                            Text(app.label, maxLines = 1, overflow = TextOverflow.Ellipsis)
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
