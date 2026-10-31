package com.westly.twelvepits.feature.game.multiplayer

import com.westly.twelvepits.core.model.AvatarId
import org.junit.Assert.assertEquals
import org.junit.Test

class EndpointNamesTest {
    @Test
    fun theAvatarAndTheNameSurviveTheTrip() {
        for (avatar in listOf(AvatarId.KING, AvatarId.QUEEN, AvatarId.WARRIOR)) {
            val player = RemotePlayer("Ada", avatar)
            assertEquals(player, EndpointNames.decode(EndpointNames.encode(player)))
        }
    }

    @Test
    fun aNameWithTheSeparatorKeepsItsText() {
        val player = RemotePlayer("A|B", AvatarId.QUEEN)
        assertEquals(player, EndpointNames.decode(EndpointNames.encode(player)))
    }

    @Test
    fun aPlainNameFromAnotherSourceStillGivesAPlayer() {
        assertEquals(RemotePlayer("Bola", AvatarId.KING), EndpointNames.decode("Bola"))
        assertEquals(RemotePlayer("?", AvatarId.KING), EndpointNames.decode(""))
        assertEquals(RemotePlayer("?", AvatarId.KING), EndpointNames.decode(null))
    }

    @Test
    fun aBadAvatarFallsBackAndAnOverlongNameIsCut() {
        assertEquals(RemotePlayer("DRAGON|Ada", AvatarId.KING), EndpointNames.decode("DRAGON|Ada"))
        assertEquals(RemotePlayer("LEOPARD|Ada", AvatarId.KING), EndpointNames.decode("LEOPARD|Ada"))
        assertEquals("A".repeat(12), EndpointNames.decode("KING|" + "A".repeat(40)).name)
    }
}
