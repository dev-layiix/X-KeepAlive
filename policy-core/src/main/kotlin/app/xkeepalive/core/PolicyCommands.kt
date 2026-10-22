package app.xkeepalive.core

object PolicyCommands {
    val standbyBuckets = setOf("active", "working_set", "frequent", "rare", "restricted", "never")
    val appOps = setOf("RUN_IN_BACKGROUND", "RUN_ANY_IN_BACKGROUND")
    val appOpModes = setOf("allow", "ignore", "deny", "default")

    /**
     * Phone tuning without touching games: only documented settings/pm
     * commands. Animation scales are constrained to 0 (off), 0.5 (fast) and
     * 1 (default); anything else is rejected before it reaches a shell.
     */
    val animScaleKeys = setOf(
        "window_animation_scale",
        "transition_animation_scale",
        "animator_duration_scale",
    )
    val animScaleValues = setOf("0", "0.5", "1")
    const val FAST_ANIM_VALUE = "0.5"
    const val DRAIN_BUCKET = "restricted"
    const val DRAIN_APP_OP_MODE = "ignore"

    fun whitelistAdd(packageName: String): String =
        "cmd deviceidle whitelist +${requirePackage(packageName)}"

    fun whitelistRemove(packageName: String): String =
        "cmd deviceidle whitelist -${requirePackage(packageName)}"

    fun whitelistList(): String = "cmd deviceidle whitelist"

    fun dumpsysWhitelistAdd(packageName: String): String =
        "dumpsys deviceidle whitelist +${requirePackage(packageName)}"

    fun setStandby(packageName: String, bucket: String): String {
        require(bucket in standbyBuckets) { "Standby bucket not allowed" }
        return "am set-standby-bucket ${requirePackage(packageName)} $bucket"
    }

    fun getStandby(packageName: String): String =
        "am get-standby-bucket ${requirePackage(packageName)}"

    fun setInactive(packageName: String, inactive: Boolean): String =
        "am set-inactive ${requirePackage(packageName)} ${if (inactive) "true" else "false"}"

    fun getInactive(packageName: String): String =
        "am get-inactive ${requirePackage(packageName)}"

    fun setAppOp(packageName: String, op: String, mode: String): String {
        require(op in appOps) { "App op not allowed" }
        require(mode in appOpModes) { "App op mode not allowed" }
        return "cmd appops set ${requirePackage(packageName)} $op $mode"
    }

    fun getAppOp(packageName: String, op: String): String {
        require(op in appOps) { "App op not allowed" }
        return "cmd appops get ${requirePackage(packageName)} $op"
    }

    fun pidof(packageName: String): String = "pidof ${requirePackage(packageName)}"

    fun psAll(): String = "ps -A"

    fun getAnimScale(key: String): String {
        require(key in animScaleKeys) { "Animation scale not allowed" }
        return "settings get global $key"
    }

    fun setAnimScale(key: String, value: String): String {
        require(key in animScaleKeys) { "Animation scale not allowed" }
        require(value in animScaleValues) { "Animation scale value not allowed" }
        return "settings put global $key $value"
    }

    fun trimCaches(): String = "pm trim-caches 999999999999"

    fun disablePackage(packageName: String): String =
        "pm disable-user --user 0 ${requirePackage(packageName)}"

    fun enablePackage(packageName: String): String =
        "pm enable ${requirePackage(packageName)}"

    fun listDisabledPackages(): String = "pm list packages -d"

    fun isDisabledPackage(packageName: String): String =
        "pm list packages -d ${requirePackage(packageName)}"

    fun isAllowed(command: String): Boolean {
        if (command.any { it.code < 32 || it in ";|&`$<>\\\"'()" }) return false
        if (command == whitelistList() || command == psAll()) return true
        val parts = command.split(' ')
        return when {
            parts.size == 4 &&
                parts[0] == "cmd" &&
                parts[1] == "deviceidle" &&
                parts[2] == "whitelist" &&
                (parts[3].startsWith("+") || parts[3].startsWith("-")) &&
                PackageNames.isValid(parts[3].drop(1)) -> true

            parts.size == 4 &&
                parts[0] == "dumpsys" &&
                parts[1] == "deviceidle" &&
                parts[2] == "whitelist" &&
                parts[3].startsWith("+") &&
                PackageNames.isValid(parts[3].drop(1)) -> true

            parts.size == 4 &&
                parts[0] == "am" &&
                parts[1] == "set-standby-bucket" &&
                PackageNames.isValid(parts[2]) &&
                parts[3] in standbyBuckets -> true

            parts.size == 3 &&
                parts[0] == "am" &&
                parts[1] == "get-standby-bucket" &&
                PackageNames.isValid(parts[2]) -> true

            parts.size == 4 &&
                parts[0] == "am" &&
                parts[1] == "set-inactive" &&
                PackageNames.isValid(parts[2]) &&
                parts[3] in setOf("true", "false") -> true

            parts.size == 3 &&
                parts[0] == "am" &&
                parts[1] == "get-inactive" &&
                PackageNames.isValid(parts[2]) -> true

            parts.size == 6 &&
                parts[0] == "cmd" &&
                parts[1] == "appops" &&
                parts[2] == "set" &&
                PackageNames.isValid(parts[3]) &&
                parts[4] in appOps &&
                parts[5] in appOpModes -> true

            parts.size == 5 &&
                parts[0] == "cmd" &&
                parts[1] == "appops" &&
                parts[2] == "get" &&
                PackageNames.isValid(parts[3]) &&
                parts[4] in appOps -> true

            parts.size == 2 &&
                parts[0] == "pidof" &&
                PackageNames.isValid(parts[1]) -> true

            parts.size == 4 &&
                parts[0] == "settings" &&
                parts[1] == "get" &&
                parts[2] == "global" &&
                parts[3] in animScaleKeys -> true

            parts.size == 5 &&
                parts[0] == "settings" &&
                parts[1] == "put" &&
                parts[2] == "global" &&
                parts[3] in animScaleKeys &&
                parts[4] in animScaleValues -> true

            parts.size == 3 &&
                parts[0] == "pm" &&
                parts[1] == "trim-caches" &&
                parts[2].all { it.isDigit() } -> true

            parts.size == 5 &&
                parts[0] == "pm" &&
                parts[1] == "disable-user" &&
                parts[2] == "--user" &&
                parts[3] == "0" &&
                PackageNames.isValid(parts[4]) -> true

            parts.size == 3 &&
                parts[0] == "pm" &&
                parts[1] == "enable" &&
                PackageNames.isValid(parts[2]) -> true

            parts.size == 4 &&
                parts[0] == "pm" &&
                parts[1] == "list" &&
                parts[2] == "packages" &&
                parts[3] == "-d" -> true

            parts.size == 5 &&
                parts[0] == "pm" &&
                parts[1] == "list" &&
                parts[2] == "packages" &&
                parts[3] == "-d" &&
                PackageNames.isValid(parts[4]) -> true

            else -> false
        }
    }

    private fun requirePackage(packageName: String): String {
        require(PackageNames.isValid(packageName)) { "Invalid package name" }
        return packageName
    }
}
