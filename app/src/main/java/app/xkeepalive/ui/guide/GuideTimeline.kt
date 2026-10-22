package app.xkeepalive.ui.guide

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.xkeepalive.R
import app.xkeepalive.ui.SectionCard
import app.xkeepalive.ui.theme.Accent
import app.xkeepalive.ui.theme.Ink
import app.xkeepalive.ui.theme.Line
import app.xkeepalive.ui.theme.Mist
import app.xkeepalive.ui.theme.Ok
import app.xkeepalive.ui.theme.PanelHigh

enum class StepState { Done, Current, Locked }

@Composable
fun SetupProgressCard(doneCount: Int, total: Int) {
    val ready = total > 0 && doneCount >= total
    SectionCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(Ok.copy(alpha = 0.18f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.Send,
                    contentDescription = null,
                    tint = Ok,
                    modifier = Modifier.size(28.dp),
                )
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    if (ready) stringResource(R.string.header_ready_title) else stringResource(R.string.header_progress_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    stringResource(R.string.header_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = Mist,
                )
            }
            ProgressRing(doneCount = doneCount, total = total)
        }
    }
}

@Composable
fun ProgressRing(doneCount: Int, total: Int, modifier: Modifier = Modifier) {
    val fraction = if (total <= 0) 0f else (doneCount.toFloat() / total.toFloat()).coerceIn(0f, 1f)
    val ready = total > 0 && doneCount >= total
    val ring = if (ready) Ok else Accent
    Box(modifier = modifier.size(72.dp), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawArc(
                color = Mist.copy(alpha = 0.25f),
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                style = Stroke(width = 7.dp.toPx(), cap = StrokeCap.Round),
            )
            if (fraction > 0f) {
                drawArc(
                    color = ring,
                    startAngle = -90f,
                    sweepAngle = 360f * fraction,
                    useCenter = false,
                    style = Stroke(width = 7.dp.toPx(), cap = StrokeCap.Round),
                )
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                "$doneCount/$total",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
            )
            Text(
                "${(fraction * 100).toInt()}%",
                style = MaterialTheme.typography.bodySmall,
                color = Ok,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}

@Composable
fun TimelineStepRow(
    number: Int,
    step: GuideStep,
    state: StepState,
    isLast: Boolean,
    expanded: Boolean,
    onToggle: () -> Unit,
    onAction: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Max),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxHeight(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            TimelineNode(number = number, state = state)
            if (!isLast) {
                Box(
                    modifier = Modifier
                        .width(2.dp)
                        .weight(1f)
                        .background(if (state == StepState.Done) Ok.copy(alpha = 0.5f) else Line),
                )
            }
        }
        SectionCard(
            modifier = if (state == StepState.Current) {
                Modifier
                    .weight(1f)
                    .border(1.dp, Ok, RoundedCornerShape(28.dp))
            } else {
                Modifier.weight(1f)
            },
        ) {
            StepHeader(
                step = step,
                state = state,
                expanded = expanded,
                onToggle = onToggle,
            )
            if (expanded) {
                Text(step.body, style = MaterialTheme.typography.bodyMedium, color = Mist)
                if (state == StepState.Current) {
                    StepActionCard(step = step, onAction = onAction)
                } else if (state == StepState.Locked) {
                    Text(
                        stringResource(R.string.step_locked_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = Mist,
                    )
                }
                if (step.details.isNotEmpty()) {
                    var showDetails by remember { mutableStateOf(state == StepState.Current) }
                    TextButton(onClick = { showDetails = !showDetails }) {
                        Text(
                            if (showDetails) {
                                stringResource(R.string.step_hide_details)
                            } else {
                                stringResource(R.string.step_show_details)
                            },
                        )
                    }
                    if (showDetails) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .background(PanelHigh)
                                .padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            step.details.forEach { line ->
                                Text(line, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
                if (step.tip.isNotEmpty()) {
                    TipRow(tip = step.tip)
                }
            }
        }
    }
}

@Composable
private fun TimelineNode(number: Int, state: StepState) {
    when (state) {
        StepState.Done -> {
            Box(
                modifier = Modifier
                    .size(30.dp)
                    .clip(CircleShape)
                    .background(Ok),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.Check,
                    contentDescription = null,
                    tint = Ink,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
        StepState.Current -> {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(Ok.copy(alpha = 0.18f))
                    .border(2.dp, Ok, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    number.toString(),
                    color = Ok,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
        StepState.Locked -> {
            Box(
                modifier = Modifier
                    .size(30.dp)
                    .clip(CircleShape)
                    .background(PanelHigh)
                    .border(1.dp, Line, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    number.toString(),
                    color = Mist,
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        }
    }
}

@Composable
private fun StepHeader(
    step: GuideStep,
    state: StepState,
    expanded: Boolean,
    onToggle: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onToggle),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(PanelHigh),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = step.icon,
                contentDescription = null,
                tint = if (state == StepState.Locked) Mist else Accent,
                modifier = Modifier.size(24.dp),
            )
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text(step.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Text(step.subtitle, style = MaterialTheme.typography.bodySmall, color = Mist)
        }
        Icon(
            imageVector = when {
                expanded -> Icons.Filled.KeyboardArrowUp
                state == StepState.Locked -> Icons.Filled.KeyboardArrowRight
                else -> Icons.Filled.KeyboardArrowDown
            },
            contentDescription = if (expanded) "Collapse" else "Expand",
            tint = Mist,
        )
    }
}

@Composable
private fun StepActionCard(step: GuideStep, onAction: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(PanelHigh)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(step.button, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Text(step.subtitle, style = MaterialTheme.typography.bodySmall, color = Mist)
        }
        Button(
            onClick = onAction,
            colors = ButtonDefaults.buttonColors(containerColor = Ok, contentColor = Ink),
            shape = RoundedCornerShape(50),
        ) {
            Text(step.button, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun TipRow(tip: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            imageVector = Icons.Filled.Info,
            contentDescription = null,
            tint = Ok,
            modifier = Modifier.size(20.dp),
        )
        Text(
            stringResource(R.string.tip_label),
            color = Ok,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
        )
        Text(tip, style = MaterialTheme.typography.bodySmall, color = Mist, modifier = Modifier.weight(1f))
    }
}

@Composable
fun GuideInfoGrid() {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            InfoCard(
                icon = Icons.Filled.Refresh,
                title = stringResource(R.string.info_reboot_title),
                body = stringResource(R.string.info_reboot_body),
                modifier = Modifier.weight(1f),
            )
            InfoCard(
                icon = Icons.Filled.Star,
                title = stringResource(R.string.info_battery_title),
                body = stringResource(R.string.info_battery_body),
                modifier = Modifier.weight(1f),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            InfoCard(
                icon = Icons.Filled.Warning,
                title = stringResource(R.string.info_cannot_title),
                body = stringResource(R.string.info_cannot_body),
                modifier = Modifier.weight(1f),
            )
            InfoCard(
                icon = Icons.Filled.Lock,
                title = stringResource(R.string.info_privacy_title),
                body = stringResource(R.string.info_privacy_body),
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun InfoCard(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, body: String, modifier: Modifier = Modifier) {
    SectionCard(modifier = modifier) {
        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(CircleShape)
                .background(Ok.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(imageVector = icon, contentDescription = null, tint = Ok, modifier = Modifier.size(24.dp))
        }
        Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        Text(body, style = MaterialTheme.typography.bodySmall, color = Mist)
    }
}

@Composable
fun GoodToKnowCard() {
    var expanded by remember { mutableStateOf(false) }
    SectionCard(
        modifier = Modifier.border(1.dp, Ok.copy(alpha = 0.6f), RoundedCornerShape(28.dp)),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .clickable { expanded = !expanded },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Ok.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(imageVector = Icons.Filled.Info, contentDescription = null, tint = Ok)
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.gtk_title), color = Ok, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Text(stringResource(R.string.gtk_subtitle), style = MaterialTheme.typography.bodySmall, color = Mist)
            }
            Icon(
                imageVector = if (expanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = Ok,
            )
        }
        if (expanded) {
            Text(
                stringResource(R.string.gtk_dev),
                style = MaterialTheme.typography.bodySmall,
                color = Mist,
            )
            Text(
                stringResource(R.string.gtk_pair),
                style = MaterialTheme.typography.bodySmall,
                color = Mist,
            )
            Text(
                stringResource(R.string.gtk_deny),
                style = MaterialTheme.typography.bodySmall,
                color = Mist,
            )
        }
    }
}
