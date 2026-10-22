package app.xkeepalive.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import app.xkeepalive.core.DrainedApp
import app.xkeepalive.core.PackageNames
import app.xkeepalive.core.PolicyPlan
import app.xkeepalive.core.PolicyRecords
import app.xkeepalive.core.StoredPolicy
import app.xkeepalive.core.TunePlan
import app.xkeepalive.core.WatchedGame
import app.xkeepalive.core.WatchedGames
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore by preferencesDataStore(name = "xkeepalive_settings")

data class UserSettings(
    val automationEnabled: Boolean = false,
    val restoreOnBoot: Boolean = true,
    val setupCompleted: Boolean = false,
    val languageCode: String = "",
    val gameBatteryHintDone: Boolean = false,    val selectedPackage: String = "",
    val selectedLabel: String = "",
    val watched: List<WatchedGame> = emptyList(),
    val policies: List<StoredPolicy> = emptyList(),
    val pollIntervalMs: Long = 2_000L,
    val fastAnims: Boolean = false,
    val savedAnimScales: String = "",
    val drainedApps: String = "",
    val disabledByUs: String = "",
    val consentAccepted: Boolean = false,
    val baselinePackage: String = "",
    val savedStandby: String = "",
    val savedInactive: String = "",
    val standbyChanged: Boolean = false,
    val policyPackage: String = "",
    val whitelistAlreadyPresent: Boolean = false,
    val whitelistAddedByUs: Boolean = false,
    val inactiveChanged: Boolean = false,
    val savedAppOps: String = "",
    val changedAppOps: String = "",
    val policyGeneration: Int = 0,
)

fun UserSettings.policyFor(packageName: String): StoredPolicy? {
    policies.firstOrNull { it.packageName == packageName }?.let { return it }
    if (policyPackage == packageName || baselinePackage == packageName) {
        return toStoredPolicy().copy(packageName = packageName)
    }
    return null
}

fun UserSettings.toStoredPolicy(): StoredPolicy = StoredPolicy(
    packageName = baselinePackage.ifBlank { policyPackage },
    generation = policyGeneration,
    whitelistAlreadyPresent = whitelistAlreadyPresent,
    whitelistAddedByUs = whitelistAddedByUs,
    savedStandby = savedStandby,
    standbyChanged = standbyChanged,
    savedInactive = savedInactive,
    inactiveChanged = inactiveChanged,
    savedAppOps = PolicyPlan.decodeAppOps(savedAppOps),
    changedAppOps = PolicyPlan.decodeChangedOps(changedAppOps),
)

class SettingsRepository(context: Context) {
    // NOTE: intentionally the passed context, not applicationContext: this is
    // also constructed with the raw base context inside attachBaseContext,
    // where applicationContext is not usable yet. The DataStore delegate is a
    // process-wide singleton, so every context reads the same file.
    private val store = context.settingsDataStore

    val flow: Flow<UserSettings> = store.data.map { it.toSettings() }

    suspend fun snapshot(): UserSettings = store.data.first().toSettings()

    suspend fun setAutomation(enabled: Boolean) = edit { it[Keys.automation] = enabled }

    suspend fun setRestoreOnBoot(enabled: Boolean) = edit { it[Keys.restoreOnBoot] = enabled }

    suspend fun setSetupCompleted(completed: Boolean = true) = edit { it[Keys.setupCompleted] = completed }

    suspend fun setLanguageCode(code: String) = edit { it[Keys.languageCode] = code }

    suspend fun setGameBatteryHintDone(done: Boolean = true) = edit { it[Keys.gameBatteryHintDone] = done }

    suspend fun setSelection(packageName: String, label: String) = edit {
        it[Keys.packageName] = packageName
        it[Keys.label] = label
    }

    suspend fun addWatched(packageName: String, label: String): Boolean {
        var added = false
        edit {
            val next = WatchedGames.add(readWatched(it), WatchedGame(packageName, label, false))
            added = next.size > readWatched(it).size
            it[Keys.watched] = WatchedGames.encode(next)
        }
        return added
    }

    suspend fun setWatchedEnabled(packageName: String, enabled: Boolean) = edit {
        val next = WatchedGames.setEnabled(readWatched(it), packageName, enabled)
        it[Keys.watched] = WatchedGames.encode(next)
        val selected = it[Keys.packageName].orEmpty()
        val selectedStillOn = next.any { game -> game.packageName == selected && game.enabled }
        if (!selectedStillOn) {
            val replacement = next.firstOrNull { game -> game.enabled }
            if (replacement != null) {
                it[Keys.packageName] = replacement.packageName
                it[Keys.label] = replacement.label
            }
        }
    }

    suspend fun removeWatched(packageName: String) = edit {
        it[Keys.watched] = WatchedGames.encode(WatchedGames.remove(readWatched(it), packageName))
    }

    suspend fun setPollInterval(intervalMs: Long) = edit {
        it[Keys.poll] = intervalMs.coerceIn(1_500L, 5_000L)
    }

    suspend fun setFastAnims(enabled: Boolean) = edit { it[Keys.fastAnims] = enabled }

    suspend fun saveAnimScales(encoded: String) = edit { it[Keys.animScales] = encoded }

    suspend fun clearAnimScales() = edit { it.remove(Keys.animScales) }

    suspend fun saveDrained(app: DrainedApp) = edit {
        val current = TunePlan.decodeDrain(it[Keys.drained].orEmpty())
            .filter { existing -> existing.packageName != app.packageName } + app
        it[Keys.drained] = TunePlan.encodeDrain(current)
    }

    suspend fun removeDrained(packageName: String) = edit {
        val current = TunePlan.decodeDrain(it[Keys.drained].orEmpty())
            .filter { existing -> existing.packageName != packageName }
        if (current.isEmpty()) it.remove(Keys.drained) else {
            it[Keys.drained] = TunePlan.encodeDrain(current)
        }
    }

    suspend fun addDisabled(packageName: String) = edit {
        val current = TunePlan.decodePackages(it[Keys.disabled].orEmpty()) + packageName
        it[Keys.disabled] = TunePlan.encodePackages(current)
    }

    suspend fun removeDisabled(packageName: String) = edit {
        val current = TunePlan.decodePackages(it[Keys.disabled].orEmpty()) - packageName
        if (current.isEmpty()) it.remove(Keys.disabled) else {
            it[Keys.disabled] = TunePlan.encodePackages(current)
        }
    }

    suspend fun setConsentAccepted() = edit { it[Keys.consent] = true }

    suspend fun saveStoredPolicy(policy: StoredPolicy) = edit {
        val next = PolicyRecords.upsert(readPolicies(it), policy)
        it[Keys.policyBundle] = PolicyRecords.encode(next)
        writeLegacyPolicy(it, policy)
    }

    suspend fun clearPolicy(packageName: String) = edit {
        val next = PolicyRecords.remove(readPolicies(it), packageName)
        if (next.isEmpty()) {
            it.remove(Keys.policyBundle)
        } else {
            it[Keys.policyBundle] = PolicyRecords.encode(next)
        }
        if (next.isEmpty() || it[Keys.policyPackage].orEmpty() == packageName) {
            clearLegacyPolicy(it)
            next.firstOrNull()?.let { remaining -> writeLegacyPolicy(it, remaining) }
        }
    }

    private suspend fun edit(block: suspend (androidx.datastore.preferences.core.MutablePreferences) -> Unit) {
        store.edit { block(it) }
    }

    private fun readWatched(preferences: Preferences): List<WatchedGame> {
        val raw = preferences[Keys.watched]
        if (raw != null) return WatchedGames.decode(raw)
        val packageName = preferences[Keys.packageName].orEmpty()
        if (!PackageNames.isValid(packageName)) return emptyList()
        return listOf(
            WatchedGame(
                packageName = packageName,
                label = preferences[Keys.label].orEmpty().ifBlank { packageName },
                enabled = preferences[Keys.automation] ?: false,
            ),
        )
    }

    private fun readPolicies(preferences: Preferences): List<StoredPolicy> {
        val stored = PolicyRecords.decode(preferences[Keys.policyBundle].orEmpty())
        if (stored.isNotEmpty()) return stored
        val legacy = legacyPolicy(preferences) ?: return emptyList()
        return listOf(legacy)
    }

    private fun legacyPolicy(preferences: Preferences): StoredPolicy? {
        val packageName = preferences[Keys.baselinePackage].orEmpty().ifBlank {
            preferences[Keys.policyPackage].orEmpty()
        }
        if (!PackageNames.isValid(packageName)) return null
        return StoredPolicy(
            packageName = packageName,
            generation = preferences[Keys.policyGeneration] ?: 0,
            whitelistAlreadyPresent = preferences[Keys.whitelistAlreadyPresent] ?: false,
            whitelistAddedByUs = preferences[Keys.whitelistAddedByUs] ?: false,
            savedStandby = preferences[Keys.savedStandby].orEmpty(),
            standbyChanged = preferences[Keys.standbyChanged] ?: false,
            savedInactive = preferences[Keys.savedInactive].orEmpty(),
            inactiveChanged = preferences[Keys.inactiveChanged] ?: false,
            savedAppOps = PolicyPlan.decodeAppOps(preferences[Keys.savedAppOps].orEmpty()),
            changedAppOps = PolicyPlan.decodeChangedOps(preferences[Keys.changedAppOps].orEmpty()),
        )
    }

    private fun writeLegacyPolicy(preferences: androidx.datastore.preferences.core.MutablePreferences, policy: StoredPolicy) {
        preferences[Keys.baselinePackage] = policy.packageName
        preferences[Keys.savedStandby] = policy.savedStandby
        preferences[Keys.savedInactive] = policy.savedInactive
        preferences[Keys.standbyChanged] = policy.standbyChanged
        preferences[Keys.policyPackage] = policy.packageName
        preferences[Keys.whitelistAlreadyPresent] = policy.whitelistAlreadyPresent
        preferences[Keys.whitelistAddedByUs] = policy.whitelistAddedByUs
        preferences[Keys.inactiveChanged] = policy.inactiveChanged
        preferences[Keys.savedAppOps] = PolicyPlan.encodeAppOps(policy.savedAppOps)
        preferences[Keys.changedAppOps] = PolicyPlan.encodeChangedOps(policy.changedAppOps)
        preferences[Keys.policyGeneration] = policy.generation
    }

    private fun clearLegacyPolicy(preferences: androidx.datastore.preferences.core.MutablePreferences) {
        preferences.remove(Keys.baselinePackage)
        preferences.remove(Keys.savedStandby)
        preferences.remove(Keys.savedInactive)
        preferences.remove(Keys.standbyChanged)
        preferences.remove(Keys.policyPackage)
        preferences.remove(Keys.whitelistAlreadyPresent)
        preferences.remove(Keys.whitelistAddedByUs)
        preferences.remove(Keys.inactiveChanged)
        preferences.remove(Keys.savedAppOps)
        preferences.remove(Keys.changedAppOps)
        preferences.remove(Keys.policyGeneration)
    }

    private fun Preferences.toSettings(): UserSettings {
        val selectedPackage = this[Keys.packageName].orEmpty()
        val selectedLabel = this[Keys.label].orEmpty()
        val automationEnabled = this[Keys.automation] ?: false
        val watched = readWatched(this)
        val policies = readPolicies(this)
        return UserSettings(
        automationEnabled = automationEnabled,
        restoreOnBoot = this[Keys.restoreOnBoot] ?: true,
        setupCompleted = this[Keys.setupCompleted] ?: false,
        languageCode = this[Keys.languageCode].orEmpty(),
        gameBatteryHintDone = this[Keys.gameBatteryHintDone] ?: false,
        selectedPackage = selectedPackage,
        selectedLabel = selectedLabel,
        watched = watched,
        policies = policies,
        pollIntervalMs = (this[Keys.poll] ?: 2_000L).coerceIn(1_500L, 5_000L),
        fastAnims = this[Keys.fastAnims] ?: false,
        savedAnimScales = this[Keys.animScales].orEmpty(),
        drainedApps = this[Keys.drained].orEmpty(),
        disabledByUs = this[Keys.disabled].orEmpty(),
        consentAccepted = this[Keys.consent] ?: false,
        baselinePackage = this[Keys.baselinePackage].orEmpty(),
        savedStandby = this[Keys.savedStandby].orEmpty(),
        savedInactive = this[Keys.savedInactive].orEmpty(),
        standbyChanged = this[Keys.standbyChanged] ?: false,
        policyPackage = this[Keys.policyPackage].orEmpty(),
        whitelistAlreadyPresent = this[Keys.whitelistAlreadyPresent] ?: false,
        whitelistAddedByUs = this[Keys.whitelistAddedByUs] ?: false,
        inactiveChanged = this[Keys.inactiveChanged] ?: false,
        savedAppOps = this[Keys.savedAppOps].orEmpty(),
        changedAppOps = this[Keys.changedAppOps].orEmpty(),
        policyGeneration = this[Keys.policyGeneration] ?: 0,
        )
    }

    private object Keys {
        val automation = booleanPreferencesKey("automation_enabled")
        val restoreOnBoot = booleanPreferencesKey("restore_on_boot")
        val setupCompleted = booleanPreferencesKey("setup_completed")
        val languageCode = stringPreferencesKey("language_code")
        val gameBatteryHintDone = booleanPreferencesKey("game_battery_hint_done")
        val packageName = stringPreferencesKey("selected_package")
        val label = stringPreferencesKey("selected_label")
        val poll = longPreferencesKey("poll_interval_ms")
        val fastAnims = booleanPreferencesKey("tune_fast_anims")
        val animScales = stringPreferencesKey("tune_anim_scales")
        val drained = stringPreferencesKey("tune_drained_apps")
        val disabled = stringPreferencesKey("tune_disabled_apps")
        val consent = booleanPreferencesKey("consent_accepted")
        val baselinePackage = stringPreferencesKey("baseline_package")
        val savedStandby = stringPreferencesKey("saved_standby")
        val savedInactive = stringPreferencesKey("saved_inactive")
        val standbyChanged = booleanPreferencesKey("standby_changed")
        val policyPackage = stringPreferencesKey("policy_package")
        val whitelistAlreadyPresent = booleanPreferencesKey("whitelist_already_present")
        val whitelistAddedByUs = booleanPreferencesKey("whitelist_added_by_us")
        val inactiveChanged = booleanPreferencesKey("inactive_changed")
        val savedAppOps = stringPreferencesKey("saved_app_ops")
        val changedAppOps = stringPreferencesKey("changed_app_ops")
        val policyGeneration = intPreferencesKey("policy_generation")
        val watched = stringPreferencesKey("watched_games")
        val policyBundle = stringPreferencesKey("policy_bundle")
    }

    companion object {
        /** Synchronous-safe read used before any Activity exists (locale wrapping). */
        suspend fun readLanguageCode(context: Context): String =
            SettingsRepository(context).snapshot().languageCode
    }
}
