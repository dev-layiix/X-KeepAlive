package app.xkeepalive.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TuneAdviceTest {
    private val watched = setOf("com.example.game")

    @Test
    fun gamesOwnAppCriticalAndHandledAreSilent() {
        assertNull(TuneAdvisor.advise("com.example.game", false, 0, null, "app.xkeepalive", watched, false))
        assertNull(TuneAdvisor.advise("app.xkeepalive", false, 0, null, "app.xkeepalive", watched, false))
        assertNull(TuneAdvisor.advise("com.android.systemui", true, 0, null, "app.xkeepalive", watched, false))
        assertNull(TuneAdvisor.advise("com.example.bloat", true, 0, null, "app.xkeepalive", watched, true))
    }

    @Test
    fun neverUsedMeansDisable() {
        val preinstalled = TuneAdvisor.advise("com.example.bloat", true, 0, null, "app.xkeepalive", watched, false)
        assertEquals(TuneAdviceKind.DISABLE, preinstalled?.kind)
        assertEquals(TuneReason.NEVER_USED_PREINSTALLED, preinstalled?.reason)
        val user = TuneAdvisor.advise("com.example.old", false, 0, 45, "app.xkeepalive", watched, false)
        assertEquals(TuneAdviceKind.DISABLE, user?.kind)
        assertEquals(TuneReason.NEVER_USED, user?.reason)
    }

    @Test
    fun rarelyUsedMeansRestrict() {
        val light = TuneAdvisor.advise("com.example.food", false, 5, 10, "app.xkeepalive", watched, false)
        assertEquals(TuneAdviceKind.RESTRICT, light?.kind)
        assertEquals(TuneReason.RARELY_USED, light?.reason)
    }

    @Test
    fun dailyAppsAreLeftAlone() {
        assertNull(TuneAdvisor.advise("com.example.chat", false, 120, 0, "app.xkeepalive", watched, false))
        assertNull(TuneAdvisor.advise("com.example.mail", false, 60, 1, "app.xkeepalive", watched, false))
    }
}
