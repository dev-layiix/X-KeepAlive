package app.xkeepalive.core

import org.junit.Assert.assertEquals
import org.junit.Test

class PolicyRecordsTest {
    @Test
    fun roundTripKeepsEachPackageSeparate() {
        val first = StoredPolicy(
            packageName = "com.example.one",
            generation = PolicyPlan.GENERATION,
            whitelistAlreadyPresent = true,
            whitelistAddedByUs = false,
            savedStandby = "rare",
            standbyChanged = true,
            savedInactive = "true",
            inactiveChanged = true,
            savedAppOps = mapOf("RUN_IN_BACKGROUND" to "ignore"),
            changedAppOps = setOf("RUN_IN_BACKGROUND"),
        )
        val second = first.copy(
            packageName = "com.example.two",
            whitelistAlreadyPresent = false,
            whitelistAddedByUs = true,
            savedStandby = "frequent",
            changedAppOps = setOf("RUN_ANY_IN_BACKGROUND"),
            savedAppOps = mapOf("RUN_ANY_IN_BACKGROUND" to "default"),
        )
        val decoded = PolicyRecords.decode(PolicyRecords.encode(listOf(first, second)))
        assertEquals(listOf(first, second), decoded)
        val replaced = PolicyRecords.upsert(decoded, second.copy(savedStandby = "active"))
        assertEquals("active", replaced.first { it.packageName == second.packageName }.savedStandby)
        assertEquals(1, PolicyRecords.remove(replaced, first.packageName).size)
    }
}
