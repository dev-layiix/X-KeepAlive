package app.xkeepalive.core

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MonitorControlTest {
    @Test
    fun sameRunStopsEvenIfAppsStayEnabled() {
        assertFalse(MonitorControl.keepService(4, 4, automationEnabled = true, enabledCount = 2))
    }

    @Test
    fun newerStartKeepsTheServiceWhenAnAppIsEnabled() {
        assertTrue(MonitorControl.keepService(4, 5, automationEnabled = true, enabledCount = 1))
    }

    @Test
    fun newerRunStillStopsWhenMonitoringWasTurnedOff() {
        assertFalse(MonitorControl.keepService(4, 5, automationEnabled = false, enabledCount = 1))
        assertFalse(MonitorControl.keepService(4, 5, automationEnabled = true, enabledCount = 0))
    }
}
