package com.westly.twelvepits.feature.menu

import com.westly.twelvepits.core.model.AvatarId
import com.westly.twelvepits.core.model.Difficulty
import com.westly.twelvepits.core.model.GameMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SetupRulesTest {
    @Test
    fun nameIsTrimmed() {
        assertEquals("Ada", finalName("  Ada  ", "You"))
    }

    @Test
    fun nameIsCutToTwelveCharacters() {
        assertEquals("ABCDEFGHIJKL", finalName("ABCDEFGHIJKLMNOP", "You"))
        assertEquals(MAX_NAME_LENGTH, limitTypedName("ABCDEFGHIJKLMNOP").length)
    }

    @Test
    fun emptyNameFallsBackToDefault() {
        assertEquals("You", finalName("", "You"))
        assertEquals("Player 2", finalName("     ", "Player 2"))
    }

    @Test
    fun leopardIsNeverSelectable() {
        assertTrue(AvatarId.LEOPARD !in SelectableAvatars)
        assertEquals(AvatarId.OBA, selectableOrDefault(AvatarId.LEOPARD, AvatarId.OBA))
        assertEquals(AvatarId.OBA, selectableOrDefault(null, AvatarId.OBA))
        assertEquals(AvatarId.WARRIOR, selectableOrDefault(AvatarId.WARRIOR, AvatarId.OBA))
    }

    @Test
    fun twoPlayersNeverShareAnAvatar() {
        for (first in SelectableAvatars) {
            for (second in SelectableAvatars) {
                assertNotEquals(first, resolveAvatarClash(first, second))
            }
        }
        assertEquals(AvatarId.IYOBA, resolveAvatarClash(AvatarId.OBA, AvatarId.IYOBA))
    }

    @Test
    fun computerGameUsesLeopardAndKeepsDifficulty() {
        val config = computerGameConfig("Ada", AvatarId.WARRIOR, Difficulty.HARD, "Computer")
        assertEquals(GameMode.VS_COMPUTER, config.mode)
        assertEquals(Difficulty.HARD, config.difficulty)
        assertEquals("Ada", config.player1Name)
        assertEquals("Computer", config.player2Name)
        assertEquals(AvatarId.LEOPARD, config.player2Avatar)
    }

    @Test
    fun twoPlayerGameKeepsBothChoices() {
        val config = twoPlayerGameConfig("Ada", AvatarId.OBA, "Bayo", AvatarId.IYOBA)
        assertEquals(GameMode.TWO_PLAYERS_ONE_PHONE, config.mode)
        assertEquals("Ada", config.player1Name)
        assertEquals(AvatarId.IYOBA, config.player2Avatar)
    }
}
