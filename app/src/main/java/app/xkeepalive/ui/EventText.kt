package app.xkeepalive.ui

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val clock: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM HH:mm:ss")

fun formatEventTime(timeMs: Long): String =
    Instant.ofEpochMilli(timeMs).atZone(ZoneId.systemDefault()).format(clock)
