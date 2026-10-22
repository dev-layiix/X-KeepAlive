package app.xkeepalive.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TunePlanTest {
    @Test
    fun tuneCommandsAreAllowlisted() {
        assertTrue(PolicyCommands.isAllowed("settings get global animator_duration_scale"))
        assertTrue(PolicyCommands.isAllowed("settings put global animator_duration_scale 0.5"))
        assertTrue(PolicyCommands.isAllowed("pm trim-caches 999999999999"))
        assertTrue(PolicyCommands.isAllowed("pm disable-user --user 0 com.example.bloat"))
        assertTrue(PolicyCommands.isAllowed("pm enable com.example.bloat"))
        assertTrue(PolicyCommands.isAllowed("pm list packages -d"))
        assertTrue(PolicyCommands.isAllowed("pm list packages -d com.example.bloat"))
        assertFalse(PolicyCommands.isAllowed("pm list packages -d com.example.bloat extra"))
        assertFalse(PolicyCommands.isAllowed("settings put global animator_duration_scale 0.25"))
        assertFalse(PolicyCommands.isAllowed("settings put global unknown_key 0.5"))
        assertFalse(PolicyCommands.isAllowed("pm disable-user --user 0 com.example.bloat; rm -rf /"))
        assertFalse(PolicyCommands.isAllowed("pm uninstall com.example.bloat"))
    }

    @Test
    fun animStepsOnlyChangeWhatDiffers() {
        val observed = mapOf(
            "window_animation_scale" to "1",
            "transition_animation_scale" to "1",
            "animator_duration_scale" to "0.5",
        )
        val target = PolicyCommands.animScaleKeys.associateWith { "0.5" }
        val steps = TunePlan.animApplySteps(observed, target)
        assertEquals(2, steps.size)
        assertTrue(steps.all { it.action == TuneAction.SET_ANIM_SCALE && it.value == "0.5" })
        assertTrue(TunePlan.animApplySteps(target, target).isEmpty())
    }

    @Test
    fun animRestoreWritesBackSavedValues() {
        val saved = mapOf("window_animation_scale" to "1", "animator_duration_scale" to "0.5")
        val steps = TunePlan.animRestoreSteps(saved)
        assertEquals(2, steps.size)
        assertEquals("1", steps.first { it.extra == "window_animation_scale" }.value)
    }

    @Test
    fun drainRestrictsAndRestores() {
        val steps = TunePlan.drainApplySteps("working_set", "allow", "default")
        assertEquals(
            listOf(TuneAction.SET_STANDBY, TuneAction.SET_APP_OP, TuneAction.SET_APP_OP),
            steps.map { it.action },
        )
        assertTrue(TunePlan.drainApplySteps("restricted", "ignore", "ignore").isEmpty())
        assertTrue(TunePlan.drainApplySteps(null, null, null).isEmpty())

        val saved = TunePlan.captureDrain("com.example.drain", "working_set", "allow", "bogus")
        assertEquals("working_set", saved.savedStandby)
        assertEquals("allow", saved.savedRunInBackground)
        assertEquals("", saved.savedRunAnyInBackground)
        val restore = TunePlan.drainRestoreSteps(saved)
        assertEquals(2, restore.size)
    }

    @Test
    fun encodingRoundTrips() {
        val scales = mapOf("window_animation_scale" to "1.0", "animator_duration_scale" to "0.5")
        assertEquals(scales.mapValues { PolicyOutput.parseAnimScale(it.value) }, TunePlan.decodeScales(TunePlan.encodeScales(scales)))
        val drain = listOf(DrainedApp("com.example.drain", "rare", "default", "allow"))
        assertEquals(drain, TunePlan.decodeDrain(TunePlan.encodeDrain(drain)))
        assertTrue(TunePlan.decodePackages("com.a, bogus!, com.b").containsAll(setOf("com.a", "com.b")))
    }

    @Test
    fun gamesOwnAppAndSystemAreNeverTuneable() {
        val watched = setOf("com.example.game")
        assertFalse(TunePlan.isTuneable("com.example.game", "app.xkeepalive", watched))
        assertFalse(TunePlan.isTuneable("app.xkeepalive", "app.xkeepalive", watched))
        assertFalse(TunePlan.isTuneable("android", "app.xkeepalive", watched))
        assertFalse(TunePlan.isTuneable("com.android.systemui", "app.xkeepalive", watched))
        assertFalse(TunePlan.isTuneable("moe.shizuku.privileged.api", "app.xkeepalive", watched))
        assertFalse(TunePlan.isTuneable("not a package", "app.xkeepalive", watched))
        assertTrue(TunePlan.isTuneable("com.example.bloat", "app.xkeepalive", watched))
    }

    @Test
    fun animScaleParsingNormalizes() {
        assertEquals("1", PolicyOutput.parseAnimScale("1.0"))
        assertEquals("0.5", PolicyOutput.parseAnimScale("0.5"))
        assertEquals(null, PolicyOutput.parseAnimScale("null"))
        assertEquals(null, PolicyOutput.parseAnimScale("fast"))
        assertEquals(
            setOf("com.example.bloat"),
            PolicyOutput.parseDisabledPackages("package:com.example.bloat\npackage:bad pkg!"),
        )
    }
}
