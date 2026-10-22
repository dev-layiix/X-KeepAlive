package app.xkeepalive.core

data class WatchedGame(
    val packageName: String,
    val label: String,
    val enabled: Boolean,
)

object WatchedGames {
    const val MAX_ACTIVE = 4
    const val MAX_LIST = 40

    fun activeCount(games: List<WatchedGame>): Int = games.count { it.enabled }

    fun canEnable(games: List<WatchedGame>, packageName: String): Boolean {
        val game = games.firstOrNull { it.packageName == packageName } ?: return false
        if (game.enabled) return true
        return activeCount(games) < MAX_ACTIVE
    }

    fun decode(raw: String): List<WatchedGame> {
        if (raw.isBlank()) return emptyList()
        return raw.lineSequence().mapNotNull { line ->
            val parts = line.split('\t')
            if (parts.size < 3) return@mapNotNull null
            val packageName = parts[0]
            if (!PackageNames.isValid(packageName)) return@mapNotNull null
            WatchedGame(
                packageName = packageName,
                label = parts[1].ifBlank { packageName },
                enabled = parts[2] == "1",
            )
        }.distinctBy { it.packageName }.take(MAX_LIST).toList()
    }

    fun encode(games: List<WatchedGame>): String =
        games.take(MAX_LIST).joinToString("\n") { game ->
            val label = game.label.replace('\t', ' ').replace('\n', ' ').replace('\r', ' ').ifBlank { game.packageName }
            "${game.packageName}\t$label\t${if (game.enabled) "1" else "0"}"
        }

    fun add(games: List<WatchedGame>, game: WatchedGame): List<WatchedGame> {
        if (!PackageNames.isValid(game.packageName)) return games
        if (games.any { it.packageName == game.packageName }) return games
        if (games.size >= MAX_LIST) return games
        return games + game.copy(label = game.label.ifBlank { game.packageName }, enabled = false)
    }

    fun setEnabled(games: List<WatchedGame>, packageName: String, enabled: Boolean): List<WatchedGame> {
        if (enabled && !canEnable(games, packageName)) return games
        return games.map { game -> if (game.packageName == packageName) game.copy(enabled = enabled) else game }
    }

    fun remove(games: List<WatchedGame>, packageName: String): List<WatchedGame> =
        games.filterNot { it.packageName == packageName }
}
