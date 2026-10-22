package app.xkeepalive.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import app.xkeepalive.MainActivity
import app.xkeepalive.R
import app.xkeepalive.XBackgroundApp
import app.xkeepalive.core.AutomationEngine
import app.xkeepalive.core.Blocker
import app.xkeepalive.core.EngineSnapshot
import app.xkeepalive.core.MonitorControl
import app.xkeepalive.core.PackageNames
import app.xkeepalive.core.Phase
import app.xkeepalive.core.PolicyReport
import app.xkeepalive.core.ProcessOutcome
import app.xkeepalive.core.Sample
import app.xkeepalive.data.SessionUi
import app.xkeepalive.data.policyFor
import app.xkeepalive.i18n.AppLanguages
import app.xkeepalive.i18n.LanguageStore
import app.xkeepalive.ui.phaseLabelRes
import app.xkeepalive.widget.WidgetUpdater
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.coroutineContext

class MonitorService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val engine = AutomationEngine()
    private val shuttingDown = AtomicBoolean(false)
    private var loopJob: Job? = null
    private var previous = EngineSnapshot.inactive()
    private var policyVerified = false
    private var shizukuWasReady = false
    private var shizukuLostSince = 0L
    private var nextVerifyAt = 0L
    private var appliedPackage = ""
    private val appliedPackages = mutableSetOf<String>()
    private val retryNotBefore = mutableMapOf<String, Long>()
    private val restoreNotBefore = mutableMapOf<String, Long>()
    private val resumeRequested = AtomicBoolean(false)
    private val loopAlive = AtomicBoolean(false)
    private val shutdownRunning = AtomicBoolean(false)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        createChannel()
        if (intent?.action == ACTION_STOP) {
            val stopRun = intent.getIntExtra(EXTRA_RUN, runId.get())
            scope.launch { shutdown(stopRun) }
            return START_NOT_STICKY
        }
        running.set(true)
        if (loopJob?.isActive == true && !shuttingDown.get()) return START_STICKY
        if (shuttingDown.get() && shutdownRunning.get()) return START_STICKY
        shuttingDown.set(false)
        startInForeground("X-KeepAlive", getString(R.string.notif_starting))
        WidgetUpdater.refresh(this)
        if (loopJob?.isActive != true) {
            loopJob = scope.launch { loop() }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        running.set(false)
        scope.cancel()
        super.onDestroy()
    }

    private suspend fun loop() {
        loopAlive.set(true)
        try {
            val container = container()
            container.detector.reset()
            engine.reset()
            previous = EngineSnapshot.inactive()
            running.set(true)
            container.events.append("SERVICE_STARTED", getString(R.string.log_monitoring_started))
            container.session.update { it.copy(serviceRunning = true) }
            while (coroutineContext.isActive) {
                if (pauseForShutdown()) break
                try {
                    tick()
                } catch (cancelled: kotlinx.coroutines.CancellationException) {
                    throw cancelled
                } catch (throwable: Throwable) {
                    container.events.append("ERROR", throwable.message ?: throwable.javaClass.simpleName)
                }
                if (pauseForShutdown()) break
                val interval = container.settings.snapshot().pollIntervalMs
                val interactive = getSystemService(PowerManager::class.java)?.isInteractive != false
                delay(if (interactive) interval else maxOf(interval, SCREEN_OFF_INTERVAL_MS))
            }
        } finally {
            loopAlive.set(false)
            if (resumeRequested.compareAndSet(true, false) && coroutineContext.isActive) {
                shuttingDown.set(false)
                loopJob = scope.launch { loop() }
            }
        }
    }

    private fun pauseForShutdown(): Boolean {
        if (!shuttingDown.get()) {
            resumeRequested.set(false)
            return false
        }
        if (resumeRequested.compareAndSet(true, false)) {
            shuttingDown.set(false)
            return false
        }
        return true
    }

    private suspend fun tick() {
        if (shuttingDown.get()) return
        val container = container()
        val settings = container.settings.snapshot()
        if (!settings.automationEnabled) {
            shutdown(runId.get())
            return
        }
        val permissions = container.permissions.snapshot()
        val shizuku = container.shizuku.snapshot()
        val readyBefore = shizukuWasReady
        val enabled = settings.watched.filter { it.enabled && PackageNames.isValid(it.packageName) }
        val enabledPackages = enabled.map { it.packageName }.toSet()
        val foregroundNow = if (permissions.usageAccess) container.detector.poll() else null
        val focusGame = enabled.firstOrNull { it.packageName == foregroundNow?.packageName }
            ?: enabled.firstOrNull { it.packageName == settings.selectedPackage }
            ?: enabled.firstOrNull()
        val selected = focusGame?.packageName.orEmpty()
        if (focusGame != null && focusGame.packageName != settings.selectedPackage) {
            container.settings.setSelection(focusGame.packageName, focusGame.label)
        }
        if (!shizuku.ready) {
            // A transient Shizuku restart (e.g. its app updating) used to
            // disable automation permanently. When Shizuku was working, wait
            // 60s for it to come back before tearing anything down.
            if (readyBefore) {
                val now = System.currentTimeMillis()
                if (shizukuLostSince == 0L) {
                    shizukuLostSince = now
                    container.events.append("SHIZUKU", getString(R.string.log_shizuku_stopped))
                }
                if (now - shizukuLostSince < SHIZUKU_GRACE_MS) return
            } else {
                container.events.append("SHIZUKU", getString(R.string.log_service_idle))
            }
            shizukuLostSince = 0L
            val stopRun = runId.get()
            policyVerified = false
            appliedPackage = ""
            appliedPackages.clear()
            retryNotBefore.clear()
            restoreNotBefore.clear()
            shizukuWasReady = false
            if (runId.get() == stopRun) {
                container.settings.setAutomation(false)
            }
            shutdown(stopRun)
            return
        }
        shizukuWasReady = true
        shizukuLostSince = 0L

        if (enabled.isNotEmpty()) {
            val now = System.currentTimeMillis()
            val pendingRestore = container.settings.snapshot().policies
                .map { it.packageName }
                .filter { PackageNames.isValid(it) && it !in enabledPackages }
            pendingRestore.forEach { packageName ->
                // A failing restore used to retry every tick (each 2s) and
                // spam the log. Back it off like applies: retry every 30s.
                val restoreDue = restoreNotBefore[packageName] ?: 0L
                if (now < restoreDue) return@forEach
                val report = container.policy.revert(packageName)
                container.events.append("POLICY", describe(report))
                appliedPackages.remove(packageName)
                retryNotBefore.remove(packageName)
                if (appliedPackage == packageName) appliedPackage = ""
                if (report.mode == "REVERT" && report.anyVerified) {
                    restoreNotBefore.remove(packageName)
                } else {
                    restoreNotBefore[packageName] = now + POLICY_RETRY_MS
                }
            }
            retryNotBefore.keys.retainAll(enabledPackages)
            val refreshed = container.settings.snapshot()
            enabled.forEach { game ->
                if (game.packageName in appliedPackages) return@forEach
                val due = retryNotBefore[game.packageName] ?: 0L
                if (now < due) return@forEach
                val report = container.policy.apply(game.packageName)
                if (report.anyVerified && report.mode == "SHIZUKU") {
                    appliedPackages += game.packageName
                    retryNotBefore.remove(game.packageName)
                } else {
                    retryNotBefore[game.packageName] = now + POLICY_RETRY_MS
                }
                if (game.packageName == selected) {
                    appliedPackage = if (game.packageName in appliedPackages) selected else ""
                    container.session.update { it.copy(policy = report) }
                }
                container.events.append("POLICY", describe(report))
            }
            if (now >= nextVerifyAt) {
                enabled.forEach { game ->
                    if (game.packageName !in appliedPackages) return@forEach
                    val changed = refreshed.policyFor(game.packageName)?.standbyChanged == true
                    val still = container.policy.verify(game.packageName, changed)
                    if (!still) {
                        appliedPackages.remove(game.packageName)
                        retryNotBefore.remove(game.packageName)
                        container.events.append("POLICY", getString(R.string.log_policy_unverified, game.packageName))
                    }
                }
                nextVerifyAt = now + VERIFY_MS
            }
            policyVerified = selected.isNotEmpty() && selected in appliedPackages
            if (!policyVerified) appliedPackage = ""
        } else {
            policyVerified = false
            appliedPackage = ""
        }
        if (shuttingDown.get()) return

        val foreground = foregroundNow
        val pids = if (shizuku.ready && PackageNames.isValid(selected)) container.policy.readPids(selected) else null
        val snapshot = engine.onSample(
            Sample(
                automationEnabled = true,
                permissionsReady = permissions.monitoringReady,
                selectedPackage = selected,
                foregroundPackage = foreground?.packageName,
                foregroundKnown = foreground?.known == true,
                policyVerified = policyVerified,
                selectedPids = pids,
                pidsChecked = pids != null,
            ),
        )
        container.session.update {
            it.copy(
                phase = snapshot.phase,
                blocker = snapshot.blocker,
                outcome = snapshot.outcome,
                limitNote = snapshot.limitNote,
                foregroundPackage = snapshot.foregroundPackage,
                policyVerified = snapshot.policyVerified,
                backgroundPids = snapshot.backgroundPids,
                observedPids = snapshot.observedPids,
                pidChange = snapshot.pidChange,
                shizuku = shizuku,
                permissions = permissions,
                serviceRunning = true,
                managedPackages = appliedPackages.toSet(),
            )
        }
        logTransitions(snapshot)
        if (snapshot.phase != previous.phase || snapshot.blocker != previous.blocker || shizuku.ready != readyBefore) {
            val title = headline(snapshot)
            val body = settings.selectedLabel.ifBlank { selected.ifBlank { getString(R.string.notif_no_game) } }
            withContext(Dispatchers.Main) { startInForeground(title, body) }
        }
        previous = snapshot
    }

    private suspend fun logTransitions(snapshot: EngineSnapshot) {
        val events = container().events
        if (snapshot.phase != previous.phase || snapshot.blocker != previous.blocker) {
            events.append("PHASE", headline(snapshot))
        }
        if (snapshot.outcome != previous.outcome && snapshot.outcome != ProcessOutcome.NOT_CHECKED) {
            events.append("PROCESS", "${snapshot.outcome} pid=${snapshot.observedPids.sorted().joinToString(",")}")
        }
        if (
            snapshot.observedPids != previous.observedPids &&
            (snapshot.observedPids.isNotEmpty() || previous.observedPids.isNotEmpty())
        ) {
            events.append(
                "PID",
                snapshot.pidChange
                    ?: "Current PID: ${if (snapshot.observedPids.isEmpty()) "none" else snapshot.observedPids.sorted().joinToString(", ")}",
            )
        }
        val note = snapshot.limitNote
        if (note != null && note != previous.limitNote) {
            events.append("LIMIT", note)
        }
    }

    private suspend fun shutdown(stopRun: Int) {
        if (runId.get() != stopRun) return
        if (!shuttingDown.compareAndSet(false, true)) return
        shutdownRunning.set(true)
        try {
            val container = container()
            if (keepRunning(stopRun)) {
                keepAlive()
                return
            }
            val pending = container.settings.snapshot().policies.map { it.packageName }.filter { PackageNames.isValid(it) }
            pending.forEach { packageName ->
                val report = container.policy.revert(packageName)
                container.events.append("POLICY", describe(report))
            }
            appliedPackages.clear()
            retryNotBefore.clear()
            restoreNotBefore.clear()
            if (keepRunning(stopRun)) {
                keepAlive()
                return
            }
            container.settings.setAutomation(false)
            if (keepRunning(stopRun)) {
                container.settings.setAutomation(true)
                keepAlive()
                return
            }
            container.shizuku.unbind()
            container.session.update {
                SessionUi(
                    shizuku = container.shizuku.snapshot(),
                    permissions = container.permissions.snapshot(),
                    serviceRunning = false,
                )
            }
            container.events.append("SERVICE_STOPPED", getString(R.string.log_monitoring_stopped))
            running.set(false)
            WidgetUpdater.refresh(this)
            withContext(Dispatchers.Main) {
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        } finally {
            shutdownRunning.set(false)
        }
    }

    private suspend fun keepRunning(stopRun: Int): Boolean {
        val settings = container().settings.snapshot()
        val enabled = settings.watched.count { game -> game.enabled && PackageNames.isValid(game.packageName) }
        return MonitorControl.keepService(stopRun, runId.get(), settings.automationEnabled, enabled)
    }

    private suspend fun keepAlive() {
        running.set(true)
        container().session.update { it.copy(serviceRunning = true, managedPackages = appliedPackages.toSet()) }
        if (loopAlive.get()) {
            resumeRequested.set(false)
            shuttingDown.set(false)
            return
        }
        resumeRequested.set(true)
        shuttingDown.set(false)
        if (resumeRequested.compareAndSet(true, false)) {
            loopJob = scope.launch { loop() }
        }
    }

    private fun container() = (application as XBackgroundApp).container

    private fun headline(snapshot: EngineSnapshot): String = when (snapshot.blocker) {
        Blocker.PERMISSIONS -> getString(R.string.blocker_permissions)
        Blocker.NO_GAME_SELECTED -> getString(R.string.blocker_no_game)
        Blocker.NONE -> getString(phaseLabelRes(snapshot.phase))
    }

    private fun describe(report: PolicyReport): String {
        val header = "${report.mode} verified=${report.anyVerified}"
        val lines = report.operations.joinToString(" · ") { op ->
            "${op.name}:${if (op.verified) "ok" else "no"} ${op.detail}"
        }
        return "$header $lines".take(900)
    }

    private fun channelId(): String {
        val code = AppLanguages.resolveStoredOrSystem(LanguageStore.read(this))
        return "${BASE_CHANNEL_ID}_$code"
    }

    private fun createChannel() {
        val manager = getSystemService(NotificationManager::class.java)
        // Drops channels created under a previous language so the channel
        // name always matches the app language. The legacy id predates this.
        (legacyChannelIds() + AppLanguages.supported.map { "${BASE_CHANNEL_ID}_${it.code}" })
            .filter { it != channelId() }
            .forEach { runCatching { manager.deleteNotificationChannel(it) } }
        val channel = NotificationChannel(
            channelId(),
            getString(R.string.notification_channel),
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = getString(R.string.notification_channel_description)
            setShowBadge(false)
        }
        manager.createNotificationChannel(channel)
    }

    private fun startInForeground(title: String, text: String) {
        val notification = notification(title, text)
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun notification(title: String, text: String): Notification {
        val open = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val stop = PendingIntent.getService(
            this,
            1,
            Intent(this, MonitorService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, channelId())
            .setSmallIcon(R.drawable.ic_stat_x)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(open)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .addAction(0, getString(R.string.notification_stop), stop)
            .build()
    }

    companion object {
        private const val BASE_CHANNEL_ID = "xkeepalive_monitor"
        private const val CHANNEL_ID = "xkeepalive_monitor"

        private fun legacyChannelIds(): List<String> = listOf(CHANNEL_ID)
        private const val NOTIFICATION_ID = 42
        private const val ACTION_STOP = "app.xkeepalive.STOP"
        private const val EXTRA_RUN = "app.xkeepalive.RUN"
        private const val POLICY_RETRY_MS = 30_000L
        private const val SHIZUKU_GRACE_MS = 60_000L
        private const val VERIFY_MS = 60_000L
        private const val SCREEN_OFF_INTERVAL_MS = 8_000L

        private val runId = AtomicInteger(0)
        val running = AtomicBoolean(false)

        fun start(context: Context) {
            val intent = Intent(context, MonitorService::class.java).putExtra(EXTRA_RUN, runId.incrementAndGet())
            ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: Context) {
            val intent = Intent(context, MonitorService::class.java)
                .setAction(ACTION_STOP)
                .putExtra(EXTRA_RUN, runId.incrementAndGet())
            context.startService(intent)
        }
    }
}
