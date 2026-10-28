package dev.chesspocket

import com.github.bhlangonijr.chesslib.Board
import com.github.bhlangonijr.chesslib.move.MoveList

const val START_FEN = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1"

data class ChessGame(
    val source: String,
    val id: String,
    val white: String,
    val black: String,
    val whiteElo: String,
    val blackElo: String,
    val result: String,
    val date: String,
    val pgn: String,
    val endTime: Long = 0L
) {
    val title: String get() = "$white  —  $black"
}

data class PlayedGame(
    val game: ChessGame,
    val startFen: String,
    val san: List<String>,
    val uci: List<String>,
    val fens: List<String>
)

data class EngineEvaluation(
    val cpWhite: Int?,
    val mateWhite: Int?,
    val bestMove: String?,
    val depth: Int
) {
    val label: String get() = when {
        mateWhite != null -> "M$mateWhite"
        cpWhite != null -> "%+.2f".format(java.util.Locale.US, cpWhite / 100.0)
        else -> "…"
    }
    fun cpForWhite(): Int = cpWhite ?: when {
        mateWhite == null -> 0
        mateWhite > 0 -> 1500
        else -> -1500
    }
}

data class MoveReview(
    val ply: Int,
    val san: String,
    val category: String,
    val loss: Int,
    val before: EngineEvaluation,
    val after: EngineEvaluation,
    val suggestedUci: String?
)

data class GameReview(val moves: List<MoveReview>) {
    val whiteAccuracy: Int get() = accuracy(0)
    val blackAccuracy: Int get() = accuracy(1)
    private fun accuracy(parity: Int): Int {
        val selected = moves.filter { it.ply % 2 == parity }
        if (selected.isEmpty()) return 100
        // Heuristic score, NOT Chess.com accuracy formula.
        return (selected.map { 100.0 * kotlin.math.exp(-it.loss / 165.0) }
            .average()).toInt().coerceIn(0, 100)
    }
}

object Pgn {
    private val tag = Regex("(?m)^\\[([A-Za-z0-9_]+)\\s+\"((?:\\\\.|[^\"\\\\])*)\"\\]\\s*$")

    fun separate(text: String): List<String> {
        val normalized = text.replace("\r\n", "\n").trim()
        if (normalized.isBlank()) return emptyList()
        val begins = Regex("(?m)^\\[Event\\s+\"").findAll(normalized).map { it.range.first }.toList()
        if (begins.size <= 1) return listOf(normalized)
        return begins.indices.map { i -> normalized.substring(begins[i], begins.getOrElse(i + 1) { normalized.length }).trim() }
    }

    fun headers(pgn: String): Map<String, String> = tag.findAll(pgn).associate { it.groupValues[1] to it.groupValues[2] }

    fun makeGame(pgn: String, source: String, id: String, endTime: Long = 0L): ChessGame {
        val tags = headers(pgn)
        return ChessGame(source, id, tags["White"] ?: "White", tags["Black"] ?: "Black",
            tags["WhiteElo"] ?: "?", tags["BlackElo"] ?: "?", tags["Result"] ?: "*",
            tags["UTCDate"] ?: tags["Date"] ?: "", pgn, endTime)
    }

    fun parse(game: ChessGame): PlayedGame {
        val headers = headers(game.pgn)
        val variant = headers["Variant"].orEmpty()
        require(variant.isBlank() || variant.equals("Standard", true) || variant.equals("Chess", true)) {
            "Пока поддерживаются только стандартные шахматы ($variant)"
        }
        val initial = headers["FEN"] ?: START_FEN
        var body = game.pgn.lineSequence().filterNot { it.trim().startsWith("[") }.joinToString(" ")
        body = body.replace(Regex("\\{[^}]*}", RegexOption.DOT_MATCHES_ALL), " ")
            .replace(Regex(";[^\\n]*"), " ")
            .replace(Regex("\\$\\d+"), " ")
        // Remove nested variations before SAN parsing.
        val noVariations = StringBuilder()
        var nesting = 0
        for (ch in body) {
            if (ch == '(') nesting++
            else if (ch == ')') nesting = (nesting - 1).coerceAtLeast(0)
            else if (nesting == 0) noVariations.append(ch)
        }
        body = noVariations.toString().replace(Regex("\\d+\\.(?:\\.\\.)?"), " ")
            .replace(Regex("(?<!\\S)(1-0|0-1|1/2-1/2|\\*)(?!\\S)"), " ")
            .replace(Regex("[!?]+"), "")
            .trim()
        require(body.isNotBlank()) { "В PGN нет ходов" }
        val moveList = MoveList(initial)
        moveList.loadFromSan(body)
        val sanMoves = moveList.toSanArray().toList()
        val board = Board()
        board.loadFromFen(initial)
        val allFens = mutableListOf(board.fen)
        val ucis = mutableListOf<String>()
        for (move in moveList) {
            ucis += move.toString()
            require(board.doMove(move)) { "Нелегальный ход: $move" }
            allFens += board.fen
        }
        return PlayedGame(game, initial, sanMoves, ucis, allFens)
    }
}

object ReviewClassifier {
    private fun material(fen: String, white: Boolean): Int {
        val values = mapOf('p' to 1, 'n' to 3, 'b' to 3, 'r' to 5, 'q' to 9)
        return fen.substringBefore(' ').sumOf { ch ->
            if (ch.isLetter() && (if (white) ch.isUpperCase() else ch.isLowerCase()))
                values[ch.lowercaseChar()] ?: 0
            else 0
        }
    }

    // Verify a sacrifice on the opponent's actual reply. This deliberately avoids
    // declaring every engine-best move brilliant. It remains a *candidate* heuristic.
    fun isSacrificeCandidate(prior: MoveReview, beforeMove: String, afterReply: String, playerWhite: Boolean,
                             finalEvaluation: EngineEvaluation): Boolean {
        if (prior.category != "Лучший") return false
        val ourLoss = material(beforeMove, playerWhite) - material(afterReply, playerWhite)
        val theirLoss = material(beforeMove, !playerWhite) - material(afterReply, !playerWhite)
        val sign = if (playerWhite) 1 else -1
        val stable = (prior.before.cpForWhite() - finalEvaluation.cpForWhite()) * sign <= 45
        return ourLoss >= 3 && ourLoss - theirLoss >= 2 && stable
    }
    fun describe(item: MoveReview): String = when (item.category) {
        "Блестящий 💎*" -> "Вероятная качественная жертва материала при сохранении оценки. Это экспериментальная эвристика, а не оценка Chess.com."
        "Лучший" -> "Ты нашёл главный ход Stockfish на выбранной глубине."
        "Отличный" -> "Ход почти не меняет оценку позиции."
        "Хороший" -> "Ход играбельный, хотя движок предпочёл другой вариант."
        "Неточность" -> "Можно было сохранить чуть больше преимущества или шансов на ничью."
        "Ошибка" -> "Позиция заметно ухудшилась. Посмотри альтернативу движка."
        "Зевок" -> "Серьёзная потеря оценки: здесь стоит проверить тактический вариант."
        else -> "Оценка рассчитана локальным шахматным движком."
    }
    fun classify(before: EngineEvaluation, after: EngineEvaluation, isWhite: Boolean, played: String): Pair<String, Int> {
        val sign = if (isWhite) 1 else -1
        val loss = ((before.cpForWhite() - after.cpForWhite()) * sign).coerceAtLeast(0)
        val label = when {
            played.equals(before.bestMove, ignoreCase = true) -> "Лучший"
            loss <= 15 -> "Отличный"
            loss <= 45 -> "Хороший"
            loss <= 110 -> "Неточность"
            loss <= 250 -> "Ошибка"
            else -> "Зевок"
        }
        return label to loss
    }
}
