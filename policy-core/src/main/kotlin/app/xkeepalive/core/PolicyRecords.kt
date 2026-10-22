package app.xkeepalive.core

object PolicyRecords {
    private const val RECORD = '\u001e'
    private const val FIELD = '\u001f'

    fun encode(policies: List<StoredPolicy>): String =
        policies.filter { PackageNames.isValid(it.packageName) }
            .distinctBy { it.packageName }
            .joinToString(RECORD.toString()) { policy ->
                listOf(
                    policy.packageName,
                    policy.generation.toString(),
                    bit(policy.whitelistAlreadyPresent),
                    bit(policy.whitelistAddedByUs),
                    policy.savedStandby,
                    bit(policy.standbyChanged),
                    policy.savedInactive,
                    bit(policy.inactiveChanged),
                    PolicyPlan.encodeAppOps(policy.savedAppOps),
                    PolicyPlan.encodeChangedOps(policy.changedAppOps),
                ).joinToString(FIELD.toString())
            }

    fun decode(raw: String): List<StoredPolicy> {
        if (raw.isBlank()) return emptyList()
        return raw.split(RECORD).mapNotNull { record ->
            val parts = record.split(FIELD)
            if (parts.size != 10) return@mapNotNull null
            val packageName = parts[0]
            if (!PackageNames.isValid(packageName)) return@mapNotNull null
            StoredPolicy(
                packageName = packageName,
                generation = parts[1].toIntOrNull() ?: 0,
                whitelistAlreadyPresent = parts[2] == "1",
                whitelistAddedByUs = parts[3] == "1",
                savedStandby = parts[4],
                standbyChanged = parts[5] == "1",
                savedInactive = parts[6],
                inactiveChanged = parts[7] == "1",
                savedAppOps = PolicyPlan.decodeAppOps(parts[8]),
                changedAppOps = PolicyPlan.decodeChangedOps(parts[9]),
            )
        }.distinctBy { it.packageName }
    }

    fun upsert(policies: List<StoredPolicy>, policy: StoredPolicy): List<StoredPolicy> {
        if (!PackageNames.isValid(policy.packageName)) return policies
        return policies.filterNot { it.packageName == policy.packageName } + policy
    }

    fun remove(policies: List<StoredPolicy>, packageName: String): List<StoredPolicy> =
        policies.filterNot { it.packageName == packageName }

    private fun bit(value: Boolean): String = if (value) "1" else "0"
}
