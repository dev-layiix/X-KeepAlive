package app.xkeepalive.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.xkeepalive.BuildConfig
import app.xkeepalive.R
import app.xkeepalive.data.SessionUi
import app.xkeepalive.data.UserSettings
import app.xkeepalive.i18n.AppLanguages
import app.xkeepalive.ui.KeyValue
import app.xkeepalive.ui.SectionCard
import app.xkeepalive.ui.SettingSwitchRow
import app.xkeepalive.ui.theme.Accent
import app.xkeepalive.ui.theme.Mist

@Composable
fun SettingsScreen(
    settings: UserSettings,
    session: SessionUi,
    onRestore: (Boolean) -> Unit,
    onPoll: (Long) -> Unit,
    onUsage: () -> Unit,
    onOwnBattery: () -> Unit,
    onGamePower: () -> Unit,
    onProtocol: () -> Unit,
    onLanguage: (String) -> Unit,
) {
    var poll by remember(settings.pollIntervalMs) { mutableFloatStateOf(settings.pollIntervalMs.toFloat()) }
    val currentLanguage = AppLanguages.resolveStoredOrSystem(settings.languageCode)
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(stringResource(R.string.settings_title), style = MaterialTheme.typography.headlineMedium)
        SectionCard {
            Text(stringResource(R.string.settings_language), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.settings_language_sub), color = Mist, style = MaterialTheme.typography.bodySmall)
            AppLanguages.supported.forEach { language ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(
                        selected = currentLanguage == language.code,
                        onClick = { onLanguage(language.code) },
                    )
                    Text(
                        "${language.flag}  ${language.nativeName}",
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }
        SectionCard {
            Text(stringResource(R.string.settings_permissions), style = MaterialTheme.typography.titleMedium)
            KeyValue(
                stringResource(R.string.settings_usage),
                if (session.permissions.usageAccess) stringResource(R.string.settings_granted) else stringResource(R.string.settings_missing),
            )
            KeyValue(
                stringResource(R.string.settings_notifications),
                if (session.permissions.notifications) stringResource(R.string.settings_granted) else stringResource(R.string.settings_missing),
            )
            KeyValue(
                stringResource(R.string.settings_own_battery),
                if (session.permissions.ownBatteryUnrestricted) {
                    stringResource(R.string.settings_unrestricted)
                } else {
                    stringResource(R.string.settings_optimized)
                },
            )
            Button(onClick = onUsage, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.settings_usage)) }
            FilledTonalButton(onClick = onOwnBattery, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.settings_own_battery)) }
            FilledTonalButton(onClick = onGamePower, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.settings_game_battery)) }
        }
        SectionCard {
            SettingSwitchRow(
                title = stringResource(R.string.settings_reboot_title),
                body = stringResource(R.string.settings_reboot_body),
                checked = settings.restoreOnBoot,
                onCheckedChange = onRestore,
            )
        }
        SectionCard {
            Text(
                stringResource(R.string.settings_interval, poll / 1000f),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(stringResource(R.string.settings_interval_note), color = Mist, style = MaterialTheme.typography.bodySmall)
            Slider(
                value = poll,
                onValueChange = { poll = it },
                onValueChangeFinished = { onPoll(poll.toLong()) },
                valueRange = 1500f..5000f,
                steps = 6,
            )
        }
        Button(onClick = onProtocol, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.settings_verify))
        }
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                stringResource(R.string.settings_developed),
                style = MaterialTheme.typography.titleMedium,
                color = Accent,
                fontWeight = FontWeight.Bold,
            )
            Text(
                stringResource(R.string.settings_version, BuildConfig.VERSION_NAME),
                color = Mist,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}
