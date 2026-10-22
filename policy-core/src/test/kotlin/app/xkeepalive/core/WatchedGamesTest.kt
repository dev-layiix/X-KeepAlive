package app.xkeepalive.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WatchedGamesTest {
    private fun game(index: Int, enabled: Boolean = false) =
        WatchedGame("com.example.game$index", "Game $index", enabled)

    @Test
    fun listCanGrowPastFourWhileOnlyFourStayActive() {
        var games = emptyList<WatchedGame>()
        repeat(6) { games = WatchedGames.add(games, game(it)) }
        assertEquals(6, games.size)
        repeat(4) { index -> games = WatchedGames.setEnabled(games, "com.example.game$index", true) }
        assertEquals(4, WatchedGames.activeCount(games))
        assertFalse(WatchedGames.canEnable(games, "com.example.game4"))
        assertEquals(4, WatchedGames.activeCount(WatchedGames.setEnabled(games, "com.example.game4", true)))
        games = WatchedGames.setEnabled(games, "com.example.game0", false)
        assertTrue(WatchedGames.canEnable(games, "com.example.game4"))
    }

    @Test
    fun encodeRoundTripKeepsMoreThanFourGames() {
        val games = List(5) { game(it, enabled = it < 2) }
        assertEquals(games, WatchedGames.decode(WatchedGames.encode(games)))
    }
}
