package dev.chesspocket

import org.junit.Assert.*
import org.junit.Test

class PgnTest {
    private val sample = """
        [Event "Local test"]
        [White "S1mclick"]
        [Black "Opponent"]
        [Result "1-0"]

        1. e4 e5 2. Nf3 Nc6 3. Bc4 Bc5 1-0
    """.trimIndent()

    @Test fun parseOpening() {
        val game = Pgn.parse(Pgn.makeGame(sample, "PGN", "test"))
        assertEquals("S1mclick", game.game.white)
        assertEquals(6, game.uci.size)
        assertEquals("e2e4", game.uci[0])
        assertEquals("e7e5", game.uci[1])
        assertEquals(7, game.fens.size)
    }

    @Test fun separateMultiple() {
        assertEquals(2, Pgn.separate("$sample\n\n$sample").size)
    }

    @Test fun classifyMove() {
        val before = EngineEvaluation(35, null, "e2e4", 10)
        val after = EngineEvaluation(24, null, null, 10)
        assertEquals("Лучший", ReviewClassifier.classify(before, after, true, "e2e4").first)
        assertEquals(11, ReviewClassifier.classify(before, after, true, "d2d4").second)
    }

    @Test fun variationAndComments() {
        val complex = sample.replace("1. e4 e5", "1. e4 {opening} (1. d4 d5) e5")
        assertEquals(6, Pgn.parse(Pgn.makeGame(complex, "PGN", "test2")).uci.size)
    }
}
