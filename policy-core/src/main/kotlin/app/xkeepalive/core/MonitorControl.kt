package app.xkeepalive.core

object MonitorControl {
    /**
     * A stop that already started must not tear the service down when a newer start
     * has turned monitoring back on for at least one app.
     */
    fun keepService(stopRun: Int, currentRun: Int, automationEnabled: Boolean, enabledCount: Int): Boolean {
        if (currentRun == stopRun) return false
        return automationEnabled && enabledCount > 0
    }
}
