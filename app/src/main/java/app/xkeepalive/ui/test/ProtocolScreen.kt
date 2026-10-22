package app.xkeepalive.ui.test

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import app.xkeepalive.data.EventEntry
import app.xkeepalive.ui.AppFilterChip
import app.xkeepalive.ui.SectionCard
import app.xkeepalive.ui.formatEventTime
import app.xkeepalive.ui.theme.Mist
import app.xkeepalive.viewmodel.TestRecord

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ProtocolScreen(
    tests: List<EventEntry>,
    onSave: (TestRecord) -> Unit,
) {
    val wait1 = stringResource(R.string.verify_wait_1)
    val wait5 = stringResource(R.string.verify_wait_5)
    val wait10 = stringResource(R.string.verify_wait_10)
    val fgHome = stringResource(R.string.verify_fg_home)
    val fgOther = stringResource(R.string.verify_fg_other)
    val procMem = stringResource(R.string.verify_proc_mem)
    val procKilled = stringResource(R.string.verify_proc_killed)
    val procUnknown = stringResource(R.string.verify_proc_unknown)
    val svcOn = stringResource(R.string.verify_svc_on)
    val svcOff = stringResource(R.string.verify_svc_off)
    val shOk = stringResource(R.string.verify_sh_ok)
    val shOff = stringResource(R.string.verify_sh_off)
    val shUnused = stringResource(R.string.verify_sh_unused)
    var wait by remember(wait1) { mutableStateOf(wait1) }
    var foreground by remember(fgHome) { mutableStateOf(fgHome) }
    var process by remember(procUnknown) { mutableStateOf(procUnknown) }
    var service by remember(svcOn) { mutableStateOf(svcOn) }
    var shizuku by remember(shUnused) { mutableStateOf(shUnused) }
    var notes by remember { mutableStateOf("") }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(stringResource(R.string.verify_title), style = MaterialTheme.typography.headlineMedium)
        Text(
            stringResource(R.string.verify_subtitle),
            color = Mist,
        )
        SectionCard {
            Text(stringResource(R.string.verify_sequence), style = MaterialTheme.typography.titleMedium)
            Text(
                stringResource(R.string.verify_sequence_body),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        Choice(stringResource(R.string.verify_wait), listOf(wait1, wait5, wait10), wait) { wait = it }
        Choice(
            stringResource(R.string.verify_fg),
            listOf(fgHome, "WhatsApp", "Chrome", fgOther),
            foreground,
        ) { foreground = it }
        Choice(
            stringResource(R.string.verify_process),
            listOf(procMem, procKilled, procUnknown),
            process,
        ) { process = it }
        Choice(
            stringResource(R.string.verify_svc),
            listOf(svcOn, svcOff),
            service,
        ) { service = it }
        Choice(
            stringResource(R.string.verify_shizuku),
            listOf(shOk, shOff, shUnused),
            shizuku,
        ) { shizuku = it }
        OutlinedTextField(
            value = notes,
            onValueChange = { notes = it.take(300) },
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.medium,
            label = { Text(stringResource(R.string.verify_notes)) },
            minLines = 2,
        )
        Button(
            onClick = {
                onSave(TestRecord(wait, foreground, process, service, shizuku, notes.trim()))
                notes = ""
            },
            modifier = Modifier.fillMaxWidth(),
        ) { Text(stringResource(R.string.verify_save)) }
        if (tests.isNotEmpty()) {
            Text(stringResource(R.string.verify_saved), style = MaterialTheme.typography.titleMedium)
            tests.asReversed().take(20).forEach { entry ->
                SectionCard {
                    Text(formatEventTime(entry.timeMs), style = MaterialTheme.typography.bodySmall)
                    Text(entry.message, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Choice(title: String, values: List<String>, selected: String, onSelect: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            values.forEach { value ->
                AppFilterChip(label = value, selected = selected == value, onClick = { onSelect(value) })
            }
        }
    }
}
