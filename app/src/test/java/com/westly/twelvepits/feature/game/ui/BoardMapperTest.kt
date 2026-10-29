package com.westly.twelvepits.feature.game.ui

import com.westly.twelvepits.core.model.Player
import com.westly.twelvepits.feature.game.engine.GameState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class BoardMapperTest {
    private val perspectives = listOf(Player.ONE, Player.TWO)

    @Test
    fun perspectiveOneIsTheIdentity() {
        val mapper = BoardMapper(Player.ONE)
        for (pit in 0 until GameState.PIT_COUNT) {
            assertEquals(pit, mapper.slotOfPit(pit))
            assertEquals(pit, mapper.pitOfSlot(pit))
        }
        assertEquals(StoreSide.RIGHT, mapper.storeSide(Player.ONE))
        assertEquals(StoreSide.LEFT, mapper.storeSide(Player.TWO))
    }

    @Test
    fun perspectiveTwoDrawsPlayerTwoAtTheBottomWithTheRightStore() {
        val mapper = BoardMapper(Player.TWO)
        for (offset in 0 until GameState.PITS_PER_SIDE) {
            assertEquals(offset, mapper.slotOfPit(GameState.PITS_PER_SIDE + offset))
            assertEquals(GameState.PITS_PER_SIDE + offset, mapper.slotOfPit(offset))
        }
        assertEquals(StoreSide.RIGHT, mapper.storeSide(Player.TWO))
        assertEquals(StoreSide.LEFT, mapper.storeSide(Player.ONE))
    }

    @Test
    fun mappingIsABijectionInBothPerspectives() {
        for (perspective in perspectives) {
            val mapper = BoardMapper(perspective)
            val slots = HashSet<Int>()
            for (pit in 0 until GameState.PIT_COUNT) {
                val slot = mapper.slotOfPit(pit)
                assertTrue(slot in 0 until GameState.PIT_COUNT)
                slots.add(slot)
                assertEquals(pit, mapper.pitOfSlot(slot))
            }
            assertEquals(GameState.PIT_COUNT, slots.size)
            for (slot in 0 until GameState.PIT_COUNT) {
                assertEquals(slot, mapper.slotOfPit(mapper.pitOfSlot(slot)))
            }
        }
    }

    @Test
    fun theNextEnginePitIsAlwaysTheNextAnticlockwiseSlot() {
        for (perspective in perspectives) {
            val mapper = BoardMapper(perspective)
            for (pit in 0 until GameState.PIT_COUNT) {
                val next = (pit + 1) % GameState.PIT_COUNT
                assertEquals(
                    (mapper.slotOfPit(pit) + 1) % GameState.PIT_COUNT,
                    mapper.slotOfPit(next),
                )
            }
        }
    }

    @Test
    fun thePlayerOfThePerspectiveIsInTheBottomRow() {
        for (perspective in perspectives) {
            val mapper = BoardMapper(perspective)
            for (pit in GameState.pitsOf(perspective)) {
                assertTrue(mapper.isBottomRow(mapper.slotOfPit(pit)))
            }
            for (pit in GameState.pitsOf(perspective.opponent)) {
                assertTrue(!mapper.isBottomRow(mapper.slotOfPit(pit)))
            }
        }
    }

    @Test
    fun numbersOutsideTheBoardAreRejected() {
        val mapper = BoardMapper(Player.ONE)
        assertThrows(IllegalArgumentException::class.java) { mapper.slotOfPit(12) }
        assertThrows(IllegalArgumentException::class.java) { mapper.slotOfPit(-1) }
        assertThrows(IllegalArgumentException::class.java) { mapper.pitOfSlot(12) }
    }
}
